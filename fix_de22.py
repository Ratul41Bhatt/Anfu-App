# -*- coding: utf-8 -*-
"""
DE22 fix: contactless (NFC) must be sent as 0071, not 0910.

Evidence: DBBL's own NexusPay reference terminal (TID 59001174) sends
    22 | 00 71
for an approved contactless SALE, while our app was sending
    22 | 09 10
which the host answered with response code 57.

'0910' = entry mode 91 (contactless MAGSTRIPE) + position 3 '0' (no PIN entry
capability).  That contradicts the DE55 EMV chip data we send in the same
message.  '0071' = entry mode 07 (contactless ICC / chip) + position 3 '1'
(terminal has a PIN pad).

Run from the project root:  python fix_de22.py
"""
import io, os, sys

SRC = os.path.join("app", "src", "main", "java", "com", "hjq", "demo",
                   "ans8583", "OnlineHost.java")

EDITS = [
    (
        "    public enum EntryMode { UNSPECIFIED, MANUAL, MAGSTRIPE, ICC, FALLBACK, QR, CLESS_MSD }",
        "    public enum EntryMode { UNSPECIFIED, MANUAL, MAGSTRIPE, ICC, FALLBACK, QR,\n"
        "                            CLESS_ICC, CLESS_MSD }",
    ),
    (
        "            case 4:  return EntryMode.CLESS_MSD; // RF_CARD - contactless EMV (DE22 '0910' matching DBBL host)",
        "            case 4:  return EntryMode.CLESS_ICC; // RF_CARD - contactless EMV chip, DE22 '0071'",
    ),
    (
        '            case CLESS_MSD: pan = "91"; break;',
        "            // Contactless EMV chip. DBBL's own NexusPay reference terminal\n"
        "            // sends DE22 '0071' for an approved contactless sale: entry mode\n"
        "            // 07 (contactless ICC) with position 3 = '1' (the terminal has PIN\n"
        "            // entry capability - it is a capability flag, not 'a PIN was\n"
        "            // entered'). Sending '0910' (contactless MAGSTRIPE, no PIN\n"
        "            // capability) while the same message carries DE55 chip data is\n"
        "            // contradictory and the host answers response code 57.\n"
        '            case CLESS_ICC: return "071";\n'
        '            case CLESS_MSD: pan = "91"; break;',
    ),
]


def main():
    if not os.path.isfile(SRC):
        print("ERROR: cannot find " + SRC)
        print("Run this script from the project root (the folder with build.gradle).")
        return 1

    with io.open(SRC, "r", encoding="utf-8", errors="strict") as f:
        text = f.read()

    if "case CLESS_ICC:" in text:
        print("Already patched - nothing to do.")
        return 0

    for old, new in EDITS:
        n = text.count(old)
        if n != 1:
            print("ERROR: anchor found %d times (expected 1):" % n)
            print("  " + old.strip()[:90])
            print("No changes were written.")
            return 2
        text = text.replace(old, new, 1)

    with io.open(SRC + ".de22.bak", "w", encoding="utf-8", newline="") as f:
        with io.open(SRC, "r", encoding="utf-8") as g:
            f.write(g.read())

    with io.open(SRC, "w", encoding="utf-8", newline="") as f:
        f.write(text)

    print("Patched " + SRC)
    print("Backup written to " + SRC + ".de22.bak")
    print("")
    print("Contactless sales now send DE22 = 0071 (was 0910).")
    print("Rebuild and install the app.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
