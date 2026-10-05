package com.hjq.demo.ans8583;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Plain-TCP host link for ISO-8583 messages.
 *
 * <p>Frames every message with a 2-byte big-endian total-length header
 * (matching the DBBL/PAX host convention): {@code [len_hi][len_lo][iso...]}.
 * A fresh socket is opened per exchange (connect &rarr; send &rarr; receive
 * &rarr; close), which is the simplest reliable model for a demo terminal.</p>
 */
public final class HostComm {

    private HostComm() { }

    /**
     * Send one ISO message and read one response.
     *
     * @return the response ISO bytes (without the 2-byte length header)
     * @throws IOException on connect/timeout/socket failure
     */
    public static byte[] exchange(String ip, int port, int timeoutMs, byte[] iso) throws IOException {
        return exchange(ip, port, timeoutMs, iso, null);
    }

    /**
     * Send one ISO message and read one response, reporting whether the request
     * actually reached the wire.
     *
     * <p>A connection failure cannot deliver the request. A write failure is
     * uncertain because the peer may already have received bytes, so recovery
     * is required from the beginning of the write.</p>
     *
     * @param sent optional one-element array, set to true the moment the
     *     request write begins (including a partial or failed write)
     */
    public static byte[] exchange(String ip, int port, int timeoutMs, byte[] iso,
                                  boolean[] sent) throws IOException {
        if (sent != null && sent.length > 0) sent[0] = false;
        Socket sock = new Socket();
        try {
            sock.connect(new InetSocketAddress(ip, port), timeoutMs);
            sock.setSoTimeout(timeoutMs);
            sock.setTcpNoDelay(true);

            OutputStream os = sock.getOutputStream();
            InputStream is = sock.getInputStream();

            byte[] send = new byte[iso.length + 2];
            send[0] = (byte) ((iso.length >> 8) & 0xFF);
            send[1] = (byte) (iso.length & 0xFF);
            System.arraycopy(iso, 0, send, 2, iso.length);
            // A failed write can still deliver part or all of the frame. Once
            // writing starts, absence of a reply is an uncertain transaction.
            if (sent != null && sent.length > 0) {
                sent[0] = true;
            }
            os.write(send);
            os.flush();

            int hi = readOne(is);
            int lo = readOne(is);
            int len = ((hi & 0xFF) << 8) | (lo & 0xFF);
            if (len <= 0 || len > 8192) {
                throw new IOException("bad response length " + len);
            }
            byte[] body = new byte[len];
            int off = 0;
            while (off < len) {
                int r = is.read(body, off, len - off);
                if (r < 0) {
                    throw new EOFException("host closed while reading body");
                }
                off += r;
            }
            return body;
        } finally {
            try {
                sock.close();
            } catch (Exception ignore) {
            }
        }
    }

    private static int readOne(InputStream is) throws IOException {
        int b = is.read();
        if (b < 0) {
            throw new EOFException("host closed");
        }
        return b;
    }
}
