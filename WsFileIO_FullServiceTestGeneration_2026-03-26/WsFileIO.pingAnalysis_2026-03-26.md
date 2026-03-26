# WsFileIO `ping` Method — Test Case Analysis

**Generated:** 2026-03-26  
**Service:** WsFileIO  
**Method:** `ping`  
**Java Client:** `HPCCWsFileIOClient`  
**Test File:** `WSFileIOClientTest.java`

---

## 1. Method Summary

### Purpose

`ping` is a lightweight service-health check method. It sends an empty SOAP request to the WsFileIO ESP endpoint and returns a boolean indicating whether the service is reachable and responsive.

### Role within the Service

WsFileIO provides file I/O capabilities on HPCC drop zones (create, read, write). `ping` sits outside the file-I/O workflow and exists solely to verify the service endpoint is alive. It is the canonical "is the service up?" probe used by client code and monitoring systems.

### Inputs

None — `WsFileIOPingRequest` is an empty SOAP complex type (`<xsd:all/>`). No fields are sent to the server.

### Outputs

- **Return type:** `boolean`
  - `true` — the SOAP call completed without throwing an exception
  - `false` — any exception was caught (server unreachable, auth failure, SOAP fault, etc.)

### Side Effects

- Logs the localised exception message at `ERROR` level on failure; nothing is logged on success.
- Increments OpenTelemetry span counts when tracing is active (inherited from base client behaviour).

### Implementation (wsclient)

```java
public boolean ping() throws Exception
{
    verifyStub();                          // throws if stub not initialised
    WsFileIOPingRequest request = new WsFileIOPingRequest();
    try
    {
        ((WsFileIOStub) stub).ping(request);
    }
    catch (Exception e)
    {
        log.error(e.getLocalizedMessage());
        return false;
    }
    return true;
}
```

### SOAP Action

```
WsFileIO/Ping?ver_=1.01
```

### Key observations

| Property | Detail |
|---|---|
| Request fields | None |
| Response fields | None |
| Throws checked exception | Only `verifyStub()` throws before the try-block; the try-block itself swallows all exceptions and returns `false` |
| Idempotent | Yes — no server state is mutated |
| Auth required | Yes — the ESP service is declared with `auth_feature("DEFERRED")`; bad credentials result in an HTTP 401 / SOAP fault |

---

## 2. Existing Test Coverage Analysis

### Existing Test Methods for `ping`

| Existing Test Method Name | Test Category | Scenario Covered | Input Data Summary | Pass Criteria | Notes |
|---|---|---|---|---|---|
| `ping()` in `WSFileIOClientTest` | Connectivity | Basic reachability with valid credentials | Empty request; uses default connection from `BaseRemoteTest` | `client.ping()` returns `true`; no exception propagates | Fails the test on any caught exception; no assertions on response content because there is none |

### Coverage Summary

- **Total existing test methods for `ping`:** 1
- **Core Functionality Tests covered:** 0 — no CFT-level test explicitly validates the true-path semantics
- **Edge Case Tests covered:** 0
- **Error Handling Tests covered:** 0 — the existing test catches exceptions and calls `Assert.fail()` rather than asserting on expected failure behaviour
- **Connectivity Tests covered:** 1 — basic reachability / valid-credentials path is covered

### Gaps Identified

1. **CNT — Invalid credentials:** No test verifies that `ping` returns `false` (or the client surfaces an appropriate error) when wrong credentials are supplied.
2. **CNT — Null / uninitialised connection:** No test verifies that `verifyStub()` throws when the client was not properly initialised.
3. **EHT — Network unreachable / wrong host:** No test exercises the false-return path caused by a connection-level failure.
4. **EHT — Wrong port / endpoint path:** No test verifies graceful handling of a malformed endpoint URL.
5. **CFT — Repeated successive calls:** No test verifies that multiple sequential pings all return `true` (idempotency / no resource leak).
6. **CFT — Concurrent calls:** No test verifies thread-safety of the method.

---

## 3. Request Structure

`WsFileIOPingRequest` is defined in the WSDL as an empty complex type:

```xml
<xsd:element name="WsFileIOPingRequest">
    <xsd:complexType>
        <xsd:all/>
    </xsd:complexType>
</xsd:element>
```

| Field Name | Type | Required | Description | Valid Range / Format | Notes |
|---|---|---|---|---|---|
| *(none)* | — | — | The request carries no payload | — | Empty SOAP body element is sufficient |

**Dependencies:** None.  
**Default behaviour for optional fields:** N/A.

---

## 4. Server Behaviour and Responses

### Server-Side Logic

`ping` is **not** explicitly implemented in `ws_fileioservice.cpp` or `ws_fileioservice.hpp`. It is automatically generated and handled by the base ESP service framework (`CWsFileIO` base class). The framework:

1. Deserialises the empty `WsFileIOPingRequest` envelope.
2. Applies deferred authentication (`auth_feature("DEFERRED")`): the request is authenticated before reaching service logic.
3. Returns an empty `WsFileIOPingResponse` envelope with HTTP 200.

### Valid Response

| Condition | HTTP Status | SOAP Envelope | Java Return |
|---|---|---|---|
| Service up, valid auth | 200 OK | Empty `WsFileIOPingResponse` | `true` |

### Invalid / Error Responses

| Condition | HTTP Status / SOAP Fault | Java Return |
|---|---|---|
| Bad credentials | 401 / `EspSoapFault` | `false` (exception caught internally) |
| Service down / TCP refused | N/A — `java.net.ConnectException` | `false` |
| Wrong host | N/A — DNS / connect exception | `false` |
| Wrong port | N/A — connect refused | `false` |
| `verifyStub()` fails (stub not initialised) | N/A — `Exception` thrown | Exception propagates (not caught by the try-block) |

---

## 5. Error Handling

### Server-Side Errors

| Error | Trigger | Expected Client Behaviour |
|---|---|---|
| `EspSoapFault` — authentication failure | Invalid username/password | Caught; `false` returned; error logged |
| `EspSoapFault` — service temporarily unavailable | ESP process overloaded | Caught; `false` returned; error logged |

### Client-Side Errors

| Error | Trigger | Expected Client Behaviour |
|---|---|---|
| `AxisFault` | SOAP-level transport or parse error | Caught by generic `Exception` handler; `false` returned |
| `java.net.ConnectException` | Host unreachable, port closed | Caught; `false` returned; error logged |
| `java.net.SocketTimeoutException` | Network timeout | Caught; `false` returned; error logged |
| `Exception` from `verifyStub()` | Client created without a valid connection | **Propagates** — NOT caught by the try-block; the stub is verified before the try-catch |

---

## 6. Existing Dataset Analysis

`ping` carries no payload and performs no file operation; it does not read or reference any HPCC dataset.

| Dataset Name | Applicable? | Reason |
|---|---|---|
| `~benchmark::all_types::200KB` | No | No file path or data is sent; ping is connection-only |
| `~benchmark::string::100MB` | No | Same — no data interaction |
| `~benchmark::integer::20KB` | No | Same — no data interaction |
| `~benchmark::all_types::superfile` | No | Same — no data interaction |
| `~benchmark::integer::20kb::key` | No | Same — no data interaction |

**No new datasets are required.** All `ping` test scenarios are connection-level only.

---

## 7. Test Case Plan

> **Note:** The existing `ping()` test in `WSFileIOClientTest` adequately covers the *happy-path connectivity* scenario (valid credentials, service up). All test cases below address **gaps only**.

---

### A. Core Functionality Tests

---

#### CFT-001 — Repeated successive pings return `true`

| Field | Detail |
|---|---|
| **Test ID** | CFT-001 |
| **Category** | Core Functionality |
| **Subcategory** | Typical Workflows |
| **Description** | Call `ping()` five times in sequence and assert every call returns `true`. Verifies idempotency and the absence of any resource leak (e.g., unclosed HTTP connection) that would cause a later call to fail. |
| **Environment Requirements** | `any` |
| **Input Data** | No request fields. Standard connection from `BaseRemoteTest`. |
| **Dataset** | N/A |
| **Expected Output** | Each of the five calls returns `true` |
| **Pass Criteria** | - All five `assertTrue(client.ping())` assertions pass<br>- No exception is thrown across all five calls |
| **Notes** | Execute calls in a simple for-loop. This is a lightweight smoke-test for connection-pool or socket reuse issues. |

---

#### CFT-002 — `ping` returns `true` after other WsFileIO operations

| Field | Detail |
|---|---|
| **Test ID** | CFT-002 |
| **Category** | Core Functionality |
| **Subcategory** | Typical Workflows |
| **Description** | Perform a `createFile` call (or any other WsFileIO operation that modifies state), then immediately call `ping`. Verifies that `ping` remains available and correct after the service has processed real work. |
| **Environment Requirements** | `any` |
| **Input Data** | A valid `CreateFile` request (drop zone name, relative path, overwrite=true) followed by an empty ping request. Uses drop-zone properties from `BaseRemoteTest` system properties (`lztestfile`, `lzname`, `lzpath`). |
| **Dataset** | N/A |
| **Expected Output** | `ping()` returns `true` |
| **Pass Criteria** | - `createFile` does not throw an unexpected exception<br>- Subsequent `ping()` returns `true` |
| **Notes** | Skip test if landing-zone properties are not configured (use `Assume.assumeNotNull`). |

---

### B. Edge Case Tests

---

#### ECT-001 — Concurrent ping calls from multiple threads

| Field | Detail |
|---|---|
| **Test ID** | ECT-001 |
| **Category** | Edge Case |
| **Subcategory** | Performance Limits |
| **Description** | Submit 10 concurrent `ping()` calls using an `ExecutorService` and assert all return `true`. Verifies that the method is thread-safe and that the underlying Axis2 stub / connection pool handles concurrent access correctly. |
| **Environment Requirements** | `any` |
| **Input Data** | No request fields. Standard connection. |
| **Dataset** | N/A |
| **Expected Output** | All 10 futures resolve to `true` |
| **Pass Criteria** | - `Future.get()` returns `true` for every submitted task<br>- No `ExecutionException` is thrown<br>- Test completes within a reasonable timeout (e.g., 30 s) |
| **Notes** | Use `Executors.newFixedThreadPool(10)` and `invokeAll`. Fails if any thread receives `false` or throws. |

---

### C. Error Handling Tests

---

#### EHT-001 — `ping` returns `false` when service host is unreachable

| Field | Detail |
|---|---|
| **Test ID** | EHT-001 |
| **Category** | Error Handling |
| **Subcategory** | Client-Side Errors — Communication Failures |
| **Description** | Create an `HPCCWsFileIOClient` pointing at a non-existent host (e.g., `http://192.0.2.1:8010` — RFC 5737 TEST-NET, guaranteed unreachable) and call `ping()`. Verifies that the method swallows the `ConnectException` / timeout and returns `false` rather than propagating the exception. |
| **Environment Requirements** | `any` |
| **Input Data** | `Connection` with URL `http://192.0.2.1:8010`; any non-empty credentials |
| **Dataset** | N/A |
| **Expected Output** | `ping()` returns `false` |
| **Pass Criteria** | - Return value is `false`<br>- No exception propagates to the test method |
| **Notes** | Connection timeout may make this test slow. Set a short socket timeout (e.g., 2 s) on the `Connection` object before creating the client. |

---

#### EHT-002 — `ping` returns `false` for wrong port

| Field | Detail |
|---|---|
| **Test ID** | EHT-002 |
| **Category** | Error Handling |
| **Subcategory** | Client-Side Errors — Communication Failures |
| **Description** | Create an `HPCCWsFileIOClient` pointing at the correct HPCC host but with an incorrect port (e.g., port `9999`). Verify `ping()` returns `false`. |
| **Environment Requirements** | `any` |
| **Input Data** | `Connection` with correct host from `BaseRemoteTest`, port `9999`; valid credentials |
| **Dataset** | N/A |
| **Expected Output** | `ping()` returns `false` |
| **Pass Criteria** | - Return value is `false`<br>- No exception propagates |
| **Notes** | Use the `hpccconn` system property to extract only the host part and substitute a bad port. Set a short connect timeout. |

---

#### EHT-003 — `verifyStub()` throws when client is uninitialised

| Field | Detail |
|---|---|
| **Test ID** | EHT-003 |
| **Category** | Error Handling |
| **Subcategory** | Client-Side Errors — API Misuse |
| **Description** | Construct an `HPCCWsFileIOClient` via its default/no-arg constructor or without calling the factory method that sets up the stub, then call `ping()`. Verifies that `verifyStub()` correctly throws an `Exception` (rather than a `NullPointerException`) before the try-block is entered, signalling improper client initialisation. |
| **Environment Requirements** | `any` |
| **Input Data** | `HPCCWsFileIOClient` instance constructed without a valid `Connection` (or with a `null` connection) |
| **Dataset** | N/A |
| **Expected Output** | An `Exception` (or a subtype such as `NullPointerException`) is thrown |
| **Pass Criteria** | - `assertThrows(Exception.class, () -> uninitClient.ping())` passes<br>- The exception is thrown *before* a network call is attempted |
| **Notes** | Investigate the factory/constructor API to determine the correct way to produce an uninitialised client without triggering a NullPointerException at construction time. If `HPCCWsFileIOClient` prevents construction without a connection, mark this test as `@Ignore` with a note explaining the protection mechanism. |

---

### D. Connectivity Tests

---

#### CNT-001 — `ping` returns `false` for invalid credentials

| Field | Detail |
|---|---|
| **Test ID** | CNT-001 |
| **Category** | Connectivity |
| **Subcategory** | Invalid Auth |
| **Description** | Create an `HPCCWsFileIOClient` using the correct endpoint but with deliberately wrong credentials (e.g., username `"invalid_user"`, password `"wrong_password"`). Call `ping()` and assert it returns `false`. Verifies that authentication failure is handled gracefully and does not propagate as an unchecked exception. |
| **Environment Requirements** | `secure` |
| **Input Data** | Correct `hpccconn` endpoint; username `"invalid_user"`; password `"wrong_password"` |
| **Dataset** | N/A |
| **Expected Output** | `ping()` returns `false` |
| **Pass Criteria** | - Return value is `false`<br>- No exception propagates to the test |
| **Notes** | Skip on unsecured clusters using `Assume`. The test should use `@ConditionalIgnore` or a similar mechanism. If the cluster is not secured, the ESP does not enforce credentials and this test would incorrectly return `true`. |

---

#### CNT-002 — `ping` returns `false` for empty credentials on a secured cluster

| Field | Detail |
|---|---|
| **Test ID** | CNT-002 |
| **Category** | Connectivity |
| **Subcategory** | Invalid Auth |
| **Description** | Create an `HPCCWsFileIOClient` with empty string username and password against a secured HPCC cluster. Assert `ping()` returns `false`. Validates that the absence of credentials is treated as an authentication failure, not silently allowed. |
| **Environment Requirements** | `secure` |
| **Input Data** | Correct `hpccconn` endpoint; `username = ""`; `password = ""` |
| **Dataset** | N/A |
| **Expected Output** | `ping()` returns `false` |
| **Pass Criteria** | - Return value is `false`<br>- No exception propagates |
| **Notes** | Skip on unsecured clusters using `Assume`. Complements CNT-001 by covering the empty-credential edge case separately. |

---

## 8. New Dataset Specifications

**No new datasets are required** for any of the test cases above. All `ping` tests are purely connection-level and send no file data to the cluster.

The existing `generate-datasets.ecl` file requires **no modifications** for the `ping` method tests.

---

## Appendix A — File Locations

| Artifact | Path |
|---|---|
| Java client class | `wsclient/src/main/java/org/hpccsystems/ws/client/HPCCWsFileIOClient.java` |
| Axis2 stub | `wsclient/src/main/java/org/hpccsystems/ws/client/gen/axis2/wsfileio/latest/WsFileIOStub.java` |
| Ping request (Axis2) | `wsclient/src/main/java/org/hpccsystems/ws/client/gen/axis2/wsfileio/latest/WsFileIOPingRequest.java` |
| Ping response (Axis2) | `wsclient/src/main/java/org/hpccsystems/ws/client/gen/axis2/wsfileio/latest/WsFileIOPingResponse.java` |
| Ping request wrapper | `wsclient/src/main/java/org/hpccsystems/ws/client/wrappers/gen/wsfileio/WsFileIOPingRequestWrapper.java` |
| Ping response wrapper | `wsclient/src/main/java/org/hpccsystems/ws/client/wrappers/gen/wsfileio/WsFileIOPingResponseWrapper.java` |
| WSDL | `wsclient/src/main/resources/WSDLs/WsFileIO.wsdl` |
| IDL (ECM) | `HPCC-Platform/esp/scm/ws_fileio.ecm` |
| Server implementation (hpp) | `HPCC-Platform/esp/services/ws_fileio/ws_fileioservice.hpp` |
| Server implementation (cpp) | `HPCC-Platform/esp/services/ws_fileio/ws_fileioservice.cpp` |
| Test class | `wsclient/src/test/java/org/hpccsystems/ws/client/WSFileIOClientTest.java` |
| Base remote test | `wsclient/src/test/java/org/hpccsystems/ws/client/BaseRemoteTest.java` |
| Dataset generation ECL | `wsclient/src/test/resources/generate-datasets.ecl` |

---

## Appendix B — Test Case Summary

| Test ID | Category | Subcategory | Environment | Gap Addressed |
|---|---|---|---|---|
| CFT-001 | Core Functionality | Typical Workflows | `any` | Idempotency / no resource leak across repeated calls |
| CFT-002 | Core Functionality | Typical Workflows | `any` | Availability after other service operations |
| ECT-001 | Edge Case | Performance Limits | `any` | Thread-safety / concurrent access |
| EHT-001 | Error Handling | Communication Failures | `any` | Unreachable host → `false` (not exception) |
| EHT-002 | Error Handling | Communication Failures | `any` | Wrong port → `false` (not exception) |
| EHT-003 | Error Handling | API Misuse | `any` | Uninitialised client → `verifyStub()` throws |
| CNT-001 | Connectivity | Invalid Auth | `secure` | Wrong credentials → `false` on secured cluster |
| CNT-002 | Connectivity | Invalid Auth | `secure` | Empty credentials → `false` on secured cluster |


---
*Generated: 2026-03-26*
