# WsFileIO.writeHPCCFileData — Test Case Analysis
**Generated:** 2026-03-26  
**Method:** `HPCCWsFileIOClient.writeHPCCFileData`  
**Service:** WsFileIO (ESP endpoint: `/WsFileIO`)  
**IDL:** `esp/scm/ws_fileio.ecm`  
**Server Implementation:** `esp/services/ws_fileio/ws_fileioservice.cpp`

---

## 1. Method Summary

### Purpose
`writeHPCCFileData` writes binary data to an existing file on an HPCC landing zone (drop zone). It operates as the second step in a two-step upload workflow: `createHPCCFile` must be called first to create the destination file, then `writeHPCCFileData` streams the binary payload to it, optionally in multiple chunks.

### Role Within Service
This is one of three core methods in `HPCCWsFileIOClient` (`createHPCCFile`, `writeHPCCFileData`, `readFileData`). It maps to the ESP server method `WriteFileData` and translates a raw byte array into a SOAP request with a MIME-typed binary attachment.

### Primary Signature (Current)
```java
public boolean writeHPCCFileData(
    byte[] data,
    String dataTypeDescriptor,   // MIME type, e.g. "text/plain; charset=UTF-8"
    String fileName,             // Relative path on the landing zone
    String targetLandingZone,    // Landing zone name (drop zone name)
    boolean append,              // Append to existing content?
    long offset,                 // Write offset (only used for first chunk; see Note A)
    int uploadchunksize,         // Chunk size in bytes; ≤0 uses default (5 MB)
    String lzAddress             // Optional: landing zone net address (bare-metal only)
) throws Exception, ArrayOfEspExceptionWrapper
```

### Deprecated Overloads
| Signature | Deprecation Reason |
|---|---|
| `writeHPCCFileData(data, fileName, LZ, append, offset, chunksize, lzAddress)` | Missing `dataTypeDescriptor`; defaults to `application/octet-stream` |
| `writeHPCCFileData(data, fileName, LZ, append, offset, chunksize)` | Also missing `lzAddress`; not suitable for bare-metal clusters |

### Inputs
| Parameter | Type | Notes |
|---|---|---|
| `data` | `byte[]` | Payload to write; must be non-null and non-empty |
| `dataTypeDescriptor` | `String` | MIME type; defaults to `application/octet-stream` if null/empty |
| `fileName` | `String` | Relative path on the landing zone |
| `targetLandingZone` | `String` | Landing zone / drop zone name |
| `append` | `boolean` | True = append; however, see **Note A** below |
| `offset` | `long` | Start offset; however, see **Note A** below |
| `uploadchunksize` | `int` | Chunk size in bytes; ≤0 defaults to 5 000 000 |
| `lzAddress` | `String` | Optional net address (bare-metal); containerized clusters pass `null` |

### Outputs
`boolean` — `true` if all chunks were successfully written; `false` on internal exception or `"Failed"` result string from the server.

### Side Effects
- Writes (or appends to) a file on the HPCC landing zone.
- Leaves the file in a partially-written state if a mid-transfer chunk fails and the loop breaks early (no rollback).
- Emits OpenTelemetry trace spans on the primary overload.

### ⚠️ Note A — Chunking Offset/Append Bug
Inside the chunk loop, the client unconditionally overrides the user-supplied `append` flag and `offset`:
```java
dataindex += payloadsize;              // updated BEFORE the next two lines
request.setAppend(dataindex > 0);      // always true  ← ignores user's append param
request.setOffset((long) dataindex);   // advances by chunk size ← ignores user's offset
```
This means:
1. `append=false` is never honoured — the first chunk is also sent with `append=true`.
2. The user-supplied `offset` parameter is ignored after the first line of the loop sets `request.setOffset(offset)` (which is then overwritten).
3. For a **single-chunk write** the effective offset sent to the server equals `payloadsize`, not `0`.

This is a pre-existing behaviour; tests must be written to reflect actual (not intended) behaviour.

---

## 2. Existing Test Coverage Analysis

### Source File
`wsclient/src/test/java/org/hpccsystems/ws/client/WSFileIOClientTest.java`

### Existing Test Methods for `writeHPCCFileData`

| Existing Test Method Name | Test Category | Scenario Covered | Input Data Summary | Pass Criteria | Notes |
|---|---|---|---|---|---|
| `BwriteHPCCFile` | CFT – Basic Operation | Small ASCII text write using deprecated overload (no `dataTypeDescriptor`), `append=true`, `offset=0`, `chunksize=20` | 65-byte ASCII string | `assertTrue(result)` | Uses system-property `lztestfile`; containerized and bare-metal branches; file pre-created by `AcreateHPCCFile` |
| `copyFile` | CFT – Typical Workflow | Write CSV data as part of a create → write → spray → copy workflow; uses deprecated overload, `append=true`, `offset=0`, `chunksize=20` | 49-byte CSV string | Implicit — test fails on spray/copy errors, not on write result | `writeHPCCFileData` result not explicitly asserted; main concern is the full workflow |

### Coverage Summary
- **Total existing test methods for `writeHPCCFileData`:** 2
- **Core Functionality Tests covered:** 1 (basic small-data write using deprecated method)
- **Edge Case Tests covered:** 0
- **Error Handling Tests covered:** 0
- **Connectivity Tests covered:** 0 (a separate `ping()` test exists for the service, but no connectivity-level write test)

### Gaps Identified
1. **No test for the primary non-deprecated signature** (`dataTypeDescriptor` parameter)
2. **No test for various MIME `dataTypeDescriptor` values** (text/plain, image/jpeg, application/json, etc.)
3. **No test for `append=false`** (fresh overwrite, not append)
4. **No test with multi-chunk data** (data size > `uploadchunksize` forcing the loop to iterate > 1 time)
5. **No test for large data** approaching or exceeding the default 5 MB chunk size
6. **No test for `uploadchunksize ≤ 0`** (triggering defaultUploadChunkSize path)
7. **No test for a non-zero user-supplied offset** (noting it is overridden by the client — test should document actual behaviour)
8. **No test for null `data`** (NPE expected before server call)
9. **No test for empty `data` array** (zero-length; server rejects with "Source data not specified")
10. **No test for null/empty `fileName`** (server rejects with "Destination path not specified")
11. **No test for null/empty `targetLandingZone`** (server rejects with "Destination not specified")
12. **No test for a non-existent target file** (file not created first; server returns "does not exist")
13. **No test for binary (non-text) data** (e.g. PNG header bytes)
14. **No test for invalid credentials** (auth failure from `ensureFeatureAccess`)
15. **No test for an invalid/unreachable connection** (stub failure)
16. **No test for `lzAddress` variations** (explicit address vs. null in bare-metal mode)
17. **No test verifying the written content** (round-trip: write then read-back to verify correctness)

---

## 3. Request Structure

### IDL Source (`esp/scm/ws_fileio.ecm`)
```ecm
ESPrequest WriteFileDataRequest
{
    binary Data;
    string DestDropZone;
    string DestRelativePath;
    [min_ver("1.01")] string DestNetAddress;
    int64  Offset(0);
    bool   Append(false);
};
```

### Field Table

| Field Name | Java Parameter | Type | Required | Description | Valid Range / Format | Notes |
|---|---|---|---|---|---|---|
| `Data` | `data` (chunked) | `binary` (MIME attachment) | Yes | Binary payload for this chunk | Any non-empty byte sequence | Server returns "Source data not specified" if empty |
| `DestDropZone` | `targetLandingZone` | `string` | Yes | Landing zone / drop zone name | Non-empty string | Server returns "Destination not specified" if absent |
| `DestRelativePath` | `fileName` | `string` | Yes | Relative file path on the landing zone | Non-empty string | Server returns "Destination path not specified" if absent; file must already exist |
| `DestNetAddress` | `lzAddress` | `string` | No | Net address of the LZ (bare-metal) | IP or hostname | Available from ESP version 1.01+; omit for containerized |
| `Offset` | `offset` (user) → overridden | `int64` | No | Byte offset at which to write | ≥ 0; default `0` | Ignored by client loop logic when `Append=true` (see Note A) |
| `Append` | `append` (user) → overridden | `bool` | No | Append to end of file | `true`/`false`; default `false` | Client always sends `true` after first chunk increment (see Note A) |

### Field Dependencies
- If `Append=true`, the server re-opens the file in read mode to determine the current size and uses that as offset (the `Offset` field is ignored by the server when `Append=true`).
- `DestNetAddress` is only required in bare-metal deployments; omit for containerized HPCC.
- The file referenced by `DestRelativePath` on `DestDropZone` **must already exist**; this method does not create files.

### Client-Side Defaults
| Parameter | Default Behaviour |
|---|---|
| `dataTypeDescriptor` null/empty | Defaults to `"application/octet-stream"` |
| `uploadchunksize ≤ 0` | Uses `defaultUploadChunkSize = 5_000_000` (5 MB) |
| `lzAddress` null/empty | `DestNetAddress` not set in request |

---

## 4. Server Behaviour and Responses

### Processing Logic (`CWsFileIOEx::onWriteFileData`)

1. **Permission check**: `context.ensureFeatureAccess("FileIOAccess", SecAccess_Write, ...)` — throws if caller lacks write permission.
2. **Validate `DestDropZone`**: If null or empty → set result `"Destination not specified"`, return `true`.
3. **Validate `DestRelativePath`**: If null or empty → set result `"Destination path not specified"`, return `true`.
4. **Validate `Data`**: If `MemoryBuffer` length is 0 → set result `"Source data not specified"`, return `true`.
5. **Drop zone access validation**: `validateDropZoneAccess(...)` — validates the caller has access to the specific drop zone path; raises `ECLWATCH_ACCESS_TO_FILE_DENIED` (5149) if denied.
6. **File existence check**: Creates `IFile` from the resolved path; if `isFile() != foundYes` → set result `"<path> does not exist."`, return `true`.
7. **Append offset calculation**: If `Append=true`, opens file in read mode and reads its current size to determine the write offset.
8. **Write**: Opens file in write mode (`IFOwrite`); writes `srcdata.length()` bytes at the computed offset.
9. **Result string**:
   - Success: `"WriteFileData done."`
   - Write failure: `"WriteFileData error."`

### Response Structure (`WriteFileDataResponse`)

| Field | Description |
|---|---|
| `DestDropZone` | Echo of request `DestDropZone` |
| `DestRelativePath` | Echo of request `DestRelativePath` |
| `Offset` | Echo of effective offset (only set if `Append=false`) |
| `Append` | Echo of `Append` flag (only set if `Append=true`) |
| `Result` | Result string (see above) |
| `Exceptions` | ESP exception array (inline) |

### Client-Side Result Parsing
The Java client checks `response.getResult().startsWith("Failed")`. Note: the server returns `"WriteFileData error."` on write failure, which does **not** start with `"Failed"`. This means a write error on the server side does **not** cause the Java method to return `false` — it returns `true` (success=true is never set to false in the loop). This is an existing defect that tests should document.

---

## 5. Error Handling

### Server-Side Errors

| Condition | Server Response | Client Handling |
|---|---|---|
| Missing `DestDropZone` | `Result = "Destination not specified"` | Loop continues; client returns `true` (bug — see above) |
| Missing `DestRelativePath` | `Result = "Destination path not specified"` | Loop continues; client returns `true` |
| Empty `Data` | `Result = "Source data not specified"` | Loop continues; client returns `true` |
| File does not exist | `Result = "<path> does not exist."` | Loop continues; client returns `true` |
| Write I/O failure | `Result = "WriteFileData error."` | Loop continues; client returns `true` (not `false`) |
| Insufficient permission | ESP exception via `ensureFeatureAccess` | `handleEspExceptions` → `ArrayOfEspExceptionWrapper` thrown |
| Drop zone access denied | `ECLWATCH_ACCESS_TO_FILE_DENIED` (5149) | ESP exception → `ArrayOfEspExceptionWrapper` thrown |
| ESP SOAP fault | `EspSoapFault` exception | Caught inside loop; logs error; returns `false` |

### Client-Side Errors

| Condition | Behaviour |
|---|---|
| `verifyStub()` fails (stub not initialized) | Throws `Exception` before any network call |
| `data` is `null` | `NullPointerException` at `data.length` — not caught; propagates to caller |
| `data` is empty (`length == 0`) | Loop body never entered (`bytesleft = 0`); method returns `true` without making any server call |
| Network / RemoteException | Caught in inner `catch (Exception e)`; logs error; returns `false` |
| Invalid connection URL | `AxisFault` during client construction; `initErrMessage` set; `verifyStub()` subsequently throws |
| `uploadchunksize` negative or zero | Replaced with `defaultUploadChunkSize` (5 000 000); no error |

---

## 6. Existing Dataset Analysis

> **Note:** `writeHPCCFileData` writes raw bytes to a landing zone file — it does not directly consume HPCC logical files. The benchmark datasets are HPCC logical files (on Thor), not landing zone files. They are not directly usable as input to this method. However, they can serve as reference content if test code reads them and then pipes the content to the write method.

| Dataset Name | Applicable? | Reason |
|---|---|---|
| `~benchmark::all_types::200KB` | No (indirect) | This is a Thor logical file, not a landing zone file. Its raw binary content could theoretically be read and re-written via the WsFileIO service, but there is no practical benefit — the method accepts arbitrary `byte[]`. Not useful for this method. |
| `~benchmark::string::100MB` | No (indirect) | Same as above. Potentially useful as a source of large data for performance/chunking tests, but the data must first be extracted as bytes; simpler to generate bytes directly in test code. |
| `~benchmark::integer::20KB` | No (indirect) | Same as above. |
| `~benchmark::all_types::superfile` | No | Superfile; even less applicable. |
| `~benchmark::integer::20kb::key` | No | Index file; not applicable. |

**Conclusion:** No existing benchmark dataset is directly applicable. All test inputs for `writeHPCCFileData` should be constructed as inline `byte[]` literals or programmatically generated byte arrays within the test code. New landing-zone-based datasets (files on the LZ) will be created at test runtime by the test setup itself (`createHPCCFile` followed by `writeHPCCFileData`).

---

## 7. Test Case Plan

> **All test cases below address gaps identified in Section 2.** Existing tests `BwriteHPCCFile` (CFT – basic small text write via deprecated overload) and `copyFile` (CFT – workflow integration) are not duplicated.

---

### A. Core Functionality Tests

---

#### CFT-001 — Write with explicit MIME type (`text/plain`)

| Field | Detail |
|---|---|
| **Test ID** | CFT-001 |
| **Category** | Core Functionality |
| **Subcategory** | Basic Operation — primary (non-deprecated) signature |
| **Description** | Write a small ASCII text payload using the primary overload with an explicit `dataTypeDescriptor` of `"text/plain; charset=UTF-8"`. Verify the method returns `true` and the data can be read back. |
| **Environment Requirements** | `any` |
| **Input Data** | `data = "Hello WsFileIO CFT-001".getBytes(StandardCharsets.UTF_8)`, `dataTypeDescriptor = "text/plain; charset=UTF-8"`, `fileName = "<timestamp>_cft001.txt"`, `targetLandingZone = targetLZ`, `append = true`, `offset = 0`, `uploadchunksize = 100`, `lzAddress = isContainerized ? null : targetLZAddress` |
| **Dataset** | No external dataset required; data is inline |
| **Expected Output** | Method returns `true`; subsequent `readFileData` returns a string equal to the original data |
| **Pass Criteria** | - Return value is `true` <br>- `readFileData(targetLZ, fileName, data.length, 0, lzAddress)` returns the exact original string <br>- No exception thrown |
| **Notes** | Requires prior `createHPCCFile` call in test setup; clean up LZ file in `@After` / `finally` block |

---

#### CFT-002 — Write with `dataTypeDescriptor` defaulting (null input)

| Field | Detail |
|---|---|
| **Test ID** | CFT-002 |
| **Category** | Core Functionality |
| **Subcategory** | Basic Operation — MIME default behaviour |
| **Description** | Call the primary overload with `dataTypeDescriptor = null`. Verify that the client defaults to `"application/octet-stream"` internally and the write succeeds. |
| **Environment Requirements** | `any` |
| **Input Data** | `data = "null-mime-test".getBytes()`, `dataTypeDescriptor = null`, `fileName = "<timestamp>_cft002.bin"`, `targetLandingZone = targetLZ`, `append = true`, `offset = 0`, `uploadchunksize = 100`, `lzAddress = isContainerized ? null : targetLZAddress` |
| **Dataset** | No external dataset required |
| **Expected Output** | Method returns `true`; no exception thrown |
| **Pass Criteria** | - Return value is `true` <br>- No `NullPointerException` or other exception |
| **Notes** | Verifies internal null-guard on `dataTypeDescriptor`; no round-trip read-back required |

---

#### CFT-003 — Write with `dataTypeDescriptor` empty string

| Field | Detail |
|---|---|
| **Test ID** | CFT-003 |
| **Category** | Core Functionality |
| **Subcategory** | Basic Operation — MIME default behaviour |
| **Description** | Call the primary overload with `dataTypeDescriptor = ""` (empty string). Verify internal default kick-in. |
| **Environment Requirements** | `any` |
| **Input Data** | `data = "empty-mime-test".getBytes()`, `dataTypeDescriptor = ""`, `fileName = "<timestamp>_cft003.bin"`, `targetLandingZone = targetLZ`, `append = true`, `offset = 0`, `uploadchunksize = 100`, `lzAddress = isContainerized ? null : targetLZAddress` |
| **Dataset** | No external dataset required |
| **Expected Output** | Method returns `true`; no exception |
| **Pass Criteria** | - Return value is `true` <br>- No exception thrown |
| **Notes** | Same guard logic as CFT-002 but exercises the `isEmpty()` branch |

---

#### CFT-004 — Write binary (non-text) data with `application/octet-stream`

| Field | Detail |
|---|---|
| **Test ID** | CFT-004 |
| **Category** | Core Functionality |
| **Subcategory** | Data Variations — binary payload |
| **Description** | Write a byte array containing non-printable / binary bytes (e.g. simulated PNG header `{0x89, 0x50, 0x4E, 0x47, …}`) and verify round-trip correctness. |
| **Environment Requirements** | `any` |
| **Input Data** | `data = new byte[]{(byte)0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A,0x00,0x01,0x02,0x03}`, `dataTypeDescriptor = "application/octet-stream"`, `fileName = "<timestamp>_cft004.bin"`, `targetLandingZone = targetLZ`, `append = true`, `offset = 0`, `uploadchunksize = 100`, `lzAddress = isContainerized ? null : targetLZAddress` |
| **Dataset** | No external dataset required |
| **Expected Output** | `true`; `readFileData` returns string whose bytes match original |
| **Pass Criteria** | - Return value is `true` <br>- Read-back bytes match original array byte-for-byte <br>- No exception |
| **Notes** | Validates that non-UTF-8 binary content is preserved through the MTOM/MIME encoding path |

---

#### CFT-005 — Write JSON payload with `application/json` MIME type

| Field | Detail |
|---|---|
| **Test ID** | CFT-005 |
| **Category** | Core Functionality |
| **Subcategory** | Data Variations — MIME type variants |
| **Description** | Write a JSON string with `dataTypeDescriptor = "application/json"` and verify server accepts it. |
| **Environment Requirements** | `any` |
| **Input Data** | `data = "{\"key\":\"value\",\"count\":42}".getBytes(StandardCharsets.UTF_8)`, `dataTypeDescriptor = "application/json"`, `fileName = "<timestamp>_cft005.json"`, `targetLandingZone = targetLZ`, `append = true`, `offset = 0`, `uploadchunksize = 500`, `lzAddress = isContainerized ? null : targetLZAddress` |
| **Dataset** | No external dataset required |
| **Expected Output** | Method returns `true`; no exception |
| **Pass Criteria** | - Return value is `true` <br>- No exception thrown |
| **Notes** | Server-side is agnostic to MIME type for storage; this tests client-side DataSource construction |

---

#### CFT-006 — Multi-chunk write (data larger than `uploadchunksize`)

| Field | Detail |
|---|---|
| **Test ID** | CFT-006 |
| **Category** | Core Functionality |
| **Subcategory** | Typical Workflow — chunked upload |
| **Description** | Write a payload that is larger than `uploadchunksize`, forcing the client loop to iterate multiple times (e.g. 100 bytes of data with chunksize=20 → 5 iterations). Verify the full data is written correctly. |
| **Environment Requirements** | `any` |
| **Input Data** | `data = new byte[100]` (filled with repeated pattern `0x41..0x5A`), `dataTypeDescriptor = "application/octet-stream"`, `fileName = "<timestamp>_cft006.bin"`, `targetLandingZone = targetLZ`, `append = true`, `offset = 0`, `uploadchunksize = 20`, `lzAddress = isContainerized ? null : targetLZAddress` |
| **Dataset** | No external dataset required |
| **Expected Output** | Method returns `true`; full 100 bytes present on LZ |
| **Pass Criteria** | - Return value is `true` <br>- `readFileData` with `datasize=100` returns data whose length equals 100 <br>- No exception |
| **Notes** | This exercises the `while (bytesleft > 0)` loop for multiple iterations |

---

#### CFT-007 — Write data using `uploadchunksize ≤ 0` (default chunk size)

| Field | Detail |
|---|---|
| **Test ID** | CFT-007 |
| **Category** | Core Functionality |
| **Subcategory** | Typical Workflow — default chunk size path |
| **Description** | Pass `uploadchunksize = 0` (and also `-1`) to verify the client substitutes the `defaultUploadChunkSize` (5 000 000) and the write succeeds. |
| **Environment Requirements** | `any` |
| **Input Data** | `data = "defaultchunk-test".getBytes()`, `dataTypeDescriptor = "text/plain"`, `fileName = "<timestamp>_cft007.txt"`, `targetLandingZone = targetLZ`, `append = true`, `offset = 0`, `uploadchunksize = 0`, `lzAddress = isContainerized ? null : targetLZAddress` |
| **Dataset** | No external dataset required |
| **Expected Output** | Method returns `true` |
| **Pass Criteria** | - Return value is `true` <br>- No `ArithmeticException` or negative-array-size error <br>- No exception |
| **Notes** | Repeat with `uploadchunksize = -1` as a second assertion in the same test method |

---

#### CFT-008 — Round-trip write then read-back verification

| Field | Detail |
|---|---|
| **Test ID** | CFT-008 |
| **Category** | Core Functionality |
| **Subcategory** | Typical Workflow — end-to-end |
| **Description** | Create a file, write known content, read it back, and assert byte-level equality. This is the definitive correctness test for the write path. |
| **Environment Requirements** | `any` |
| **Input Data** | `data = "Round-trip 123 \r\n ABC".getBytes(StandardCharsets.UTF_8)`, `dataTypeDescriptor = "text/plain; charset=UTF-8"`, `fileName = "<timestamp>_cft008.txt"`, `targetLandingZone = targetLZ`, `append = true`, `offset = 0`, `uploadchunksize = 5`, `lzAddress = isContainerized ? null : targetLZAddress` |
| **Dataset** | No external dataset required |
| **Expected Output** | `readFileData` returns string whose `.getBytes(UTF-8)` equals original `data` |
| **Pass Criteria** | - Write returns `true` <br>- Read response is non-null <br>- `assertArrayEquals(data, response.getBytes(StandardCharsets.UTF_8))` passes |
| **Notes** | Small chunksize (5) means multiple chunks — exercises both multi-chunk correctness and data ordering |

---

### B. Edge Case Tests

---

#### ECT-001 — Write with `uploadchunksize` exactly equal to `data.length` (single-chunk boundary)

| Field | Detail |
|---|---|
| **Test ID** | ECT-001 |
| **Category** | Edge Case |
| **Subcategory** | Boundary Values — chunk size equals payload |
| **Description** | Set `uploadchunksize` exactly to `data.length` so that the loop executes exactly once. Verify correct behaviour. |
| **Environment Requirements** | `any` |
| **Input Data** | `data = "ExactChunk".getBytes()` (10 bytes), `dataTypeDescriptor = "text/plain"`, `fileName = "<timestamp>_ect001.txt"`, `targetLandingZone = targetLZ`, `append = true`, `offset = 0`, `uploadchunksize = 10`, `lzAddress = isContainerized ? null : targetLZAddress` |
| **Dataset** | No external dataset required |
| **Expected Output** | Method returns `true`; loop iterates exactly once |
| **Pass Criteria** | - Return value is `true` <br>- No exception |
| **Notes** | Boundary condition: `payloadsize = bytesleft = data.length`; after one iteration `bytesleft = 0` |

---

#### ECT-002 — Write with `uploadchunksize = data.length + 1` (chunk larger than data)

| Field | Detail |
|---|---|
| **Test ID** | ECT-002 |
| **Category** | Edge Case |
| **Subcategory** | Boundary Values — chunk exceeds payload |
| **Description** | `uploadchunksize` is larger than the data; loop runs exactly once with `payloadsize = data.length`. |
| **Environment Requirements** | `any` |
| **Input Data** | `data = "SmallData".getBytes()` (9 bytes), `uploadchunksize = 100`, others same as ECT-001 |
| **Dataset** | No external dataset required |
| **Expected Output** | Method returns `true` |
| **Pass Criteria** | - Return value is `true` <br>- No `ArrayIndexOutOfBoundsException` |
| **Notes** | Verifies `Arrays.copyOfRange` handles `end > array.length` correctly (it pads with zeros; but since `payloadsize = min(bytesleft, limit) = bytesleft`, end = dataindex + payloadsize = data.length, so no out-of-bounds) |

---

#### ECT-003 — Write single byte

| Field | Detail |
|---|---|
| **Test ID** | ECT-003 |
| **Category** | Edge Case |
| **Subcategory** | Boundary Values — minimum valid data |
| **Description** | Write a single-byte payload. |
| **Environment Requirements** | `any` |
| **Input Data** | `data = new byte[]{0x42}`, `dataTypeDescriptor = "application/octet-stream"`, `fileName = "<timestamp>_ect003.bin"`, `targetLandingZone = targetLZ`, `append = true`, `offset = 0`, `uploadchunksize = 1`, `lzAddress = isContainerized ? null : targetLZAddress` |
| **Dataset** | No external dataset required |
| **Expected Output** | Method returns `true` |
| **Pass Criteria** | - Return value is `true` <br>- No exception |
| **Notes** | Minimum possible non-empty write |

---

#### ECT-004 — Explicit bare-metal `lzAddress` provided

| Field | Detail |
|---|---|
| **Test ID** | ECT-004 |
| **Category** | Edge Case |
| **Subcategory** | Optional Parameters — `lzAddress` non-null |
| **Description** | In bare-metal environments, provide an explicit `lzAddress` (from system property `lzaddress`). Verify the client sets `DestNetAddress` in the request and the write succeeds. |
| **Environment Requirements** | `baremetal` |
| **Input Data** | `data = "baremetal-test".getBytes()`, `dataTypeDescriptor = "text/plain"`, `fileName = "<timestamp>_ect004.txt"`, `targetLandingZone = targetLZ`, `append = true`, `offset = 0`, `uploadchunksize = 100`, `lzAddress = targetLZAddress` (non-null, non-empty) |
| **Dataset** | No external dataset required |
| **Expected Output** | Method returns `true` |
| **Pass Criteria** | - Return value is `true` <br>- No exception |
| **Notes** | Use `assumeFalse("Not bare-metal", isContainerized)` to skip in containerized environments |

---

#### ECT-005 — Large data write (~1 MB, multiple chunks)

| Field | Detail |
|---|---|
| **Test ID** | ECT-005 |
| **Category** | Edge Case |
| **Subcategory** | Performance Limits — large payload |
| **Description** | Write approximately 1 MB of data with a 100 KB chunk size (~10 iterations). Verify all bytes are written. |
| **Environment Requirements** | `any` |
| **Input Data** | `data = new byte[1_048_576]` (filled with sequential modular pattern), `dataTypeDescriptor = "application/octet-stream"`, `fileName = "<timestamp>_ect005.bin"`, `targetLandingZone = targetLZ`, `append = true`, `offset = 0`, `uploadchunksize = 102_400`, `lzAddress = isContainerized ? null : targetLZAddress` |
| **Dataset** | No external dataset required |
| **Expected Output** | Method returns `true` |
| **Pass Criteria** | - Return value is `true` <br>- No timeout or exception <br>- Optional: `readFileData` confirms length = 1 048 576 |
| **Notes** | Tests chunked loop performance; `readFileData` is limited by ESP response size so the read-back assertion is optional |

---

#### ECT-006 — Confirm behaviour when user-supplied `append=false` (documents actual bug)

| Field | Detail |
|---|---|
| **Test ID** | ECT-006 |
| **Category** | Edge Case |
| **Subcategory** | Unusual Valid Inputs — `append=false` behaviour |
| **Description** | Pass `append=false` and document that the client overrides this to `append=true` in the loop. The server thus always appends, and the test asserts the **actual** (not intended) behaviour. |
| **Environment Requirements** | `any` |
| **Input Data** | First write: `data = "FIRST".getBytes()`, `append = true`, `offset = 0`, `chunksize = 10`. Second write: `data = "SECOND".getBytes()`, `append = false`, `offset = 0`, `chunksize = 10` (both to same file) |
| **Dataset** | No external dataset required |
| **Expected Output** | Due to client-side bug, both writes use `append=true`; `readFileData` after second write returns `"FIRSTSECOND"` (11 bytes), not `"SECOND"` (6 bytes) |
| **Pass Criteria** | - Both writes return `true` <br>- Total read-back length = 11 bytes (`"FIRSTSECOND"`) |
| **Notes** | This test **intentionally documents the bug** described in Note A. When the bug is fixed, this test will need updating. Add a comment in the test code explaining the expected-vs-actual discrepancy. |

---

#### ECT-007 — User-supplied non-zero `offset` is overridden (documents actual behaviour)

| Field | Detail |
|---|---|
| **Test ID** | ECT-007 |
| **Category** | Edge Case |
| **Subcategory** | Unusual Valid Inputs — offset override behaviour |
| **Description** | Pass `offset=5` and a chunk size equal to the data length. Document that the effective offset sent to the server is `data.length` (not 5), due to the loop overriding `request.setOffset((long) dataindex)`. |
| **Environment Requirements** | `any` |
| **Input Data** | `data = "OFFSET".getBytes()` (6 bytes), `dataTypeDescriptor = "text/plain"`, `fileName = "<timestamp>_ect007.txt"`, `append = true`, `offset = 5`, `uploadchunksize = 6`, `lzAddress = isContainerized ? null : targetLZAddress` |
| **Dataset** | No external dataset required |
| **Expected Output** | Write returns `true`; data is written at effective offset 6 (not 5) due to the bug |
| **Pass Criteria** | - Return value is `true` <br>- No exception |
| **Notes** | Documents the Note A bug for `offset`. Since `append=true` effectively causes the server to ignore offset anyway, the observable difference is subtle; this test primarily exists as a bug regression marker. |

---

### C. Error Handling Tests

---

#### EHT-001 — Null `data` array causes `NullPointerException`

| Field | Detail |
|---|---|
| **Test ID** | EHT-001 |
| **Category** | Error Handling |
| **Subcategory** | Invalid Inputs — null data |
| **Description** | Pass `data = null`. Expect a `NullPointerException` at `data.length` before any server call is made. |
| **Environment Requirements** | `any` |
| **Input Data** | `data = null`, all other params valid |
| **Dataset** | No external dataset required |
| **Expected Output** | `NullPointerException` thrown from the method |
| **Pass Criteria** | - `NullPointerException` is thrown (or wrapping `Exception`) <br>- Method does not return `true` or `false` |
| **Notes** | Use `@Test(expected = NullPointerException.class)` or `assertThrows`; no server call should be made |

---

#### EHT-002 — Empty `data` array — no server call, returns `true`

| Field | Detail |
|---|---|
| **Test ID** | EHT-002 |
| **Category** | Error Handling |
| **Subcategory** | Invalid Inputs — empty payload |
| **Description** | Pass `data = new byte[0]`. `bytesleft = 0` so the loop never runs; method returns `true` without writing anything. |
| **Environment Requirements** | `any` |
| **Input Data** | `data = new byte[0]`, `dataTypeDescriptor = "text/plain"`, `fileName = "<timestamp>_eht002.txt"`, `targetLandingZone = targetLZ`, `append = true`, `offset = 0`, `uploadchunksize = 100`, `lzAddress = isContainerized ? null : targetLZAddress` |
| **Dataset** | No external dataset required |
| **Expected Output** | Method returns `true`; no server communication |
| **Pass Criteria** | - Return value is `true` <br>- No exception <br>- If the file is pre-created and read back, it remains empty (0 bytes) |
| **Notes** | This is a silent success that may mislead callers; documents the edge behaviour |

---

#### EHT-003 — Null `fileName` — server returns "Destination path not specified"

| Field | Detail |
|---|---|
| **Test ID** | EHT-003 |
| **Category** | Error Handling |
| **Subcategory** | Invalid Inputs — missing required field |
| **Description** | Pass `fileName = null`. The server receives a null `DestRelativePath` and responds with `"Destination path not specified"` in `Result`. The client does not detect this as an error and returns `true`. |
| **Environment Requirements** | `any` |
| **Input Data** | `data = "test".getBytes()`, `dataTypeDescriptor = "text/plain"`, `fileName = null`, `targetLandingZone = targetLZ`, `append = true`, `offset = 0`, `uploadchunksize = 100`, `lzAddress = isContainerized ? null : targetLZAddress` |
| **Dataset** | No external dataset required |
| **Expected Output** | Method returns `true` (client-side bug: does not detect server error); no exception |
| **Pass Criteria** | - Method returns `true` <br>- No exception thrown |
| **Notes** | Documents the client bug where server-side validation failures are not translated to `false` return values. Omit file pre-creation in setup since no valid file path is provided. |

---

#### EHT-004 — Empty `fileName` — server returns "Destination path not specified"

| Field | Detail |
|---|---|
| **Test ID** | EHT-004 |
| **Category** | Error Handling |
| **Subcategory** | Invalid Inputs — empty required field |
| **Description** | Same as EHT-003 but with `fileName = ""`. |
| **Environment Requirements** | `any` |
| **Input Data** | `fileName = ""`, everything else valid |
| **Dataset** | No external dataset required |
| **Expected Output** | Method returns `true` (same client-side gap as EHT-003) |
| **Pass Criteria** | - Return value is `true` <br>- No exception |
| **Notes** | Same bug documentation as EHT-003 |

---

#### EHT-005 — Null `targetLandingZone` — server returns "Destination not specified"

| Field | Detail |
|---|---|
| **Test ID** | EHT-005 |
| **Category** | Error Handling |
| **Subcategory** | Invalid Inputs — missing required field |
| **Description** | Pass `targetLandingZone = null`. The server receives null `DestDropZone` and returns `"Destination not specified"`. Client returns `true`. |
| **Environment Requirements** | `any` |
| **Input Data** | `data = "test".getBytes()`, `dataTypeDescriptor = "text/plain"`, `fileName = "eht005.txt"`, `targetLandingZone = null`, `append = true`, `offset = 0`, `uploadchunksize = 100`, `lzAddress = isContainerized ? null : targetLZAddress` |
| **Dataset** | No external dataset required |
| **Expected Output** | Method returns `true` |
| **Pass Criteria** | - Return value is `true` <br>- No exception |
| **Notes** | Documents server-side error not surfaced as client-side failure |

---

#### EHT-006 — Empty `targetLandingZone`

| Field | Detail |
|---|---|
| **Test ID** | EHT-006 |
| **Category** | Error Handling |
| **Subcategory** | Invalid Inputs — empty required field |
| **Description** | Same as EHT-005 but with `targetLandingZone = ""`. |
| **Environment Requirements** | `any` |
| **Input Data** | `targetLandingZone = ""`, everything else valid |
| **Dataset** | No external dataset required |
| **Expected Output** | Method returns `true` |
| **Pass Criteria** | - Return value is `true` <br>- No exception |
| **Notes** | Parallel to EHT-005 |

---

#### EHT-007 — Write to a file that does not exist on the landing zone

| Field | Detail |
|---|---|
| **Test ID** | EHT-007 |
| **Category** | Error Handling |
| **Subcategory** | Server-Side Errors — resource not found |
| **Description** | Call `writeHPCCFileData` without first calling `createHPCCFile`. The server returns `"<path> does not exist."`. The client returns `true` (bug). |
| **Environment Requirements** | `any` |
| **Input Data** | `data = "orphan".getBytes()`, `dataTypeDescriptor = "text/plain"`, `fileName = "nonexistent_" + System.currentTimeMillis() + ".txt"`, `targetLandingZone = targetLZ`, `append = true`, `offset = 0`, `uploadchunksize = 100`, `lzAddress = isContainerized ? null : targetLZAddress` |
| **Dataset** | No external dataset required |
| **Expected Output** | Method returns `true` (due to client bug); no exception thrown |
| **Pass Criteria** | - Return value is `true` <br>- No exception |
| **Notes** | Documents that the client silently ignores a "file not found" result from the server. If the bug is fixed, this test should expect `false`. |

---

#### EHT-008 — Invalid `lzAddress` (non-existent host)

| Field | Detail |
|---|---|
| **Test ID** | EHT-008 |
| **Category** | Error Handling |
| **Subcategory** | Server-Side Errors — invalid drop zone address |
| **Description** | Provide an invalid `lzAddress` (e.g. `"999.999.999.999"`) in a bare-metal environment. The server's `validateDropZoneAccess` should reject the request with a permission/validation error. |
| **Environment Requirements** | `baremetal` |
| **Input Data** | `data = "invalid-lz".getBytes()`, `dataTypeDescriptor = "text/plain"`, `fileName = "<timestamp>_eht008.txt"`, `targetLandingZone = targetLZ`, `append = true`, `offset = 0`, `uploadchunksize = 100`, `lzAddress = "999.999.999.999"` |
| **Dataset** | No external dataset required |
| **Expected Output** | `ArrayOfEspExceptionWrapper` thrown, or method returns `false` |
| **Pass Criteria** | - Either an exception is thrown OR method returns `false` <br>- No data written |
| **Notes** | Use `assumeFalse("Not bare-metal", isContainerized)` to skip in containerized environments |

---

#### EHT-009 — Write via client with invalid connection (stub not initialized)

| Field | Detail |
|---|---|
| **Test ID** | EHT-009 |
| **Category** | Error Handling |
| **Subcategory** | Client-Side Errors — invalid connection |
| **Description** | Construct an `HPCCWsFileIOClient` with an invalid host/port and call `writeHPCCFileData`. Expect an `Exception` from `verifyStub()` or from the network layer. |
| **Environment Requirements** | `any` |
| **Input Data** | `HPCCWsFileIOClient badClient = HPCCWsFileIOClient.get("http", "invalid.host.invalid", "9999", "user", "pass")`, then call `badClient.writeHPCCFileData("test".getBytes(), "text/plain", "f.txt", "lz", true, 0, 100, null)` |
| **Dataset** | No external dataset required |
| **Expected Output** | `Exception` thrown (connection refused, host not found, or stub verification failure) |
| **Pass Criteria** | - An exception is thrown (any subclass of `Exception`) <br>- Method does not return `true` |
| **Notes** | Uses `assertThrows(Exception.class, ...)` |

---

#### EHT-010 — Unauthorized write (invalid credentials)

| Field | Detail |
|---|---|
| **Test ID** | EHT-010 |
| **Category** | Error Handling |
| **Subcategory** | Server-Side Errors — permission denied |
| **Description** | Attempt to write using a client constructed with deliberately wrong credentials. Server should reject with an authorization error. |
| **Environment Requirements** | `secure` |
| **Input Data** | `HPCCWsFileIOClient unauthorizedClient = HPCCWsFileIOClient.get(protocol, host, port, "baduser", "badpassword")`, `data = "auth-test".getBytes()`, `fileName = "<timestamp>_eht010.txt"`, `targetLandingZone = targetLZ` |
| **Dataset** | No external dataset required |
| **Expected Output** | `ArrayOfEspExceptionWrapper` thrown with code `5149` (ECLWATCH_ACCESS_TO_FILE_DENIED) or HTTP 401 |
| **Pass Criteria** | - Exception is thrown <br>- Method does not return `true` |
| **Notes** | Use `assumeTrue` to skip if the cluster is unsecured (no security plugin) |

---

### D. Connectivity Tests

---

#### CNT-001 — Service reachability via minimal write attempt

| Field | Detail |
|---|---|
| **Test ID** | CNT-001 |
| **Category** | Connectivity |
| **Subcategory** | Reachability |
| **Description** | Confirm the WsFileIO service is reachable by executing a valid minimal `writeHPCCFileData` call and verifying it completes without a connection-level exception. This is distinct from the existing `ping()` test in that it exercises the full `writeFileData` SOAP operation path. |
| **Environment Requirements** | `any` |
| **Input Data** | `data = "ping-write".getBytes()`, `dataTypeDescriptor = "text/plain"`, `fileName = "<timestamp>_cnt001.txt"`, `targetLandingZone = targetLZ`, `append = true`, `offset = 0`, `uploadchunksize = 100`, `lzAddress = isContainerized ? null : targetLZAddress` |
| **Dataset** | No external dataset required |
| **Expected Output** | Method returns `true` or a server-level error; no `AxisFault` or `ConnectException` |
| **Pass Criteria** | - No connectivity-level exception (`AxisFault`, `ConnectException`, `UnknownHostException`) is thrown <br>- Method completes (returns any boolean) |
| **Notes** | Pre-create file with `createHPCCFile` before this test; this is a reachability smoke test for the `WriteFileData` operation specifically |

---

#### CNT-002 — Valid credentials succeed

| Field | Detail |
|---|---|
| **Test ID** | CNT-002 |
| **Category** | Connectivity |
| **Subcategory** | Valid Authentication |
| **Description** | Confirm that a write request using the configured valid credentials (`hpccUser`/`hpccPass`) succeeds. |
| **Environment Requirements** | `secure` |
| **Input Data** | Standard valid `writeHPCCFileData` call using `hpccUser` and `hpccPass` from system properties |
| **Dataset** | No external dataset required |
| **Expected Output** | Method returns `true` |
| **Pass Criteria** | - Return value is `true` <br>- No authentication exception |
| **Notes** | Use `assumeTrue` to skip if cluster is unsecured |

---

#### CNT-003 — Invalid credentials are rejected

| Field | Detail |
|---|---|
| **Test ID** | CNT-003 |
| **Category** | Connectivity |
| **Subcategory** | Invalid Authentication |
| **Description** | Confirm that a write request with bad credentials is rejected at the authentication layer. |
| **Environment Requirements** | `secure` |
| **Input Data** | `HPCCWsFileIOClient` built with `("wronguser", "wrongpassword")`; same write parameters as CNT-001 |
| **Dataset** | No external dataset required |
| **Expected Output** | Exception thrown (auth error) or `false` returned |
| **Pass Criteria** | - Either exception thrown OR return `false` <br>- No data written |
| **Notes** | Equivalent to EHT-010 from an auth perspective; included here for completeness of the connectivity matrix. Use `assumeTrue` to skip if cluster is unsecured. |

---

## 8. New Dataset Specifications

No new HPCC logical datasets are required for `writeHPCCFileData` tests. All test payloads are constructed as in-memory `byte[]` arrays within the test code itself. Landing zone files are created at test runtime via `createHPCCFile` as part of each test's setup.

> **No additions to `generate-datasets.ecl` are required for these tests.**

---

## Appendix: Test Class Setup Guidance

All tests should be placed in `WSFileIOClientTest.java` (already exists at `wsclient/src/test/java/org/hpccsystems/ws/client/WSFileIOClientTest.java`) and must extend `BaseRemoteTest`.

### Recommended Helper Methods

```java
/** Creates and returns a unique LZ filename for a given test ID. */
private String uniqueLzFile(String testId) {
    return System.currentTimeMillis() + "_" + testId + ".bin";
}

/** Creates a file on the LZ and optionally deletes it in cleanup. */
private void ensureLzFile(String fileName) throws Exception {
    client.createHPCCFile(fileName, targetLZ, true,
        isContainerized ? null : targetLZAddress);
}

/** Deletes a file from the LZ if it exists (best-effort, ignore failures). */
private void cleanupLzFile(String fileName) {
    try {
        // Call deleteFile via available API if accessible, or use a shell command.
        // If no direct delete API exists in HPCCWsFileIOClient, skip LZ cleanup.
    } catch (Exception ignored) {}
}
```

### Test Ordering Considerations
- Tests using `createHPCCFile` + `writeHPCCFileData` + `readFileData` should manage their own file lifecycle (create in setup, clean up in `finally`).
- Error-handling tests (EHT-003 through EHT-007) that do **not** pre-create files should NOT call `ensureLzFile`.
- The existing `@FixMethodOrder(MethodSorters.NAME_ASCENDING)` ordering is maintained by the class; name new methods with a consistent prefix if ordering matters (e.g. `DwriteWithMimeType_CFT001`).

### Environment Properties Reference

| System Property | Default | Used For |
|---|---|---|
| `hpccconn` | `http://localhost:8010` | Connection string |
| `lztestfile` | `myfilename.txt` | Pre-existing test file name |
| `lzname` | `mydropzone` | Landing zone name |
| `lzpath` | `/var/lib/HPCCSystems/mydropzone` | LZ file system path |
| `lzaddress` | `.` | LZ net address (bare-metal) |
| `hpccuser` | `JunitUser` | Auth username |
| `hpccpass` | `` (empty) | Auth password |


---
*Generated: 2026-03-26*
