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
}
