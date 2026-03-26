# WsFileIO `createHPCCFile` — Expected Test Results

**Generated:** 2026-03-26  
**Method Under Test:** `HPCCWsFileIOClient.createHPCCFile`  
**Test Class:** `WSFileIOClientTest`

---

## Core Functionality Tests (CFT)

### CFT-001 — `testCreateHPCCFile_overwriteFalseNewFile`
| Field | Detail |
|-------|--------|
| **Expected Outcome** | PASS |
| **Return Value** | `true` |
| **Exception Expected** | None |
| **Server Response** | `"<path> has been created."` |
| **Notes** | Unique timestamped file name guarantees the file does not pre-exist. The call with `overwrite=false` should succeed because the file is new. |

---

### CFT-002 — `testCreateHPCCFile_overwriteTrueFileExists`
| Field | Detail |
|-------|--------|
| **Expected Outcome** | PASS |
| **Return Value** | `true` for both the first and second calls |
| **Exception Expected** | None |
| **Server Response** | `"<path> has been created."` on both calls |
| **Notes** | The second call with `overwrite=true` silently overwrites the existing file. No `"Failure:"` prefix in the result string. |

---

### CFT-003 — `testCreateHPCCFile_deprecatedThreeParamOverload`
| Field | Detail |
|-------|--------|
| **Expected Outcome** | PASS (containerized only; SKIP on baremetal) |
| **Return Value** | `true` |
| **Exception Expected** | None |
| **Server Response** | `"<path> has been created."` |
| **Notes** | The 3-param overload delegates to `createHPCCFile(fileName, lz, overwrite, null)`. Test is skipped when `isContainerized` is `false` because `null` lzAddress is not valid for baremetal deployments. |

---

### CFT-004 — `testCreateHPCCFile_lzAddressNullContainerized`
| Field | Detail |
|-------|--------|
| **Expected Outcome** | PASS (containerized only; SKIP on baremetal) |
| **Return Value** | `true` |
| **Exception Expected** | None |
| **Server Response** | `"<path> has been created."` |
| **Notes** | Explicit `null` lzAddress causes the client to omit `DestNetAddress` from the SOAP request. On containerized HPCC this is the standard mode. |

---

## Edge Case Tests (ECT)

### ECT-001 — `testCreateHPCCFile_fileNameWithSubdirectory`
| Field | Detail |
|-------|--------|
| **Expected Outcome** | PASS |
| **Return Value** | `true` if `wsfileio_subdir/` directory pre-exists on the LZ; `false` otherwise |
| **Exception Expected** | `ArrayOfEspExceptionWrapper` possible if the server rejects the path entirely |
| **Server Response** | `"<path> has been created."` or `"Failure: <path> ..."` or ESP exception |
| **Notes** | Test documents actual server behaviour for subdirectory paths. No assertion on the boolean result; the test passes as long as no unchecked exception escapes. |

---

### ECT-002 — `testCreateHPCCFile_fileNameNoExtension`
| Field | Detail |
|-------|--------|
| **Expected Outcome** | PASS |
| **Return Value** | `true` |
| **Exception Expected** | None |
| **Server Response** | `"<path> has been created."` |
| **Notes** | The server does not enforce a file extension. A plain name without `.ext` is valid. |

---

### ECT-003 — `testCreateHPCCFile_fileNameWithSpecialChars`
| Field | Detail |
|-------|--------|
| **Expected Outcome** | PASS |
| **Return Value** | `true` |
| **Exception Expected** | None |
| **Server Response** | `"<path> has been created."` |
| **Notes** | Hyphens (`-`) and underscores (`_`) are valid POSIX filename characters and should be accepted without modification. |

---

### ECT-004 — `testCreateHPCCFile_lzAddressEmptyString`
| Field | Detail |
|-------|--------|
| **Expected Outcome** | PASS (containerized only; SKIP on baremetal) |
| **Return Value** | `true` |
| **Exception Expected** | None |
| **Server Response** | `"<path> has been created."` |
| **Notes** | The client guards: `if (lzAddress != null && !lzAddress.isEmpty())`. An empty string causes `DestNetAddress` to be omitted, identical to `null`. Valid only in containerized mode. |

---

### ECT-005 — `testCreateHPCCFile_veryLongFileName`
| Field | Detail |
|-------|--------|
| **Expected Outcome** | PASS |
| **Return Value** | `true` on Linux (ext4/xfs support 255-byte filenames); `false` or ESP exception on systems with stricter limits |
| **Exception Expected** | `ArrayOfEspExceptionWrapper` or `Exception` possible if server rejects |
| **Server Response** | `"<path> has been created."` or server-side error |
| **Notes** | The test asserts only that no unchecked/unexpected exception escapes. Both `true`/`false` and expected exception types are acceptable outcomes. |

---

### ECT-006 — `testCreateHPCCFile_singleCharFileName`
| Field | Detail |
|-------|--------|
| **Expected Outcome** | PASS |
| **Return Value** | `true` |
| **Exception Expected** | None |
| **Server Response** | `"x has been created."` (relative path within the drop zone) |
| **Notes** | Tests the minimum-length boundary of `DestRelativePath`. A single ASCII character is a valid filename on all supported platforms. |

---

## Error Handling Tests (EHT)

### EHT-001 — `testCreateHPCCFile_nullFileName`
| Field | Detail |
|-------|--------|
| **Expected Outcome** | PASS |
| **Return Value** | N/A (exception thrown) |
| **Exception Expected** | `Exception` with message containing `"fileName required"` |
| **Server Response** | None (no network call made) |
| **Notes** | Client-side guard: `if (fileName == null || fileName.isEmpty()) throw new Exception("HPCCWsFileIOClient::createHPCCFile: fileName required!")` |

---

### EHT-002 — `testCreateHPCCFile_emptyFileName`
| Field | Detail |
|-------|--------|
| **Expected Outcome** | PASS |
| **Return Value** | N/A (exception thrown) |
| **Exception Expected** | `Exception` with message containing `"fileName required"` |
| **Server Response** | None (no network call made) |
| **Notes** | Empty string triggers the same client-side guard as `null`. |

---

### EHT-003 — `testCreateHPCCFile_nullTargetLandingZone`
| Field | Detail |
|-------|--------|
| **Expected Outcome** | PASS |
| **Return Value** | N/A (exception thrown) |
| **Exception Expected** | `Exception` with message containing `"targetLandingZone required"` |
| **Server Response** | None (no network call made) |
| **Notes** | `targetLandingZone` is validated before `fileName` in the current implementation. |

---

### EHT-004 — `testCreateHPCCFile_emptyTargetLandingZone`
| Field | Detail |
|-------|--------|
| **Expected Outcome** | PASS |
| **Return Value** | N/A (exception thrown) |
| **Exception Expected** | `Exception` with message containing `"targetLandingZone required"` |
| **Server Response** | None (no network call made) |
| **Notes** | Empty string triggers the same client-side guard as `null` for `targetLandingZone`. |

---

### EHT-005 — `testCreateHPCCFile_overwriteFalseFileExists`
| Field | Detail |
|-------|--------|
| **Expected Outcome** | PASS |
| **Return Value** | First call: `true`; Second call: `false` |
| **Exception Expected** | None on either call |
| **Server Response** | First: `"<path> has been created."`; Second: `"Failure: <path> exists."` |
| **Notes** | `result.startsWith("Fail")` evaluates to `true` for `"Failure: ..."`, so the method returns `false`. This is the most common user error scenario. |

---

### EHT-006 — `testCreateHPCCFile_fileNameIsDirectory`
| Field | Detail |
|-------|--------|
| **Expected Outcome** | PASS |
| **Return Value** | `false` (if server detects directory) OR `true` (if `"."` is not interpreted as directory in all HPCC versions) |
| **Exception Expected** | `ArrayOfEspExceptionWrapper` or `Exception` possible |
| **Server Response** | `"Failure: <path> is a directory."` when `"."` resolves to the LZ root directory |
| **Notes** | Behaviour depends on how the HPCC server resolves `"."` relative to the drop zone base path. Test asserts `false` if the call succeeds without exception; accepts exception as an alternative valid outcome. |

---

### EHT-007 — `testCreateHPCCFile_invalidDropZoneName`
| Field | Detail |
|-------|--------|
| **Expected Outcome** | PASS |
| **Return Value** | `false` if server returns a `"Fail..."` result; OR exception if `validateDropZoneAccess` raises an ESP exception |
| **Exception Expected** | `ArrayOfEspExceptionWrapper` (likely — server calls `validateDropZoneAccess`) |
| **Server Response** | ESP exception or failure result |
| **Notes** | The test asserts `false` when no exception is thrown, and treats `ArrayOfEspExceptionWrapper` as an expected outcome. Neither path constitutes silent success. |

---

### EHT-008 — `testCreateHPCCFile_unreachableLzAddress`
| Field | Detail |
|-------|--------|
| **Expected Outcome** | PASS (baremetal only; SKIP on containerized) |
| **Return Value** | `false` or exception |
| **Exception Expected** | `ArrayOfEspExceptionWrapper` or transport `Exception` |
| **Server Response** | Failure — server cannot route to `192.0.2.1` (RFC 5737 TEST-NET) |
| **Notes** | Only runs when `isContainerized` is `false`. The server uses `DestNetAddress` to contact the drop zone node; an unreachable address causes a drop zone validation failure. |

---

## Connectivity Tests (CNT)

### CNT-001 — `testCreateHPCCFile_connectivity`
| Field | Detail |
|-------|--------|
| **Expected Outcome** | PASS |
| **Return Value** | `true` |
| **Exception Expected** | None |
| **Server Response** | `"<path> has been created."` |
| **Notes** | Minimal connectivity check: confirms the WsFileIO `CreateFile` endpoint is reachable and responds successfully. Complements the generic `ping()` connectivity test. |

---

### CNT-002 — `testCreateHPCCFile_invalidCredentials`
| Field | Detail |
|-------|--------|
| **Expected Outcome** | PASS (secure only; SKIP when `doesTargetHPCCAuthenticate()` returns `false`) |
| **Return Value** | N/A (exception expected) OR `false` |
| **Exception Expected** | `ArrayOfEspExceptionWrapper` (ECLWATCH_ACCESS_TO_FILE_DENIED) or transport `Exception` (HTTP 401/403) |
| **Server Response** | Authentication/authorisation failure |
| **Notes** | The test passes if an exception is thrown OR if the method returns `false`. The important constraint is that it must NOT return `true`. |

---

### CNT-003 — `testCreateHPCCFile_validCredentialsSecured`
| Field | Detail |
|-------|--------|
| **Expected Outcome** | PASS (secure only; SKIP when `doesTargetHPCCAuthenticate()` returns `false`) |
| **Return Value** | `true` |
| **Exception Expected** | None |
| **Server Response** | `"<path> has been created."` |
| **Notes** | Uses the default test credentials (`hpccUser` / `hpccPass`) that are assumed to have write access to the landing zone. Validates the HPCC permission grant path for `FILE_IO_URL`. |

---

## Summary

| Test Method | Category | Expected Result | Environment |
|-------------|----------|-----------------|-------------|
| `testCreateHPCCFile_overwriteFalseNewFile` | CFT | `true` returned, no exception | any |
| `testCreateHPCCFile_overwriteTrueFileExists` | CFT | `true` on both calls | any |
| `testCreateHPCCFile_deprecatedThreeParamOverload` | CFT | `true` returned | containerized |
| `testCreateHPCCFile_lzAddressNullContainerized` | CFT | `true` returned | containerized |
| `testCreateHPCCFile_fileNameWithSubdirectory` | ECT | No unchecked exception; `true` or `false` or ESP exception | any |
| `testCreateHPCCFile_fileNameNoExtension` | ECT | `true` returned | any |
| `testCreateHPCCFile_fileNameWithSpecialChars` | ECT | `true` returned | any |
| `testCreateHPCCFile_lzAddressEmptyString` | ECT | `true` returned | containerized |
| `testCreateHPCCFile_veryLongFileName` | ECT | No unchecked exception; any result | any |
| `testCreateHPCCFile_singleCharFileName` | ECT | `true` returned | any |
| `testCreateHPCCFile_nullFileName` | EHT | `Exception` with `"fileName required"` | any |
| `testCreateHPCCFile_emptyFileName` | EHT | `Exception` with `"fileName required"` | any |
| `testCreateHPCCFile_nullTargetLandingZone` | EHT | `Exception` with `"targetLandingZone required"` | any |
| `testCreateHPCCFile_emptyTargetLandingZone` | EHT | `Exception` with `"targetLandingZone required"` | any |
| `testCreateHPCCFile_overwriteFalseFileExists` | EHT | Second call returns `false` | any |
| `testCreateHPCCFile_fileNameIsDirectory` | EHT | `false` or ESP exception | any |
| `testCreateHPCCFile_invalidDropZoneName` | EHT | `false` or `ArrayOfEspExceptionWrapper` | any |
| `testCreateHPCCFile_unreachableLzAddress` | EHT | `false` or exception | baremetal |
| `testCreateHPCCFile_connectivity` | CNT | `true` returned | any |
| `testCreateHPCCFile_invalidCredentials` | CNT | Exception or `false` | secure |
| `testCreateHPCCFile_validCredentialsSecured` | CNT | `true` returned | secure |

---
*Generated: 2026-03-26*
