# POS TMS integration — initial implementation

`TmsManifestClient` implements HTTPS download, response size/time limits, bank-signature verification, terminal targeting, expiry/revision checks and atomic staging in app-private storage. It does not contact a server until called with a provisioned endpoint and public verification key. It does not change transaction settings or install APKs.

## Proposed contract for bank review

GET the provisioned HTTPS manifest endpoint with `X-Terminal-Id`. Response: JSON containing `payload` (Base64-encoded UTF-8 JSON) and `signature` (Base64 RSA/SHA-256 signature over the exact decoded payload bytes).

Payload fields: `schemaVersion: 1`, `terminalId`, monotonically increasing `revision`, Unix timestamps `issuedAt` and `expiresAt`, and a `parameters` object. This is a proposed contract, not an assumption about an existing DBBL TMS service.

## Needed from the TMS provider

- Test/production endpoints and enrollment/authentication protocol (including any client certificate).
- Pinned bank verification public key and key-rotation procedure.
- Approved parameter schema, AID/CAPK distribution format, and firmware/APK update protocol.
- Heartbeat, inventory, acknowledgment, rollback and audit requirements.

## Next implementation stage

Add the provisioned enrollment flow and admin status page. Validate every allowed parameter before activation, then apply an entire revision atomically only when no transaction, unsettled recovery, reader or printer operation is active. Keep the previous configuration for rollback and acknowledge the committed revision. Recheck signature and expiry when activating the staged file. Never distribute or log clear PIN keys, PAN, track data or PIN blocks through this channel. APK updates require separate signature/version verification and the vendor installer API.

No live server, enrollment credential or trust key was supplied, so enrollment, automatic synchronization and deployment remain disabled.
