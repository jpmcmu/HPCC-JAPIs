# WsFileIO Service Analysis
**Date:** 2026-03-26  
**Client Class:** `HPCCWsFileIOClient`  
**Java Source:** `wsclient/src/main/java/org/hpccsystems/ws/client/HPCCWsFileIOClient.java`  
**Server IDL:** `HPCC-Platform/esp/scm/ws_fileio.ecm`  
**Server Implementation:** `HPCC-Platform/esp/services/ws_fileio/ws_fileioservice.cpp`

---

## Overview

`WsFileIO` is a lightweight HPCC ESP service providing direct file I/O operations on HPCC Landing Zones (DropZones). It supports three core server-side operations: **CreateFile**, **WriteFileData**, and **ReadFileData**, corresponding to the HPCC ECM definitions.

The Java client class (`HPCCWsFileIOClient`) wraps these operations and adds a standard `ping` method for connectivity checking. It extends `BaseHPCCWsClient` and uses the Axis2-generated `WsFileIOStub` for actual SOAP communication.

**Key characteristics:**
- Files are created on Landing Zones (DropZones), not directly in the HPCC distributed file system
- After creation, files can be sprayed (imported) into the HPCC DFS using `HPCCFileSprayClient`
- Data is chunked during write operations (default chunk size: 5,000,000 bytes)
- Containerized HPCC deployments do not require a `lzAddress` / `dropzoneAddress`

---

## A. Method Inventory Table

| # | Method Name | Signature | Group | Overloads | Calls Other Methods | Server State Effect | Prerequisites |
|---|-------------|-----------|-------|-----------|---------------------|---------------------|---------------|
| 1 | `ping` | `public boolean ping() throws Exception` | Health/Connectivity | No | None | None | None |
| 2 | `createHPCCFile` | `public boolean createHPCCFile(String fileName, String targetLandingZone, boolean overwritefile, String lzAddress) throws Exception, ArrayOfEspExceptionWrapper` | File Creation | Yes (3-param overload) | None (canonical form) | Create | Target landing zone must exist |
| 3 | `writeHPCCFileData` | `public boolean writeHPCCFileData(byte[] data, String dataTypeDescriptor, String fileName, String targetLandingZone, boolean append, long offset, int uploadchunksize, String lzAddress) throws Exception, ArrayOfEspExceptionWrapper` | File Write | Yes (multiple overloads) | None (canonical form) | Create/Update | File should exist on landing zone (`createHPCCFile` called first, unless `append=false` implies overwrite) |
| 4 | `readFileData` | `public String readFileData(String dropzone, String fileName, long datasize, long offset, String dropzoneAddress) throws Exception, ArrayOfEspExceptionWrapper` | File Read | Yes (4-param overload) | None (canonical form) | Read | File must exist with data on landing zone |

### Overload Summary

| Canonical Method | Overloads | Delegation Chain |
|-----------------|-----------|-----------------|
| `createHPCCFile(fileName, lz, overwrite, lzAddress)` | `createHPCCFile(fileName, lz, overwrite)` | 3-param → 4-param (lzAddress=null) |
| `writeHPCCFileData(data, mimeType, fileName, lz, append, offset, chunkSize, lzAddress)` | `writeHPCCFileData(data, fileName, lz, append, offset, chunkSize, lzAddress)` (deprecated); `writeHPCCFileData(data, fileName, lz, append, offset, chunkSize)` (deprecated) | 6-param → 7-param (lzAddress=null) → 8-param (dataTypeDescriptor=null) |
| `readFileData(dropzone, fileName, datasize, offset, dropzoneAddress)` | `readFileData(dropzone, fileName, datasize, offset)` | 4-param → 5-param (dropzoneAddress=null) |

---

## B. Dependency Graph

```
ping
  └── (no dependencies)

createHPCCFile
  └── (no method dependencies)
  └── requires: target landing zone exists on server

writeHPCCFileData
  └── state dependency: createHPCCFile must have been called to create the file
      (or server-side behavior allows creation implicitly depending on overwrite flags)

readFileData
  └── state dependency: writeHPCCFileData must have written data to the file
  └── state dependency: createHPCCFile must have created the file first

Typical workflow:
  createHPCCFile → writeHPCCFileData → readFileData
```

### Internal Overload Delegation Graph

```
createHPCCFile(3-param)  →  createHPCCFile(4-param) [canonical]

writeHPCCFileData(6-param) → writeHPCCFileData(7-param) → writeHPCCFileData(8-param) [canonical]

readFileData(4-param) → readFileData(5-param) [canonical]
```

---

## C. Functional Groups

### 1. Health/Connectivity
- `ping()` — Verifies connectivity to the WsFileIO service endpoint.

### 2. File Creation
- `createHPCCFile(String fileName, String targetLandingZone, boolean overwritefile, String lzAddress)` — Creates a new (empty) file on the specified HPCC Landing Zone.
- `createHPCCFile(String fileName, String targetLandingZone, boolean overwritefile)` _(deprecated overload, delegates to above)_

### 3. File Write
- `writeHPCCFileData(byte[] data, String dataTypeDescriptor, String fileName, String targetLandingZone, boolean append, long offset, int uploadchunksize, String lzAddress)` — Writes binary data to an existing file on the Landing Zone in chunks. Supports append and offset.
- `writeHPCCFileData(byte[] data, String fileName, String targetLandingZone, boolean append, long offset, int uploadchunksize, String lzAddress)` _(deprecated overload, no MIME type)_
- `writeHPCCFileData(byte[] data, String fileName, String targetLandingZone, boolean append, long offset, int uploadchunksize)` _(deprecated overload, no MIME type, no lzAddress)_

### 4. File Read
- `readFileData(String dropzone, String fileName, long datasize, long offset, String dropzoneAddress)` — Reads a segment of data from a file on the Landing Zone, returning it as a String.
- `readFileData(String dropzone, String fileName, long datasize, long offset)` _(deprecated overload, no dropzoneAddress)_

---

## D. Test Independence Guidelines

### General Principles

1. **Unique file names per test**: Each test must generate a unique file name to avoid collisions:
   ```java
   String testFileName = "test_" + methodUnderTest + "_" + System.currentTimeMillis() + ".dat";
   ```

2. **Self-contained setup**: Each test that calls `writeHPCCFileData` or `readFileData` must first call `createHPCCFile` within the same test method.

3. **No ordering dependencies**: Tests must NOT rely on `@FixMethodOrder` for correctness. Each test must independently set up and tear down its own state.

4. **LZ address handling**: Tests must handle both containerized and non-containerized HPCC deployments:
   - Containerized: pass `null` for `lzAddress`/`dropzoneAddress`
   - Non-containerized: pass the actual LZ address from system properties

5. **Cleanup in `finally` blocks**: Since LZ files persist, tests should attempt cleanup:
   ```java
   // Note: WsFileIO does not have a delete operation — cleanup may require WsDFU or FileSpray client
   // If no delete is available, use unique names to avoid interference
   ```

6. **No server-side delete in WsFileIO**: The service has no delete operation. Cleanup of test files requires using another service (e.g., `HPCCFileSprayClient`). If unavailable, tests should use unique names to avoid state pollution.

### Helper Method Recommendations

```java
// Recommended helper methods in the test class:
private String createUniqueFileName(String testName) {
    return "wsfileio_test_" + testName + "_" + System.currentTimeMillis() + ".dat";
}

private boolean setupTestFile(String fileName, byte[] data) throws Exception {
    boolean created = client.createHPCCFile(fileName, targetLZ, true, lzAddressOrNull());
    if (created) {
        client.writeHPCCFileData(data, fileName, targetLZ, false, 0, 0, lzAddressOrNull());
    }
    return created;
}

private String lzAddressOrNull() {
    return isContainerized ? null : targetLZAddress;
}
```

### Landing Zone Prerequisites
- A valid, accessible landing zone must be configured
- Use system properties for LZ name, path, and address:
  - `-Dlzname=<name>` — Landing zone name
  - `-Dlzpath=<path>` — Landing zone path on host
  - `-Dlzaddress=<address>` — Network address (non-containerized only)

---

## E. Recommended Analysis Order

Methods ordered by dependency depth (least-dependent first):

| Order | Method | Reason |
|-------|--------|--------|
| 1 | `ping` | Standalone health check — no state dependencies, no prerequisites |
| 2 | `createHPCCFile` | Creates file state — depends only on external LZ infrastructure, not on other methods |
| 3 | `writeHPCCFileData` | Writes to file — depends on `createHPCCFile` having run first to create the file |
| 4 | `readFileData` | Reads from file — depends on both `createHPCCFile` and `writeHPCCFileData` having completed successfully |

### Rationale
- `ping` is purely infrastructural and independent
- `createHPCCFile` creates the server-side file resource needed by both `writeHPCCFileData` and `readFileData`
- `writeHPCCFileData` produces data that `readFileData` can verify
- `readFileData` is the deepest in the dependency chain and should be analyzed last

---

## F. Additional Notes for Test Generation

### Server-Side IDL (from `ws_fileio.ecm`)

```ecm
ESPrequest CreateFileRequest {
    string DestDropZone;       // LZ name or IP address
    string DestRelativePath;   // file name and/or path
    string DestNetAddress;     // [min_ver("1.01")]
    bool   Overwrite(false);
};

ESPrequest WriteFileDataRequest {
    binary Data;
    string DestDropZone;
    string DestRelativePath;
    string DestNetAddress;     // [min_ver("1.01")]
    int64  Offset(0);
    bool   Append(false);
};

ESPrequest ReadFileDataRequest {
    string DestDropZone;
    string DestRelativePath;
    string DestNetAddress;     // [min_ver("1.01")]
    int64  Offset(0);
    int64  DataSize;
};
```

### Key Validation Points

| Method | Success Indicator | Failure Indicator |
|--------|-------------------|-------------------|
| `ping` | Returns `true` | Returns `false` or throws exception |
| `createHPCCFile` | Returns `true`; `resp.getResult()` does NOT start with "Fail" | Returns `false`; result starts with "Fail"; throws exception |
| `writeHPCCFileData` | Returns `true`; each chunk result does NOT start with "Failed" | Returns `false`; chunk result starts with "Failed" |
| `readFileData` | Returns non-null String matching written data | Throws exception; returns null; result is not "ReadFileData done." |

### Important Client-Side Behavior Notes

1. **`writeHPCCFileData` chunking**: Data is split into chunks of `uploadchunksize` bytes (default: 5,000,000). Each chunk is sent in a separate SOAP call. After the first chunk, `Append=true` is automatically set internally regardless of the parameter.
2. **`createHPCCFile` result check**: Success is determined by checking if `resp.getResult()` does NOT start with `"Fail"` — not by a dedicated status field.
3. **`writeHPCCFileData` result check**: Success per chunk is determined by checking if `result` does NOT start with `"Failed"`.
4. **`readFileData` result check**: `result` of `"ReadFileData done."` indicates success (or null/empty). Any other non-empty result is treated as an error.
5. **Null `lzAddress`**: Both `createHPCCFile` and `writeHPCCFileData` skip setting `DestNetAddress` if `lzAddress` is null or empty — important for containerized mode.


---
*Generated: 2026-03-26*
