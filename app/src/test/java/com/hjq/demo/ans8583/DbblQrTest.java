package com.hjq.demo.ans8583;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import com.google.zxing.*;
import com.google.zxing.common.*;

public class DbblQrTest {
    private String payload(long amount) {
        return DbblQr.payload("000591860513100", "A6008667", "4111", "Agora", "Dhaka", "009",
                amount, "001245", "125478", "A600866720260927123456123");
    }
    private Map<String,String> fields(String data) {
        Map<String,String> out = new LinkedHashMap<>();
        for (int i = 0; i < data.length();) {
            String id = data.substring(i, i + 2);
            int size = Integer.parseInt(data.substring(i + 2, i + 4));
            assertFalse("duplicate tag " + id, out.containsKey(id));
            out.put(id, data.substring(i + 4, i + 4 + size)); i += size + 4;
        }
        return out;
    }
    @Test public void crcMatchesIndependentCcittCheckVector() { assertEquals("29B1", DbblQr.crc("123456789")); }
    @Test public void payloadCarriesAmountMerchantAndSharedUniqueReference() {
        String payload = payload(12500);
        Map<String,String> root = fields(payload);
        assertEquals("01", root.get("00")); assertEquals("12", root.get("01"));
        assertEquals("000591860513100", fields(root.get("44")).get("01"));
        assertEquals("com.konasl.konapay.nexuspay", fields(root.get("44")).get("00"));
        assertEquals("125.00", root.get("54")); assertEquals("050", root.get("53"));
        assertEquals("4111", root.get("52")); assertEquals("BD", root.get("58"));
        assertEquals("001245", fields(root.get("62")).get("01"));
        assertEquals("A6008667", fields(root.get("62")).get("07"));
        assertEquals(fields(root.get("62")).get("05"), fields(root.get("88")).get("02"));
        assertEquals("125478", fields(root.get("88")).get("01"));
        assertEquals("009", fields(root.get("88")).get("03"));
        assertEquals(DbblQr.crc(payload.substring(0, payload.length()-4)), root.get("63"));
        assertNotEquals(payload, payload(12600));
    }
    @Test public void renderedQrRoundTripsThroughScanner() throws Exception {
        String data = payload(12000);
        Map<EncodeHintType,Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8"); hints.put(EncodeHintType.MARGIN, 4);
        BitMatrix matrix = new MultiFormatWriter().encode(data, BarcodeFormat.QR_CODE, 780, 780, hints);
        int[] pixels = new int[780 * 780];
        for (int y = 0; y < 780; y++) for (int x = 0; x < 780; x++) pixels[y*780+x] = matrix.get(x,y) ? 0xff000000 : 0xffffffff;
        assertEquals(data, new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(780, 780, pixels)))).getText());
    }
    @Test public void invalidAmountsAndMissingMerchantSettingsDoNotGenerateQr() {
        assertThrows(IllegalArgumentException.class, () -> payload(0));
        assertThrows(IllegalArgumentException.class, () -> payload(-1));
        assertThrows(IllegalArgumentException.class, () -> payload(1000000000000L));
        assertThrows(IllegalArgumentException.class, () -> DbblQr.validateSettings("", "6000050000", "4111", "Dhaka", "Shop"));
        assertThrows(IllegalArgumentException.class, () -> DbblQr.validateSettings("009", "6000050000", "", "Dhaka", "Shop"));
    }
    @Test public void payloadCarriesStoreIdWhenProvided() {
        String payload = DbblQr.payload("000591860513100", "A6008667", "4111", "Agora", "Dhaka", "009",
                12500, "001245", "125478", "A600866720260927123456123", "STORE-001");
        Map<String,String> root = fields(payload);
        Map<String,String> tag62 = fields(root.get("62"));
        assertEquals("001245", tag62.get("01"));
        assertEquals("STORE-001", tag62.get("03"));
        assertEquals("A6008667", tag62.get("07"));
    }
}
