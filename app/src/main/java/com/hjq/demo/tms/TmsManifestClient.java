package com.hjq.demo.tms;

import android.content.Context;
import android.util.AtomicFile;
import android.util.Base64;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import javax.net.ssl.HttpsURLConnection;

/** First TMS stage: authenticated, bounded download to a private staging file. No automatic activation. */
public final class TmsManifestClient {
    private static final int MAX_BYTES = 128 * 1024;
    private TmsManifestClient() { }

    /** Worker-thread API. Endpoint and verification key must come from bank provisioning. */
    public static JSONObject downloadAndStage(Context context, URL endpoint, String terminalId,
                                               PublicKey verificationKey, long appliedRevision) throws Exception {
        if (!"https".equalsIgnoreCase(endpoint.getProtocol()) || endpoint.getUserInfo() != null)
            throw new IllegalArgumentException("TMS requires an HTTPS endpoint without embedded credentials");
        if (verificationKey == null || terminalId == null || !terminalId.matches("[A-Za-z0-9]{8}"))
            throw new IllegalArgumentException("TMS terminal identity and bank verification key are required");
        HttpsURLConnection connection = (HttpsURLConnection) endpoint.openConnection();
        connection.setConnectTimeout(15000); connection.setReadTimeout(15000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("X-Terminal-Id", terminalId);
        byte[] envelopeBytes;
        try {
            if (connection.getResponseCode() != 200) throw new IllegalStateException("TMS HTTP " + connection.getResponseCode());
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096]; int size;
                while ((size = input.read(buffer)) != -1) {
                    if (output.size() + size > MAX_BYTES) throw new IllegalArgumentException("TMS manifest exceeds size limit");
                    output.write(buffer, 0, size);
                }
                envelopeBytes = output.toByteArray();
            }
        } finally { connection.disconnect(); }
        JSONObject envelope = new JSONObject(new String(envelopeBytes, StandardCharsets.UTF_8));
        byte[] payload = Base64.decode(envelope.getString("payload"), Base64.DEFAULT);
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(verificationKey); signature.update(payload);
        if (!signature.verify(Base64.decode(envelope.getString("signature"), Base64.DEFAULT)))
            throw new SecurityException("Invalid bank TMS signature");
        JSONObject manifest = new JSONObject(new String(payload, StandardCharsets.UTF_8));
        long now = System.currentTimeMillis() / 1000L;
        if (manifest.getInt("schemaVersion") != 1 || !terminalId.equals(manifest.getString("terminalId")))
            throw new SecurityException("TMS schema or terminal identity mismatch");
        if (manifest.getLong("revision") <= appliedRevision)
            throw new SecurityException("TMS revision is not newer than the applied profile");
        if (manifest.getLong("expiresAt") <= now || manifest.getLong("issuedAt") > now + 300)
            throw new SecurityException("TMS manifest expired or terminal clock is incorrect");
        manifest.getJSONObject("parameters");
        AtomicFile staging = new AtomicFile(new File(context.getFilesDir(), "tms-staged-manifest.json"));
        FileOutputStream output = null;
        try {
            output = staging.startWrite(); output.write(envelopeBytes); staging.finishWrite(output);
        } catch (Exception e) {
            if (output != null) staging.failWrite(output);
            throw e;
        }
        return manifest;
    }
}
