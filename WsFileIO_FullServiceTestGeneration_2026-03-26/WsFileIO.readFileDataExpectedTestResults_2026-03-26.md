# WsFileIO — `readFileData` Expected Test Results

**Generated:** 2026-03-26  
**Service:** WsFileIO  
**Method:** `readFileData`  
**Java Test Class:** `WSFileIOClientTest`

---

## Core Functionality Tests (CFT)

### CFT-001 — `testReadFileData_partialReadNonZeroOffset`

| Field | Value |
|---|---|
| **Label** | CFT-001-readFileData |
| **Expected Outcome** | PASS |
| **Expected Response** | Non-null String of exactly 10 bytes; content equals bytes 5–14 of the test string (i.e., `" MY DARLIN"`) |
| **Assertion** | `response != null`; `response.getBytes(ISO_8859_1).length == 10`; content equals `new String(data, 5, 10, ISO_8859_1)` |
| **Failure Modes** | Setup fails (LZ/file not configured) → test skipped via `Assume`; wrong byte range returned → `AssertionError` |

---

### CFT-002 — `testReadFileData_dataSizeLargerThanRemainingBytes`

| Field | Value |
|---|---|
| **Label** | CFT-002-readFileData |
| **Expected Outcome** | PASS |
| **Expected Response** | Non-null String; length equals `fileSize - 60`; content matches the tail of the test string starting at index 60 |
| **Assertion** | `response != null`; `response.getBytes(ISO_8859_1).length == data.length - 60`; content equals `new String(data, 60, data.length - 60, ISO_8859_1)` |
| **Failure Modes** | Server throws instead of clamping → `Exception` causes test failure; wrong content → `AssertionError` |

---

### CFT-003 — `testReadFileData_exactFileSizeFromOffset0`

| Field | Value |
|---|---|
| **Label** | CFT-003-readFileData |
| **Expected Outcome** | PASS |
| **Expected Response** | Non-null String; full file content returned; length equals total file size |
| **Assertion** | `response != null`; `response.getBytes(UTF_8).length == data.length`; `Arrays.equals(data, response.getBytes(UTF_8))` |
| **Failure Modes** | Truncation or padding → length mismatch; byte content difference → `assertArrayEquals` failure |

---

### CFT-004 — `testReadFileData_sequentialChunkedReads`

| Field | Value |
|---|---|
| **Label** | CFT-004-readFileData |
| **Expected Outcome** | PASS |
| **Expected Response** | Three non-null Strings; concatenation of all three equals original file content |
| **Assertion** | `part1 != null && part2 != null && part3 != null`; `Arrays.equals(data, (part1+part2+part3).getBytes(ISO_8859_1))` |
| **Failure Modes** | Any chunk returns null → assertion fails; data overlaps or gaps at chunk boundaries → concatenation mismatch |

---

### CFT-005 — `testReadFileData_largeFileRead`

| Field | Value |
|---|---|
| **Label** | CFT-005-readFileData |
| **Expected Outcome** | PASS |
| **Expected Response** | Non-null String of exactly 1,048,576 bytes |
| **Assertion** | `response != null`; `response.getBytes(ISO_8859_1).length == 1048576` |
| **Failure Modes** | Timeout/network limit exceeded → `Exception`; response truncated → length mismatch; large file setup fails → test skipped |

---

## Edge Case Tests (ECT)

### ECT-001 — `testReadFileData_dataSizeOfOne`

| Field | Value |
|---|---|
| **Label** | ECT-001-readFileData |
| **Expected Outcome** | PASS |
| **Expected Response** | Non-null String of exactly 1 byte with value `"H"` |
| **Assertion** | `response != null`; `response.getBytes(ISO_8859_1).length == 1`; `response.equals("H")` |
| **Failure Modes** | Server rejects dataSize=1 → unexpected exception; wrong byte returned → `assertEquals` failure |

---

### ECT-002 — `testReadFileData_offsetAtLastValidPosition`

| Field | Value |
|---|---|
| **Label** | ECT-002-readFileData |
| **Expected Outcome** | PASS |
| **Expected Response** | Non-null String of exactly 1 byte; content equals the last character of the test string |
| **Assertion** | `response != null`; `response.getBytes(ISO_8859_1).length == 1`; content equals `new String(data, data.length-1, 1, ISO_8859_1)` |
| **Failure Modes** | Server incorrectly treats `offset = fileSize - 1` as invalid → exception; wrong byte returned → `assertEquals` failure |

---

### ECT-003 — `testReadFileData_dropzoneAddressExplicitBaremetal`

| Field | Value |
|---|---|
| **Label** | ECT-003-readFileData |
| **Expected Outcome** | PASS (baremetal only; skipped on containerized) |
| **Expected Response** | Non-null String; content matches full test data |
| **Assertion** | `response != null`; `Arrays.equals(data, response.getBytes(UTF_8))` |
| **Failure Modes** | Skipped if `isContainerized == true`; wrong content → `assertArrayEquals` failure |

---

### ECT-004 — `testReadFileData_dropzoneAddressNullContainerized`

| Field | Value |
|---|---|
| **Label** | ECT-004-readFileData |
| **Expected Outcome** | PASS (containerized only; skipped on baremetal) |
| **Expected Response** | Non-null String; content matches full test data |
| **Assertion** | `response != null`; `Arrays.equals(data, response.getBytes(UTF_8))` |
| **Failure Modes** | Skipped if `isContainerized == false`; DestNetAddress accidentally sent → server may reject |

---

### ECT-005 — `testReadFileData_dropzoneAddressEmptyString`

| Field | Value |
|---|---|
| **Label** | ECT-005-readFileData |
| **Expected Outcome** | PASS (containerized only; skipped on baremetal) |
| **Expected Response** | Non-null String; content matches full test data |
| **Assertion** | `response != null`; `Arrays.equals(data, response.getBytes(UTF_8))` |
| **Failure Modes** | Skipped if `isContainerized == false`; client sets empty DestNetAddress on wire → server may reject |

---

### ECT-006 — `testReadFileData_binaryFileRoundTrip`

| Field | Value |
|---|---|
| **Label** | ECT-006-readFileData |
| **Expected Outcome** | PASS |
| **Expected Response** | Non-null String of 128 bytes; `response.getBytes(ISO_8859_1)` equals the original 128 bytes (values 0x00–0x7F) |
| **Assertion** | `response != null`; `response.getBytes(ISO_8859_1).length == 128`; `Arrays.equals(originalBytes, responseBytes)` |
| **Failure Modes** | DataHandler encoding corrupts bytes → `assertArrayEquals` failure; bytes 0x00 stripped by JVM charset → length mismatch |

---

## Error Handling Tests (EHT)

### EHT-001 — `testReadFileData_emptyDropzone`

| Field | Value |
|---|---|
| **Label** | EHT-001-readFileData |
| **Expected Outcome** | PASS |
| **Expected Server Response** | `Result = "Destination not specified"` |
| **Expected Java Behavior** | Exception thrown; message contains `"Destination not specified"` |
| **Assertion** | `fail()` if no exception; exception message contains expected substring |
| **Failure Modes** | Server silently ignores empty dropzone → `fail()` called; exception type differs (caught generically) |

---

### EHT-002 — `testReadFileData_nullDropzone`

| Field | Value |
|---|---|
| **Label** | EHT-002-readFileData |
| **Expected Outcome** | PASS |
| **Expected Behavior** | Any exception is thrown; method does not silently return |
| **Assertion** | `fail()` if no exception is thrown |
| **Failure Modes** | Server or client silently returns null → `fail()` called |

---

### EHT-003 — `testReadFileData_emptyFileName`

| Field | Value |
|---|---|
| **Label** | EHT-003-readFileData |
| **Expected Outcome** | PASS |
| **Expected Server Response** | `Result = "Destination path not specified"` |
| **Expected Java Behavior** | Exception thrown; message contains `"Destination path not specified"` |
| **Assertion** | `fail()` if no exception; message contains expected substring |
| **Failure Modes** | Server uses targetLZ that isn't accessible → different error surfaced first |

---

### EHT-004 — `testReadFileData_dataSizeZero`

| Field | Value |
|---|---|
| **Label** | EHT-004-readFileData |
| **Expected Outcome** | PASS |
| **Expected Server Response** | `Result = "Invalid data size."` |
| **Expected Java Behavior** | Exception thrown; message contains `"Invalid data size."` |
| **Assertion** | `fail()` if no exception; message contains `"Invalid data size."` |
| **Failure Modes** | Test file setup fails → skipped via `Assume`; server skips dataSize validation → unexpected success |

---

### EHT-005 — `testReadFileData_dataSizeNegative`

| Field | Value |
|---|---|
| **Label** | EHT-005-readFileData |
| **Expected Outcome** | PASS |
| **Expected Server Response** | `Result = "Invalid data size."` |
| **Expected Java Behavior** | Exception thrown; message contains `"Invalid data size."` |
| **Assertion** | `fail()` if no exception; message contains `"Invalid data size."` |
| **Failure Modes** | Java long → server int64 negative value not transmitted correctly → unexpected success |

---

### EHT-006 — `testReadFileData_negativeOffset`

| Field | Value |
|---|---|
| **Label** | EHT-006-readFileData |
| **Expected Outcome** | PASS |
| **Expected Server Response** | `Result = "Invalid offset."` |
| **Expected Java Behavior** | Exception thrown; message contains `"Invalid offset."` |
| **Assertion** | `fail()` if no exception; message contains `"Invalid offset."` |
| **Failure Modes** | Negative long treated as large unsigned value → different error path |

---

### EHT-007 — `testReadFileData_offsetEqualsFileSize`

| Field | Value |
|---|---|
| **Label** | EHT-007-readFileData |
| **Expected Outcome** | PASS |
| **Expected Server Response** | `Result = "Invalid offset: file size = N."` |
| **Expected Java Behavior** | Exception thrown; message contains `"Invalid offset:"` |
| **Assertion** | `fail()` if no exception; message contains `"Invalid offset:"` |
| **Failure Modes** | Off-by-one in server — treats `offset == fileSize` as valid → test fails |

---

### EHT-008 — `testReadFileData_offsetBeyondFileSize`

| Field | Value |
|---|---|
| **Label** | EHT-008-readFileData |
| **Expected Outcome** | PASS |
| **Expected Server Response** | `Result = "Invalid offset: file size = N."` |
| **Expected Java Behavior** | Exception thrown; message contains `"Invalid offset:"` |
| **Assertion** | `fail()` if no exception; message contains `"Invalid offset:"` |
| **Failure Modes** | Test file setup fails → skipped via `Assume` |

---

### EHT-009 — `testReadFileData_nonExistentFile`

| Field | Value |
|---|---|
| **Label** | EHT-009-readFileData |
| **Expected Outcome** | PASS |
| **Expected Server Response** | `Result = "<path> does not exist."` |
| **Expected Java Behavior** | Exception thrown; message contains `"does not exist."` |
| **Assertion** | `fail()` if no exception; message contains `"does not exist."` |
| **Failure Modes** | Filename accidentally exists on LZ (unlikely with timestamp) → unexpected success |

---

### EHT-010 — `testReadFileData_invalidDropzoneName`

| Field | Value |
|---|---|
| **Label** | EHT-010-readFileData |
| **Expected Outcome** | PASS |
| **Expected Behavior** | Any exception is thrown during `validateDropZoneAccess` |
| **Assertion** | `fail()` if no exception is thrown |
| **Failure Modes** | Exact error message varies by HPCC version; test accepts any exception |

---

## Connectivity Tests (CNT)

### CNT-001 — `testReadFileData_connectivity`

| Field | Value |
|---|---|
| **Label** | CNT-001-readFileData |
| **Expected Outcome** | PASS |
| **Expected Behavior** | Any non-transport-level response (success or server-side error); no `ConnectException` or `UnknownHostException` |
| **Assertion** | Test fails only if a transport/connectivity exception (`Connection refused`, `ConnectException`, `UnknownHost`) is thrown |
| **Failure Modes** | Service endpoint unreachable → transport exception → test fails |

---

### CNT-002 — `testReadFileData_invalidCredentialsRejected`

| Field | Value |
|---|---|
| **Label** | CNT-002-readFileData |
| **Expected Outcome** | PASS (secure clusters only; skipped if authentication is not enforced) |
| **Expected Behavior** | Exception thrown or null returned; data is not served to unauthenticated caller |
| **Assertion** | `assertNull` if no exception; exception message logged |
| **Failure Modes** | Cluster does not enforce auth → test skipped via `Assume` |

---

### CNT-003 — `testReadFileData_emptyCredentialsRejected`

| Field | Value |
|---|---|
| **Label** | CNT-003-readFileData |
| **Expected Outcome** | PASS (secure clusters only; skipped if authentication is not enforced) |
| **Expected Behavior** | Exception thrown or null returned; data is not served to empty-credential caller |
| **Assertion** | `assertNull` if no exception; exception message logged |
| **Failure Modes** | Cluster does not enforce auth → test skipped via `Assume` |

---

## Summary

| Test Method | Category | Expected Outcome | Requires File Setup |
|---|---|---|---|
| `testReadFileData_partialReadNonZeroOffset` | CFT | PASS | Yes |
| `testReadFileData_dataSizeLargerThanRemainingBytes` | CFT | PASS | Yes |
| `testReadFileData_exactFileSizeFromOffset0` | CFT | PASS | Yes |
| `testReadFileData_sequentialChunkedReads` | CFT | PASS | Yes |
| `testReadFileData_largeFileRead` | CFT | PASS | Yes (1 MB) |
| `testReadFileData_dataSizeOfOne` | ECT | PASS | Yes |
| `testReadFileData_offsetAtLastValidPosition` | ECT | PASS | Yes |
| `testReadFileData_dropzoneAddressExplicitBaremetal` | ECT | PASS (baremetal) | Yes |
| `testReadFileData_dropzoneAddressNullContainerized` | ECT | PASS (containerized) | Yes |
| `testReadFileData_dropzoneAddressEmptyString` | ECT | PASS (containerized) | Yes |
| `testReadFileData_binaryFileRoundTrip` | ECT | PASS | Yes (128 bytes) |
| `testReadFileData_emptyDropzone` | EHT | PASS (exception) | No |
| `testReadFileData_nullDropzone` | EHT | PASS (exception) | No |
| `testReadFileData_emptyFileName` | EHT | PASS (exception) | No |
| `testReadFileData_dataSizeZero` | EHT | PASS (exception) | Yes |
| `testReadFileData_dataSizeNegative` | EHT | PASS (exception) | Yes |
| `testReadFileData_negativeOffset` | EHT | PASS (exception) | Yes |
| `testReadFileData_offsetEqualsFileSize` | EHT | PASS (exception) | Yes |
| `testReadFileData_offsetBeyondFileSize` | EHT | PASS (exception) | Yes |
| `testReadFileData_nonExistentFile` | EHT | PASS (exception) | No |
| `testReadFileData_invalidDropzoneName` | EHT | PASS (exception) | No |
| `testReadFileData_connectivity` | CNT | PASS | Yes |
| `testReadFileData_invalidCredentialsRejected` | CNT | PASS/SKIP | Conditional |
| `testReadFileData_emptyCredentialsRejected` | CNT | PASS/SKIP | Conditional |

---
*Generated: 2026-03-26*
