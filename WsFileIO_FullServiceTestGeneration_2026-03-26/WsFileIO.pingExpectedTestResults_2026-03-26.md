# WsFileIO `ping` — Expected Test Results

**Generated:** 2026-03-26  
**Service:** WsFileIO  
**Method:** `ping`  
**Test Class:** `WSFileIOClientTest`

---

## Test Results Summary

| Test ID | Method Name | Category | Environment | Expected Outcome |
|---------|-------------|----------|-------------|-----------------|
| CFT-001 | `testPing_repeatedSuccessiveCalls` | Core Functionality | any | PASS |
| CFT-002 | `testPing_afterOtherOperations` | Core Functionality | any | PASS (or SKIP if LZ not configured) |
| ECT-001 | `testPing_concurrentCalls` | Edge Case | any | PASS |
| EHT-001 | `testPing_unreachableHost` | Error Handling | any | PASS |
| EHT-002 | `testPing_wrongPort` | Error Handling | any | PASS |
| EHT-003 | `testPing_uninitializedClient` | Error Handling | any | SKIP (`@Ignore`) |
| CNT-001 | `testPing_invalidCredentials` | Connectivity | secure | PASS (or SKIP on unsecured cluster) |
| CNT-002 | `testPing_emptyCredentials` | Connectivity | secure | PASS (or SKIP on unsecured cluster) |

---

## Detailed Expected Results

---

### CFT-001 — `testPing_repeatedSuccessiveCalls`

**Category:** Core Functionality  
**Environment:** any

**Expected Outcome:** PASS

| Step | Action | Expected Result |
|------|--------|----------------|
| 1 | Call `client.ping()` (1st) | Returns `true` |
| 2 | Call `client.ping()` (2nd) | Returns `true` |
| 3 | Call `client.ping()` (3rd) | Returns `true` |
| 4 | Call `client.ping()` (4th) | Returns `true` |
| 5 | Call `client.ping()` (5th) | Returns `true` |

**Pass Criteria:**
- All five `assertTrue` assertions pass without exception.
- No exception propagates to the test method across all five calls.

**Failure Indicators:**
- Any call returning `false` — suggests a resource leak (e.g., unclosed HTTP connection) or transient cluster issue.
- Any exception propagating — would indicate the try-catch in `ping()` is not working correctly.

---

### CFT-002 — `testPing_afterOtherOperations`

**Category:** Core Functionality  
**Environment:** any

**Expected Outcome:** PASS (SKIP if `-Dlzname` and `-Dlztestfile` are not provided)

| Step | Action | Expected Result |
|------|--------|----------------|
| 0 | Check `-Dlzname` and `-Dlztestfile` system properties | If absent, test is skipped via `Assume.assumeNotNull` |
| 1 | Call `client.createHPCCFile(...)` with unique file name | May succeed or throw (acceptable) |
| 2 | Call `client.ping()` | Returns `true` |

**Pass Criteria:**
- `ping()` returns `true` regardless of the outcome of `createHPCCFile`.

**Failure Indicators:**
- `ping()` returning `false` after `createHPCCFile` — would indicate the service became unavailable after processing a write operation.

---

### ECT-001 — `testPing_concurrentCalls`

**Category:** Edge Case  
**Environment:** any

**Expected Outcome:** PASS

| Step | Action | Expected Result |
|------|--------|----------------|
| 1 | Submit 10 concurrent `ping()` tasks via `ExecutorService` | All tasks accepted |
| 2 | `invokeAll` with 30-second timeout | All futures complete within 30 s |
| 3 | Retrieve each `Future.get()` result | Each returns `true` |

**Pass Criteria:**
- All 10 `Future.get()` calls return `true`.
- No `ExecutionException` is thrown.
- Test completes within the 30-second timeout.

**Failure Indicators:**
- Any `Future.get()` returning `false` — indicates thread-safety issue or connection pool exhaustion.
- `ExecutionException` — indicates uncaught exception inside a worker thread.
- Timeout — indicates a deadlock or extreme connection saturation.

---

### EHT-001 — `testPing_unreachableHost`

**Category:** Error Handling  
**Environment:** any

**Expected Outcome:** PASS

| Step | Action | Expected Result |
|------|--------|----------------|
| 1 | Create `HPCCWsFileIOClient` pointing at `192.0.2.1:8010` with 2 s timeout | Client constructed successfully |
| 2 | Call `unreachableClient.ping()` | Returns `false` |

**Pass Criteria:**
- Return value is `false`.
- No exception propagates to the test method (exception is swallowed internally by `ping()`).

**Failure Indicators:**
- Exception propagating to the test — means the try-catch in `ping()` is not catching network-level exceptions correctly.
- Returns `true` — would indicate the TEST-NET address `192.0.2.1` is unexpectedly reachable (environment issue).

**Notes:**
- The 2-second connection timeout prevents the test from waiting the default OS TCP timeout (which can be 20+ seconds).
- The IP `192.0.2.1` is reserved by RFC 5737 as documentation/test-only and must never route to a real host.

---

### EHT-002 — `testPing_wrongPort`

**Category:** Error Handling  
**Environment:** any

**Expected Outcome:** PASS

| Step | Action | Expected Result |
|------|--------|----------------|
| 1 | Extract host from `hpccconn` system property | Host string obtained |
| 2 | Create `HPCCWsFileIOClient` with correct host, port `9999`, 2 s timeout | Client constructed |
| 3 | Call `wrongPortClient.ping()` | Returns `false` |

**Pass Criteria:**
- Return value is `false`.
- No exception propagates to the test method.

**Failure Indicators:**
- Exception propagating — means `ping()` failed to swallow the connection-refused error.
- Returns `true` — means something is listening on port 9999 at the test host (environment issue).

---

### EHT-003 — `testPing_uninitializedClient`

**Category:** Error Handling  
**Environment:** any

**Expected Outcome:** SKIP (`@Ignore`)

**Reason for Skip:**
`HPCCWsFileIOClient` has no no-arg constructor and no deferred-init factory method. Constructing the client with a `null` `Connection` argument throws a `NullPointerException` during `initWSFileIOStub()` — before `ping()` is ever called. The `verifyStub()` guard in `BaseHPCCWsClient` (which throws a descriptive `Exception` when `stub == null`) cannot be reached via the public API. The test is retained as documentation of this design constraint.

---

### CNT-001 — `testPing_invalidCredentials`

**Category:** Connectivity  
**Environment:** secure (skipped on unsecured clusters via `Assume.assumeTrue`)

**Expected Outcome:** PASS on secured clusters; SKIP on unsecured clusters

| Step | Action | Expected Result |
|------|--------|----------------|
| 1 | Call `client.doesTargetHPCCAuthenticate()` | Returns `true` (secured) or `false` (unsecured — test skipped) |
| 2 | Create `HPCCWsFileIOClient` with `username="invalid_user"`, `password="wrong_password"` | Client constructed |
| 3 | Call `badCredsClient.ping()` | Returns `false` |

**Pass Criteria:**
- Return value is `false`.
- No exception propagates.

**Failure Indicators:**
- Returns `true` — means the ESP is not enforcing authentication (would indicate a misconfigured secured cluster or the `Assume` check failed to skip).
- Exception propagating — means the auth error is not being swallowed by the try-catch in `ping()`.

---

### CNT-002 — `testPing_emptyCredentials`

**Category:** Connectivity  
**Environment:** secure (skipped on unsecured clusters via `Assume.assumeTrue`)

**Expected Outcome:** PASS on secured clusters; SKIP on unsecured clusters

| Step | Action | Expected Result |
|------|--------|----------------|
| 1 | Call `client.doesTargetHPCCAuthenticate()` | Returns `true` (secured) or `false` (skipped) |
| 2 | Create `HPCCWsFileIOClient` with `username=""`, `password=""` | Client constructed |
| 3 | Call `emptyCredsClient.ping()` | Returns `false` |

**Pass Criteria:**
- Return value is `false`.
- No exception propagates.

**Failure Indicators:**
- Returns `true` — means empty credentials are silently accepted (authentication not enforced).
- Exception propagating — means the auth error is not being swallowed.

---

## Notes

1. **EHT-001 / EHT-002 test duration**: These tests use a 2-second socket/connect timeout to avoid lengthy TCP timeout waits. In slow network environments, the tests may still take up to 2 seconds each per call attempt.
2. **CFT-002 conditional skip**: This test uses `Assume.assumeNotNull` and will be reported as SKIPPED (not FAILED) when landing zone system properties (`lzname`, `lztestfile`) are absent. This is expected behavior in CI environments without a configured landing zone.
3. **CNT-001 / CNT-002 conditional skip**: These tests use `Assume.assumeTrue(client.doesTargetHPCCAuthenticate())` and will be SKIPPED on clusters without authentication enabled. This is expected.

---

*Generated: 2026-03-26*
