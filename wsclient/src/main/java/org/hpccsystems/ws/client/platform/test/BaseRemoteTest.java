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

package org.hpccsystems.ws.client.platform.test;

import static org.junit.Assert.fail;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.List;

import org.hpccsystems.ws.client.HPCCWsWorkUnitsClient;
import org.hpccsystems.ws.client.wrappers.wsworkunits.WorkunitWrapper;
import org.hpccsystems.ws.client.HPCCWsClient;
import org.hpccsystems.ws.client.HPCCWsTopologyClient.TopologyGroupQueryKind;
import org.hpccsystems.ws.client.platform.Platform;
import org.hpccsystems.ws.client.utils.Connection;
import org.hpccsystems.ws.client.wrappers.gen.wstopology.TpGroupWrapper;
import org.junit.Assert;
import org.junit.experimental.categories.Category;

import java.net.URL;

import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.file.Files;

@Category(org.hpccsystems.commons.annotations.RemoteTests.class)
public abstract class BaseRemoteTest
{
    protected static Platform platform;
    protected static HPCCWsClient wsclient;

    protected final static String connString = System.getProperty("hpccconn", "http://localhost:8010");
    protected static String thorClusterFileGroup = System.getProperty("thorgroupname");
    protected final static String thorclustername = System.getProperty("thorclustername", "thor");

    protected static String roxieClusterGroup = System.getProperty("roxiegroupname");
    protected final static String roxieclustername = System.getProperty("roxieclustername", "roxie");

    protected final static String defaultUserName = "JunitUser";
    protected static Connection connection = null;

    protected final static String hpccUser = System.getProperty("hpccuser", defaultUserName);
    protected final static String hpccPass = System.getProperty("hpccpass", "");
    protected final static Integer connTO = System.getProperty("connecttimeoutmillis")==null?null:Integer.valueOf(System.getProperty("connecttimeoutmillis"));
    protected final static String sockTO = System.getProperty("sockettimeoutmillis");

    /*
      Code used to generate HPCC file
      unique_keys :=  100000;  // Should be less than number of records
      unique_values := 10212; // Should be less than number of records
      dataset_name := '~benchmark::all_types::200KB';
      totalrecs := 779449/500;

      childRec := {STRING8 childField1, INTEGER8 childField2, REAL8 childField3};

      rec := { INTEGER8 int8, UNSIGNED8 uint8, INTEGER4 int4, UNSIGNED4 uint4,
               INTEGER2 int2, UNSIGNED2 uint2, REAL8 r8, REAL4 r4,
               DECIMAL16_8 dec16, UDECIMAL16_8 udec16, QSTRING qStr,
               STRING8 fixStr8, STRING str, VARSTRING varStr, VARSTRING varStr8,
               UTF8 utfStr, UNICODE8 uni8, UNICODE uni, VARUNICODE varUni,
               DATASET(childRec) childDataset,  SET OF INTEGER1 int1Set
             };

             ds := DATASET(totalrecs, transform(rec,
                                  self.int8 := (INTEGER)(random() % unique_keys);
                                  self.uint8 := (INTEGER)(random() % unique_values);
                                  self.int4 := (INTEGER)(random() % unique_values);
                                  self.uint4 := (INTEGER)(random() % unique_values);
                                  self.int2 := (INTEGER)(random() % unique_values);
                                  self.uint2 := (INTEGER)(random() % unique_values);
                                  self.r8 := (REAL)(random() % unique_values);
                                  self.r4 := (REAL)(random() % unique_values);
                                  self.dec16 := (REAL)(random() % unique_values);
                                  self.udec16 := (REAL)(random() % unique_values);
                                  self.qStr := (STRING)(random() % unique_values);
                                  self.fixStr8 := (STRING)(random() % unique_values);
                                  self.str := (STRING)(random() % unique_values);
                                  self.varStr := (STRING)(random() % unique_values);
                                  self.varStr8 := (STRING)(random() % unique_values);
                                  self.utfStr := (STRING)(random() % unique_values);
                                  self.uni8 := (STRING)(random() % unique_values);
                                  self.uni := (STRING)(random() % unique_values);
                                  self.varUni := (STRING)(random() % unique_values);
                                  self.childDataset := DATASET([{'field1',2,3},{'field1',2,3}],childRec);
                                  self.int1Set := [1,2,3];
                           ), DISTRIBUTED);
              OUTPUT(ds,,dataset_name,overwrite);
     */
    public static final String DEFAULTHPCCFILENAME      = "benchmark::all_types::200kb";

    /*
     * Code to generate superfile with default file as subfile
     * Import STD;
     * String subfilename := '~benchmark::all_types::200KB';
     * String sfname := '~benchmark::all_types::superfile';
     * IF(false = STD.file.SuperFileExists(sfname),STD.file.CreateSuperFile(sfname));
     * output(STD.file.SuperFileExists(sfname));
     * STD.file.AddSuperFile(sfname, subfilename);
     */
    public static final String DEFAULTHPCCSUPERFILENAME = "benchmark::all_types::superfile";

    static
    {
        // This allows testing against locally created self signed certs to work.
        // In production certs will need to be created valid hostnames
        javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier(
        new javax.net.ssl.HostnameVerifier()
        {
            public boolean verify(String hostname,javax.net.ssl.SSLSession sslSession)
            {
                if (hostname.equals("localhost"))
                {
                    return true;
                }

                return false;
            }
        });

        String legacythorcluster = System.getProperty("thorcluster");
        if (legacythorcluster != null && !legacythorcluster.isEmpty())
            System.out.println("WARNING! 'thorcluster' has been deprecated - Use 'thorclustername' and/or 'thorgroupname' instead");

        if (System.getProperty("thorclustername") == null)
            System.out.println("thorclustername not provided - defaulting to '" + thorclustername + "'");

        if (System.getProperty("roxieclustername") == null)
            System.out.println("roxieclustername not provided - defaulting to '" + roxieclustername + "'");

        InetAddress ip;
        String hostname;
        try
        {
            ip = InetAddress.getLocalHost();
            hostname = ip.getHostName();
            System.out.println("RemoteTest executing on: " + hostname + "(" + ip + ")");
        }
        catch (UnknownHostException e)
        {
            e.printStackTrace();
        }

        if (System.getProperty("hpccconn") == null)
            System.out.println("RemoteTest: No 'hpccconn' provided, defaulting to http://localhost:8010");
        else
            System.out.println("RemoteTest: 'hpccconn' set to: '" + connString + "'");

        if (System.getProperty("hpccuser") == null)
            System.out.println("RemoteTest: No 'hpccuser' provided, defaulting to '" + defaultUserName + "'");
        else
            System.out.println("RemoteTest: 'hpccuser' set to: '" + hpccUser + "'");

        if (System.getProperty("hpccpass") == null)
            System.out.println("RemoteTest: No 'hpccpass' provided.");

        if (platform == null)
        {
            try
            {
                connection = new Connection(connString);
            }
            catch (MalformedURLException e)
            {
                fail("Could not adquire connection object based on: '" + connString + "' - " + e.getLocalizedMessage());
            }

            Assert.assertNotNull("Could not adquire connection object", connection);
            connection.setCredentials(hpccUser, hpccPass);

            if (connTO != null)
                connection.setConnectTimeoutMilli(connTO);

            if (sockTO != null)
                connection.setSocketTimeoutMilli(Integer.valueOf(sockTO));

            platform = Platform.get(connection);

            Assert.assertNotNull("Could not adquire platform object", platform);
        }

        try
        {
            wsclient = platform.checkOutHPCCWsClient();
            if (thorClusterFileGroup == null || thorClusterFileGroup.isEmpty())
            {
                List<TpGroupWrapper> grouplist = wsclient.getTopologyGroups(wsclient.isContainerized() ? TopologyGroupQueryKind.PLANE : TopologyGroupQueryKind.THOR);
                for (TpGroupWrapper tpGroupWrapper : grouplist)
                {
                    thorClusterFileGroup = tpGroupWrapper.getName();
                    if (thorClusterFileGroup != null)
                        break;
                }
                System.out.println("RemoteTest: No 'thorClusterFileGroup' provided, using '" + thorClusterFileGroup + "'");
            }
            else
            {
                System.out.println("RemoteTest: 'thorClusterFileGroup': '" + thorClusterFileGroup + "'");
            }

            if (roxieClusterGroup == null || roxieClusterGroup.isEmpty())
            {
                List<TpGroupWrapper> grouplist =  wsclient.getTopologyGroups(wsclient.isContainerized() ? TopologyGroupQueryKind.PLANE : TopologyGroupQueryKind.ROXIE);
                for (TpGroupWrapper tpGroupWrapper : grouplist)
                {
                    roxieClusterGroup = tpGroupWrapper.getName();
                    if (roxieClusterGroup != null)
                        break;
                }
                System.out.println("RemoteTest: No 'roxiegroupname' provided, using '" + roxieClusterGroup + "'");
            }
            else
            {
            	System.out.println("RemoteTest: 'roxiegroupname': '" + roxieclustername + "'");
            }
        }
        catch (Exception e)
        {
            fail("Could not adquire wsclient object: " + e.getMessage() );
        }

        Assert.assertNotNull("Could not adquire wsclient object", wsclient);
    }

    public String executeECLScript(String eclFile) throws Exception
    {
        URL eclFileURL = getClass().getClassLoader().getResource(eclFile);
        Path eclFilePath = Paths.get(eclFileURL.toURI());

        byte[] eclData = Files.readAllBytes(eclFilePath);
        String ecl = new String(eclData, "UTF-8");

        WorkunitWrapper wu = new WorkunitWrapper();
        wu.setECL(ecl);
        wu.setJobname("UnitTest ECL Script: " + eclFile);
        wu.setCluster(thorclustername);

        HPCCWsWorkUnitsClient client = wsclient.getWsWorkunitsClient();
        return client.createAndRunWUFromECLAndGetResults(wu);
    }
}
