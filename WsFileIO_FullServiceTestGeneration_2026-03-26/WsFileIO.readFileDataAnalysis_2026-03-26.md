# WsFileIO — `readFileData` Test Case Analysis

**Generated:** 2026-03-26  
**Service:** WsFileIO  
**Method:** `readFileData`  
**Java Client Class:** `HPCCWsFileIOClient`  
**Test File:** `WSFileIOClientTest.java`

---

## 1. Method Summary

### Purpose
`readFileData` reads a contiguous block of binary data from a file on an HPCC landing zone dropzone. It enables clients to retrieve file content either entirely or in chunks (by specifying an offset and data size).

### Role Within the Service
WsFileIO is a low-level file I/O service that exposes three operations — `CreateFile`, `WriteFileData`, and `ReadFileData` — for managing files on HPCC landing zones. `readFileData` is the read counterpart to `writeFileData`, completing the full read/write lifecycle for dropzone files.

### Java Method Signature
```java
public String readFileData(String dropzone, String fileName, long datasize,
                           long offset, String dropzoneAddress)
        throws Exception, ArrayOfEspExceptionWrapper
```

**Deprecated overload (delegates to the above):**
```java
public String readFileData(String dropzone, String fileName, long datasize, long offset)
        throws Exception, ArrayOfEspExceptionWrapper
```

### Inputs

| Parameter | Java Type | Maps to Request Field | Description |
|---|---|---|---|
| `dropzone` | `String` | `DestDropZone` | Name or IP address of the landing zone dropzone |
| `fileName` | `String` | `DestRelativePath` | Relative (or absolute) path to the file |
| `datasize` | `long` | `DataSize` | Number of bytes to read |
| `offset` | `long` | `Offset` | Byte offset from the start of the file |
| `dropzoneAddress` | `String` | `DestNetAddress` | Optional network address of the dropzone host |

### Outputs
- **Returns:** `String` — the raw file bytes decoded as a string via `DataHandler`. Returns `null` if the response data handler is null.
- **Side effects:** None (read-only operation).
- **Throws:** `Exception` (wrapping `RemoteException`), `ArrayOfEspExceptionWrapper` (ESP-level exceptions), `EspSoapFault` (server-reported error in `result` field).

---

## 2. Existing Test Coverage Analysis

**Test file:** `wsclient/src/test/java/org/hpccsystems/ws/client/WSFileIOClientTest.java`

### Existing Test Methods for `readFileData`

| Existing Test Method Name | Test Category | Scenario Covered | Input Data Summary | Pass Criteria | Notes |
|---|---|---|---|---|---|
| `CreadHPCCFile()` | CFT | Basic read — full file from offset 0 | dropzone=`targetLZ`, file=`testfilename` (pre-created by `BwriteHPCCFile`), datasize=66 bytes, offset=0; dropzoneAddress=null (containerized) or `targetLZAddress` | Response is not null; `response.getBytes()` equals the 66-byte test string `HELLO MY DARLING, HELLO MY DEAR!1234567890ABCDEFGHIJKLMNOPQRSTUVXYZ` | Depends on `AcreateHPCCFile` and `BwriteHPCCFile` running first; tests both containerized and non-containerized |

### Coverage Summary

- **Total existing test methods for `readFileData`:** 1
- **Core Functionality Tests covered:** 1 — full file read from offset 0 with known data, response content verified byte-for-byte
- **Edge Case Tests covered:** 0
- **Error Handling Tests covered:** 0
- **Connectivity Tests covered:** 0 (connectivity is partially covered by the `ping()` test in the same class, but not specific to `readFileData`)

### Gaps Identified

The following scenarios are **not** covered by existing tests:

1. **Partial read with non-zero offset** — reading a middle segment of a file
2. **Read with datasize larger than remaining bytes** — server should clamp to available data
3. **Exact file size read** — requesting precisely the file's total byte count
4. **Zero `dataSize`** — server returns "Invalid data size."
5. **Negative `dataSize`** — server returns "Invalid data size."
6. **Negative `offset`** — server returns "Invalid offset."
7. **Offset equal to file size** — server returns "Invalid offset: file size = N."
8. **Offset beyond file size** — server returns "Invalid offset: file size = N."
9. **Null/empty `dropzone`** — server returns "Destination not specified"
10. **Null/empty `fileName`** — server returns "Destination path not specified"
11. **Non-existent file** — server returns "\<path\> does not exist."
12. **`dropzoneAddress` provided explicitly** — path with non-null network address (non-containerized only)
13. **Large data read** — reading megabytes in a single call
14. **Sequential chunked reads** — simulate paging through a file in multiple calls
15. **Invalid authentication** — request with bad credentials should be rejected
16. **`readFileData` connectivity** — minimal valid call confirming the endpoint is reachable

---

## 3. Request Structure

### Request Fields

| Field Name | Type | Required | Description | Valid Range / Format | Notes |
|---|---|---|---|---|---|
| `DestDropZone` | `string` | Yes | Name or IP address of the landing zone | Non-empty string | Returns "Destination not specified" if empty |
| `DestRelativePath` | `string` | Yes | File path (relative or absolute) on the dropzone | Non-empty string | Supports both relative and absolute paths despite the name |
| `DestNetAddress` | `string` | No | Network address of the dropzone host | Valid IP or hostname | Introduced in service version 1.01; omit for containerized clusters |
| `Offset` | `int64` | No (default: 0) | Byte offset to start reading from | `>= 0` and `< file size` | Returns "Invalid offset." if negative; returns "Invalid offset: file size = N." if >= file size |
| `DataSize` | `int64` | Yes | Maximum number of bytes to read | `>= 1` | Returns "Invalid data size." if `< 1`; server clamps to available bytes if larger than `file size - offset` |

### Field Dependencies
- `DestNetAddress` is optional in containerized environments (dropzone name alone is sufficient).  
- `DataSize` controls the ceiling of data returned; the actual bytes returned = `min(DataSize, fileSize - Offset)`.
- `Offset` must be strictly less than the file size; an `Offset` equal to or beyond the end of file returns an error.

### Default Behavior for Optional Fields
- **`Offset`**: Defaults to `0` (start of file) when not set.
- **`DestNetAddress`**: When omitted or empty, the Java client skips setting it on the request object.

---

## 4. Server Behavior and Responses

### Processing Logic (from `ws_fileioservice.cpp`)

1. **Authorization:** `context.ensureFeatureAccess(FILE_IO_URL, SecAccess_Read, ...)` — rejects unauthorized requests immediately.
2. **Input validation:**
   - Empty `DestDropZone` → sets `result = "Destination not specified"`, returns `true`.
   - Empty `DestRelativePath` → sets `result = "Destination path not specified"`, returns `true`.
   - `DataSize < 1` → sets `result = "Invalid data size."`, returns `true`.
   - `Offset < 0` → sets `result = "Invalid offset."`, returns `true`.
3. **Drop zone access check:** `validateDropZoneAccess(...)` — validates the requester has read access to the given path.
4. **File existence check:** `file->isFile() != foundYes` → sets `result = "<path> does not exist."`.
5. **Offset bounds check:** `offset >= size` → sets `result = "Invalid offset: file size = N."`.
6. **Read:** Reads `min(dataSize, fileSize - offset)` bytes from `offset` into a `MemoryBuffer`.
7. **Success:** Sets `result = "ReadFileData done."`, populates `Data`.
8. **Failure:** If `io->read(...)` returns wrong byte count, sets `result = "ReadFileData error."`.

### Response Fields (echoed from request + computed)

| Field | Type | Description |
|---|---|---|
| `Data` | `binary` (DataHandler) | The read bytes; null on validation/error paths |
| `DestDropZone` | `string` | Echoed from request |
| `DestRelativePath` | `string` | Echoed from request |
| `Offset` | `int64` | Echoed from request |
| `DataSize` | `int64` | Echoed from request |
| `Result` | `string` | Status message: `"ReadFileData done."`, `"ReadFileData error."`, or a validation error string |
| `Exceptions` | `ArrayOfEspException` | Populated on ESP-level errors |

### Java Client Post-Processing
- If `resp.getResult()` is non-empty and not `"ReadFileData done."` → throws `EspSoapFault` with the result message.
- If `resp.getData()` is null → returns `null`.
- Otherwise, writes the `DataHandler` to a `ByteArrayOutputStream` and returns `output.toString()`.

---

## 5. Error Handling

### Server-Side Errors

| Condition | Server Response (`result` field) | Java Client Behavior |
|---|---|---|
| `DestDropZone` empty | `"Destination not specified"` | Throws `EspSoapFault` |
| `DestRelativePath` empty | `"Destination path not specified"` | Throws `EspSoapFault` |
| `DataSize < 1` | `"Invalid data size."` | Throws `EspSoapFault` |
| `Offset < 0` | `"Invalid offset."` | Throws `EspSoapFault` |
| `Offset >= fileSize` | `"Invalid offset: file size = N."` | Throws `EspSoapFault` |
| File not found | `"<path> does not exist."` | Throws `EspSoapFault` |
| Read I/O failure | `"ReadFileData error."` | Throws `EspSoapFault` |
| Insufficient permissions | ESP exception / SOAP fault | `handleEspExceptions` / `handleEspSoapFaults` |

### Client-Side Errors

| Condition | Java Exception |
|---|---|
| Network / transport failure | `Exception` wrapping `RemoteException` |
| ESP SOAP fault | `EspSoapFault` via `handleEspSoapFaults` |
| ESP exceptions in response | `ArrayOfEspExceptionWrapper` via `handleEspExceptions` |
| Bad credentials | ESP exception (HTTP 401 or ESP auth error) |

---

## 6. Existing Dataset Analysis

> **Note:** The benchmark datasets on the HPCC cluster are logical datasets (HPCC Distributed File System), not files on a landing zone dropzone. `readFileData` reads **landing zone files** (physical files on a dropzone server), not logical HPCC datasets. Therefore none of the benchmark datasets are directly readable via this API.  
> The existing test creates its own landing zone file via `CreateFile` + `WriteFileData`, and this pattern must be followed for all `readFileData` tests.

| Dataset Name | Applicable? | Reason |
|---|---|---|
| `~benchmark::all_types::200KB` | No | Logical HPCC dataset; not a landing zone file; cannot be read by `readFileData` |
| `~benchmark::string::100MB` | No | Logical HPCC dataset; same reason as above |
| `~benchmark::integer::20KB` | No | Logical HPCC dataset; same reason as above |
| `~benchmark::all_types::superfile` | No | Superfile (logical); not accessible as a landing zone file |
| `~benchmark::integer::20kb::key` | No | Index file (logical); not accessible as a landing zone file |

**All test cases must set up their own landing zone files** by calling `CreateFile` + `WriteFileData` as a precondition, then clean up with file deletion after the test.

---

## 7. Test Case Plan

> All scenarios below are gaps **not** covered by the existing `CreadHPCCFile()` test.

---

### A. Core Functionality Tests

---

#### CFT-001 — Partial Read with Non-Zero Offset

| Field | Value |
|---|---|
| **Test ID** | CFT-001 |
| **Category** | Core Functionality |
| **Subcategory** | Data Variations |
| **Description** | Read a subset of file data starting from a non-zero offset |
| **Environment Requirements** | `any` |
| **Input Data** | `dropzone`: `targetLZ`, `fileName`: `testfilename`, `datasize`: 10, `offset`: 5, `dropzoneAddress`: null (containerized) or `targetLZAddress` |
| **Dataset** | `[LANDING ZONE FILE]` — Use file created by `CreateFile` + `WriteFileData` with content `"HELLO MY DARLING, HELLO MY DEAR!1234567890ABCDEFGHIJKLMNOPQRSTUVXYZ"` (66 bytes) |
| **Expected Output** | Response string equals bytes 5–14 of the test data: `"MY DAR"` → actually characters at index 5 for 10 bytes: `"MY DARLIN"` → `"MY DARLIN"` (10 bytes) |
| **Pass Criteria** | Response is not null; `response.getBytes().length == 10`; content equals `"MY DARLIN"` (characters 5–14 of the test string) |
| **Notes** | Pre-condition: file created and written by helper methods. This verifies offset-based chunked reading works correctly. |

---

#### CFT-002 — Read with DataSize Larger Than Remaining Bytes

| Field | Value |
|---|---|
| **Test ID** | CFT-002 |
| **Category** | Core Functionality |
| **Subcategory** | Typical Workflows |
| **Description** | Request more bytes than remain in file from a given offset; server should clamp to available data |
| **Environment Requirements** | `any` |
| **Input Data** | `dropzone`: `targetLZ`, `fileName`: `testfilename`, `datasize`: 1000 (much larger than file), `offset`: 60, `dropzoneAddress`: null or `targetLZAddress` |
| **Dataset** | `[LANDING ZONE FILE]` — 66-byte test file |
| **Expected Output** | Response is not null; response contains the last 6 bytes of the file (`"UVXYZ"` + final char) |
| **Pass Criteria** | No exception thrown; response length equals `fileSize - offset` (6 bytes); content matches the tail of the test data |
| **Notes** | Verifies the server-side clamping logic: `dataToRead = min(dataSize, fileSize - offset)` |

---

#### CFT-003 — Read Exact File Size from Offset 0

| Field | Value |
|---|---|
| **Test ID** | CFT-003 |
| **Category** | Core Functionality |
| **Subcategory** | Boundary Values |
| **Description** | Read precisely the total number of bytes in the file from offset 0 |
| **Environment Requirements** | `any` |
| **Input Data** | `dropzone`: `targetLZ`, `fileName`: `testfilename`, `datasize`: 66 (exact file size), `offset`: 0, `dropzoneAddress`: null or `targetLZAddress` |
| **Dataset** | `[LANDING ZONE FILE]` — 66-byte test file |
| **Expected Output** | Full file content returned as string |
| **Pass Criteria** | Response is not null; `response.getBytes().length == 66`; content equals full test string |
| **Notes** | Validates the boundary condition where `dataSize == fileSize` exactly. This is nearly identical to `CreadHPCCFile` but is kept because `CreadHPCCFile` verifies byte-for-byte equality while this focuses on the boundary value aspect. Include only if you want an independent isolation of the boundary; otherwise this can be considered covered. |

---

#### CFT-004 — Sequential Chunked Reads Reconstruct Full File

| Field | Value |
|---|---|
| **Test ID** | CFT-004 |
| **Category** | Core Functionality |
| **Subcategory** | Typical Workflows |
| **Description** | Read a file in multiple sequential chunks using incremented offsets and verify the concatenated result matches the original data |
| **Environment Requirements** | `any` |
| **Input Data** | Three calls: (offset=0, size=22), (offset=22, size=22), (offset=44, size=22); all with same dropzone and filename |
| **Dataset** | `[LANDING ZONE FILE]` — 66-byte test file |
| **Expected Output** | Three response strings; concatenated equals the full 66-byte test string |
| **Pass Criteria** | Each call returns a non-null string; concatenation of all three matches the original file content exactly |
| **Notes** | Simulates a real paging pattern. Validates no data is duplicated or lost across chunk boundaries. |

---

#### CFT-005 — Large File Read (Performance / Large Data)

| Field | Value |
|---|---|
| **Test ID** | CFT-005 |
| **Category** | Core Functionality |
| **Subcategory** | Performance Limits |
| **Description** | Read a large file (1 MB) in a single call to verify no timeout or memory issues |
| **Environment Requirements** | `any` |
| **Input Data** | `dropzone`: `targetLZ`, `fileName`: `largefile.dat`, `datasize`: 1048576 (1 MB), `offset`: 0 |
| **Dataset** | `[NEW LANDING ZONE FILE]` — 1 MB file created by `CreateFile` + `WriteFileData` with repeated pattern; see new dataset spec below |
| **Expected Output** | Response string of length 1048576 bytes |
| **Pass Criteria** | No exception thrown; response is not null; `response.getBytes().length == 1048576` |
| **Notes** | Tests the client and server's ability to handle large binary payloads over MTOM/SOAP. |

---

### B. Edge Case Tests

---

#### ECT-001 — DataSize of 1 (Minimum Valid)

| Field | Value |
|---|---|
| **Test ID** | ECT-001 |
| **Category** | Edge Case |
| **Subcategory** | Boundary Values |
| **Description** | Read exactly 1 byte from the start of the file |
| **Environment Requirements** | `any` |
| **Input Data** | `dropzone`: `targetLZ`, `fileName`: `testfilename`, `datasize`: 1, `offset`: 0 |
| **Dataset** | `[LANDING ZONE FILE]` — 66-byte test file |
| **Expected Output** | Response string of length 1; content equals `"H"` (first character) |
| **Pass Criteria** | No exception; `response.getBytes().length == 1`; `response.equals("H")` |
| **Notes** | Boundary: minimum valid `dataSize`. |

---

#### ECT-002 — Offset at Last Valid Position (fileSize - 1)

| Field | Value |
|---|---|
| **Test ID** | ECT-002 |
| **Category** | Edge Case |
| **Subcategory** | Boundary Values |
| **Description** | Read from the last byte of the file (offset = fileSize - 1) |
| **Environment Requirements** | `any` |
| **Input Data** | `dropzone`: `targetLZ`, `fileName`: `testfilename`, `datasize`: 1, `offset`: 65 (= 66 - 1) |
| **Dataset** | `[LANDING ZONE FILE]` — 66-byte test file |
| **Expected Output** | Response of 1 byte; content equals the last character of the test string (`"Z"`) |
| **Pass Criteria** | No exception; `response.getBytes().length == 1`; `response.equals("Z")` |
| **Notes** | Boundary: highest valid offset. |

---

#### ECT-003 — `dropzoneAddress` Explicitly Provided (Non-Containerized)

| Field | Value |
|---|---|
| **Test ID** | ECT-003 |
| **Category** | Edge Case |
| **Subcategory** | Optional Parameters |
| **Description** | Verify that providing `dropzoneAddress` explicitly works correctly on non-containerized clusters |
| **Environment Requirements** | `baremetal` |
| **Input Data** | `dropzone`: `targetLZ`, `fileName`: `testfilename`, `datasize`: 66, `offset`: 0, `dropzoneAddress`: `targetLZAddress` (non-null) |
| **Dataset** | `[LANDING ZONE FILE]` — 66-byte test file |
| **Expected Output** | Full file content returned correctly |
| **Pass Criteria** | Response is not null; content matches test string |
| **Notes** | `DestNetAddress` was added in version 1.01. This test explicitly passes it on baremetal. |

---

#### ECT-004 — `dropzoneAddress` Null on Containerized Cluster

| Field | Value |
|---|---|
| **Test ID** | ECT-004 |
| **Category** | Edge Case |
| **Subcategory** | Optional Parameters |
| **Description** | Verify that omitting `dropzoneAddress` (null) works correctly in containerized mode |
| **Environment Requirements** | `containerized` |
| **Input Data** | `dropzone`: `targetLZ`, `fileName`: `testfilename`, `datasize`: 66, `offset`: 0, `dropzoneAddress`: `null` |
| **Dataset** | `[LANDING ZONE FILE]` — 66-byte test file |
| **Expected Output** | Full file content returned correctly |
| **Pass Criteria** | Response is not null; content matches test string; `DestNetAddress` not set on request |
| **Notes** | Validates the `if (dropzoneAddress != null && !dropzoneAddress.isEmpty())` branch in Java client. |

---

#### ECT-005 — `dropzoneAddress` Empty String

| Field | Value |
|---|---|
| **Test ID** | ECT-005 |
| **Category** | Edge Case |
| **Subcategory** | Unusual Valid Inputs |
| **Description** | Pass an empty string for `dropzoneAddress`; Java client should treat it the same as null and not set the field |
| **Environment Requirements** | `containerized` |
| **Input Data** | `dropzone`: `targetLZ`, `fileName`: `testfilename`, `datasize`: 66, `offset`: 0, `dropzoneAddress`: `""` |
| **Dataset** | `[LANDING ZONE FILE]` — 66-byte test file |
| **Expected Output** | Full file content returned correctly; no exception |
| **Pass Criteria** | Response is not null; content matches test string |
| **Notes** | Exercises the `!dropzoneAddress.isEmpty()` guard in the client. |

---

#### ECT-006 — File Containing Binary / Non-UTF-8 Bytes

| Field | Value |
|---|---|
| **Test ID** | ECT-006 |
| **Category** | Edge Case |
| **Subcategory** | Unusual Valid Inputs |
| **Description** | Read a file that contains binary data (bytes outside printable ASCII range) and verify the data round-trips correctly |
| **Environment Requirements** | `any` |
| **Input Data** | `dropzone`: `targetLZ`, `fileName`: `binaryfile.dat`, `datasize`: 256, `offset`: 0 |
| **Dataset** | `[NEW LANDING ZONE FILE]` — 256-byte file containing bytes 0x00–0xFF written via `WriteFileData`; see new dataset spec |
| **Expected Output** | Response byte array equals the original 256 bytes |
| **Pass Criteria** | `response.getBytes().length == 256`; `Arrays.equals(response.getBytes(), originalBytes)` |
| **Notes** | Tests binary fidelity through the DataHandler/MTOM pipeline. |

---

### C. Error Handling Tests

---

#### EHT-001 — Empty `dropzone` Parameter

| Field | Value |
|---|---|
| **Test ID** | EHT-001 |
| **Category** | Error Handling |
| **Subcategory** | Missing Required Fields |
| **Description** | Pass an empty string for `dropzone`; server should return "Destination not specified" |
| **Environment Requirements** | `any` |
| **Input Data** | `dropzone`: `""`, `fileName`: `testfilename`, `datasize`: 10, `offset`: 0, `dropzoneAddress`: null |
| **Dataset** | N/A — no file read needed (request fails before file access) |
| **Expected Output** | `EspSoapFault` thrown with message containing `"Destination not specified"` |
| **Pass Criteria** | `EspSoapFault` (or `Exception`) is thrown; message contains `"Destination not specified"` |
| **Notes** | Java client does not validate this field; error comes from server. |

---

#### EHT-002 — Null `dropzone` Parameter

| Field | Value |
|---|---|
| **Test ID** | EHT-002 |
| **Category** | Error Handling |
| **Subcategory** | Invalid Inputs |
| **Description** | Pass null for `dropzone` |
| **Environment Requirements** | `any` |
| **Input Data** | `dropzone`: `null`, `fileName`: `testfilename`, `datasize`: 10, `offset`: 0, `dropzoneAddress`: null |
| **Dataset** | N/A |
| **Expected Output** | `EspSoapFault` or `Exception` thrown |
| **Pass Criteria** | An exception is thrown; method does not silently succeed |
| **Notes** | Java client calls `setDestDropZone(null)` which maps to empty/missing on the wire. |

---

#### EHT-003 — Empty `fileName` Parameter

| Field | Value |
|---|---|
| **Test ID** | EHT-003 |
| **Category** | Error Handling |
| **Subcategory** | Missing Required Fields |
| **Description** | Pass an empty string for `fileName`; server should return "Destination path not specified" |
| **Environment Requirements** | `any` |
| **Input Data** | `dropzone`: `targetLZ`, `fileName`: `""`, `datasize`: 10, `offset`: 0, `dropzoneAddress`: null |
| **Dataset** | N/A |
| **Expected Output** | `EspSoapFault` thrown with message containing `"Destination path not specified"` |
| **Pass Criteria** | Exception thrown; message contains `"Destination path not specified"` |
| **Notes** | Server validates after `DestDropZone`, so `targetLZ` must be valid. |

---

#### EHT-004 — `dataSize` of Zero

| Field | Value |
|---|---|
| **Test ID** | EHT-004 |
| **Category** | Error Handling |
| **Subcategory** | Out-of-Range Values |
| **Description** | Pass `dataSize = 0`; server validates `dataSize < 1` and returns "Invalid data size." |
| **Environment Requirements** | `any` |
| **Input Data** | `dropzone`: `targetLZ`, `fileName`: `testfilename`, `datasize`: 0, `offset`: 0, `dropzoneAddress`: null |
| **Dataset** | `[LANDING ZONE FILE]` — 66-byte test file (needed to pass earlier validations) |
| **Expected Output** | `EspSoapFault` thrown with message containing `"Invalid data size."` |
| **Pass Criteria** | Exception thrown; message contains `"Invalid data size."` |
| **Notes** | File must exist for the server to reach the dataSize check. |

---

#### EHT-005 — Negative `dataSize`

| Field | Value |
|---|---|
| **Test ID** | EHT-005 |
| **Category** | Error Handling |
| **Subcategory** | Out-of-Range Values |
| **Description** | Pass a negative `dataSize`; server validates `dataSize < 1` |
| **Environment Requirements** | `any` |
| **Input Data** | `dropzone`: `targetLZ`, `fileName`: `testfilename`, `datasize`: -1, `offset`: 0, `dropzoneAddress`: null |
| **Dataset** | `[LANDING ZONE FILE]` — 66-byte test file |
| **Expected Output** | `EspSoapFault` thrown with message containing `"Invalid data size."` |
| **Pass Criteria** | Exception thrown; message contains `"Invalid data size."` |
| **Notes** | — |

---

#### EHT-006 — Negative `offset`

| Field | Value |
|---|---|
| **Test ID** | EHT-006 |
| **Category** | Error Handling |
| **Subcategory** | Out-of-Range Values |
| **Description** | Pass a negative `offset`; server returns "Invalid offset." |
| **Environment Requirements** | `any` |
| **Input Data** | `dropzone`: `targetLZ`, `fileName`: `testfilename`, `datasize`: 10, `offset`: -1, `dropzoneAddress`: null |
| **Dataset** | `[LANDING ZONE FILE]` — 66-byte test file |
| **Expected Output** | `EspSoapFault` thrown with message containing `"Invalid offset."` |
| **Pass Criteria** | Exception thrown; message contains `"Invalid offset."` |
| **Notes** | Java `long` type allows negative values; test that they are correctly rejected by the server. |

---

#### EHT-007 — `offset` Equal to File Size

| Field | Value |
|---|---|
| **Test ID** | EHT-007 |
| **Category** | Error Handling |
| **Subcategory** | Boundary Values / Invalid Inputs |
| **Description** | Pass `offset` equal to the file size (off-by-one: first invalid position); server returns "Invalid offset: file size = N." |
| **Environment Requirements** | `any` |
| **Input Data** | `dropzone`: `targetLZ`, `fileName`: `testfilename`, `datasize`: 10, `offset`: 66 (= file size), `dropzoneAddress`: null |
| **Dataset** | `[LANDING ZONE FILE]` — 66-byte test file |
| **Expected Output** | `EspSoapFault` thrown with message containing `"Invalid offset: file size = 66."` |
| **Pass Criteria** | Exception thrown; message matches expected pattern |
| **Notes** | Boundary condition: offset == size is invalid (valid range is 0 to size-1). |

---

#### EHT-008 — `offset` Beyond File Size

| Field | Value |
|---|---|
| **Test ID** | EHT-008 |
| **Category** | Error Handling |
| **Subcategory** | Out-of-Range Values |
| **Description** | Pass `offset` much larger than file size |
| **Environment Requirements** | `any` |
| **Input Data** | `dropzone`: `targetLZ`, `fileName`: `testfilename`, `datasize`: 10, `offset`: 999999, `dropzoneAddress`: null |
| **Dataset** | `[LANDING ZONE FILE]` — 66-byte test file |
| **Expected Output** | `EspSoapFault` thrown with message containing `"Invalid offset: file size = 66."` |
| **Pass Criteria** | Exception thrown; message contains `"Invalid offset:"` |
| **Notes** | — |

---

#### EHT-009 — Non-Existent File

| Field | Value |
|---|---|
| **Test ID** | EHT-009 |
| **Category** | Error Handling |
| **Subcategory** | Resource Not Found |
| **Description** | Attempt to read a file that does not exist on the dropzone |
| **Environment Requirements** | `any` |
| **Input Data** | `dropzone`: `targetLZ`, `fileName`: `nonexistent_file_12345.dat`, `datasize`: 10, `offset`: 0, `dropzoneAddress`: null |
| **Dataset** | N/A — file must not exist |
| **Expected Output** | `EspSoapFault` thrown with message containing `"does not exist."` |
| **Pass Criteria** | Exception thrown; message contains `"does not exist."` |
| **Notes** | Use a filename that is guaranteed not to exist. Consider a UUID-based filename. |

---

#### EHT-010 — Invalid `dropzone` Name

| Field | Value |
|---|---|
| **Test ID** | EHT-010 |
| **Category** | Error Handling |
| **Subcategory** | Invalid Inputs |
| **Description** | Pass a dropzone name that does not correspond to any registered landing zone |
| **Environment Requirements** | `any` |
| **Input Data** | `dropzone`: `"nonexistent_lz_xyz"`, `fileName`: `testfilename`, `datasize`: 10, `offset`: 0, `dropzoneAddress`: null |
| **Dataset** | N/A |
| **Expected Output** | Exception thrown (server rejects unknown dropzone during `validateDropZoneAccess`) |
| **Pass Criteria** | An exception is thrown; method does not silently succeed |
| **Notes** | The exact error message may vary by HPCC version; check for any exception. |

---

### D. Connectivity Tests

---

#### CNT-001 — Service Reachability via `readFileData`

| Field | Value |
|---|---|
| **Test ID** | CNT-001 |
| **Category** | Connectivity |
| **Subcategory** | Reachability |
| **Description** | Confirm the `readFileData` endpoint is reachable by making a minimal valid request and receiving a non-connection-error response |
| **Environment Requirements** | `any` |
| **Input Data** | `dropzone`: `targetLZ`, `fileName`: `testfilename`, `datasize`: 1, `offset`: 0, `dropzoneAddress`: null |
| **Dataset** | `[LANDING ZONE FILE]` — 66-byte test file |
| **Expected Output** | A response is received (success or known server-side error); no `RemoteException` / network exception |
| **Pass Criteria** | No `RemoteException` or connectivity-related exception is thrown |
| **Notes** | Distinct from `ping()` — this confirms the specific `readFileData` operation is routed and handled. |

---

#### CNT-002 — Invalid Credentials Rejected

| Field | Value |
|---|---|
| **Test ID** | CNT-002 |
| **Category** | Connectivity |
| **Subcategory** | Invalid Auth |
| **Description** | Attempt `readFileData` with invalid (wrong password) credentials; expect an authentication error |
| **Environment Requirements** | `secure` |
| **Input Data** | Client configured with valid host but `username: "baduser"`, `password: "wrongpassword"`; `dropzone`: `targetLZ`, `fileName`: `testfilename`, `datasize`: 10, `offset`: 0 |
| **Dataset** | `[LANDING ZONE FILE]` — 66-byte test file (may not be reached due to auth failure) |
| **Expected Output** | `Exception` or `ArrayOfEspExceptionWrapper` thrown with HTTP 401 or ESP auth error |
| **Pass Criteria** | An authentication-related exception is thrown; data is not returned |
| **Notes** | Only run on clusters with security plugins enabled. Skip on open/unsecured clusters. |

---

#### CNT-003 — Empty Credentials on Secured Cluster

| Field | Value |
|---|---|
| **Test ID** | CNT-003 |
| **Category** | Connectivity |
| **Subcategory** | Invalid Auth |
| **Description** | Attempt `readFileData` with empty username/password on a secured cluster |
| **Environment Requirements** | `secure` |
| **Input Data** | Client configured with `username: ""`, `password: ""`; otherwise minimal valid request |
| **Dataset** | N/A — auth failure expected before reaching file |
| **Expected Output** | Authentication-related exception thrown |
| **Pass Criteria** | Exception thrown; method does not silently succeed or return null data |
| **Notes** | Complements CNT-002 with the empty credentials case. |

---

## 8. New Dataset Specifications

All `readFileData` tests use **landing zone files**, not logical HPCC datasets. Files are created programmatically within the tests using `CreateFile` + `WriteFileData`. Therefore no additions to `generate-datasets.ecl` are required for most tests.

Two tests (CFT-005, ECT-006) require setup of non-trivial landing zone files:

### Landing Zone File: `largefile.dat` (for CFT-005)

```
Purpose: 1 MB file for large data read performance test
Required For: CFT-005
Setup Method: Use CreateFile + WriteFileData with 1 MB of repeating ASCII pattern data
Content: Repeating pattern of "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789" truncated/repeated to exactly 1,048,576 bytes
Teardown: Delete after test
```

**Java setup pseudocode:**
```java
byte[] largeData = new byte[1048576];
// Fill with repeating pattern
String pattern = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
for (int i = 0; i < largeData.length; i++) {
    largeData[i] = (byte) pattern.charAt(i % pattern.length());
}
// CreateFile + WriteFileData calls
client.createFile(targetLZ, "largefile.dat", targetLZAddress);
client.writeFileData(targetLZ, "largefile.dat", largeData, 0, targetLZAddress);
```

---

### Landing Zone File: `binaryfile.dat` (for ECT-006)

```
Purpose: 256-byte file with all possible byte values (0x00–0xFF)
Required For: ECT-006
Setup Method: Use CreateFile + WriteFileData with byte array [0, 1, 2, ..., 255]
Content: One occurrence of each byte value from 0 to 255
Teardown: Delete after test
Note: The round-trip test should compare byte arrays, not String equality,
      since non-UTF-8 bytes may not round-trip cleanly through String encoding.
```

**Java setup pseudocode:**
```java
byte[] binaryData = new byte[256];
for (int i = 0; i < 256; i++) {
    binaryData[i] = (byte) i;
}
client.createFile(targetLZ, "binaryfile.dat", targetLZAddress);
client.writeFileData(targetLZ, "binaryfile.dat", binaryData, 0, targetLZAddress);
```

> ⚠️ **Important for ECT-006:** The Java client returns `output.toString()` using the JVM's default charset. For binary round-trip testing, use `output.toByteArray()` directly, or modify assertions to compare the underlying bytes. The current `readFileData` return type (`String`) may not faithfully represent all 256 byte values depending on the platform's default charset.

---

## Appendix — Method Reference

### Java Client Source
`wsclient/src/main/java/org/hpccsystems/ws/client/HPCCWsFileIOClient.java` (lines 501–568)

### Wrapper Classes
- `wsclient/src/main/java/org/hpccsystems/ws/client/wrappers/gen/wsfileio/ReadFileDataRequestWrapper.java`
- `wsclient/src/main/java/org/hpccsystems/ws/client/wrappers/gen/wsfileio/ReadFileDataResponseWrapper.java`

### IDL Definition
`HPCC-Platform/esp/scm/ws_fileio.ecm`

### Server Implementation
`HPCC-Platform/esp/services/ws_fileio/ws_fileioservice.cpp` — `CWsFileIOEx::onReadFileData` (lines 88–172)

### Existing Test File
`wsclient/src/test/java/org/hpccsystems/ws/client/WSFileIOClientTest.java` — `CreadHPCCFile()` (lines 199–214)


---
*Generated: 2026-03-26*
