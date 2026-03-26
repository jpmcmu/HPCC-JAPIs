# WsFileIO `createHPCCFile` — Test Case Analysis

**Generated:** 2026-03-26  
**Target Method:** `HPCCWsFileIOClient.createHPCCFile`  
**Service:** `WsFileIO` (ESP service, C++ backend)  
**Test File:** `wsclient/src/test/java/org/hpccsystems/ws/client/WSFileIOClientTest.java`

---

## 1. Method Summary

### Purpose
`createHPCCFile` creates an empty file on a specified HPCC Landing Zone (drop zone). It is the first step in a typical file-upload workflow: create the empty file, then write data into it using `writeHPCCFileData`.

### Role Within Service
WsFileIO exposes three operations — `CreateFile`, `WriteFileData`, and `ReadFileData`. `createHPCCFile` corresponds to `CreateFile` and must be called before writing data to the landing zone file.

### Signatures

**Current (preferred):**
```java
@WithSpan
public boolean createHPCCFile(
    @SpanAttribute String fileName,
    @SpanAttribute String targetLandingZone,
    boolean overwritefile,
    @SpanAttribute String lzAddress)
throws Exception, ArrayOfEspExceptionWrapper
```

**Deprecated (delegates to current with `lzAddress = null`):**
```java
public boolean createHPCCFile(String fileName, String targetLandingZone, boolean overwritefile)
throws Exception, ArrayOfEspExceptionWrapper
```

### Inputs
| Parameter | Type | Description |
|-----------|------|-------------|
| `fileName` | `String` | Relative path/name of the file to create on the landing zone |
| `targetLandingZone` | `String` | Drop zone name (not an IP address — fetched from `FileSprayClient`) |
| `overwritefile` | `boolean` | Whether to overwrite the file if it already exists |
| `lzAddress` | `String` (nullable) | Network address of the landing zone; omitted for containerized deployments |

### Outputs / Side Effects
- Returns `true` if the file was created successfully (result string does **not** start with `"Fail"`).
- Returns `false` if the server reported a failure.
- Throws `Exception` for client-side validation failures or `RemoteException` from transport.
- Throws `ArrayOfEspExceptionWrapper` for server-reported ESP exceptions.
- Creates an empty file at `<dropzone-base-path>/<fileName>` on the target landing zone node.

### Success-Detection Logic
```java
if (!result.startsWith("Fail")) success = true;
```
Success/failure is determined by string prefix, not by an explicit status code.

---

## 2. Existing Test Coverage Analysis

### Existing Tests for `createHPCCFile`

| Existing Test Method Name | Test Category | Scenario Covered | Input Data Summary | Pass Criteria | Notes |
|---------------------------|---------------|------------------|--------------------|---------------|-------|
| `AcreateHPCCFile()` | CFT — Basic Operation | Creates a file with `overwrite=true`; branches on containerized vs. non-containerized | `fileName=testfilename` (`myfilename.txt`), `targetLZ`, `overwrite=true`; `lzAddress=null` (containerized) or `targetLZAddress` (bare-metal) | `assertTrue(result)` | Only covers the happy path with `overwrite=true`; no negative cases |
| `copyFile()` | CFT — Typical Workflow | Uses `createHPCCFile` as a helper step to set up a CSV file before spraying | `lzfile = <timestamp>_csvtest.csv`, `targetLZ`, `overwrite=true`, containerized-aware | File spray and copy succeed | `createHPCCFile` is not the focal point; it is used as setup infrastructure |

### Coverage Summary

- **Total existing test methods that exercise `createHPCCFile`:** 2  
- **Core Functionality Tests covered:** 1 (basic creation, overwrite=true, containerized and bare-metal branches)  
- **Edge Case Tests covered:** 0  
- **Error Handling Tests covered:** 0  
- **Connectivity Tests covered:** 0 (a general `ping()` test exists but is not specific to `createHPCCFile`)

### Gaps Identified

1. `overwrite=false` — file does not yet exist (should succeed)
2. `overwrite=false` — file already exists (server should return `"Failure: <path> exists."`, method returns `false`)
3. `overwrite=true` — file already exists (should succeed; overwrites)
4. `fileName` is `null` — client should throw `Exception` before contacting server
5. `fileName` is empty string — client should throw `Exception` before contacting server
6. `targetLandingZone` is `null` — client should throw `Exception` before contacting server
7. `targetLandingZone` is empty string — client should throw `Exception` before contacting server
8. `fileName` with subdirectory path (e.g., `subdir/file.txt`)
9. `fileName` with no extension (e.g., `datafile`)
10. `fileName` with special characters (e.g., spaces, hyphens, underscores)
11. Very long file name (boundary value)
12. Invalid / non-existent drop zone name (server-side validation failure)
13. `lzAddress` is empty string (should be treated same as `null` by the client)
14. Invalid `lzAddress` — server cannot resolve host
15. Server returns an ESP exception in the response
16. Authentication failure (invalid credentials) — connectivity category
17. Connectivity / reachability with valid credentials — CNT

---

## 3. Request Structure

### `CreateFileRequest` Fields

| Field Name | Type | Required | Description | Valid Range / Format | Notes |
|------------|------|----------|-------------|----------------------|-------|
| `DestDropZone` | `string` | Yes | Landing zone name (or IP address — see server comment) | Non-empty string; must match a configured drop zone | Client validates non-null/non-empty; server returns `"Destination not specified"` if absent |
| `DestRelativePath` | `string` | Yes | File name and/or relative path within the drop zone base directory | Non-empty string; must not resolve to a directory | Client validates non-null/non-empty; server returns `"Destination path not specified"` if absent |
| `DestNetAddress` | `string` | No (added v1.01) | Network address of the landing zone machine | IP address or hostname | Only sent when `lzAddress` is non-null and non-empty; omitted for containerized clusters |
| `Overwrite` | `bool` | No (default `false`) | If `true`, overwrite an existing file | `true` / `false` | Server checks this before creating; if `false` and file exists, returns failure |

### Field Dependencies / Conditional Requirements

- `DestNetAddress` is only meaningful in bare-metal deployments where the drop zone is on a specific host.  
- In containerized HPCC, `DestNetAddress` should be omitted (`null` / not set).  
- `DestDropZone` and `DestRelativePath` are both required; sending either as null/empty causes a client-side `Exception` before the request is sent.

### Default Behavior for Optional Fields

| Field | Default | Behavior when omitted |
|-------|---------|----------------------|
| `DestNetAddress` | `null` (not set) | Server uses the drop zone's configured address |
| `Overwrite` | `false` | File creation fails if the target path already exists |

---

## 4. Server Behavior and Responses

### Processing Logic (`CWsFileIOEx::onCreateFile`)

1. **Permission check** — `context.ensureFeatureAccess(FILE_IO_URL, SecAccess_Write, ...)`. If insufficient permission, throws an ESP exception with code `ECLWATCH_ACCESS_TO_FILE_DENIED`.
2. **Validate `DestDropZone`** — Returns `"Destination not specified"` if empty/null.
3. **Validate `DestRelativePath`** — Returns `"Destination path not specified"` if empty/null.
4. **Validate drop zone access** — Calls `validateDropZoneAccess(...)`. May throw if the drop zone name is invalid or the net address doesn't match.
5. **Check if path is a directory** — If `file->isDirectory() == foundYes`, returns `"Failure: <path> is a directory."`.
6. **Check overwrite flag** — If `!Overwrite && file != notFound`, returns `"Failure: <path> exists."`.
7. **Create file** — `file->open(IFOcreate)`.
8. **Return success** — Returns `"<path> has been created."`.

### Response Fields (`CreateFileResponse`)

| Field | Type | Meaning |
|-------|------|---------|
| `DestDropZone` | `string` | Echo of request field |
| `DestRelativePath` | `string` | Echo of request field |
| `Overwrite` | `bool` | Echo of request field |
| `Result` | `string` | Human-readable outcome; starts with `"Fail"` on any server-side failure |
| `Exceptions` | `ArrayOfEspException` | Populated on permission errors or unexpected server exceptions |

### Result String Patterns

| Result String Pattern | Meaning |
|-----------------------|---------|
| `"<path> has been created."` | Success |
| `"Destination not specified"` | Server received empty `DestDropZone` |
| `"Destination path not specified"` | Server received empty `DestRelativePath` |
| `"Failure: <path> is a directory."` | Resolved path is a directory |
| `"Failure: <path> exists."` | File exists and `Overwrite=false` |

---

## 5. Error Handling

### Server-Side Errors

| Scenario | Server Response | Java Client Behavior |
|----------|-----------------|----------------------|
| Insufficient write permission | ESP exception with `ECLWATCH_ACCESS_TO_FILE_DENIED` | `handleEspExceptions(...)` throws `ArrayOfEspExceptionWrapper` |
| `DestDropZone` empty/null (bypassed client check) | `result = "Destination not specified"` | Returns `false` (does not start with "Fail" — **actually returns `true`!** — see Note below) |
| `DestRelativePath` empty/null (bypassed client check) | `result = "Destination path not specified"` | Returns `true` (same prefix issue) |
| Path resolves to a directory | `result = "Failure: <path> is a directory."` | Returns `false` |
| File exists, `Overwrite=false` | `result = "Failure: <path> exists."` | Returns `false` |
| Invalid drop zone name | Throws ESP exception via `validateDropZoneAccess` | Propagates as `ArrayOfEspExceptionWrapper` |

> ⚠️ **Note on "Destination not specified" / "Destination path not specified":** These result strings do **not** begin with `"Fail"`, so the Java client's check `if (!result.startsWith("Fail"))` will treat them as **success** and return `true`. This is a known client-side logic limitation.

### Client-Side Errors

| Scenario | Thrown Exception | Message |
|----------|-----------------|---------|
| `targetLandingZone` null or empty | `Exception` | `"HPCCWsFileIOClient::createHPCCFile: targetLandingZone required!"` |
| `fileName` null or empty | `Exception` | `"HPCCWsFileIOClient::createHPCCFile: fileName required!"` |
| Stub initialization failure | `Exception` (from `verifyStub()`) | Stub-specific message |
| Transport-level failure | `Exception` wrapping `RemoteException` | `"HPCCWsDFUClient.createHPCCFile(...) encountered RemoteException."` |
| ESP SOAP fault | `ArrayOfEspExceptionWrapper` (via `handleEspSoapFaults`) | SOAP fault details |

---

## 6. Existing Dataset Analysis

The `createHPCCFile` method creates an **empty file** on a landing zone — it does not read from or write to any HPCC logical dataset. Therefore, none of the benchmark datasets are directly used as input to this method. They are relevant only when datasets are sprayed *after* the file is created (as in the `copyFile` test).

| Dataset Name | Applicable? | Reason |
|--------------|-------------|--------|
| `~benchmark::all_types::200KB` | No | This method creates an empty landing-zone file; no HPCC dataset is read or written |
| `~benchmark::string::100MB` | No | Same reason as above |
| `~benchmark::integer::20KB` | No | Same reason as above |
| `~benchmark::all_types::superfile` | No | Same reason as above |
| `~benchmark::integer::20kb::key` | No | Same reason as above |

All test cases below use dynamically generated unique file names (via `System.currentTimeMillis()`) to avoid cross-test interference. No new HPCC datasets are required.

---

## 7. Test Case Plan

> Only scenarios **not already covered** by `AcreateHPCCFile()` or `copyFile()` are listed here.

---

### A. Core Functionality Tests

---

#### CFT-001 — Overwrite=false, New File (Does Not Exist)

| Field | Detail |
|-------|--------|
| **Test ID** | CFT-001 |
| **Category** | Core Functionality |
| **Subcategory** | Basic Operation |
| **Description** | Create a new file with `overwrite=false`. Since the file doesn't pre-exist, creation should succeed. |
| **Environment Requirements** | `any` |
| **Input Data** | `fileName`: unique timestamped `.txt` name, `targetLandingZone`: configured LZ name, `overwritefile`: `false`, `lzAddress`: containerized→`null`, bare-metal→`targetLZAddress` |
| **Dataset** | N/A — no dataset required |
| **Expected Output** | `true` |
| **Pass Criteria** | Return value is `true`; no exception thrown |
| **Notes** | Generates a fresh unique name to guarantee the file does not pre-exist. Clean up the created file via LZ delete or subsequent overwrite. |

---

#### CFT-002 — Overwrite=true, File Already Exists

| Field | Detail |
|-------|--------|
| **Test ID** | CFT-002 |
| **Category** | Core Functionality |
| **Subcategory** | Data Variations |
| **Description** | Create a file, then create it again with `overwrite=true`. The second call should succeed. |
| **Environment Requirements** | `any` |
| **Input Data** | Same `fileName` used in both calls, `overwrite=true` in second call |
| **Dataset** | N/A |
| **Expected Output** | Both calls return `true` |
| **Pass Criteria** | Second call returns `true` without exception; no `"Failure: ... exists."` in result |
| **Notes** | Depends on `AcreateHPCCFile` pattern; use unique timestamp-based name |

---

#### CFT-003 — Deprecated 3-Parameter Overload

| Field | Detail |
|-------|--------|
| **Test ID** | CFT-003 |
| **Category** | Core Functionality |
| **Subcategory** | Typical Workflow |
| **Description** | Invoke the deprecated `createHPCCFile(fileName, targetLZ, overwrite)` signature (no `lzAddress`). Should delegate to the 4-parameter version with `lzAddress=null`. |
| **Environment Requirements** | `containerized` |
| **Input Data** | `fileName`: unique name, `targetLandingZone`: LZ name, `overwritefile`: `true` |
| **Dataset** | N/A |
| **Expected Output** | `true` |
| **Pass Criteria** | Return value is `true`; no exception; verifies backward compatibility of the deprecated API |
| **Notes** | Only meaningful for containerized deployments where `null` lzAddress is valid |

---

#### CFT-004 — lzAddress Explicitly Null (Containerized Deployment)

| Field | Detail |
|-------|--------|
| **Test ID** | CFT-004 |
| **Category** | Core Functionality |
| **Subcategory** | Data Variations |
| **Description** | Explicitly pass `lzAddress=null` with the 4-parameter signature in a containerized environment. |
| **Environment Requirements** | `containerized` |
| **Input Data** | `fileName`: unique name, `targetLandingZone`: LZ, `overwrite`: `true`, `lzAddress`: `null` |
| **Dataset** | N/A |
| **Expected Output** | `true` |
| **Pass Criteria** | No `DestNetAddress` is set on the request; server accepts it; method returns `true` |
| **Notes** | Distinct from `AcreateHPCCFile` because it is an explicit null-address test without environment branching |

---

### B. Edge Case Tests

---

#### ECT-001 — File Name With Subdirectory Path

| Field | Detail |
|-------|--------|
| **Test ID** | ECT-001 |
| **Category** | Edge Case |
| **Subcategory** | Unusual Valid Inputs |
| **Description** | Use a `fileName` that includes a subdirectory component (e.g., `"subdir/testfile.txt"`). Verifies server handles path separators in `DestRelativePath`. |
| **Environment Requirements** | `any` |
| **Input Data** | `fileName`: `"subdir/<timestamp>.txt"`, `targetLandingZone`: LZ, `overwrite`: `true`, `lzAddress`: environment-appropriate |
| **Dataset** | N/A |
| **Expected Output** | `true` if the subdirectory exists; otherwise a server-side failure result |
| **Pass Criteria** | Method returns without throwing an unexpected exception; result is handled gracefully (either `true` or `false` with logged message) |
| **Notes** | Outcome depends on whether the subdirectory pre-exists on the LZ; document actual behavior |

---

#### ECT-002 — File Name With No Extension

| Field | Detail |
|-------|--------|
| **Test ID** | ECT-002 |
| **Category** | Edge Case |
| **Subcategory** | Unusual Valid Inputs |
| **Description** | Use a `fileName` with no file extension (e.g., `"nodotfile"`). |
| **Environment Requirements** | `any` |
| **Input Data** | `fileName`: `"<timestamp>_nodotfile"`, `overwrite`: `true` |
| **Dataset** | N/A |
| **Expected Output** | `true` |
| **Pass Criteria** | Server creates the file; method returns `true` |
| **Notes** | Validates that the server does not enforce an extension requirement |

---

#### ECT-003 — File Name With Special Characters

| Field | Detail |
|-------|--------|
| **Test ID** | ECT-003 |
| **Category** | Edge Case |
| **Subcategory** | Unusual Valid Inputs |
| **Description** | Use a `fileName` containing hyphens and underscores (e.g., `"my-test_file.dat"`). |
| **Environment Requirements** | `any` |
| **Input Data** | `fileName`: `"<timestamp>_my-test_file.dat"`, `overwrite`: `true` |
| **Dataset** | N/A |
| **Expected Output** | `true` |
| **Pass Criteria** | Method returns `true`; no exception |
| **Notes** | Tests common naming patterns that include separators |

---

#### ECT-004 — lzAddress as Empty String

| Field | Detail |
|-------|--------|
| **Test ID** | ECT-004 |
| **Category** | Edge Case |
| **Subcategory** | Optional Parameters |
| **Description** | Pass an empty string `""` as `lzAddress`. The client should skip setting `DestNetAddress` (same behavior as `null`). |
| **Environment Requirements** | `any` |
| **Input Data** | `fileName`: unique name, `targetLandingZone`: LZ, `overwrite`: `true`, `lzAddress`: `""` |
| **Dataset** | N/A |
| **Expected Output** | `true` |
| **Pass Criteria** | Method returns `true`; `DestNetAddress` is NOT included in request (verified by successful response) |
| **Notes** | Client code: `if (lzAddress != null && !lzAddress.isEmpty())` — empty string is explicitly excluded |

---

#### ECT-005 — Very Long File Name (Boundary)

| Field | Detail |
|-------|--------|
| **Test ID** | ECT-005 |
| **Category** | Edge Case |
| **Subcategory** | Boundary Values |
| **Description** | Use a very long file name (255 characters) to test file system and server limits. |
| **Environment Requirements** | `any` |
| **Input Data** | `fileName`: string of 255 `a` characters + `.txt`, `overwrite`: `true` |
| **Dataset** | N/A |
| **Expected Output** | `true` if OS supports it; failure otherwise |
| **Pass Criteria** | Method does not throw an unexpected unchecked exception; either returns `true` or `false` with a meaningful result |
| **Notes** | Most Linux file systems support up to 255 bytes per path component |

---

#### ECT-006 — File Name That Is Just a Single Character

| Field | Detail |
|-------|--------|
| **Test ID** | ECT-006 |
| **Category** | Edge Case |
| **Subcategory** | Boundary Values |
| **Description** | Use a minimal single-character file name (e.g., `"x"`) to test the lower boundary of the `DestRelativePath` field. |
| **Environment Requirements** | `any` |
| **Input Data** | `fileName`: `"x"`, `overwrite`: `true` |
| **Dataset** | N/A |
| **Expected Output** | `true` |
| **Pass Criteria** | File is created; method returns `true` |
| **Notes** | Clean up afterward |

---

### C. Error Handling Tests

---

#### EHT-001 — fileName is Null

| Field | Detail |
|-------|--------|
| **Test ID** | EHT-001 |
| **Category** | Error Handling |
| **Subcategory** | Invalid Inputs — Missing required field |
| **Description** | Pass `null` as `fileName`. Client should throw an `Exception` before making any network call. |
| **Environment Requirements** | `any` |
| **Input Data** | `fileName`: `null`, `targetLandingZone`: valid LZ, `overwrite`: `true`, `lzAddress`: environment-appropriate |
| **Dataset** | N/A |
| **Expected Output** | `Exception` thrown |
| **Pass Criteria** | `Exception` is thrown with message containing `"fileName required"` |
| **Notes** | No server contact should occur |

---

#### EHT-002 — fileName is Empty String

| Field | Detail |
|-------|--------|
| **Test ID** | EHT-002 |
| **Category** | Error Handling |
| **Subcategory** | Invalid Inputs — Missing required field |
| **Description** | Pass empty string `""` as `fileName`. Client should throw an `Exception`. |
| **Environment Requirements** | `any` |
| **Input Data** | `fileName`: `""`, `targetLandingZone`: valid LZ, `overwrite`: `true` |
| **Dataset** | N/A |
| **Expected Output** | `Exception` thrown |
| **Pass Criteria** | `Exception` message contains `"fileName required"` |
| **Notes** | No server contact should occur |

---

#### EHT-003 — targetLandingZone is Null

| Field | Detail |
|-------|--------|
| **Test ID** | EHT-003 |
| **Category** | Error Handling |
| **Subcategory** | Invalid Inputs — Missing required field |
| **Description** | Pass `null` as `targetLandingZone`. Client should throw an `Exception` before network call. |
| **Environment Requirements** | `any` |
| **Input Data** | `fileName`: valid name, `targetLandingZone`: `null`, `overwrite`: `true` |
| **Dataset** | N/A |
| **Expected Output** | `Exception` thrown |
| **Pass Criteria** | `Exception` message contains `"targetLandingZone required"` |
| **Notes** | Checked first (before `fileName`) in the current implementation |

---

#### EHT-004 — targetLandingZone is Empty String

| Field | Detail |
|-------|--------|
| **Test ID** | EHT-004 |
| **Category** | Error Handling |
| **Subcategory** | Invalid Inputs — Missing required field |
| **Description** | Pass empty string `""` as `targetLandingZone`. Client should throw an `Exception`. |
| **Environment Requirements** | `any` |
| **Input Data** | `fileName`: valid name, `targetLandingZone`: `""`, `overwrite`: `true` |
| **Dataset** | N/A |
| **Expected Output** | `Exception` thrown |
| **Pass Criteria** | `Exception` message contains `"targetLandingZone required"` |
| **Notes** | No server contact should occur |

---

#### EHT-005 — Overwrite=false, File Already Exists

| Field | Detail |
|-------|--------|
| **Test ID** | EHT-005 |
| **Category** | Error Handling |
| **Subcategory** | Server-Side Errors — Validation failure |
| **Description** | Create a file, then attempt to create the same file again with `overwrite=false`. Server should return `"Failure: <path> exists."` and method should return `false`. |
| **Environment Requirements** | `any` |
| **Input Data** | Same unique `fileName` for both calls; `overwrite=false` on second call |
| **Dataset** | N/A |
| **Expected Output** | Second call returns `false` |
| **Pass Criteria** | Second call returns `false`; no exception thrown; logged result contains `"Failure"` |
| **Notes** | First call must use `overwrite=true` to ensure the file exists. Represents the most common user error. |

---

#### EHT-006 — fileName Resolves to a Directory Path

| Field | Detail |
|-------|--------|
| **Test ID** | EHT-006 |
| **Category** | Error Handling |
| **Subcategory** | Server-Side Errors — Validation failure |
| **Description** | Pass a `fileName` that corresponds to an existing directory on the landing zone. Server should return `"Failure: <path> is a directory."` and method returns `false`. |
| **Environment Requirements** | `any` |
| **Input Data** | `fileName`: path of an existing directory on the LZ (e.g., the LZ root directory name) |
| **Dataset** | N/A |
| **Expected Output** | `false` |
| **Pass Criteria** | Returns `false`; no exception thrown; result logged contains `"Failure"` and `"is a directory"` |
| **Notes** | The LZ root directory itself can be used if it is a known path component |

---

#### EHT-007 — Invalid Drop Zone Name

| Field | Detail |
|-------|--------|
| **Test ID** | EHT-007 |
| **Category** | Error Handling |
| **Subcategory** | Server-Side Errors — Resource not found |
| **Description** | Pass a `targetLandingZone` that does not correspond to any configured drop zone. Server should fail via `validateDropZoneAccess`. |
| **Environment Requirements** | `any` |
| **Input Data** | `targetLandingZone`: `"nonexistent_dropzone_xyz"`, `fileName`: valid name |
| **Dataset** | N/A |
| **Expected Output** | `ArrayOfEspExceptionWrapper` thrown OR `false` returned |
| **Pass Criteria** | Either an exception is thrown or the method returns `false`; no silent success |
| **Notes** | Documents whether the server returns a result string starting with "Fail" or raises an ESP exception |

---

#### EHT-008 — Invalid lzAddress (Unreachable Host)

| Field | Detail |
|-------|--------|
| **Test ID** | EHT-008 |
| **Category** | Error Handling |
| **Subcategory** | Server-Side Errors — Resource not found |
| **Description** | Pass an `lzAddress` that cannot be reached (e.g., `"192.0.2.1"` — a TEST-NET address). Server should fail during drop zone access validation. |
| **Environment Requirements** | `baremetal` |
| **Input Data** | `lzAddress`: `"192.0.2.1"`, `targetLandingZone`: valid LZ, `fileName`: valid name |
| **Dataset** | N/A |
| **Expected Output** | Exception thrown or `false` returned |
| **Pass Criteria** | No silent success; error is surfaced to caller |
| **Notes** | Only relevant on bare-metal where `DestNetAddress` is used for routing |

---

### D. Connectivity Tests

---

#### CNT-001 — Service Reachability and Basic Success

| Field | Detail |
|-------|--------|
| **Test ID** | CNT-001 |
| **Category** | Connectivity |
| **Subcategory** | Reachability |
| **Description** | Confirm `createHPCCFile` returns a non-exception response when called with valid minimal inputs against a live cluster. |
| **Environment Requirements** | `any` |
| **Input Data** | `fileName`: unique timestamped name, `targetLandingZone`: configured LZ, `overwrite`: `true`, `lzAddress`: environment-appropriate |
| **Dataset** | N/A |
| **Expected Output** | `true` |
| **Pass Criteria** | No exception; result is `true`; service endpoint is reachable |
| **Notes** | Complements the existing `ping()` test with a method-specific connectivity check |

---

#### CNT-002 — Invalid Credentials (Authentication Failure)

| Field | Detail |
|-------|--------|
| **Test ID** | CNT-002 |
| **Category** | Connectivity |
| **Subcategory** | Invalid auth |
| **Description** | Call `createHPCCFile` using a client configured with invalid credentials. Expects an authentication error. |
| **Environment Requirements** | `secure` |
| **Input Data** | Client with username `"invaliduser"`, password `"wrongpass"`; `fileName`: valid name, `targetLandingZone`: valid LZ |
| **Dataset** | N/A |
| **Expected Output** | `ArrayOfEspExceptionWrapper` thrown or `AxisFault` with HTTP 401/403 |
| **Pass Criteria** | An authentication-related exception is thrown; method does not return `true` |
| **Notes** | Only run when `secure` environment is available with authentication plugin enabled |

---

#### CNT-003 — Valid Credentials on Secured Cluster

| Field | Detail |
|-------|--------|
| **Test ID** | CNT-003 |
| **Category** | Connectivity |
| **Subcategory** | Valid auth |
| **Description** | Confirm that `createHPCCFile` succeeds when valid credentials are provided on a secured cluster. |
| **Environment Requirements** | `secure` |
| **Input Data** | Client with valid credentials; `fileName`: unique name, `targetLandingZone`: valid LZ, `overwrite`: `true` |
| **Dataset** | N/A |
| **Expected Output** | `true` |
| **Pass Criteria** | Returns `true`; no auth exception thrown |
| **Notes** | Validates that the permission check in `onCreateFile` passes for authorized users |

---

## 8. New Dataset Specifications

**No new datasets are required** for any test case in this analysis.

All test cases exercise the file-creation behavior of `createHPCCFile`, which operates exclusively on landing zone files (not HPCC logical datasets). Test data consists of dynamically generated unique file names that do not depend on any pre-existing HPCC dataset.

No changes to `wsclient/src/test/resources/generate-datasets.ecl` are needed.

---

## Test Case Summary Table

| Test ID | Category | Subcategory | Key Scenario | Env |
|---------|----------|-------------|--------------|-----|
| CFT-001 | Core Functionality | Basic Operation | `overwrite=false`, new file | any |
| CFT-002 | Core Functionality | Data Variations | `overwrite=true`, file pre-exists | any |
| CFT-003 | Core Functionality | Typical Workflow | Deprecated 3-param overload | containerized |
| CFT-004 | Core Functionality | Data Variations | `lzAddress=null` explicit (containerized) | containerized |
| ECT-001 | Edge Case | Unusual Valid Inputs | `fileName` with subdirectory | any |
| ECT-002 | Edge Case | Unusual Valid Inputs | `fileName` with no extension | any |
| ECT-003 | Edge Case | Unusual Valid Inputs | `fileName` with hyphens/underscores | any |
| ECT-004 | Edge Case | Optional Parameters | `lzAddress=""` (empty string) | any |
| ECT-005 | Edge Case | Boundary Values | 255-character file name | any |
| ECT-006 | Edge Case | Boundary Values | Single-character file name | any |
| EHT-001 | Error Handling | Invalid Inputs | `fileName=null` → client exception | any |
| EHT-002 | Error Handling | Invalid Inputs | `fileName=""` → client exception | any |
| EHT-003 | Error Handling | Invalid Inputs | `targetLandingZone=null` → client exception | any |
| EHT-004 | Error Handling | Invalid Inputs | `targetLandingZone=""` → client exception | any |
| EHT-005 | Error Handling | Server-Side | `overwrite=false`, file exists → `false` | any |
| EHT-006 | Error Handling | Server-Side | `fileName` is a directory → `false` | any |
| EHT-007 | Error Handling | Server-Side | Non-existent drop zone name | any |
| EHT-008 | Error Handling | Server-Side | Unreachable `lzAddress` | baremetal |
| CNT-001 | Connectivity | Reachability | Minimal valid call succeeds | any |
| CNT-002 | Connectivity | Invalid auth | Bad credentials → auth exception | secure |
| CNT-003 | Connectivity | Valid auth | Good credentials on secured cluster | secure |


---
*Generated: 2026-03-26*
