# WsFileIO.writeHPCCFileData — Expected Test Results
**Generated:** 2026-03-26  
**Method:** `HPCCWsFileIOClient.writeHPCCFileData`  
**Test Class:** `WSFileIOClientTest`

---

## Summary

| Category | Count | Description |
|----------|-------|-------------|
| Core Functionality (CFT) | 8 | Happy-path and typical workflow tests |
| Edge Case (ECT) | 7 | Boundary values, unusual-but-valid inputs, documented bugs |
| Error Handling (EHT) | 10 | Invalid inputs, failure scenarios |
| Connectivity (CNT) | 3 | Service reachability, authentication |
| **Total** | **28** | |

---

## Core Functionality Tests (CFT)

### CFT-001 — `testWriteHPCCFileData_explicitTextMimeType`

| Field | Detail |
|-------|--------|
| **Label** | CFT-001-writeHPCCFileData |
| **Environment** | any |
| **Expected Return** | `true` |
| **Expected Behaviour** | Method returns `true`; `readFileData` returns the exact original string `"Hello WsFileIO CFT-001"` |
| **Expected Exceptions** | None |
| **Pass Criteria** | `assertTrue(result)`, `assertArrayEquals(data, response.getBytes(UTF_8))` |
| **Notes** | Exercises the primary non-deprecated overload with explicit `"text/plain; charset=UTF-8"` MIME type |

---

### CFT-002 — `testWriteHPCCFileData_nullMimeTypeDefaultsToOctetStream`

| Field | Detail |
|-------|--------|
| **Label** | CFT-002-writeHPCCFileData |
| **Environment** | any |
| **Expected Return** | `true` |
| **Expected Behaviour** | Client internally defaults to `"application/octet-stream"`; write succeeds |
| **Expected Exceptions** | None (especially no `NullPointerException`) |
| **Pass Criteria** | `assertTrue(result)` |
| **Notes** | Validates the null-guard on `dataTypeDescriptor` in `HPCCWsFileIOClient` |

---

### CFT-003 — `testWriteHPCCFileData_emptyMimeTypeDefaultsToOctetStream`

| Field | Detail |
|-------|--------|
| **Label** | CFT-003-writeHPCCFileData |
| **Environment** | any |
| **Expected Return** | `true` |
| **Expected Behaviour** | Empty string triggers `isEmpty()` guard; defaults to `"application/octet-stream"`; write succeeds |
| **Expected Exceptions** | None |
| **Pass Criteria** | `assertTrue(result)` |
| **Notes** | Parallel to CFT-002 but exercises the `isEmpty()` branch rather than the null check |

---

### CFT-004 — `testWriteHPCCFileData_binaryData`

| Field | Detail |
|-------|--------|
| **Label** | CFT-004-writeHPCCFileData |
| **Environment** | any |
| **Expected Return** | `true` |
| **Expected Behaviour** | Binary (non-UTF-8) bytes survive MTOM/MIME encoding; `readFileData` returns bytes matching the simulated PNG header |
| **Expected Exceptions** | None |
| **Pass Criteria** | `assertTrue(result)`, `assertArrayEquals(data, response.getBytes(ISO_8859_1))` |
| **Notes** | Uses ISO-8859-1 for byte comparison since `readFileData` returns a Java `String` |

---

### CFT-005 — `testWriteHPCCFileData_jsonMimeType`

| Field | Detail |
|-------|--------|
| **Label** | CFT-005-writeHPCCFileData |
| **Environment** | any |
| **Expected Return** | `true` |
| **Expected Behaviour** | Server accepts the payload regardless of MIME type; write succeeds |
| **Expected Exceptions** | None |
| **Pass Criteria** | `assertTrue(result)` |
| **Notes** | Server-side storage is MIME-agnostic; this primarily tests client-side `DataSource` construction |

---

### CFT-006 — `testWriteHPCCFileData_multiChunkWrite`

| Field | Detail |
|-------|--------|
| **Label** | CFT-006-writeHPCCFileData |
| **Environment** | any |
| **Expected Return** | `true` |
| **Expected Behaviour** | 100-byte payload with chunksize=20 produces 5 loop iterations; all bytes written; `readFileData` returns 100 bytes |
| **Expected Exceptions** | None |
| **Pass Criteria** | `assertTrue(result)`, `assertEquals(100, response.length())` |
| **Notes** | Directly exercises the `while (bytesleft > 0)` loop for multiple iterations |

---

### CFT-007 — `testWriteHPCCFileData_defaultChunkSizeWhenZeroOrNegative`

| Field | Detail |
|-------|--------|
| **Label** | CFT-007-writeHPCCFileData |
| **Environment** | any |
| **Expected Return** | `true` for both `uploadchunksize=0` and `uploadchunksize=-1` |
| **Expected Behaviour** | Client substitutes `defaultUploadChunkSize` (5,000,000); no arithmetic error |
| **Expected Exceptions** | None (`ArithmeticException` or negative-array-size would be a failure) |
| **Pass Criteria** | `assertTrue(resultZero)`, `assertTrue(resultNeg)` |
| **Notes** | Two file creates/writes in a single test method; both must return `true` |

---

### CFT-008 — `testWriteHPCCFileData_roundTripVerification`

| Field | Detail |
|-------|--------|
| **Label** | CFT-008-writeHPCCFileData |
| **Environment** | any |
| **Expected Return** | `true` |
| **Expected Behaviour** | Data `"Round-trip 123 \r\n ABC"` is written in 5-byte chunks, then read back with exact byte-level equality |
| **Expected Exceptions** | None |
| **Pass Criteria** | `assertTrue(result)`, `assertNotNull(response)`, `assertArrayEquals(data, response.getBytes(UTF_8))` |
| **Notes** | Small chunksize (5) exercises multi-chunk correctness and data ordering simultaneously |

---

## Edge Case Tests (ECT)

### ECT-001 — `testWriteHPCCFileData_chunkSizeEqualsDataLength`

| Field | Detail |
|-------|--------|
| **Label** | ECT-001-writeHPCCFileData |
| **Environment** | any |
| **Expected Return** | `true` |
| **Expected Behaviour** | Loop executes exactly once (`bytesleft` equals `payloadsize`); `bytesleft` becomes 0 after first iteration |
| **Expected Exceptions** | None |
| **Pass Criteria** | `assertTrue(result)` |
| **Notes** | Boundary condition: `chunksize == data.length` (10 bytes each) |

---

### ECT-002 — `testWriteHPCCFileData_chunkSizeLargerThanData`

| Field | Detail |
|-------|--------|
| **Label** | ECT-002-writeHPCCFileData |
| **Environment** | any |
| **Expected Return** | `true` |
| **Expected Behaviour** | `payloadsize = min(bytesleft, chunksize) = bytesleft = data.length`; single iteration; no `ArrayIndexOutOfBoundsException` |
| **Expected Exceptions** | None |
| **Pass Criteria** | `assertTrue(result)` |
| **Notes** | 9-byte data with chunksize=100 |

---

### ECT-003 — `testWriteHPCCFileData_singleByte`

| Field | Detail |
|-------|--------|
| **Label** | ECT-003-writeHPCCFileData |
| **Environment** | any |
| **Expected Return** | `true` |
| **Expected Behaviour** | Minimum possible non-empty write (1 byte, 0x42) succeeds |
| **Expected Exceptions** | None |
| **Pass Criteria** | `assertTrue(result)` |
| **Notes** | Validates the minimum boundary for non-empty data |

---

### ECT-004 — `testWriteHPCCFileData_explicitLzAddressBaremetal`

| Field | Detail |
|-------|--------|
| **Label** | ECT-004-writeHPCCFileData |
| **Environment** | baremetal |
| **Expected Return** | `true` |
| **Expected Behaviour** | Client sets `DestNetAddress` from the provided `lzAddress`; server accepts the request |
| **Expected Exceptions** | None |
| **Pass Criteria** | `assertTrue(result)` |
| **Notes** | Skipped in containerized environments via `assumeFalse(isContainerized)` |

---

### ECT-005 — `testWriteHPCCFileData_largeDataMultipleChunks`

| Field | Detail |
|-------|--------|
| **Label** | ECT-005-writeHPCCFileData |
| **Environment** | any |
| **Expected Return** | `true` |
| **Expected Behaviour** | 1,048,576 bytes written in ~10 iterations of 100 KB each; no timeout or memory error |
| **Expected Exceptions** | None |
| **Pass Criteria** | `assertTrue(result)` |
| **Notes** | The optional `readFileData` length check is omitted due to ESP response size limits |

---

### ECT-006 — `testWriteHPCCFileData_appendFalseOverriddenByClientBug`

| Field | Detail |
|-------|--------|
| **Label** | ECT-006-writeHPCCFileData |
| **Environment** | any |
| **Expected Return** | `true` for both writes |
| **Expected Behaviour** | Due to client-side bug (Note A), `append=false` is overridden to `append=true` in the loop; second write appends rather than overwrites; `readFileData` returns `"FIRSTSECOND"` (11 bytes) |
| **Expected Exceptions** | None |
| **Pass Criteria** | `assertTrue(firstResult)`, `assertTrue(secondResult)`, `assertEquals("FIRSTSECOND", response)` |
| **Notes** | **Intentional bug documentation test.** When the bug is fixed, the expected read-back should be `"SECOND"` (6 bytes) and this test will need updating |

---

### ECT-007 — `testWriteHPCCFileData_userOffsetOverriddenByClientBug`

| Field | Detail |
|-------|--------|
| **Label** | ECT-007-writeHPCCFileData |
| **Environment** | any |
| **Expected Return** | `true` |
| **Expected Behaviour** | User-supplied `offset=5` is overridden by the loop to `data.length` (6); write still succeeds since `append=true` causes the server to ignore `Offset` anyway |
| **Expected Exceptions** | None |
| **Pass Criteria** | `assertTrue(result)` |
| **Notes** | **Intentional bug documentation test.** Exists as a regression marker for Note A offset bug |

---

## Error Handling Tests (EHT)

### EHT-001 — `testWriteHPCCFileData_nullDataThrowsNullPointerException`

| Field | Detail |
|-------|--------|
| **Label** | EHT-001-writeHPCCFileData |
| **Environment** | any |
| **Expected Return** | N/A (exception expected) |
| **Expected Behaviour** | `NullPointerException` thrown at `data.length` before any server call |
| **Expected Exceptions** | `NullPointerException` |
| **Pass Criteria** | `catch (NullPointerException e)` block is entered |
| **Notes** | No `createHPCCFile` setup required; exception occurs client-side before network |

---

### EHT-002 — `testWriteHPCCFileData_emptyDataReturnsTrueWithoutWrite`

| Field | Detail |
|-------|--------|
| **Label** | EHT-002-writeHPCCFileData |
| **Environment** | any |
| **Expected Return** | `true` |
| **Expected Behaviour** | `bytesleft = 0`; the `while` loop body never executes; method returns `true` without any server communication |
| **Expected Exceptions** | None |
| **Pass Criteria** | `assertTrue(result)` |
| **Notes** | Silent success — may mislead callers; documents edge behaviour |

---

### EHT-003 — `testWriteHPCCFileData_nullFileNameReturnsTrueDueToClientBug`

| Field | Detail |
|-------|--------|
| **Label** | EHT-003-writeHPCCFileData |
| **Environment** | any |
| **Expected Return** | `true` |
| **Expected Behaviour** | Server returns `"Destination path not specified"`; client does not map this to `false`; method returns `true` |
| **Expected Exceptions** | None |
| **Pass Criteria** | `assertTrue(result)` |
| **Notes** | **Intentional client bug documentation.** If the bug is fixed, expect `false` |

---

### EHT-004 — `testWriteHPCCFileData_emptyFileNameReturnsTrueDueToClientBug`

| Field | Detail |
|-------|--------|
| **Label** | EHT-004-writeHPCCFileData |
| **Environment** | any |
| **Expected Return** | `true` |
| **Expected Behaviour** | Same as EHT-003 but with empty string `""`; server returns same validation error; client returns `true` |
| **Expected Exceptions** | None |
| **Pass Criteria** | `assertTrue(result)` |
| **Notes** | Parallel to EHT-003 |

---

### EHT-005 — `testWriteHPCCFileData_nullTargetLandingZoneReturnsTrueDueToClientBug`

| Field | Detail |
|-------|--------|
| **Label** | EHT-005-writeHPCCFileData |
| **Environment** | any |
| **Expected Return** | `true` |
| **Expected Behaviour** | Server returns `"Destination not specified"` for null `DestDropZone`; client returns `true` |
| **Expected Exceptions** | None |
| **Pass Criteria** | `assertTrue(result)` |
| **Notes** | Documents server-side error not surfaced as client-side failure |

---

### EHT-006 — `testWriteHPCCFileData_emptyTargetLandingZoneReturnsTrueDueToClientBug`

| Field | Detail |
|-------|--------|
| **Label** | EHT-006-writeHPCCFileData |
| **Environment** | any |
| **Expected Return** | `true` |
| **Expected Behaviour** | Same as EHT-005 but with empty string `""`; client returns `true` |
| **Expected Exceptions** | None |
| **Pass Criteria** | `assertTrue(result)` |
| **Notes** | Parallel to EHT-005 |

---

### EHT-007 — `testWriteHPCCFileData_nonExistentFileReturnsTrueDueToClientBug`

| Field | Detail |
|-------|--------|
| **Label** | EHT-007-writeHPCCFileData |
| **Environment** | any |
| **Expected Return** | `true` |
| **Expected Behaviour** | `createHPCCFile` is intentionally not called; server returns `"<path> does not exist."`; client returns `true` |
| **Expected Exceptions** | None |
| **Pass Criteria** | `assertTrue(result)` |
| **Notes** | Documents that the client silently ignores "file not found" result; if bug is fixed, expect `false` |

---

### EHT-008 — `testWriteHPCCFileData_invalidLzAddressBaremetal`

| Field | Detail |
|-------|--------|
| **Label** | EHT-008-writeHPCCFileData |
| **Environment** | baremetal |
| **Expected Return** | `false` OR exception thrown |
| **Expected Behaviour** | Server's `validateDropZoneAccess` rejects the request for the invalid IP `"999.999.999.999"` |
| **Expected Exceptions** | `ArrayOfEspExceptionWrapper` (preferred) or method returns `false` |
| **Pass Criteria** | `assertFalse(result)` OR `catch (ArrayOfEspExceptionWrapper e)` |
| **Notes** | Skipped in containerized environments via `assumeFalse(isContainerized)` |

---

### EHT-009 — `testWriteHPCCFileData_invalidConnectionThrowsException`

| Field | Detail |
|-------|--------|
| **Label** | EHT-009-writeHPCCFileData |
| **Environment** | any |
| **Expected Return** | N/A (exception expected) |
| **Expected Behaviour** | `verifyStub()` or network layer throws an exception for an invalid host `"invalid.host.invalid:9999"` |
| **Expected Exceptions** | Any subclass of `Exception` |
| **Pass Criteria** | `catch (Exception e)` block is entered; `fail()` is NOT reached |
| **Notes** | Uses `HPCCWsFileIOClient.get(...)` factory with invalid host |

---

### EHT-010 — `testWriteHPCCFileData_unauthorizedWriteRejected`

| Field | Detail |
|-------|--------|
| **Label** | EHT-010-writeHPCCFileData |
| **Environment** | secure |
| **Expected Return** | `false` OR exception thrown |
| **Expected Behaviour** | Server's `ensureFeatureAccess` rejects the write request for bad credentials |
| **Expected Exceptions** | `ArrayOfEspExceptionWrapper` (preferred) or method returns `false` |
| **Pass Criteria** | `assertFalse(result)` OR exception caught |
| **Notes** | Skipped via `assumeTrue(client.doesTargetHPCCAuthenticate())` on unsecured clusters |

---

## Connectivity Tests (CNT)

### CNT-001 — `testWriteHPCCFileData_connectivity`

| Field | Detail |
|-------|--------|
| **Label** | CNT-001-writeHPCCFileData |
| **Environment** | any |
| **Expected Return** | Any boolean |
| **Expected Behaviour** | Full `WriteFileData` SOAP operation completes; no `AxisFault`, `ConnectException`, or `UnknownHostException` |
| **Expected Exceptions** | None (connectivity-level) |
| **Pass Criteria** | No connectivity exception thrown; method completes |
| **Notes** | Distinct from existing `ping()` test — exercises the full SOAP write path for reachability |

---

### CNT-002 — `testWriteHPCCFileData_validCredentialsSucceed`

| Field | Detail |
|-------|--------|
| **Label** | CNT-002-writeHPCCFileData |
| **Environment** | secure |
| **Expected Return** | `true` |
| **Expected Behaviour** | Valid credentials (`hpccUser`/`hpccPass`) pass the server's `ensureFeatureAccess` check |
| **Expected Exceptions** | None |
| **Pass Criteria** | `assertTrue(result)` |
| **Notes** | Skipped via `assumeTrue(client.doesTargetHPCCAuthenticate())` on unsecured clusters |

---

### CNT-003 — `testWriteHPCCFileData_invalidCredentialsRejected`

| Field | Detail |
|-------|--------|
| **Label** | CNT-003-writeHPCCFileData |
| **Environment** | secure |
| **Expected Return** | `false` OR exception thrown |
| **Expected Behaviour** | Invalid credentials `("wronguser", "wrongpassword")` are rejected at the authentication layer |
| **Expected Exceptions** | `ArrayOfEspExceptionWrapper` (preferred) or method returns `false` |
| **Pass Criteria** | `assertFalse(result)` OR exception caught |
| **Notes** | Equivalent to EHT-010 from an auth perspective; included for connectivity matrix completeness |

---

## Dataset Requirements

No HPCC logical datasets are required for `writeHPCCFileData` tests. All test payloads are constructed as in-memory `byte[]` arrays. Landing zone files are created at test runtime via `createHPCCFile` within each test method.

> **No additions to `generate-datasets.ecl` are required.**

---

*Generated: 2026-03-26*
