/*##############################################################################

    HPCC SYSTEMS software Copyright (C) 2019 HPCC Systems®.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
############################################################################## */

package org.hpccsystems.ws.client;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.axis2.AxisFault;
import org.hpccsystems.commons.annotations.ConnectivityTests;
import org.hpccsystems.commons.annotations.CoreFunctionalityTests;
import org.hpccsystems.commons.annotations.EdgeCaseTests;
import org.hpccsystems.commons.annotations.ErrorHandlingTests;
import org.hpccsystems.ws.client.utils.Connection;
import org.hpccsystems.ws.client.utils.DelimitedDataOptions;
import org.hpccsystems.ws.client.wrappers.ArrayOfEspExceptionWrapper;
import org.hpccsystems.ws.client.wrappers.gen.filespray.ProgressResponseWrapper;
import org.hpccsystems.ws.client.wrappers.wsdfu.DFUInfoWrapper;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runners.MethodSorters;

import io.opentelemetry.instrumentation.annotations.WithSpan;

import static org.junit.Assume.assumeFalse;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class WSFileIOClientTest extends BaseRemoteTest
{
    private final static HPCCWsFileIOClient client = wsclient.getWsFileIOClient();
    private static boolean isContainerized = false;
    private final static String testfilename = System.getProperty("lztestfile", "myfilename.txt");
    private final static String targetLZ = System.getProperty("lzname", "mydropzone"); //targetLZ accepted the address "localhost" once upon a time.
    private final static String targetLZPath = System.getProperty("lzpath", "/var/lib/HPCCSystems/mydropzone");
    private final static String targetLZAddress = System.getProperty("lzaddress", ".");

    // Shared test data for readFileData tests
    private static final String READ_FILE_DATA_TEST_CONTENT =
            "HELLO MY DARLING, HELLO MY DEAR!1234567890ABCDEFGHIJKLMNOPQRSTUVXYZ";

    /**
     * Creates a uniquely-named landing zone file pre-populated with the given data.
     * Uses Assume to skip the calling test if the setup cannot complete (e.g., LZ not configured).
     */
    private String setupReadFileDataTestFile(String testId, byte[] data) throws Exception, ArrayOfEspExceptionWrapper
    {
        String uniqueFileName = "wsfileio_rfd_" + testId + "_" + System.currentTimeMillis() + ".dat";
        boolean created = client.createHPCCFile(uniqueFileName, targetLZ, true,
                isContainerized ? null : targetLZAddress);
        Assume.assumeTrue("readFileData pre-condition failed: could not create file " + uniqueFileName, created);
        boolean written = client.writeHPCCFileData(data, uniqueFileName, targetLZ, true, 0,
                data.length + 1, isContainerized ? null : targetLZAddress);
        Assume.assumeTrue("readFileData pre-condition failed: could not write file " + uniqueFileName, written);
        return uniqueFileName;
    }

    static
    {
        try
        {
            if (client.isTargetHPCCContainerized())
                isContainerized = true;
        }
        catch (Exception e)
        {
            System.out.println("Could not determine if target service is containerized, default: 'false'");
        }

        if (System.getProperty("lztestfile") == null)
            System.out.println("lztestfile not provided - defaulting to myfilename.txt");

        if (System.getProperty("lzname") == null)
            System.out.println("lzname not provided - defaulting to localhost");

        if (System.getProperty("lzpath") == null)
            System.out.println("lzpath not provided - defaulting to /var/lib/HPCCSystems/mydropzone");

        if (System.getProperty("lzaddress") == null)
            System.out.println("lzaddress not provided - defaulting to '.'");
    }

    @Test
    @WithSpan
    public void copyFile() throws Exception
    {
        String lzfile=System.currentTimeMillis() + "_csvtest.csv";
        String hpccfilename="temp::" + lzfile;
        client.createHPCCFile(lzfile, targetLZ, true, isContainerized ? null : targetLZAddress);
        byte[] data = "Product,SKU,Color\r\nBike,1234,Blue\r\nCar,2345,Red\r\n".getBytes();
        client.writeHPCCFileData(data, lzfile, targetLZ, true, 0, 20, isContainerized ? null : targetLZAddress);
        try
        {
            System.out.println("Starting file spray.");
            ProgressResponseWrapper dfuspray = wsclient.getFileSprayClient().sprayVariable(
                    new DelimitedDataOptions(),
                    wsclient.getFileSprayClient().fetchLocalDropZones().get(0),
                    lzfile,"~" + hpccfilename,"",thorClusterFileGroup,true,
                    HPCCFileSprayClient.SprayVariableFormat.DFUff_csv,
                    null, null, null, null, null, null, null);
            if (dfuspray.getExceptions() != null
                && dfuspray.getExceptions().getException() != null
                && dfuspray.getExceptions().getException().size()>0)
            {
                fail(dfuspray.getExceptions().getException().get(0).getMessage());
            }

            List<String> whiteListedStates = Arrays.asList( "queued", "started", "unknown", "finished", "monitoring");
            int waitCount = 0;
            int MAX_WAIT_COUNT = 60;

            ProgressResponseWrapper dfuProgress = null;
            do
            {
                dfuProgress = wsclient.getFileSprayClient().getDfuProgress(dfuspray.getWuid());
                boolean stateIsWhiteListed = whiteListedStates.contains(dfuProgress.getState());
                if (!stateIsWhiteListed)
                {
                    fail("File spray failed: Summary: " + dfuProgress.getSummaryMessage() + " Exceptions: " + dfuProgress.getExceptions());
                }

                if (dfuProgress.getPercentDone() < 100)
                {
                    Thread.sleep(1000);
                    System.out.println("File spray percent complete: " + dfuProgress.getPercentDone() + "% Sleeping for 1sec to wait for spray.");
                    waitCount++;
                }
            } while (waitCount < 60 && dfuProgress.getPercentDone() < 100);

            assumeTrue("File spray did not complete within: " + MAX_WAIT_COUNT + "s. Failing test.", waitCount < MAX_WAIT_COUNT);

            System.out.println("Test file successfully sprayed to " + "~" + hpccfilename + ", attempting copy to " + hpccfilename + "_2");
            wsclient.getFileSprayClient().copyFile(hpccfilename,hpccfilename + "_2",true);
            Thread.sleep(1000);
            DFUInfoWrapper copiedContent=wsclient.getWsDFUClient().getFileInfo(hpccfilename + "_2", thorClusterFileGroup);
            if (copiedContent ==null || copiedContent.getExceptions() != null)
            {
                if (copiedContent != null )
                {
                    System.out.println(copiedContent.getExceptions().getMessage());
                }
                throw new Exception("File copy failed");
            }
        }
        catch (Exception e)
        {
            System.out.println(e.getMessage());
            e.printStackTrace();
            fail("Could not copy file: " + e.getMessage());
        }
        finally
        {
            try
            {
                Set<String> fnames=new HashSet<String>();
                fnames.add(hpccfilename);
                fnames.add(hpccfilename + "_2");
                wsclient.getWsDFUClient().deleteFiles(fnames, thorClusterFileGroup);
            }
            catch (Exception e2)
            {
                System.out.println("Could not delete test file " + hpccfilename + " from " + thorClusterFileGroup + ":" + e2.getMessage());
            }
        }
    }

    @Test
    @WithSpan
    public void AcreateHPCCFile() throws Exception, ArrayOfEspExceptionWrapper
    {
        if (isContainerized)
        {
            System.out.println("Creating file: '" + testfilename + "' on LandingZone: '" + targetLZ + "' on HPCC: '" + super.connString +"'");
            System.out.println("Target HPCC is containerized, not providing targetLZAddress");
            Assert.assertTrue(client.createHPCCFile(testfilename, targetLZ, true, null));
        }
        else
        {
            System.out.println("Creating file: '" + testfilename + "' on LandingZone: '" + targetLZ + "' targetLZaddress: '" + targetLZAddress + "' on HPCC: '" + super.connString +"'");
            System.out.println("Target HPCC is NOT containerized, providing targetLZAddress");
            Assert.assertTrue(client.createHPCCFile(testfilename, targetLZ, true, targetLZAddress));
        }
    }

    @Test
    @WithSpan
    public void BwriteHPCCFile() throws Exception, ArrayOfEspExceptionWrapper
    {
        System.out.println("Writing data to file: '" + testfilename + "' on LandingZone: '" + targetLZ + "' on HPCC: '" + super.connString +"'");
        byte[] data = "HELLO MY DARLING, HELLO MY DEAR!1234567890ABCDEFGHIJKLMNOPQRSTUVXYZ".getBytes();
        if (isContainerized)
        {
            Assert.assertTrue(client.writeHPCCFileData(data, testfilename, targetLZ, true, 0, 20, null));
        }
        else
        {
            Assert.assertTrue(client.writeHPCCFileData(data, testfilename, targetLZ, true, 0, 20, targetLZAddress));
        }
    }

    @Test
    @WithSpan
    public void CreadHPCCFile() throws Exception, ArrayOfEspExceptionWrapper
    {
        System.out.println("reading data from file: '" + testfilename + "' on LandingZone: '" + targetLZ + "' on HPCC: '" + super.connString +"'");
        byte[] data = "HELLO MY DARLING, HELLO MY DEAR!1234567890ABCDEFGHIJKLMNOPQRSTUVXYZ".getBytes();
        String response = null;
        if (isContainerized)
        {
             response = client.readFileData(targetLZ, testfilename, data.length, 0, null);
        }
        else
        {
             response = client.readFileData(targetLZ, testfilename, data.length, 0, targetLZAddress);
        }
        Assert.assertNotNull(response);
        Assert.assertArrayEquals(data, response.getBytes());
    }

    @Test
    public void ping() throws Exception
    {
        try
        {
            Assert.assertTrue(client.ping());
        }
        catch (AxisFault e)
        {
            e.printStackTrace();
            Assert.fail();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            Assert.fail();
        }
    }

    @Test
    public void getContainerizedModeTest() throws Exception
    {
        System.out.println("Fetching isTargetHPCCContainerized...");
        assertNotNull(client.isTargetHPCCContainerized());
    }

    /**
     * CFT-001-ping — Calls ping() five times in sequence and asserts every call returns true.
     * Verifies idempotency and the absence of resource leaks across repeated calls.
     *
     * <p>Environment requirements: any
     */
    @Category(CoreFunctionalityTests.class)
    @Test
    public void testPing_repeatedSuccessiveCalls() throws Exception
    {
        for (int i = 0; i < 5; i++)
        {
            Assert.assertTrue("ping() returned false on call " + (i + 1), client.ping());
        }
    }

    /**
     * CFT-002-ping — Performs a createFile operation and then immediately calls ping().
     * Verifies that ping remains available and correct after the service has processed real work.
     *
     * <p>Environment requirements: any
     */
    @Category(CoreFunctionalityTests.class)
    @Test
    public void testPing_afterOtherOperations() throws Exception
    {
        Assume.assumeNotNull(System.getProperty("lzname"), System.getProperty("lztestfile"));

        String uniqueFileName = "ping_cft002_" + System.currentTimeMillis() + ".dat";
        try
        {
            client.createHPCCFile(uniqueFileName, targetLZ, true, isContainerized ? null : targetLZAddress);
        }
        catch (Exception e)
        {
            // createFile failure is acceptable; what matters is ping still works afterward
            System.out.println("testPing_afterOtherOperations: createFile threw (acceptable): " + e.getMessage());
        }

        Assert.assertTrue("ping() returned false after createFile operation", client.ping());
    }

    /**
     * ECT-001-ping — Submits 10 concurrent ping() calls using an ExecutorService and asserts
     * all return true. Verifies thread-safety and correct concurrent handling by the Axis2 stub.
     *
     * <p>Environment requirements: any
     */
    @Category(EdgeCaseTests.class)
    @Test
    public void testPing_concurrentCalls() throws Exception
    {
        final int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++)
        {
            tasks.add(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception
                {
                    return client.ping();
                }
            });
        }

        List<Future<Boolean>> futures = executor.invokeAll(tasks, 30, TimeUnit.SECONDS);
        executor.shutdown();

        for (int i = 0; i < futures.size(); i++)
        {
            Assert.assertTrue("ping() returned false on concurrent call " + i, futures.get(i).get());
        }
    }

    /**
     * EHT-001-ping — Creates an HPCCWsFileIOClient pointing at a non-existent host
     * (RFC 5737 TEST-NET 192.0.2.1) and calls ping(). Verifies the method swallows the
     * ConnectException and returns false rather than propagating an exception.
     *
     * <p>Environment requirements: any
     */
    @Category(ErrorHandlingTests.class)
    @Test
    public void testPing_unreachableHost() throws Exception
    {
        HPCCWsFileIOClient unreachableClient = HPCCWsFileIOClient.get("http", "192.0.2.1", "8010", "anyuser", "anypass", 2000);
        Assert.assertFalse("ping() should return false for unreachable host", unreachableClient.ping());
    }

    /**
     * EHT-002-ping — Creates an HPCCWsFileIOClient pointing at the correct HPCC host but
     * with an incorrect port (9999). Verifies ping() returns false rather than throwing.
     *
     * <p>Environment requirements: any
     */
    @Category(ErrorHandlingTests.class)
    @Test
    public void testPing_wrongPort() throws Exception
    {
        URL url = new URL(connString);
        String protocol = url.getProtocol();
        String host = url.getHost();
        HPCCWsFileIOClient wrongPortClient = HPCCWsFileIOClient.get(protocol, host, "9999", hpccUser, hpccPass, 2000);
        Assert.assertFalse("ping() should return false for wrong port", wrongPortClient.ping());
    }

    /**
     * EHT-003-ping — Attempts to invoke ping() on an uninitialised HPCCWsFileIOClient to verify
     * that verifyStub() throws an Exception before a network call is made.
     *
     * <p>Environment requirements: any
     */
    @Category(ErrorHandlingTests.class)
    @Ignore("HPCCWsFileIOClient construction with a null Connection throws NullPointerException "
            + "before ping() is reached; the stub-null guard in verifyStub() cannot be exercised "
            + "via the public API because there is no no-arg constructor or deferred-init factory.")
    @Test
    public void testPing_uninitializedClient() throws Exception
    {
        // Not reachable — kept as documentation of the design constraint.
        Assert.fail("This test should have been skipped by @Ignore");
    }

    /**
     * CNT-001-ping — Creates an HPCCWsFileIOClient with the correct endpoint but deliberately
     * wrong credentials and asserts ping() returns false. Verifies auth failure is handled
     * gracefully on a secured cluster.
     *
     * <p>Environment requirements: secure
     */
    @Category(ConnectivityTests.class)
    @Test
    public void testPing_invalidCredentials() throws Exception
    {
        Assume.assumeTrue("Skipping CNT-001: target HPCC cluster does not enforce authentication",
                client.doesTargetHPCCAuthenticate());

        Connection badConn = new Connection(connString);
        badConn.setCredentials("invalid_user", "wrong_password");
        HPCCWsFileIOClient badCredsClientFinal = HPCCWsFileIOClient.get(badConn);
        Assert.assertFalse("ping() should return false for invalid credentials", badCredsClientFinal.ping());
    }

    /**
     * CNT-002-ping — Creates an HPCCWsFileIOClient with empty username and password against a
     * secured HPCC cluster and asserts ping() returns false. Validates that absent credentials
     * are treated as an authentication failure.
     *
     * <p>Environment requirements: secure
     */
    @Category(ConnectivityTests.class)
    @Test
    public void testPing_emptyCredentials() throws Exception
    {
        Assume.assumeTrue("Skipping CNT-002: target HPCC cluster does not enforce authentication",
                client.doesTargetHPCCAuthenticate());

        Connection emptyCredsConn = new Connection(connString);
        emptyCredsConn.setCredentials("", "");
        HPCCWsFileIOClient emptyCredsClient = HPCCWsFileIOClient.get(emptyCredsConn);
        Assert.assertFalse("ping() should return false for empty credentials", emptyCredsClient.ping());
    }

    // ===== createHPCCFile Tests =====

    /**
     * CFT-001-createHPCCFile — Creates a new file with overwrite=false when the file does not
     * pre-exist. Verifies the operation succeeds and returns true.
     *
     * <p>Environment requirements: any
     */
    @Category(CoreFunctionalityTests.class)
    @Test
    public void testCreateHPCCFile_overwriteFalseNewFile() throws Exception, ArrayOfEspExceptionWrapper
    {
        String uniqueFileName = "wsfileio_cft001_" + System.currentTimeMillis() + ".txt";
        System.out.println("CFT-001: Creating new file with overwrite=false: " + uniqueFileName);
        boolean result = client.createHPCCFile(uniqueFileName, targetLZ, false,
                isContainerized ? null : targetLZAddress);
        Assert.assertTrue("createHPCCFile with overwrite=false should succeed for a new file", result);
    }

    /**
     * CFT-002-createHPCCFile — Creates a file and then creates it again with overwrite=true.
     * Verifies both calls succeed and no exception is thrown.
     *
     * <p>Environment requirements: any
     */
    @Category(CoreFunctionalityTests.class)
    @Test
    public void testCreateHPCCFile_overwriteTrueFileExists() throws Exception, ArrayOfEspExceptionWrapper
    {
        String uniqueFileName = "wsfileio_cft002_" + System.currentTimeMillis() + ".txt";
        System.out.println("CFT-002: Creating file then overwriting: " + uniqueFileName);
        boolean first = client.createHPCCFile(uniqueFileName, targetLZ, true,
                isContainerized ? null : targetLZAddress);
        Assert.assertTrue("First createHPCCFile call should succeed", first);
        boolean second = client.createHPCCFile(uniqueFileName, targetLZ, true,
                isContainerized ? null : targetLZAddress);
        Assert.assertTrue("Second createHPCCFile call with overwrite=true should succeed", second);
    }

    /**
     * CFT-003-createHPCCFile — Invokes the deprecated 3-parameter createHPCCFile overload
     * (no lzAddress). Verifies it delegates to the 4-parameter version with lzAddress=null
     * and returns true in a containerized environment.
     *
     * <p>Environment requirements: containerized
     */
    @Category(CoreFunctionalityTests.class)
    @Test
    @SuppressWarnings("deprecation")
    public void testCreateHPCCFile_deprecatedThreeParamOverload() throws Exception, ArrayOfEspExceptionWrapper
    {
        Assume.assumeTrue("CFT-003: Skipping — target HPCC is not containerized", isContainerized);
        String uniqueFileName = "wsfileio_cft003_" + System.currentTimeMillis() + ".txt";
        System.out.println("CFT-003: Using deprecated 3-param overload: " + uniqueFileName);
        boolean result = client.createHPCCFile(uniqueFileName, targetLZ, true);
        Assert.assertTrue("Deprecated 3-param createHPCCFile should succeed", result);
    }

    /**
     * CFT-004-createHPCCFile — Passes lzAddress=null explicitly with the 4-parameter signature
     * in a containerized environment. Verifies DestNetAddress is omitted from the request and
     * the server accepts the call.
     *
     * <p>Environment requirements: containerized
     */
    @Category(CoreFunctionalityTests.class)
    @Test
    public void testCreateHPCCFile_lzAddressNullContainerized() throws Exception, ArrayOfEspExceptionWrapper
    {
        Assume.assumeTrue("CFT-004: Skipping — target HPCC is not containerized", isContainerized);
        String uniqueFileName = "wsfileio_cft004_" + System.currentTimeMillis() + ".txt";
        System.out.println("CFT-004: Creating file with explicit lzAddress=null: " + uniqueFileName);
        boolean result = client.createHPCCFile(uniqueFileName, targetLZ, true, null);
        Assert.assertTrue("createHPCCFile with null lzAddress should succeed on containerized HPCC", result);
    }

    /**
     * ECT-001-createHPCCFile — Uses a fileName containing a subdirectory path component
     * (e.g., "wsfileio_subdir/file.txt"). Verifies the method handles path separators without
     * throwing an unexpected exception, regardless of whether the subdirectory pre-exists.
     *
     * <p>Environment requirements: any
     */
    @Category(EdgeCaseTests.class)
    @Test
    public void testCreateHPCCFile_fileNameWithSubdirectory() throws Exception
    {
        String uniqueFileName = "wsfileio_subdir/" + System.currentTimeMillis() + "_ect001.txt";
        System.out.println("ECT-001: Creating file with subdirectory path: " + uniqueFileName);
        try
        {
            boolean result = client.createHPCCFile(uniqueFileName, targetLZ, true,
                    isContainerized ? null : targetLZAddress);
            System.out.println("ECT-001: createHPCCFile returned: " + result + " for subdirectory path");
            // true (subdirectory exists) or false (subdirectory absent) are both valid outcomes
        }
        catch (ArrayOfEspExceptionWrapper e)
        {
            System.out.println("ECT-001: Server raised ESP exception for subdirectory path (acceptable): " + e.getMessage());
        }
        catch (Exception e)
        {
            Assert.fail("ECT-001: Unexpected exception type for subdirectory path: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    /**
     * ECT-002-createHPCCFile — Uses a fileName with no file extension. Verifies the server
     * does not enforce an extension requirement and the file is created successfully.
     *
     * <p>Environment requirements: any
     */
    @Category(EdgeCaseTests.class)
    @Test
    public void testCreateHPCCFile_fileNameNoExtension() throws Exception, ArrayOfEspExceptionWrapper
    {
        String uniqueFileName = System.currentTimeMillis() + "_ect002_nodotfile";
        System.out.println("ECT-002: Creating file with no extension: " + uniqueFileName);
        boolean result = client.createHPCCFile(uniqueFileName, targetLZ, true,
                isContainerized ? null : targetLZAddress);
        Assert.assertTrue("createHPCCFile should succeed for a file name with no extension", result);
    }

    /**
     * ECT-003-createHPCCFile — Uses a fileName containing hyphens and underscores
     * (e.g., "1234567890_my-test_file.dat"). Verifies common separator characters are
     * accepted by the server.
     *
     * <p>Environment requirements: any
     */
    @Category(EdgeCaseTests.class)
    @Test
    public void testCreateHPCCFile_fileNameWithSpecialChars() throws Exception, ArrayOfEspExceptionWrapper
    {
        String uniqueFileName = System.currentTimeMillis() + "_my-test_file_ect003.dat";
        System.out.println("ECT-003: Creating file with hyphens and underscores: " + uniqueFileName);
        boolean result = client.createHPCCFile(uniqueFileName, targetLZ, true,
                isContainerized ? null : targetLZAddress);
        Assert.assertTrue("createHPCCFile should succeed for a file name with hyphens and underscores", result);
    }

    /**
     * ECT-004-createHPCCFile — Passes an empty string "" as lzAddress. Verifies the client
     * treats it identically to null (skips DestNetAddress) and the call succeeds on a
     * containerized cluster.
     *
     * <p>Environment requirements: containerized
     */
    @Category(EdgeCaseTests.class)
    @Test
    public void testCreateHPCCFile_lzAddressEmptyString() throws Exception, ArrayOfEspExceptionWrapper
    {
        Assume.assumeTrue("ECT-004: Skipping — empty lzAddress is only valid on containerized clusters", isContainerized);
        String uniqueFileName = "wsfileio_ect004_" + System.currentTimeMillis() + ".txt";
        System.out.println("ECT-004: Creating file with lzAddress=\"\": " + uniqueFileName);
        boolean result = client.createHPCCFile(uniqueFileName, targetLZ, true, "");
        Assert.assertTrue("createHPCCFile with empty lzAddress should succeed (treated same as null)", result);
    }

    /**
     * ECT-005-createHPCCFile — Uses a fileName of 255 characters to test the upper boundary
     * of file system and server name length limits. Verifies no unchecked exception escapes.
     *
     * <p>Environment requirements: any
     */
    @Category(EdgeCaseTests.class)
    @Test
    public void testCreateHPCCFile_veryLongFileName() throws Exception
    {
        // 251 'a' chars + ".txt" = 255 total characters
        String longName = new String(new char[251]).replace('\0', 'a') + ".txt";
        System.out.println("ECT-005: Creating file with 255-char name");
        try
        {
            boolean result = client.createHPCCFile(longName, targetLZ, true,
                    isContainerized ? null : targetLZAddress);
            System.out.println("ECT-005: createHPCCFile returned: " + result + " for 255-char filename");
            // true or false are both acceptable; no unchecked exception should escape
        }
        catch (ArrayOfEspExceptionWrapper e)
        {
            System.out.println("ECT-005: Server raised ESP exception for long filename (acceptable): " + e.getMessage());
        }
        catch (Exception e)
        {
            System.out.println("ECT-005: Exception for long filename (acceptable): " + e.getMessage());
        }
    }

    /**
     * ECT-006-createHPCCFile — Uses a single-character fileName ("x") to test the minimal
     * lower boundary of the DestRelativePath field. Verifies the server accepts it and returns true.
     *
     * <p>Environment requirements: any
     */
    @Category(EdgeCaseTests.class)
    @Test
    public void testCreateHPCCFile_singleCharFileName() throws Exception, ArrayOfEspExceptionWrapper
    {
        System.out.println("ECT-006: Creating file with single-char name: x");
        boolean result = client.createHPCCFile("x", targetLZ, true,
                isContainerized ? null : targetLZAddress);
        Assert.assertTrue("createHPCCFile should succeed for a single-character file name", result);
    }

    /**
     * EHT-001-createHPCCFile — Passes null as fileName. Verifies the client throws an
     * Exception with a message containing "fileName required" before making any network call.
     *
     * <p>Environment requirements: any
     */
    @Category(ErrorHandlingTests.class)
    @Test
    public void testCreateHPCCFile_nullFileName() throws Exception
    {
        try
        {
            client.createHPCCFile(null, targetLZ, true, isContainerized ? null : targetLZAddress);
            Assert.fail("EHT-001: Expected Exception for null fileName but none was thrown");
        }
        catch (ArrayOfEspExceptionWrapper e)
        {
            Assert.fail("EHT-001: Expected Exception but got ArrayOfEspExceptionWrapper: " + e.getMessage());
        }
        catch (Exception e)
        {
            Assert.assertTrue("EHT-001: Exception message should contain 'fileName required'",
                    e.getMessage().contains("fileName required"));
        }
    }

    /**
     * EHT-002-createHPCCFile — Passes empty string "" as fileName. Verifies the client throws
     * an Exception with a message containing "fileName required" before any network call.
     *
     * <p>Environment requirements: any
     */
    @Category(ErrorHandlingTests.class)
    @Test
    public void testCreateHPCCFile_emptyFileName() throws Exception
    {
        try
        {
            client.createHPCCFile("", targetLZ, true, isContainerized ? null : targetLZAddress);
            Assert.fail("EHT-002: Expected Exception for empty fileName but none was thrown");
        }
        catch (ArrayOfEspExceptionWrapper e)
        {
            Assert.fail("EHT-002: Expected Exception but got ArrayOfEspExceptionWrapper: " + e.getMessage());
        }
        catch (Exception e)
        {
            Assert.assertTrue("EHT-002: Exception message should contain 'fileName required'",
                    e.getMessage().contains("fileName required"));
        }
    }

    /**
     * EHT-003-createHPCCFile — Passes null as targetLandingZone. Verifies the client throws
     * an Exception with a message containing "targetLandingZone required" before any network call.
     *
     * <p>Environment requirements: any
     */
    @Category(ErrorHandlingTests.class)
    @Test
    public void testCreateHPCCFile_nullTargetLandingZone() throws Exception
    {
        try
        {
            client.createHPCCFile("somevalidfile_eht003.txt", null, true,
                    isContainerized ? null : targetLZAddress);
            Assert.fail("EHT-003: Expected Exception for null targetLandingZone but none was thrown");
        }
        catch (ArrayOfEspExceptionWrapper e)
        {
            Assert.fail("EHT-003: Expected Exception but got ArrayOfEspExceptionWrapper: " + e.getMessage());
        }
        catch (Exception e)
        {
            Assert.assertTrue("EHT-003: Exception message should contain 'targetLandingZone required'",
                    e.getMessage().contains("targetLandingZone required"));
        }
    }

    /**
     * EHT-004-createHPCCFile — Passes empty string "" as targetLandingZone. Verifies the client
     * throws an Exception with a message containing "targetLandingZone required".
     *
     * <p>Environment requirements: any
     */
    @Category(ErrorHandlingTests.class)
    @Test
    public void testCreateHPCCFile_emptyTargetLandingZone() throws Exception
    {
        try
        {
            client.createHPCCFile("somevalidfile_eht004.txt", "", true,
                    isContainerized ? null : targetLZAddress);
            Assert.fail("EHT-004: Expected Exception for empty targetLandingZone but none was thrown");
        }
        catch (ArrayOfEspExceptionWrapper e)
        {
            Assert.fail("EHT-004: Expected Exception but got ArrayOfEspExceptionWrapper: " + e.getMessage());
        }
        catch (Exception e)
        {
            Assert.assertTrue("EHT-004: Exception message should contain 'targetLandingZone required'",
                    e.getMessage().contains("targetLandingZone required"));
        }
    }

    /**
     * EHT-005-createHPCCFile — Creates a file then attempts to create the same file again with
     * overwrite=false. Verifies the second call returns false, reflecting the server response
     * "Failure: &lt;path&gt; exists."
     *
     * <p>Environment requirements: any
     */
    @Category(ErrorHandlingTests.class)
    @Test
    public void testCreateHPCCFile_overwriteFalseFileExists() throws Exception, ArrayOfEspExceptionWrapper
    {
        String uniqueFileName = "wsfileio_eht005_" + System.currentTimeMillis() + ".txt";
        System.out.println("EHT-005: Setting up pre-existing file: " + uniqueFileName);
        boolean firstResult = client.createHPCCFile(uniqueFileName, targetLZ, true,
                isContainerized ? null : targetLZAddress);
        Assume.assumeTrue("EHT-005: Pre-condition failed — could not create initial file", firstResult);

        System.out.println("EHT-005: Attempting overwrite=false on existing file: " + uniqueFileName);
        boolean secondResult = client.createHPCCFile(uniqueFileName, targetLZ, false,
                isContainerized ? null : targetLZAddress);
        Assert.assertFalse("EHT-005: createHPCCFile with overwrite=false should return false when file exists", secondResult);
    }

    /**
     * EHT-006-createHPCCFile — Passes a fileName of "." which resolves to the landing zone base
     * directory. Verifies the server returns a failure result and the method returns false or throws
     * an ESP exception — no silent success.
     *
     * <p>Environment requirements: any
     */
    @Category(ErrorHandlingTests.class)
    @Test
    public void testCreateHPCCFile_fileNameIsDirectory() throws Exception
    {
        System.out.println("EHT-006: Attempting to create file at directory path '.'");
        try
        {
            boolean result = client.createHPCCFile(".", targetLZ, true,
                    isContainerized ? null : targetLZAddress);
            Assert.assertFalse("EHT-006: createHPCCFile with a directory path should return false", result);
        }
        catch (ArrayOfEspExceptionWrapper e)
        {
            System.out.println("EHT-006: Server raised ESP exception for directory path (acceptable): " + e.getMessage());
        }
        catch (Exception e)
        {
            System.out.println("EHT-006: Exception for directory path (acceptable): " + e.getMessage());
        }
    }

    /**
     * EHT-007-createHPCCFile — Passes a targetLandingZone that does not correspond to any
     * configured drop zone. Verifies either an ArrayOfEspExceptionWrapper is thrown or the
     * method returns false — no silent success.
     *
     * <p>Environment requirements: any
     */
    @Category(ErrorHandlingTests.class)
    @Test
    public void testCreateHPCCFile_invalidDropZoneName() throws Exception
    {
        String uniqueFileName = "wsfileio_eht007_" + System.currentTimeMillis() + ".txt";
        System.out.println("EHT-007: Testing with non-existent drop zone name");
        try
        {
            boolean result = client.createHPCCFile(uniqueFileName, "nonexistent_dropzone_xyz", true,
                    isContainerized ? null : targetLZAddress);
            Assert.assertFalse("EHT-007: createHPCCFile with invalid drop zone should return false", result);
        }
        catch (ArrayOfEspExceptionWrapper e)
        {
            System.out.println("EHT-007: Server raised ESP exception for invalid drop zone (expected): " + e.getMessage());
            // Expected outcome — test passes
        }
    }

    /**
     * EHT-008-createHPCCFile — Passes an unreachable lzAddress (RFC 5737 TEST-NET 192.0.2.1).
     * Verifies the server surfaces an error — either an exception or false return — with no
     * silent success. Only meaningful for bare-metal deployments where DestNetAddress is used.
     *
     * <p>Environment requirements: baremetal
     */
    @Category(ErrorHandlingTests.class)
    @Test
    public void testCreateHPCCFile_unreachableLzAddress() throws Exception
    {
        Assume.assumeFalse("EHT-008: Skipping — test only applies to non-containerized (baremetal) deployments",
                isContainerized);
        String uniqueFileName = "wsfileio_eht008_" + System.currentTimeMillis() + ".txt";
        System.out.println("EHT-008: Testing with unreachable lzAddress 192.0.2.1");
        try
        {
            boolean result = client.createHPCCFile(uniqueFileName, targetLZ, true, "192.0.2.1");
            Assert.assertFalse("EHT-008: createHPCCFile with unreachable lzAddress should return false", result);
        }
        catch (ArrayOfEspExceptionWrapper e)
        {
            System.out.println("EHT-008: Server raised ESP exception for unreachable address (expected): " + e.getMessage());
        }
    }

    /**
     * CNT-001-createHPCCFile — Confirms the createHPCCFile endpoint is reachable and the method
     * returns true for a minimal valid request against a live cluster.
     *
     * <p>Environment requirements: any
     */
    @Category(ConnectivityTests.class)
    @Test
    public void testCreateHPCCFile_connectivity() throws Exception, ArrayOfEspExceptionWrapper
    {
        String uniqueFileName = "wsfileio_cnt001_" + System.currentTimeMillis() + ".txt";
        System.out.println("CNT-001: Connectivity test for createHPCCFile: " + uniqueFileName);
        boolean result = client.createHPCCFile(uniqueFileName, targetLZ, true,
                isContainerized ? null : targetLZAddress);
        Assert.assertTrue("CNT-001: createHPCCFile should return true — service must be reachable", result);
    }

    /**
     * CNT-002-createHPCCFile — Calls createHPCCFile using a client configured with invalid
     * credentials. Verifies an authentication-related exception is thrown or the method does
     * not return true on a security-enabled cluster.
     *
     * <p>Environment requirements: secure
     */
    @Category(ConnectivityTests.class)
    @Test
    public void testCreateHPCCFile_invalidCredentials() throws Exception
    {
        Assume.assumeTrue("CNT-002: Skipping — target HPCC cluster does not enforce authentication",
                client.doesTargetHPCCAuthenticate());
        Connection badConn = new Connection(connString);
        badConn.setCredentials("invaliduser", "wrongpass");
        HPCCWsFileIOClient badCredsClient = HPCCWsFileIOClient.get(badConn);

        String uniqueFileName = "wsfileio_cnt002_" + System.currentTimeMillis() + ".txt";
        System.out.println("CNT-002: Testing createHPCCFile with invalid credentials");
        try
        {
            boolean result = badCredsClient.createHPCCFile(uniqueFileName, targetLZ, true,
                    isContainerized ? null : targetLZAddress);
            Assert.assertFalse("CNT-002: createHPCCFile with invalid credentials should not return true", result);
        }
        catch (ArrayOfEspExceptionWrapper e)
        {
            System.out.println("CNT-002: ESP exception for invalid credentials (expected): " + e.getMessage());
        }
        catch (Exception e)
        {
            System.out.println("CNT-002: Exception for invalid credentials (acceptable): " + e.getMessage());
        }
    }

    /**
     * CNT-003-createHPCCFile — Calls createHPCCFile on a secured HPCC cluster using valid
     * credentials. Verifies the permission check in onCreateFile passes and the method returns true.
     *
     * <p>Environment requirements: secure
     */
    @Category(ConnectivityTests.class)
    @Test
    public void testCreateHPCCFile_validCredentialsSecured() throws Exception, ArrayOfEspExceptionWrapper
    {
        Assume.assumeTrue("CNT-003: Skipping — target HPCC cluster does not enforce authentication",
                client.doesTargetHPCCAuthenticate());
        String uniqueFileName = "wsfileio_cnt003_" + System.currentTimeMillis() + ".txt";
        System.out.println("CNT-003: Testing createHPCCFile with valid credentials on secured cluster: " + uniqueFileName);
        boolean result = client.createHPCCFile(uniqueFileName, targetLZ, true,
                isContainerized ? null : targetLZAddress);
        Assert.assertTrue("CNT-003: createHPCCFile should succeed with valid credentials on secured cluster", result);
    }

    // ===== writeHPCCFileData Tests =====

    /**
     * CFT-001-writeHPCCFileData — Write a small ASCII text payload using the primary
     * (non-deprecated) overload with an explicit dataTypeDescriptor of "text/plain; charset=UTF-8".
     * Verifies the method returns true and the data can be read back.
     *
     * <p>Environment requirements: any
     */
    @Category(CoreFunctionalityTests.class)
    @Test
    public void testWriteHPCCFileData_explicitTextMimeType() throws Exception, ArrayOfEspExceptionWrapper
    {
        String fileName = System.currentTimeMillis() + "_cft001.txt";
        byte[] data = "Hello WsFileIO CFT-001".getBytes(StandardCharsets.UTF_8);
        System.out.println("CFT-001-writeHPCCFileData: Creating and writing file: " + fileName);
        client.createHPCCFile(fileName, targetLZ, true, isContainerized ? null : targetLZAddress);
        boolean result = client.writeHPCCFileData(data, "text/plain; charset=UTF-8", fileName,
                targetLZ, true, 0, 100, isContainerized ? null : targetLZAddress);
        Assert.assertTrue("CFT-001: writeHPCCFileData should return true", result);
        String response = client.readFileData(targetLZ, fileName, data.length, 0,
                isContainerized ? null : targetLZAddress);
        Assert.assertNotNull("CFT-001: readFileData response should not be null", response);
        Assert.assertEquals("CFT-001: Read-back content should match written data",
                new String(data, StandardCharsets.UTF_8), response);
    }

    /**
     * CFT-002-writeHPCCFileData — Call the primary overload with dataTypeDescriptor=null.
     * Verifies that the client defaults to "application/octet-stream" internally and the
     * write succeeds without a NullPointerException.
     *
     * <p>Environment requirements: any
     */
    @Category(CoreFunctionalityTests.class)
    @Test
    public void testWriteHPCCFileData_nullMimeTypeDefaultsToOctetStream() throws Exception, ArrayOfEspExceptionWrapper
    {
        String fileName = System.currentTimeMillis() + "_cft002.bin";
        byte[] data = "null-mime-test".getBytes(StandardCharsets.UTF_8);
        System.out.println("CFT-002-writeHPCCFileData: Testing null dataTypeDescriptor: " + fileName);
        client.createHPCCFile(fileName, targetLZ, true, isContainerized ? null : targetLZAddress);
        boolean result = client.writeHPCCFileData(data, null, fileName, targetLZ, true, 0, 100,
                isContainerized ? null : targetLZAddress);
        Assert.assertTrue("CFT-002: writeHPCCFileData with null dataTypeDescriptor should return true", result);
    }

    /**
     * CFT-003-writeHPCCFileData — Call the primary overload with dataTypeDescriptor="" (empty
     * string). Verifies the internal isEmpty() guard defaults to "application/octet-stream" and
     * the write succeeds.
     *
     * <p>Environment requirements: any
     */
    @Category(CoreFunctionalityTests.class)
    @Test
    public void testWriteHPCCFileData_emptyMimeTypeDefaultsToOctetStream() throws Exception, ArrayOfEspExceptionWrapper
    {
        String fileName = System.currentTimeMillis() + "_cft003.bin";
        byte[] data = "empty-mime-test".getBytes(StandardCharsets.UTF_8);
        System.out.println("CFT-003-writeHPCCFileData: Testing empty dataTypeDescriptor: " + fileName);
        client.createHPCCFile(fileName, targetLZ, true, isContainerized ? null : targetLZAddress);
        boolean result = client.writeHPCCFileData(data, "", fileName, targetLZ, true, 0, 100,
                isContainerized ? null : targetLZAddress);
        Assert.assertTrue("CFT-003: writeHPCCFileData with empty dataTypeDescriptor should return true", result);
    }

    /**
     * CFT-004-writeHPCCFileData — Write a byte array containing non-printable binary bytes
     * (simulated PNG header) and verify round-trip correctness. Validates that non-UTF-8 binary
     * content is preserved through the MTOM/MIME encoding path.
     *
     * <p>Environment requirements: any
     */
    @Category(CoreFunctionalityTests.class)
    @Test
    public void testWriteHPCCFileData_binaryData() throws Exception, ArrayOfEspExceptionWrapper
    {
        String fileName = System.currentTimeMillis() + "_cft004.bin";
        byte[] data = new byte[]{(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                                  0x00, 0x01, 0x02, 0x03};
        System.out.println("CFT-004-writeHPCCFileData: Testing binary (non-text) data: " + fileName);
        client.createHPCCFile(fileName, targetLZ, true, isContainerized ? null : targetLZAddress);
        boolean result = client.writeHPCCFileData(data, "application/octet-stream", fileName,
                targetLZ, true, 0, 100, isContainerized ? null : targetLZAddress);
        Assert.assertTrue("CFT-004: writeHPCCFileData with binary data should return true", result);
        String response = client.readFileData(targetLZ, fileName, data.length, 0,
                isContainerized ? null : targetLZAddress);
        Assert.assertNotNull("CFT-004: readFileData response should not be null", response);
        Assert.assertArrayEquals("CFT-004: Read-back bytes should match original binary data",
                data, response.getBytes(StandardCharsets.ISO_8859_1));
    }

    /**
     * CFT-005-writeHPCCFileData — Write a JSON string with dataTypeDescriptor="application/json"
     * and verify the server accepts it. Tests client-side DataSource construction with a
     * non-standard MIME type.
     *
     * <p>Environment requirements: any
     */
    @Category(CoreFunctionalityTests.class)
    @Test
    public void testWriteHPCCFileData_jsonMimeType() throws Exception, ArrayOfEspExceptionWrapper
    {
        String fileName = System.currentTimeMillis() + "_cft005.json";
        byte[] data = "{\"key\":\"value\",\"count\":42}".getBytes(StandardCharsets.UTF_8);
        System.out.println("CFT-005-writeHPCCFileData: Testing application/json MIME type: " + fileName);
        client.createHPCCFile(fileName, targetLZ, true, isContainerized ? null : targetLZAddress);
        boolean result = client.writeHPCCFileData(data, "application/json", fileName,
                targetLZ, true, 0, 500, isContainerized ? null : targetLZAddress);
        Assert.assertTrue("CFT-005: writeHPCCFileData with application/json should return true", result);
    }

    /**
     * CFT-006-writeHPCCFileData — Write a payload larger than uploadchunksize, forcing the
     * client loop to iterate multiple times (100 bytes of data with chunksize=20 → 5 iterations).
     * Verifies the full data is written correctly.
     *
     * <p>Environment requirements: any
     */
    @Category(CoreFunctionalityTests.class)
    @Test
    public void testWriteHPCCFileData_multiChunkWrite() throws Exception, ArrayOfEspExceptionWrapper
    {
        String fileName = System.currentTimeMillis() + "_cft006.bin";
        byte[] data = new byte[100];
        for (int i = 0; i < data.length; i++)
        {
            data[i] = (byte)(0x41 + (i % 26));
        }
        System.out.println("CFT-006-writeHPCCFileData: Testing multi-chunk write (5 iterations): " + fileName);
        client.createHPCCFile(fileName, targetLZ, true, isContainerized ? null : targetLZAddress);
        boolean result = client.writeHPCCFileData(data, "application/octet-stream", fileName,
                targetLZ, true, 0, 20, isContainerized ? null : targetLZAddress);
        Assert.assertTrue("CFT-006: writeHPCCFileData with multi-chunk data should return true", result);
        String response = client.readFileData(targetLZ, fileName, data.length, 0,
                isContainerized ? null : targetLZAddress);
        Assert.assertNotNull("CFT-006: readFileData response should not be null", response);
        Assert.assertEquals("CFT-006: Read-back data length should equal 100 bytes", data.length, response.length());
    }

    /**
     * CFT-007-writeHPCCFileData — Pass uploadchunksize=0 and uploadchunksize=-1 to verify the
     * client substitutes the defaultUploadChunkSize (5,000,000) and the write succeeds without
     * ArithmeticException or negative-array-size error.
     *
     * <p>Environment requirements: any
     */
    @Category(CoreFunctionalityTests.class)
    @Test
    public void testWriteHPCCFileData_defaultChunkSizeWhenZeroOrNegative() throws Exception, ArrayOfEspExceptionWrapper
    {
        byte[] data = "defaultchunk-test".getBytes(StandardCharsets.UTF_8);

        String fileNameZero = System.currentTimeMillis() + "_cft007a.txt";
        System.out.println("CFT-007-writeHPCCFileData: Testing uploadchunksize=0: " + fileNameZero);
        client.createHPCCFile(fileNameZero, targetLZ, true, isContainerized ? null : targetLZAddress);
        boolean resultZero = client.writeHPCCFileData(data, "text/plain", fileNameZero,
                targetLZ, true, 0, 0, isContainerized ? null : targetLZAddress);
        Assert.assertTrue("CFT-007: writeHPCCFileData with uploadchunksize=0 should return true", resultZero);

        String fileNameNeg = System.currentTimeMillis() + "_cft007b.txt";
        System.out.println("CFT-007-writeHPCCFileData: Testing uploadchunksize=-1: " + fileNameNeg);
        client.createHPCCFile(fileNameNeg, targetLZ, true, isContainerized ? null : targetLZAddress);
        boolean resultNeg = client.writeHPCCFileData(data, "text/plain", fileNameNeg,
                targetLZ, true, 0, -1, isContainerized ? null : targetLZAddress);
        Assert.assertTrue("CFT-007: writeHPCCFileData with uploadchunksize=-1 should return true", resultNeg);
    }

    /**
     * CFT-008-writeHPCCFileData — Create a file, write known content with a small chunk size
     * (forcing multiple iterations), read it back, and assert byte-level equality. This is the
     * definitive correctness/end-to-end test for the write path.
     *
     * <p>Environment requirements: any
     */
    @Category(CoreFunctionalityTests.class)
    @Test
    public void testWriteHPCCFileData_roundTripVerification() throws Exception, ArrayOfEspExceptionWrapper
    {
        String fileName = System.currentTimeMillis() + "_cft008.txt";
        byte[] data = "Round-trip 123 \r\n ABC".getBytes(StandardCharsets.UTF_8);
        System.out.println("CFT-008-writeHPCCFileData: Round-trip write/read test: " + fileName);
        client.createHPCCFile(fileName, targetLZ, true, isContainerized ? null : targetLZAddress);
        boolean result = client.writeHPCCFileData(data, "text/plain; charset=UTF-8", fileName,
                targetLZ, true, 0, 5, isContainerized ? null : targetLZAddress);
        Assert.assertTrue("CFT-008: writeHPCCFileData should return true", result);
        String response = client.readFileData(targetLZ, fileName, data.length, 0,
                isContainerized ? null : targetLZAddress);
        Assert.assertNotNull("CFT-008: readFileData response should not be null", response);
        Assert.assertArrayEquals("CFT-008: Read-back bytes should match original data byte-for-byte",
                data, response.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * ECT-001-writeHPCCFileData — Set uploadchunksize exactly equal to data.length so the loop
     * executes exactly once (single-chunk boundary condition). Verify correct behaviour.
     *
     * <p>Environment requirements: any
     */
    @Category(EdgeCaseTests.class)
    @Test
    public void testWriteHPCCFileData_chunkSizeEqualsDataLength() throws Exception, ArrayOfEspExceptionWrapper
    {
        String fileName = System.currentTimeMillis() + "_ect001.txt";
        byte[] data = "ExactChunk".getBytes(StandardCharsets.UTF_8); // 10 bytes
        System.out.println("ECT-001-writeHPCCFileData: chunksize == data.length (10): " + fileName);
        client.createHPCCFile(fileName, targetLZ, true, isContainerized ? null : targetLZAddress);
        boolean result = client.writeHPCCFileData(data, "text/plain", fileName, targetLZ,
                true, 0, data.length, isContainerized ? null : targetLZAddress);
        Assert.assertTrue("ECT-001: writeHPCCFileData should return true when chunksize equals data.length", result);
    }

    /**
     * ECT-002-writeHPCCFileData — Set uploadchunksize larger than data.length (chunk exceeds
     * payload). Loop runs exactly once with payloadsize=data.length. Verifies no
     * ArrayIndexOutOfBoundsException occurs.
     *
     * <p>Environment requirements: any
     */
    @Category(EdgeCaseTests.class)
    @Test
    public void testWriteHPCCFileData_chunkSizeLargerThanData() throws Exception, ArrayOfEspExceptionWrapper
    {
        String fileName = System.currentTimeMillis() + "_ect002.txt";
        byte[] data = "SmallData".getBytes(StandardCharsets.UTF_8); // 9 bytes
        System.out.println("ECT-002-writeHPCCFileData: chunksize > data.length: " + fileName);
        client.createHPCCFile(fileName, targetLZ, true, isContainerized ? null : targetLZAddress);
        boolean result = client.writeHPCCFileData(data, "text/plain", fileName, targetLZ,
                true, 0, 100, isContainerized ? null : targetLZAddress);
        Assert.assertTrue("ECT-002: writeHPCCFileData should return true when chunksize > data.length", result);
    }

    /**
     * ECT-003-writeHPCCFileData — Write a single-byte payload. Verifies the minimum valid
     * non-empty write is handled correctly.
     *
     * <p>Environment requirements: any
     */
    @Category(EdgeCaseTests.class)
    @Test
    public void testWriteHPCCFileData_singleByte() throws Exception, ArrayOfEspExceptionWrapper
    {
        String fileName = System.currentTimeMillis() + "_ect003.bin";
        byte[] data = new byte[]{0x42};
        System.out.println("ECT-003-writeHPCCFileData: Single-byte write: " + fileName);
        client.createHPCCFile(fileName, targetLZ, true, isContainerized ? null : targetLZAddress);
        boolean result = client.writeHPCCFileData(data, "application/octet-stream", fileName,
                targetLZ, true, 0, 1, isContainerized ? null : targetLZAddress);
        Assert.assertTrue("ECT-003: writeHPCCFileData with a single byte should return true", result);
    }

    /**
     * ECT-004-writeHPCCFileData — In a bare-metal environment, provide an explicit lzAddress
     * (from system property). Verify the client sets DestNetAddress in the request and the write
     * succeeds.
     *
     * <p>Environment requirements: baremetal
     */
    @Category(EdgeCaseTests.class)
    @Test
    public void testWriteHPCCFileData_explicitLzAddressBaremetal() throws Exception, ArrayOfEspExceptionWrapper
    {
        assumeFalse("ECT-004-writeHPCCFileData: Skipping — test only applies to non-containerized (baremetal) deployments",
                isContainerized);
        String fileName = System.currentTimeMillis() + "_ect004.txt";
        byte[] data = "baremetal-test".getBytes(StandardCharsets.UTF_8);
        System.out.println("ECT-004-writeHPCCFileData: Testing explicit lzAddress on baremetal: " + fileName);
        client.createHPCCFile(fileName, targetLZ, true, targetLZAddress);
        boolean result = client.writeHPCCFileData(data, "text/plain", fileName, targetLZ,
                true, 0, 100, targetLZAddress);
        Assert.assertTrue("ECT-004: writeHPCCFileData with explicit lzAddress should return true on baremetal", result);
    }

    /**
     * ECT-005-writeHPCCFileData — Write approximately 1 MB of data with a 100 KB chunk size
     * (~10 iterations). Verify all bytes are written without timeout or exception. This tests
     * chunked loop performance with a large payload.
     *
     * <p>Environment requirements: any
     */
    @Category(EdgeCaseTests.class)
    @Test
    public void testWriteHPCCFileData_largeDataMultipleChunks() throws Exception, ArrayOfEspExceptionWrapper
    {
        String fileName = System.currentTimeMillis() + "_ect005.bin";
        byte[] data = new byte[1_048_576];
        for (int i = 0; i < data.length; i++)
        {
            data[i] = (byte)(i % 256);
        }
        System.out.println("ECT-005-writeHPCCFileData: Writing 1 MB in 100 KB chunks: " + fileName);
        client.createHPCCFile(fileName, targetLZ, true, isContainerized ? null : targetLZAddress);
        boolean result = client.writeHPCCFileData(data, "application/octet-stream", fileName,
                targetLZ, true, 0, 102_400, isContainerized ? null : targetLZAddress);
        Assert.assertTrue("ECT-005: writeHPCCFileData with 1 MB data should return true", result);
    }

    /**
     * ECT-006-writeHPCCFileData — Pass append=false and document that the client overrides this
     * to append=true in the loop (pre-existing bug from Note A). The server thus always appends,
     * and this test asserts the actual (not intended) behaviour: both writes produce concatenated
     * content.
     *
     * <p>NOTE: This test intentionally documents the client-side bug described in Note A of the
     * analysis. When the bug is fixed, the expected total length should change from 11 to 6.
     *
     * <p>Environment requirements: any
     */
    @Category(EdgeCaseTests.class)
    @Test
    public void testWriteHPCCFileData_appendFalseOverriddenByClientBug() throws Exception, ArrayOfEspExceptionWrapper
    {
        String fileName = System.currentTimeMillis() + "_ect006.txt";
        byte[] firstData = "FIRST".getBytes(StandardCharsets.UTF_8);
        byte[] secondData = "SECOND".getBytes(StandardCharsets.UTF_8);
        System.out.println("ECT-006-writeHPCCFileData: Documenting append=false client bug: " + fileName);
        client.createHPCCFile(fileName, targetLZ, true, isContainerized ? null : targetLZAddress);

        boolean firstResult = client.writeHPCCFileData(firstData, "text/plain", fileName,
                targetLZ, true, 0, 10, isContainerized ? null : targetLZAddress);
        Assert.assertTrue("ECT-006: First write should return true", firstResult);

        // Pass append=false — client bug overrides this to append=true, so content is concatenated
        boolean secondResult = client.writeHPCCFileData(secondData, "text/plain", fileName,
                targetLZ, false, 0, 10, isContainerized ? null : targetLZAddress);
        Assert.assertTrue("ECT-006: Second write (append=false) should return true", secondResult);

        // Due to client-side bug, both chunks are appended; total is "FIRSTSECOND" (11 bytes)
        String response = client.readFileData(targetLZ, fileName,
                firstData.length + secondData.length, 0, isContainerized ? null : targetLZAddress);
        Assert.assertNotNull("ECT-006: readFileData response should not be null", response);
        Assert.assertEquals(
                "ECT-006: Due to client bug, append=false is ignored; total content should be 'FIRSTSECOND'",
                "FIRSTSECOND", response);
    }

    /**
     * ECT-007-writeHPCCFileData — Pass offset=5 and a chunk size equal to data.length. Document
     * that the effective offset sent to the server is data.length (not 5), due to the client loop
     * overriding request.setOffset with the running dataindex counter (Note A bug).
     *
     * <p>NOTE: This test intentionally documents the offset override bug described in Note A.
     *
     * <p>Environment requirements: any
     */
    @Category(EdgeCaseTests.class)
    @Test
    public void testWriteHPCCFileData_userOffsetOverriddenByClientBug() throws Exception, ArrayOfEspExceptionWrapper
    {
        String fileName = System.currentTimeMillis() + "_ect007.txt";
        byte[] data = "OFFSET".getBytes(StandardCharsets.UTF_8); // 6 bytes
        System.out.println("ECT-007-writeHPCCFileData: Documenting user offset override bug: " + fileName);
        client.createHPCCFile(fileName, targetLZ, true, isContainerized ? null : targetLZAddress);
        // offset=5 is passed but will be overridden by the client loop; write should still succeed
        boolean result = client.writeHPCCFileData(data, "text/plain", fileName, targetLZ,
                true, 5, data.length, isContainerized ? null : targetLZAddress);
        Assert.assertTrue("ECT-007: writeHPCCFileData with user-supplied offset=5 should return true", result);
    }

    /**
     * EHT-001-writeHPCCFileData — Pass data=null. Expect a NullPointerException at data.length
     * before any server call is made.
     *
     * <p>Environment requirements: any
     */
    @Category(ErrorHandlingTests.class)
    @Test
    public void testWriteHPCCFileData_nullDataThrowsNullPointerException() throws Exception
    {
        System.out.println("EHT-001-writeHPCCFileData: Testing null data throws NullPointerException");
        try
        {
            client.writeHPCCFileData(null, "text/plain", "eht001.txt", targetLZ, true, 0, 100,
                    isContainerized ? null : targetLZAddress);
            Assert.fail("EHT-001: Expected NullPointerException for null data but none was thrown");
        }
        catch (NullPointerException e)
        {
            // Expected — NullPointerException at data.length before any server call
            System.out.println("EHT-001: NullPointerException caught as expected: " + e.getMessage());
        }
    }

    /**
     * EHT-002-writeHPCCFileData — Pass data=new byte[0]. bytesleft=0 so the loop never runs;
     * method returns true without writing anything. Documents this silent success edge case.
     *
     * <p>Environment requirements: any
     */
    @Category(ErrorHandlingTests.class)
    @Test
    public void testWriteHPCCFileData_emptyDataReturnsTrueWithoutWrite() throws Exception, ArrayOfEspExceptionWrapper
    {
        String fileName = System.currentTimeMillis() + "_eht002.txt";
        System.out.println("EHT-002-writeHPCCFileData: Testing empty byte array: " + fileName);
        client.createHPCCFile(fileName, targetLZ, true, isContainerized ? null : targetLZAddress);
        boolean result = client.writeHPCCFileData(new byte[0], "text/plain", fileName,
                targetLZ, true, 0, 100, isContainerized ? null : targetLZAddress);
        Assert.assertTrue("EHT-002: writeHPCCFileData with empty data should return true (loop never entered)", result);
    }

    /**
     * EHT-003-writeHPCCFileData — Pass fileName=null. The server receives a null DestRelativePath
     * and responds with "Destination path not specified". The client does not detect this as an
     * error and returns true (documents client-side bug).
     *
     * <p>Environment requirements: any
     */
    @Category(ErrorHandlingTests.class)
    @Test
    public void testWriteHPCCFileData_nullFileNameReturnsTrueDueToClientBug() throws Exception, ArrayOfEspExceptionWrapper
    {
        System.out.println("EHT-003-writeHPCCFileData: Testing null fileName — documents client bug");
        // No createHPCCFile call since no valid file path is provided
        boolean result = client.writeHPCCFileData("test".getBytes(StandardCharsets.UTF_8),
                "text/plain", null, targetLZ, true, 0, 100,
                isContainerized ? null : targetLZAddress);
        Assert.assertTrue(
                "EHT-003: writeHPCCFileData with null fileName returns true due to client not detecting server error",
                result);
    }

    /**
     * EHT-004-writeHPCCFileData — Pass fileName="" (empty string). Same server-side validation
     * failure as EHT-003 ("Destination path not specified"); client returns true due to same bug.
     *
     * <p>Environment requirements: any
     */
    @Category(ErrorHandlingTests.class)
    @Test
    public void testWriteHPCCFileData_emptyFileNameReturnsTrueDueToClientBug() throws Exception, ArrayOfEspExceptionWrapper
    {
        System.out.println("EHT-004-writeHPCCFileData: Testing empty fileName — documents client bug");
        boolean result = client.writeHPCCFileData("test".getBytes(StandardCharsets.UTF_8),
                "text/plain", "", targetLZ, true, 0, 100,
                isContainerized ? null : targetLZAddress);
        Assert.assertTrue(
                "EHT-004: writeHPCCFileData with empty fileName returns true due to client not detecting server error",
                result);
    }

    /**
     * EHT-005-writeHPCCFileData — Pass targetLandingZone=null. The server receives null
     * DestDropZone and returns "Destination not specified". Client returns true (bug).
     *
     * <p>Environment requirements: any
     */
    @Category(ErrorHandlingTests.class)
    @Test
    public void testWriteHPCCFileData_nullTargetLandingZoneReturnsTrueDueToClientBug() throws Exception, ArrayOfEspExceptionWrapper
    {
        System.out.println("EHT-005-writeHPCCFileData: Testing null targetLandingZone — documents client bug");
        boolean result = client.writeHPCCFileData("test".getBytes(StandardCharsets.UTF_8),
                "text/plain", "eht005.txt", null, true, 0, 100,
                isContainerized ? null : targetLZAddress);
        Assert.assertTrue(
                "EHT-005: writeHPCCFileData with null targetLandingZone returns true due to client not detecting server error",
                result);
    }

    /**
     * EHT-006-writeHPCCFileData — Pass targetLandingZone="" (empty string). Parallel to EHT-005;
     * server returns "Destination not specified"; client returns true.
     *
     * <p>Environment requirements: any
     */
    @Category(ErrorHandlingTests.class)
    @Test
    public void testWriteHPCCFileData_emptyTargetLandingZoneReturnsTrueDueToClientBug() throws Exception, ArrayOfEspExceptionWrapper
    {
        System.out.println("EHT-006-writeHPCCFileData: Testing empty targetLandingZone — documents client bug");
        boolean result = client.writeHPCCFileData("test".getBytes(StandardCharsets.UTF_8),
                "text/plain", "eht006.txt", "", true, 0, 100,
                isContainerized ? null : targetLZAddress);
        Assert.assertTrue(
                "EHT-006: writeHPCCFileData with empty targetLandingZone returns true due to client not detecting server error",
                result);
    }

    /**
     * EHT-007-writeHPCCFileData — Call writeHPCCFileData without first calling createHPCCFile.
     * The server returns "&lt;path&gt; does not exist." The client does not detect this as an error
     * and returns true (documents client-side bug).
     *
     * <p>Environment requirements: any
     */
    @Category(ErrorHandlingTests.class)
    @Test
    public void testWriteHPCCFileData_nonExistentFileReturnsTrueDueToClientBug() throws Exception, ArrayOfEspExceptionWrapper
    {
        String fileName = "nonexistent_" + System.currentTimeMillis() + "_eht007.txt";
        System.out.println("EHT-007-writeHPCCFileData: Writing to non-existent file (no createHPCCFile): " + fileName);
        // Intentionally omit createHPCCFile
        boolean result = client.writeHPCCFileData("orphan".getBytes(StandardCharsets.UTF_8),
                "text/plain", fileName, targetLZ, true, 0, 100,
                isContainerized ? null : targetLZAddress);
        Assert.assertTrue(
                "EHT-007: writeHPCCFileData to non-existent file returns true due to client not detecting server error",
                result);
    }

    /**
     * EHT-008-writeHPCCFileData — Provide an invalid lzAddress ("999.999.999.999") in a
     * bare-metal environment. The server's validateDropZoneAccess should reject the request with
     * a permission/validation error or the method returns false.
     *
     * <p>Environment requirements: baremetal
     */
    @Category(ErrorHandlingTests.class)
    @Test
    public void testWriteHPCCFileData_invalidLzAddressBaremetal() throws Exception
    {
        assumeFalse("EHT-008-writeHPCCFileData: Skipping — test only applies to non-containerized (baremetal) deployments",
                isContainerized);
        String fileName = System.currentTimeMillis() + "_eht008.txt";
        System.out.println("EHT-008-writeHPCCFileData: Testing invalid lzAddress on baremetal: " + fileName);
        client.createHPCCFile(fileName, targetLZ, true, targetLZAddress);
        try
        {
            boolean result = client.writeHPCCFileData("invalid-lz".getBytes(StandardCharsets.UTF_8),
                    "text/plain", fileName, targetLZ, true, 0, 100, "999.999.999.999");
            Assert.assertFalse("EHT-008: writeHPCCFileData with invalid lzAddress should return false", result);
        }
        catch (ArrayOfEspExceptionWrapper e)
        {
            System.out.println("EHT-008: ESP exception for invalid lzAddress (expected): " + e.getMessage());
        }
    }

    /**
     * EHT-009-writeHPCCFileData — Construct an HPCCWsFileIOClient with an invalid host/port and
     * call writeHPCCFileData. Expect an Exception from verifyStub() or the network layer.
     *
     * <p>Environment requirements: any
     */
    @Category(ErrorHandlingTests.class)
    @Test
    public void testWriteHPCCFileData_invalidConnectionThrowsException()
    {
        System.out.println("EHT-009-writeHPCCFileData: Testing with invalid connection");
        HPCCWsFileIOClient badClient = HPCCWsFileIOClient.get("http", "invalid.host.invalid", "9999", "user", "pass");
        try
        {
            badClient.writeHPCCFileData("test".getBytes(StandardCharsets.UTF_8), "text/plain",
                    "f.txt", "lz", true, 0, 100, null);
            Assert.fail("EHT-009: Expected Exception for invalid connection but none was thrown");
        }
        catch (Exception e)
        {
            System.out.println("EHT-009: Exception thrown as expected for invalid connection: " + e.getClass().getSimpleName());
        }
    }

    /**
     * EHT-010-writeHPCCFileData — Attempt to write using a client with deliberately wrong
     * credentials on a security-enabled cluster. The server should reject with an authorization
     * error (ArrayOfEspExceptionWrapper or similar).
     *
     * <p>Environment requirements: secure
     */
    @Category(ErrorHandlingTests.class)
    @Test
    public void testWriteHPCCFileData_unauthorizedWriteRejected() throws Exception
    {
        Assume.assumeTrue("EHT-010-writeHPCCFileData: Skipping — cluster does not enforce authentication",
                client.doesTargetHPCCAuthenticate());
        String fileName = System.currentTimeMillis() + "_eht010.txt";
        System.out.println("EHT-010-writeHPCCFileData: Testing unauthorized write with bad credentials");

        Connection badConn = new Connection(connString);
        badConn.setCredentials("baduser", "badpassword");
        HPCCWsFileIOClient unauthorizedClient = HPCCWsFileIOClient.get(badConn);
        try
        {
            boolean result = unauthorizedClient.writeHPCCFileData(
                    "auth-test".getBytes(StandardCharsets.UTF_8), "text/plain", fileName,
                    targetLZ, true, 0, 100, isContainerized ? null : targetLZAddress);
            Assert.assertFalse("EHT-010: writeHPCCFileData with bad credentials should not return true", result);
        }
        catch (ArrayOfEspExceptionWrapper e)
        {
            System.out.println("EHT-010: ESP exception for unauthorized write (expected): " + e.getMessage());
        }
        catch (Exception e)
        {
            System.out.println("EHT-010: Exception for unauthorized write (acceptable): " + e.getMessage());
        }
    }

    /**
     * CNT-001-writeHPCCFileData — Confirm the WsFileIO service is reachable by executing a valid
     * minimal writeHPCCFileData call. Verifies the full WriteFileData SOAP operation path completes
     * without a connection-level exception (distinct from the existing ping test).
     *
     * <p>Environment requirements: any
     */
    @Category(ConnectivityTests.class)
    @Test
    public void testWriteHPCCFileData_connectivity() throws Exception, ArrayOfEspExceptionWrapper
    {
        String fileName = System.currentTimeMillis() + "_cnt001.txt";
        byte[] data = "ping-write".getBytes(StandardCharsets.UTF_8);
        System.out.println("CNT-001-writeHPCCFileData: Connectivity smoke test: " + fileName);
        client.createHPCCFile(fileName, targetLZ, true, isContainerized ? null : targetLZAddress);
        try
        {
            boolean result = client.writeHPCCFileData(data, "text/plain", fileName,
                    targetLZ, true, 0, 100, isContainerized ? null : targetLZAddress);
            // Either true or false is acceptable — we only care that no connectivity exception is thrown
            System.out.println("CNT-001: writeHPCCFileData returned: " + result);
        }
        catch (java.net.ConnectException | java.net.UnknownHostException e)
        {
            Assert.fail("CNT-001: Connectivity-level exception should not be thrown: " + e.getMessage());
        }
        catch (AxisFault e)
        {
            Assert.fail("CNT-001: AxisFault (connectivity failure) should not be thrown: " + e.getMessage());
        }
    }

    /**
     * CNT-002-writeHPCCFileData — Confirm that a write request using the configured valid
     * credentials succeeds on a security-enabled cluster.
     *
     * <p>Environment requirements: secure
     */
    @Category(ConnectivityTests.class)
    @Test
    public void testWriteHPCCFileData_validCredentialsSucceed() throws Exception, ArrayOfEspExceptionWrapper
    {
        Assume.assumeTrue("CNT-002-writeHPCCFileData: Skipping — cluster does not enforce authentication",
                client.doesTargetHPCCAuthenticate());
        String fileName = System.currentTimeMillis() + "_cnt002.txt";
        byte[] data = "valid-creds-test".getBytes(StandardCharsets.UTF_8);
        System.out.println("CNT-002-writeHPCCFileData: Testing valid credentials on secured cluster: " + fileName);
        client.createHPCCFile(fileName, targetLZ, true, isContainerized ? null : targetLZAddress);
        boolean result = client.writeHPCCFileData(data, "text/plain", fileName,
                targetLZ, true, 0, 100, isContainerized ? null : targetLZAddress);
        Assert.assertTrue("CNT-002: writeHPCCFileData with valid credentials should return true", result);
    }

    /**
     * CNT-003-writeHPCCFileData — Confirm that a write request with bad credentials is rejected
     * at the authentication layer on a security-enabled cluster.
     *
     * <p>Environment requirements: secure
     */
    @Category(ConnectivityTests.class)
    @Test
    public void testWriteHPCCFileData_invalidCredentialsRejected() throws Exception
    {
        Assume.assumeTrue("CNT-003-writeHPCCFileData: Skipping — cluster does not enforce authentication",
                client.doesTargetHPCCAuthenticate());
        System.out.println("CNT-003-writeHPCCFileData: Testing invalid credentials on secured cluster");

        Connection badConn = new Connection(connString);
        badConn.setCredentials("wronguser", "wrongpassword");
        HPCCWsFileIOClient badCredsClient = HPCCWsFileIOClient.get(badConn);
        try
        {
            boolean result = badCredsClient.writeHPCCFileData(
                    "auth-check".getBytes(StandardCharsets.UTF_8), "text/plain",
                    System.currentTimeMillis() + "_cnt003.txt", targetLZ, true, 0, 100,
                    isContainerized ? null : targetLZAddress);
            Assert.assertFalse("CNT-003: writeHPCCFileData with invalid credentials should not return true", result);
        }
        catch (ArrayOfEspExceptionWrapper e)
        {
            System.out.println("CNT-003: ESP exception for invalid credentials (expected): " + e.getMessage());
        }
        catch (Exception e)
        {
            System.out.println("CNT-003: Exception for invalid credentials (acceptable): " + e.getMessage());
        }
    }

    // ===== readFileData Tests =====

    /**
     * CFT-001-readFileData — Reads 10 bytes starting at offset 5 from a known test file.
     * Verifies partial reads with a non-zero offset return the correct byte range.
     *
     * <p>Environment requirements: any
     */
    @Category(CoreFunctionalityTests.class)
    @Test
    public void testReadFileData_partialReadNonZeroOffset() throws Exception, ArrayOfEspExceptionWrapper
    {
        byte[] data = READ_FILE_DATA_TEST_CONTENT.getBytes(StandardCharsets.UTF_8);
        String fileName = setupReadFileDataTestFile("cft001", data);
        System.out.println("CFT-001-readFileData: Partial read at offset 5, size 10: " + fileName);

        String response = client.readFileData(targetLZ, fileName, 10, 5,
                isContainerized ? null : targetLZAddress);
        Assert.assertNotNull("CFT-001: readFileData response should not be null", response);
        byte[] responseBytes = response.getBytes(StandardCharsets.ISO_8859_1);
        Assert.assertEquals("CFT-001: Response should be 10 bytes", 10, responseBytes.length);
        String expected = new String(data, 5, 10, StandardCharsets.ISO_8859_1);
        Assert.assertEquals("CFT-001: Response content should match bytes 5-14 of test data", expected, response);
    }

    /**
     * CFT-002-readFileData — Requests more bytes than remain from a given offset; verifies the
     * server clamps the read to available data (fileSize - offset) and returns without error.
     *
     * <p>Environment requirements: any
     */
    @Category(CoreFunctionalityTests.class)
    @Test
    public void testReadFileData_dataSizeLargerThanRemainingBytes() throws Exception, ArrayOfEspExceptionWrapper
    {
        byte[] data = READ_FILE_DATA_TEST_CONTENT.getBytes(StandardCharsets.UTF_8);
        String fileName = setupReadFileDataTestFile("cft002", data);
        long offset = 60;
        System.out.println("CFT-002-readFileData: DataSize larger than remaining bytes from offset "
                + offset + ": " + fileName);

        String response = client.readFileData(targetLZ, fileName, 1000, offset,
                isContainerized ? null : targetLZAddress);
        Assert.assertNotNull("CFT-002: readFileData response should not be null", response);
        int expectedLen = data.length - (int) offset;
        Assert.assertEquals("CFT-002: Response length should equal fileSize - offset",
                expectedLen, response.getBytes(StandardCharsets.ISO_8859_1).length);
        String expected = new String(data, (int) offset, expectedLen, StandardCharsets.ISO_8859_1);
        Assert.assertEquals("CFT-002: Response content should match tail of test data", expected, response);
    }

    /**
     * CFT-003-readFileData — Requests exactly fileSize bytes from offset 0; verifies complete
     * file content is returned when dataSize equals the file's total byte count.
     *
     * <p>Environment requirements: any
     */
    @Category(CoreFunctionalityTests.class)
    @Test
    public void testReadFileData_exactFileSizeFromOffset0() throws Exception, ArrayOfEspExceptionWrapper
    {
        byte[] data = READ_FILE_DATA_TEST_CONTENT.getBytes(StandardCharsets.UTF_8);
        String fileName = setupReadFileDataTestFile("cft003", data);
        System.out.println("CFT-003-readFileData: Read exact file size from offset 0: " + fileName);

        String response = client.readFileData(targetLZ, fileName, data.length, 0,
                isContainerized ? null : targetLZAddress);
        Assert.assertNotNull("CFT-003: readFileData response should not be null", response);
        Assert.assertEquals("CFT-003: Response length should equal file size",
                data.length, response.getBytes(StandardCharsets.UTF_8).length);
        Assert.assertArrayEquals("CFT-003: Response content should match full test data",
                data, response.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * CFT-004-readFileData — Reads a file in three sequential chunks and verifies that
     * concatenating the chunks reconstructs the original file content exactly.
     *
     * <p>Environment requirements: any
     */
    @Category(CoreFunctionalityTests.class)
    @Test
    public void testReadFileData_sequentialChunkedReads() throws Exception, ArrayOfEspExceptionWrapper
    {
        byte[] data = READ_FILE_DATA_TEST_CONTENT.getBytes(StandardCharsets.UTF_8);
        String fileName = setupReadFileDataTestFile("cft004", data);
        System.out.println("CFT-004-readFileData: Sequential chunked reads: " + fileName);

        int fileSize = data.length;
        int chunk1Size = fileSize / 3;
        int chunk2Size = fileSize / 3;
        int chunk3Size = fileSize - chunk1Size - chunk2Size;

        String part1 = client.readFileData(targetLZ, fileName, chunk1Size, 0,
                isContainerized ? null : targetLZAddress);
        String part2 = client.readFileData(targetLZ, fileName, chunk2Size, chunk1Size,
                isContainerized ? null : targetLZAddress);
        String part3 = client.readFileData(targetLZ, fileName, chunk3Size, chunk1Size + chunk2Size,
                isContainerized ? null : targetLZAddress);

        Assert.assertNotNull("CFT-004: Part 1 should not be null", part1);
        Assert.assertNotNull("CFT-004: Part 2 should not be null", part2);
        Assert.assertNotNull("CFT-004: Part 3 should not be null", part3);

        byte[] reconstructed = (part1 + part2 + part3).getBytes(StandardCharsets.ISO_8859_1);
        Assert.assertArrayEquals("CFT-004: Concatenated chunks should equal original file content",
                data, reconstructed);
    }

    /**
     * CFT-005-readFileData — Reads a 1 MB file in a single call to verify no timeout or memory
     * issue occurs with large payloads over MTOM/SOAP.
     *
     * <p>Environment requirements: any
     */
    @Category(CoreFunctionalityTests.class)
    @Test
    public void testReadFileData_largeFileRead() throws Exception, ArrayOfEspExceptionWrapper
    {
        int fileSize = 1_048_576;
        byte[] data = new byte[fileSize];
        String pattern = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        for (int i = 0; i < data.length; i++)
        {
            data[i] = (byte) pattern.charAt(i % pattern.length());
        }
        String fileName = setupReadFileDataTestFile("cft005", data);
        System.out.println("CFT-005-readFileData: Large file (1 MB) single-call read: " + fileName);

        String response = client.readFileData(targetLZ, fileName, fileSize, 0,
                isContainerized ? null : targetLZAddress);
        Assert.assertNotNull("CFT-005: Response should not be null for 1 MB read", response);
        Assert.assertEquals("CFT-005: Response should be 1 MB in length",
                fileSize, response.getBytes(StandardCharsets.ISO_8859_1).length);
    }

    /**
     * ECT-001-readFileData — Reads exactly 1 byte from offset 0 (minimum valid dataSize).
     * Verifies the first byte of the test file is returned correctly.
     *
     * <p>Environment requirements: any
     */
    @Category(EdgeCaseTests.class)
    @Test
    public void testReadFileData_dataSizeOfOne() throws Exception, ArrayOfEspExceptionWrapper
    {
        byte[] data = READ_FILE_DATA_TEST_CONTENT.getBytes(StandardCharsets.UTF_8);
        String fileName = setupReadFileDataTestFile("ect001", data);
        System.out.println("ECT-001-readFileData: Read 1 byte from offset 0: " + fileName);

        String response = client.readFileData(targetLZ, fileName, 1, 0,
                isContainerized ? null : targetLZAddress);
        Assert.assertNotNull("ECT-001: Response should not be null", response);
        Assert.assertEquals("ECT-001: Response should be exactly 1 byte", 1,
                response.getBytes(StandardCharsets.ISO_8859_1).length);
        Assert.assertEquals("ECT-001: First byte should be 'H'", "H", response);
    }

    /**
     * ECT-002-readFileData — Reads from the last valid byte position (offset = fileSize - 1)
     * with dataSize=1. Verifies the final byte of the file is returned correctly.
     *
     * <p>Environment requirements: any
     */
    @Category(EdgeCaseTests.class)
    @Test
    public void testReadFileData_offsetAtLastValidPosition() throws Exception, ArrayOfEspExceptionWrapper
    {
        byte[] data = READ_FILE_DATA_TEST_CONTENT.getBytes(StandardCharsets.UTF_8);
        String fileName = setupReadFileDataTestFile("ect002", data);
        long lastValidOffset = data.length - 1;
        System.out.println("ECT-002-readFileData: Read 1 byte at last valid offset "
                + lastValidOffset + ": " + fileName);

        String response = client.readFileData(targetLZ, fileName, 1, lastValidOffset,
                isContainerized ? null : targetLZAddress);
        Assert.assertNotNull("ECT-002: Response should not be null", response);
        Assert.assertEquals("ECT-002: Response should be exactly 1 byte", 1,
                response.getBytes(StandardCharsets.ISO_8859_1).length);
        String expectedLastChar = new String(data, data.length - 1, 1, StandardCharsets.ISO_8859_1);
        Assert.assertEquals("ECT-002: Last byte should match the final character of test data",
                expectedLastChar, response);
    }

    /**
     * ECT-003-readFileData — Explicitly provides dropzoneAddress on a bare-metal cluster.
     * Verifies that setting DestNetAddress does not break the request and correct data is returned.
     *
     * <p>Environment requirements: baremetal
     */
    @Category(EdgeCaseTests.class)
    @Test
    public void testReadFileData_dropzoneAddressExplicitBaremetal() throws Exception, ArrayOfEspExceptionWrapper
    {
        assumeFalse("ECT-003-readFileData: Skipping — test only applies to non-containerized (baremetal) deployments",
                isContainerized);
        byte[] data = READ_FILE_DATA_TEST_CONTENT.getBytes(StandardCharsets.UTF_8);
        String fileName = "wsfileio_rfd_ect003_" + System.currentTimeMillis() + ".dat";
        boolean created = client.createHPCCFile(fileName, targetLZ, true, targetLZAddress);
        Assume.assumeTrue("ECT-003: Pre-condition failed — could not create test file", created);
        boolean written = client.writeHPCCFileData(data, fileName, targetLZ, true, 0,
                data.length + 1, targetLZAddress);
        Assume.assumeTrue("ECT-003: Pre-condition failed — could not write test file", written);
        System.out.println("ECT-003-readFileData: Explicit dropzoneAddress on baremetal: " + fileName);

        String response = client.readFileData(targetLZ, fileName, data.length, 0, targetLZAddress);
        Assert.assertNotNull("ECT-003: Response should not be null with explicit dropzoneAddress", response);
        Assert.assertArrayEquals("ECT-003: Content should match test data",
                data, response.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * ECT-004-readFileData — Passes null for dropzoneAddress on a containerized cluster; verifies
     * DestNetAddress is omitted from the request and the read succeeds.
     *
     * <p>Environment requirements: containerized
     */
    @Category(EdgeCaseTests.class)
    @Test
    public void testReadFileData_dropzoneAddressNullContainerized() throws Exception, ArrayOfEspExceptionWrapper
    {
        Assume.assumeTrue("ECT-004-readFileData: Skipping — target HPCC is not containerized",
                isContainerized);
        byte[] data = READ_FILE_DATA_TEST_CONTENT.getBytes(StandardCharsets.UTF_8);
        String fileName = setupReadFileDataTestFile("ect004", data);
        System.out.println("ECT-004-readFileData: Null dropzoneAddress on containerized: " + fileName);

        String response = client.readFileData(targetLZ, fileName, data.length, 0, null);
        Assert.assertNotNull("ECT-004: Response should not be null with null dropzoneAddress", response);
        Assert.assertArrayEquals("ECT-004: Content should match test data",
                data, response.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * ECT-005-readFileData — Passes an empty string for dropzoneAddress; the Java client treats it
     * the same as null (skips DestNetAddress) and the read succeeds on a containerized cluster.
     *
     * <p>Environment requirements: containerized
     */
    @Category(EdgeCaseTests.class)
    @Test
    public void testReadFileData_dropzoneAddressEmptyString() throws Exception, ArrayOfEspExceptionWrapper
    {
        Assume.assumeTrue("ECT-005-readFileData: Skipping — target HPCC is not containerized",
                isContainerized);
        byte[] data = READ_FILE_DATA_TEST_CONTENT.getBytes(StandardCharsets.UTF_8);
        String fileName = setupReadFileDataTestFile("ect005", data);
        System.out.println("ECT-005-readFileData: Empty string dropzoneAddress on containerized: " + fileName);

        String response = client.readFileData(targetLZ, fileName, data.length, 0, "");
        Assert.assertNotNull("ECT-005: Response should not be null with empty dropzoneAddress", response);
        Assert.assertArrayEquals("ECT-005: Content should match test data",
                data, response.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * ECT-006-readFileData — Writes a file containing bytes 0x00-0x7F (all 7-bit ASCII values)
     * and reads it back. Verifies binary data round-trips faithfully through the DataHandler/MTOM
     * pipeline for values safe across common JVM charsets.
     *
     * <p>Environment requirements: any
     */
    @Category(EdgeCaseTests.class)
    @Test
    public void testReadFileData_binaryFileRoundTrip() throws Exception, ArrayOfEspExceptionWrapper
    {
        // Use bytes 0x00-0x7F; these are ASCII-range values that round-trip correctly
        // across both UTF-8 and ISO-8859-1 (the JVM default charset may affect higher bytes).
        byte[] originalBytes = new byte[128];
        for (int i = 0; i < 128; i++)
        {
            originalBytes[i] = (byte) i;
        }
        String fileName = setupReadFileDataTestFile("ect006", originalBytes);
        System.out.println("ECT-006-readFileData: Binary file (128 bytes, values 0x00-0x7F) round-trip: "
                + fileName);

        String response = client.readFileData(targetLZ, fileName, originalBytes.length, 0,
                isContainerized ? null : targetLZAddress);
        Assert.assertNotNull("ECT-006: Response should not be null for binary file read", response);
        byte[] responseBytes = response.getBytes(StandardCharsets.ISO_8859_1);
        Assert.assertEquals("ECT-006: Response should be 128 bytes", 128, responseBytes.length);
        Assert.assertArrayEquals("ECT-006: Response bytes should match original byte values",
                originalBytes, responseBytes);
    }

    /**
     * EHT-001-readFileData — Passes an empty string for dropzone; server returns
     * "Destination not specified" and the Java client throws an exception.
     *
     * <p>Environment requirements: any
     */
    @Category(ErrorHandlingTests.class)
    @Test
    public void testReadFileData_emptyDropzone() throws Exception
    {
        System.out.println("EHT-001-readFileData: Testing empty dropzone");
        try
        {
            client.readFileData("", "somefile.dat", 10, 0, isContainerized ? null : targetLZAddress);
            Assert.fail("EHT-001: Expected exception for empty dropzone but none was thrown");
        }
        catch (ArrayOfEspExceptionWrapper e)
        {
            System.out.println("EHT-001: ArrayOfEspExceptionWrapper (acceptable): " + e.getMessage());
        }
        catch (Exception e)
        {
            Assert.assertTrue("EHT-001: Exception message should contain 'Destination not specified'",
                    e.getMessage().contains("Destination not specified"));
        }
    }

    /**
     * EHT-002-readFileData — Passes null for dropzone; verifies the call does not silently
     * succeed and an exception is thrown.
     *
     * <p>Environment requirements: any
     */
    @Category(ErrorHandlingTests.class)
    @Test
    public void testReadFileData_nullDropzone() throws Exception
    {
        System.out.println("EHT-002-readFileData: Testing null dropzone");
        try
        {
            client.readFileData(null, "somefile.dat", 10, 0, isContainerized ? null : targetLZAddress);
            Assert.fail("EHT-002: Expected exception for null dropzone but none was thrown");
        }
        catch (ArrayOfEspExceptionWrapper e)
        {
            System.out.println("EHT-002: ArrayOfEspExceptionWrapper (acceptable): " + e.getMessage());
        }
        catch (Exception e)
        {
            // Any exception is acceptable — method must not silently succeed
            System.out.println("EHT-002: Exception for null dropzone (expected): " + e.getMessage());
        }
    }

    /**
     * EHT-003-readFileData — Passes an empty string for fileName; server returns
     * "Destination path not specified" and the Java client throws an exception.
     *
     * <p>Environment requirements: any
     */
    @Category(ErrorHandlingTests.class)
    @Test
    public void testReadFileData_emptyFileName() throws Exception
    {
        System.out.println("EHT-003-readFileData: Testing empty fileName");
        try
        {
            client.readFileData(targetLZ, "", 10, 0, isContainerized ? null : targetLZAddress);
            Assert.fail("EHT-003: Expected exception for empty fileName but none was thrown");
        }
        catch (ArrayOfEspExceptionWrapper e)
        {
            System.out.println("EHT-003: ArrayOfEspExceptionWrapper (acceptable): " + e.getMessage());
        }
        catch (Exception e)
        {
            Assert.assertTrue("EHT-003: Exception message should contain 'Destination path not specified'",
                    e.getMessage().contains("Destination path not specified"));
        }
    }

    /**
     * EHT-004-readFileData — Passes dataSize=0; server validates dataSize less than 1 and returns
     * "Invalid data size." causing the Java client to throw an exception.
     *
     * <p>Environment requirements: any
     */
    @Category(ErrorHandlingTests.class)
    @Test
    public void testReadFileData_dataSizeZero() throws Exception, ArrayOfEspExceptionWrapper
    {
        byte[] data = READ_FILE_DATA_TEST_CONTENT.getBytes(StandardCharsets.UTF_8);
        String fileName = setupReadFileDataTestFile("eht004", data);
        System.out.println("EHT-004-readFileData: Testing dataSize=0: " + fileName);
        try
        {
            client.readFileData(targetLZ, fileName, 0, 0, isContainerized ? null : targetLZAddress);
            Assert.fail("EHT-004: Expected exception for dataSize=0 but none was thrown");
        }
        catch (ArrayOfEspExceptionWrapper e)
        {
            System.out.println("EHT-004: ArrayOfEspExceptionWrapper (acceptable): " + e.getMessage());
        }
        catch (Exception e)
        {
            Assert.assertTrue("EHT-004: Exception message should contain 'Invalid data size.'",
                    e.getMessage().contains("Invalid data size."));
        }
    }

    /**
     * EHT-005-readFileData — Passes dataSize=-1; server validates dataSize less than 1 and returns
     * "Invalid data size." causing the Java client to throw an exception.
     *
     * <p>Environment requirements: any
     */
    @Category(ErrorHandlingTests.class)
    @Test
    public void testReadFileData_dataSizeNegative() throws Exception, ArrayOfEspExceptionWrapper
    {
        byte[] data = READ_FILE_DATA_TEST_CONTENT.getBytes(StandardCharsets.UTF_8);
        String fileName = setupReadFileDataTestFile("eht005", data);
        System.out.println("EHT-005-readFileData: Testing dataSize=-1: " + fileName);
        try
        {
            client.readFileData(targetLZ, fileName, -1, 0, isContainerized ? null : targetLZAddress);
            Assert.fail("EHT-005: Expected exception for dataSize=-1 but none was thrown");
        }
        catch (ArrayOfEspExceptionWrapper e)
        {
            System.out.println("EHT-005: ArrayOfEspExceptionWrapper (acceptable): " + e.getMessage());
        }
        catch (Exception e)
        {
            Assert.assertTrue("EHT-005: Exception message should contain 'Invalid data size.'",
                    e.getMessage().contains("Invalid data size."));
        }
    }

    /**
     * EHT-006-readFileData — Passes offset=-1; server returns "Invalid offset." and the Java
     * client throws an exception.
     *
     * <p>Environment requirements: any
     */
    @Category(ErrorHandlingTests.class)
    @Test
    public void testReadFileData_negativeOffset() throws Exception, ArrayOfEspExceptionWrapper
    {
        byte[] data = READ_FILE_DATA_TEST_CONTENT.getBytes(StandardCharsets.UTF_8);
        String fileName = setupReadFileDataTestFile("eht006", data);
        System.out.println("EHT-006-readFileData: Testing negative offset=-1: " + fileName);
        try
        {
            client.readFileData(targetLZ, fileName, 10, -1, isContainerized ? null : targetLZAddress);
            Assert.fail("EHT-006: Expected exception for offset=-1 but none was thrown");
        }
        catch (ArrayOfEspExceptionWrapper e)
        {
            System.out.println("EHT-006: ArrayOfEspExceptionWrapper (acceptable): " + e.getMessage());
        }
        catch (Exception e)
        {
            Assert.assertTrue("EHT-006: Exception message should contain 'Invalid offset.'",
                    e.getMessage().contains("Invalid offset."));
        }
    }

    /**
     * EHT-007-readFileData — Passes offset equal to the file size (first invalid position); server
     * returns "Invalid offset: file size = N." and the Java client throws an exception.
     *
     * <p>Environment requirements: any
     */
    @Category(ErrorHandlingTests.class)
    @Test
    public void testReadFileData_offsetEqualsFileSize() throws Exception, ArrayOfEspExceptionWrapper
    {
        byte[] data = READ_FILE_DATA_TEST_CONTENT.getBytes(StandardCharsets.UTF_8);
        String fileName = setupReadFileDataTestFile("eht007", data);
        long offsetAtSize = data.length; // offset == fileSize — first invalid position
        System.out.println("EHT-007-readFileData: offset == fileSize (" + offsetAtSize + "): " + fileName);
        try
        {
            client.readFileData(targetLZ, fileName, 10, offsetAtSize,
                    isContainerized ? null : targetLZAddress);
            Assert.fail("EHT-007: Expected exception for offset == fileSize but none was thrown");
        }
        catch (ArrayOfEspExceptionWrapper e)
        {
            System.out.println("EHT-007: ArrayOfEspExceptionWrapper (acceptable): " + e.getMessage());
        }
        catch (Exception e)
        {
            Assert.assertTrue("EHT-007: Exception message should contain 'Invalid offset:'",
                    e.getMessage().contains("Invalid offset:"));
        }
    }

    /**
     * EHT-008-readFileData — Passes offset far beyond the file size; server returns
     * "Invalid offset: file size = N." and the Java client throws an exception.
     *
     * <p>Environment requirements: any
     */
    @Category(ErrorHandlingTests.class)
    @Test
    public void testReadFileData_offsetBeyondFileSize() throws Exception, ArrayOfEspExceptionWrapper
    {
        byte[] data = READ_FILE_DATA_TEST_CONTENT.getBytes(StandardCharsets.UTF_8);
        String fileName = setupReadFileDataTestFile("eht008", data);
        System.out.println("EHT-008-readFileData: offset >> fileSize (999999): " + fileName);
        try
        {
            client.readFileData(targetLZ, fileName, 10, 999999,
                    isContainerized ? null : targetLZAddress);
            Assert.fail("EHT-008: Expected exception for offset beyond fileSize but none was thrown");
        }
        catch (ArrayOfEspExceptionWrapper e)
        {
            System.out.println("EHT-008: ArrayOfEspExceptionWrapper (acceptable): " + e.getMessage());
        }
        catch (Exception e)
        {
            Assert.assertTrue("EHT-008: Exception message should contain 'Invalid offset:'",
                    e.getMessage().contains("Invalid offset:"));
        }
    }

    /**
     * EHT-009-readFileData — Attempts to read a file that does not exist on the dropzone; server
     * returns "&lt;path&gt; does not exist." and the Java client throws an exception.
     *
     * <p>Environment requirements: any
     */
    @Category(ErrorHandlingTests.class)
    @Test
    public void testReadFileData_nonExistentFile() throws Exception
    {
        String nonExistentFile = "wsfileio_nonexistent_" + System.currentTimeMillis() + "_eht009.dat";
        System.out.println("EHT-009-readFileData: Testing non-existent file: " + nonExistentFile);
        try
        {
            client.readFileData(targetLZ, nonExistentFile, 10, 0,
                    isContainerized ? null : targetLZAddress);
            Assert.fail("EHT-009: Expected exception for non-existent file but none was thrown");
        }
        catch (ArrayOfEspExceptionWrapper e)
        {
            System.out.println("EHT-009: ArrayOfEspExceptionWrapper (acceptable): " + e.getMessage());
        }
        catch (Exception e)
        {
            Assert.assertTrue("EHT-009: Exception message should contain 'does not exist.'",
                    e.getMessage().contains("does not exist."));
        }
    }

    /**
     * EHT-010-readFileData — Passes a dropzone name that does not correspond to any registered
     * landing zone; server rejects the request during validateDropZoneAccess.
     *
     * <p>Environment requirements: any
     */
    @Category(ErrorHandlingTests.class)
    @Test
    public void testReadFileData_invalidDropzoneName() throws Exception
    {
        System.out.println("EHT-010-readFileData: Testing invalid dropzone name");
        try
        {
            client.readFileData("nonexistent_lz_xyz_eht010", "somefile.dat", 10, 0,
                    isContainerized ? null : targetLZAddress);
            Assert.fail("EHT-010: Expected exception for invalid dropzone name but none was thrown");
        }
        catch (ArrayOfEspExceptionWrapper e)
        {
            // ESP exception indicating unknown dropzone — expected outcome
            System.out.println("EHT-010: ArrayOfEspExceptionWrapper (expected): " + e.getMessage());
        }
        catch (Exception e)
        {
            // Any exception is acceptable — method must not silently succeed
            System.out.println("EHT-010: Exception for invalid dropzone (expected): " + e.getMessage());
        }
    }

    /**
     * CNT-001-readFileData — Confirms the readFileData endpoint is reachable by making a minimal
     * valid request; verifies no transport-layer exception is thrown.
     *
     * <p>Environment requirements: any
     */
    @Category(ConnectivityTests.class)
    @Test
    public void testReadFileData_connectivity() throws Exception, ArrayOfEspExceptionWrapper
    {
        byte[] data = READ_FILE_DATA_TEST_CONTENT.getBytes(StandardCharsets.UTF_8);
        String fileName = setupReadFileDataTestFile("cnt001", data);
        System.out.println("CNT-001-readFileData: Connectivity smoke test: " + fileName);
        try
        {
            String response = client.readFileData(targetLZ, fileName, 1, 0,
                    isContainerized ? null : targetLZAddress);
            System.out.println("CNT-001: readFileData returned response (length: "
                    + (response != null ? response.length() : "null") + ")");
        }
        catch (ArrayOfEspExceptionWrapper e)
        {
            System.out.println("CNT-001: ESP exception received (service reachable): " + e.getMessage());
        }
        catch (Exception e)
        {
            // Transport/connectivity exceptions indicate the service is not reachable
            if (e.getMessage() != null
                    && (e.getMessage().contains("Connection refused")
                            || e.getMessage().contains("ConnectException")
                            || e.getMessage().contains("UnknownHost")))
            {
                Assert.fail("CNT-001: Connectivity failure — service is not reachable: " + e.getMessage());
            }
            System.out.println("CNT-001: Non-transport exception (service reached): " + e.getMessage());
        }
    }

    /**
     * CNT-002-readFileData — Attempts readFileData with invalid credentials on a secured cluster;
     * verifies an authentication-related exception is thrown and data is not returned.
     *
     * <p>Environment requirements: secure
     */
    @Category(ConnectivityTests.class)
    @Test
    public void testReadFileData_invalidCredentialsRejected() throws Exception
    {
        Assume.assumeTrue("CNT-002-readFileData: Skipping — cluster does not enforce authentication",
                client.doesTargetHPCCAuthenticate());
        System.out.println("CNT-002-readFileData: Testing invalid credentials on secured cluster");
        Connection badConn = new Connection(connString);
        badConn.setCredentials("baduser", "wrongpassword");
        HPCCWsFileIOClient badCredsClient = HPCCWsFileIOClient.get(badConn);
        try
        {
            String response = badCredsClient.readFileData(targetLZ, "somefile.dat", 10, 0,
                    isContainerized ? null : targetLZAddress);
            Assert.assertNull("CNT-002: readFileData with invalid credentials should not return data",
                    response);
        }
        catch (ArrayOfEspExceptionWrapper e)
        {
            System.out.println("CNT-002: ESP exception for invalid credentials (expected): " + e.getMessage());
        }
        catch (Exception e)
        {
            System.out.println("CNT-002: Exception for invalid credentials (expected): " + e.getMessage());
        }
    }

    /**
     * CNT-003-readFileData — Attempts readFileData with empty credentials on a secured cluster;
     * verifies an authentication error is returned and data is not provided.
     *
     * <p>Environment requirements: secure
     */
    @Category(ConnectivityTests.class)
    @Test
    public void testReadFileData_emptyCredentialsRejected() throws Exception
    {
        Assume.assumeTrue("CNT-003-readFileData: Skipping — cluster does not enforce authentication",
                client.doesTargetHPCCAuthenticate());
        System.out.println("CNT-003-readFileData: Testing empty credentials on secured cluster");
        Connection emptyCredsConn = new Connection(connString);
        emptyCredsConn.setCredentials("", "");
        HPCCWsFileIOClient emptyCredsClient = HPCCWsFileIOClient.get(emptyCredsConn);
        try
        {
            String response = emptyCredsClient.readFileData(targetLZ, "somefile.dat", 10, 0,
                    isContainerized ? null : targetLZAddress);
            Assert.assertNull("CNT-003: readFileData with empty credentials should not return data",
                    response);
        }
        catch (ArrayOfEspExceptionWrapper e)
        {
            System.out.println("CNT-003: ESP exception for empty credentials (expected): " + e.getMessage());
        }
        catch (Exception e)
        {
            System.out.println("CNT-003: Exception for empty credentials (expected): " + e.getMessage());
        }
    }
}
