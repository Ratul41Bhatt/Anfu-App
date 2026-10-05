package com.hjq.demo.ans8583;

import org.junit.Assert;
import org.junit.Test;

public class KonaMoneyTest {

    @Test public void phoneNfcRequiresKonaAidAndHceAccountParameter() {
        String phone = "4000001234567892D26120001122331239999";
        Assert.assertTrue(KonaMoney.isPhoneNfc(KonaMoney.AID, phone));
        Assert.assertTrue(KonaMoney.isPhoneNfc(KonaMoney.AID.toLowerCase(), phone + "F"));
        Assert.assertFalse(KonaMoney.isPhoneNfc(KonaMoney.AID, phone.replace("112233", "FFFFFF")));
        Assert.assertFalse(KonaMoney.isPhoneNfc("A0000000031010", phone));
        Assert.assertFalse(KonaMoney.isPhoneNfc(null, phone));
        Assert.assertFalse(KonaMoney.isPhoneNfc(KonaMoney.AID, null));
        Assert.assertFalse(KonaMoney.isPhoneNfc(KonaMoney.AID, "malformed"));
    }

    @Test public void onlyPhoneTapSelectsNexusPayEntryMode() {
        String phone = "4000001234567892D26120001122331239999";
        Assert.assertEquals(OnlineHost.EntryMode.NEXUSPAY_PHONE_NFC,
                OnlineHost.entryModeFor(4, false, KonaMoney.AID, phone));
        Assert.assertEquals(OnlineHost.EntryMode.CLESS_ICC,
                OnlineHost.entryModeFor(4, false, KonaMoney.AID, phone.replace("112233", "FFFFFF")));
        Assert.assertEquals(OnlineHost.EntryMode.ICC,
                OnlineHost.entryModeFor(2, false, KonaMoney.AID, phone));
        Assert.assertEquals(OnlineHost.EntryMode.FALLBACK,
                OnlineHost.entryModeFor(1, true, KonaMoney.AID, phone));
        Assert.assertEquals("NFC", com.hjq.demo.utils.DetailReport.entryLabel("910"));
    }

    @Test
    public void testTrack2ParsingValid() {
        // Standard KonaMoney 37-digit track-2
        // PAN(16) + 'D' + Expiry(4) + ServiceCode(3) + AcctParam(6) + dCVV(3) + ATC(4)
        String raw = "4000001234567892D26120001122331239999";
        KonaMoney.Track2 t2 = KonaMoney.parseTrack2(raw);

        Assert.assertTrue(t2.ok());
        Assert.assertEquals("4000001234567892", t2.pan);
        Assert.assertEquals("2612", t2.expiry);
        Assert.assertEquals("000", t2.serviceCode);
        Assert.assertEquals("112233", t2.accountParamIndex);
        Assert.assertEquals("123", t2.dcvv);
        Assert.assertEquals("9999", t2.atc);
    }

    @Test
    public void testQrParsingValid() {
        // 40 digits: 37 digits of Track2 + 3 digits of dCVV_MD
        String qr = "4000001234567892D26120001122331239999789";
        KonaMoney.Qr q = KonaMoney.parseQr(qr);

        Assert.assertTrue(q.ok());
        Assert.assertEquals("4000001234567892D26120001122331239999", q.track2);
        Assert.assertEquals("789", q.dcvvMd);
        Assert.assertEquals("4000001234567892", q.parsed.pan);
    }

    @Test
    public void testAidParameters() {
        String contactAid = KonaMoney.aidParamContact();
        String contactlessAid = KonaMoney.aidParamContactless();

        Assert.assertTrue(contactAid.contains("A0000002514025260010"));
        Assert.assertTrue(contactAid.contains("9F08020020")); // Application Version Number 0020
        Assert.assertTrue(contactlessAid.contains("9F6604B6000000")); // Contactless TTQ
    }
}
