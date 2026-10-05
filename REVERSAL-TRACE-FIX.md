# Missing 0400 in the receipt trace — version 2.0.1

## Confirmed source defect

`FindCardActivity.addPacketTrace()` printed only the original `HostResult.requestDump` and `responseDump`.
Both recovery paths discarded the recovery result's packet data:

- `OnlineHost.reverseNowIfQueued()` retained an acknowledgement flag and error text, but not the actual recovery exchange.
- The host-approved/card-declined path retained only the reversal's `approved` flag.

Consequently, a receipt could show only **0200/0210 even when the app attempted 0400**. This explains missing receipt evidence; it does not establish what happened at the bank for a particular transaction.

## Changes

- Retain the actual recovery `HostResult` with its originating transaction.
- Render sale request/response followed by reversal request/response in the packet trace.
- Distinguish acknowledged reversal, attempted write awaiting acknowledgement, and reversal not sent.
- Do not invent a 0410 when no response arrived.
- Record the reversal decision in the debug trace and device log.
- Show `REVERSING TRANSACTION...` during recovery after chip completion failure.
- Increase app version to **2.0.1**, version code **25**, to make the installed build identifiable.

The receipt packet trace still honours the existing Debug/packet-trace setting.
It captures the immediate reversal attempt; later background retries remain in the device log.

## Expected behavior

| Transaction result | Reversal behavior |
| --- | --- |
| Host approves and transaction completes | No reversal required; 0200/0210 is expected. |
| Host returns a normal valid decline | No reversal required. |
| Request write is attempted but no valid transaction response arrives | Recovery attempts 0400. |
| Host approves, but the contact chip fails completion | Recovery attempts 0400; the receipt now includes that attempt's trace. |
| Recovery receives valid 0410 / code 00 | Show reversal acknowledged. |
| Recovery cannot connect or receives no valid acknowledgement | Show not-sent/pending outcome and retain recovery data. |

## Verification and remaining evidence

Regression tests in `ReversalTraceTest` cover packet ordering, unchanged original host verdict, unsent recovery, pending recovery, untrusted acknowledgement and absence of invented reversal packets for a completed transaction.

Build and tests passed: **17 tests, 0 failures/errors**. Build/test output: `reversal-trace-build.log`.

An AF980 was initially visible over ADB, then disconnected before log capture. No current device transaction log or installed build version could be obtained. No live bank transaction or forced reversal was initiated. To diagnose an absent 0400 in **bank-side logs**, reconnect the terminal and capture the failing test transaction's host response code and GAC2 outcome using version 2.0.1.
