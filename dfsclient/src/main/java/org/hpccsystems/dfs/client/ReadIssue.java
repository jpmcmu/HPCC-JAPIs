package org.hpccsystems.dfs.client;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.hpccsystems.commons.ecl.FieldDef;
import org.hpccsystems.commons.ecl.FieldFilter;
import org.hpccsystems.commons.ecl.FieldFilterRange;
import org.hpccsystems.commons.ecl.FieldType;
import org.hpccsystems.commons.ecl.FileFilter;
import org.hpccsystems.commons.ecl.HpccSrcType;
import org.hpccsystems.commons.ecl.RecordDefinitionTranslator;

import org.hpccsystems.commons.ecl.FieldDef;
import org.json.JSONArray;
import org.json.JSONObject;

import java.security.SecureRandom;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import org.hpccsystems.ws.client.HPCCWsClient;
import org.hpccsystems.ws.client.platform.Platform;
import org.hpccsystems.ws.client.utils.Connection;

import org.hpccsystems.dfs.cluster.*;
import org.hpccsystems.commons.ecl.RecordDefinitionTranslator;
import org.hpccsystems.commons.errors.HpccFileException;
import org.hpccsystems.ws.client.HPCCWsDFUClient;
import org.hpccsystems.ws.client.wrappers.wsdfu.DFUCreateFileWrapper;
import org.hpccsystems.ws.client.wrappers.wsdfu.DFUFilePartWrapper;
import org.hpccsystems.ws.client.wrappers.wsdfu.DFUFileTypeWrapper;

public class ReadIssue
{
    private static HPCCWsClient wsclient = null;

    public static void main(String[] args)
    {
        Options options = getOptions();
        CommandLineParser parser = new DefaultParser();

        CommandLine cmd = null;
        try
        {
            cmd = parser.parse(options, args);
        }
        catch (ParseException e)
        {
            System.out.println("Error parsing commandline options:\n" + e.getMessage());
            return;
        }

        String connString = cmd.getOptionValue("url");
        String user = cmd.getOptionValue("user");
        String pass = cmd.getOptionValue("pass");
        String cluster = cmd.getOptionValue("cluster", "thor");

        // Prompt user, should we create a new file or use an existing one?
        System.out.println("Generate test file? (y/n)");
        try
        {
            int read = System.in.read();
            if (read == 'y')
            {
                createFile(connString, user, pass, cluster);
            }
        }
        catch (Exception e)
        {
            System.out.println("Error reading input: " + e.getMessage());
            return;
        }

        for (int i = 0; i < 10; i++)
        {
            System.out.println("Test run: " + i);
            System.out.println("------------------------------------\n");
            try
            {
                runTest(connString, user, pass, cluster);
            }
            catch (Exception e)
            {
                System.out.println("Test run: " + i + "  failed with error:" + e.getMessage());
                e.printStackTrace();
                return;
            }
        }
    }

    private static final String       ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ-_";
    private static final SecureRandom RANDOM   = new SecureRandom();

    private static String generateRandomString(int count)
    {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; ++i)
        {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    private static Options getOptions()
    {
        Options options = new Options();
        options.addRequiredOption("url", "Source Cluster URL", true, "Specifies the URL of the ESP to connect to.");
        options.addOption("user", true, "Specifies the username used to connect. Defaults to null.");
        options.addOption("pass", true, "Specifies the password used to connect. Defaults to null.");
        options.addOption("cluster", true, "Specifies thor cluster. Defaults to thor.");

        return options;
    }

    private static List<HPCCRecord> createFile(String connString, String user, String pass, String clusterName) throws Exception
    {
        // Create a large record dataset
        FieldDef[] fieldDefs = new FieldDef[3];
        fieldDefs[0] = new FieldDef("key", FieldType.INTEGER, "lNTEGER4", 4, true, false, HpccSrcType.LITTLE_ENDIAN, new FieldDef[0]);
        fieldDefs[1] = new FieldDef("char", FieldType.CHAR, "STRING1", 1, true, false, HpccSrcType.SINGLE_BYTE_CHAR, new FieldDef[0]);
        fieldDefs[2] = new FieldDef("value", FieldType.STRING, "STRING", 0, false, false, HpccSrcType.UTF8, new FieldDef[0]);
        FieldDef recordDef = new FieldDef("RootRecord", FieldType.RECORD, "rec", 4, false, false, HpccSrcType.LITTLE_ENDIAN, fieldDefs);

        List<HPCCRecord> records = new ArrayList<HPCCRecord>();
        for (int i = 0; i < 10; i++)
        {
            Object[] fields = new Object[3];
            fields[0] = Long.valueOf(i);
            fields[1] = "C";
            fields[2] = generateRandomString(8096 * 1024);
            HPCCRecord record = new HPCCRecord(fields, recordDef);
            records.add(record);
        }

        HPCCWsDFUClient dfuClient = wsclient.getWsDFUClient();

        writeFile(dfuClient, records, "benchmark::large_record_8MB::10rows", recordDef, -1, clusterName);
        return records;
    }

    private static void runTest(String connString, String user, String pass, String clusterName) throws Exception
    {
        if (wsclient == null)
        {
            Connection connection = new Connection(connString);
            Platform platform = Platform.get(connection);
            wsclient = platform.checkOutHPCCWsClient();
        }

        // List<HPCCRecord> records = createFile(connString, user, pass, clusterName);

        HPCCFile file = new HPCCFile("benchmark::large_record_8MB::10rows", connString, user, pass);
        List<HPCCRecord> readRecords = readFile(file, -1);
        if (readRecords.size() < 10)
        {
            throw new Exception("Invalid record count");
        }

        // for (int i = 0; i < 10; i++)
        // {
        //     HPCCRecord record = readRecords.get(i);
        //     String charStr = (String) record.getField(1);
        //     if (charStr.equals("C") == false)
        //     {
        //         throw new Exception("Record mismatch");
        //     }
        // }
    }

    public static List<HPCCRecord> readFile(HPCCFile file, Integer connectTimeoutMillis) throws Exception
    {
        ArrayList<HPCCRecord> records = new ArrayList<HPCCRecord>();
        ArrayList<HpccRemoteFileReader<HPCCRecord>> fileReaders = new ArrayList<HpccRemoteFileReader<HPCCRecord>>();
        try
        {
            DataPartition[] fileParts = file.getFileParts();
            FieldDef originalRD = file.getRecordDefinition();

            for (int i = 0; i < fileParts.length; i++)
            {
                HPCCRecordBuilder recordBuilder = new HPCCRecordBuilder(file.getProjectedRecordDefinition());
                HpccRemoteFileReader<HPCCRecord> fileReader = new HpccRemoteFileReader<HPCCRecord>(fileParts[i], originalRD, recordBuilder);
                fileReaders.add(fileReader);
            }

            for (int i = 0; i < fileReaders.size(); i++)
            {
                HpccRemoteFileReader<HPCCRecord> fileReader = fileReaders.get(i);

                while (fileReader.hasNext())
                {
                    HPCCRecord record = fileReader.next();
                    records.add(record);
                }
                fileReader.close();

                if (fileReader.getRemoteReadMessageCount() > 0)
                    System.out.println("Messages from file part (" + i + ") read operation:\n" + fileReader.getRemoteReadMessages());
            }
        }
        finally
        {
            for (int i = 0; i < fileReaders.size(); i++)
            {
                fileReaders.get(i).close();
            }
        }

        return records;
    }

    private static void writeFile(HPCCWsDFUClient dfuClient, List<HPCCRecord> records, String fileName, FieldDef recordDef, Integer connectTimeoutMs, String clusterName) throws Exception
    {
        //------------------------------------------------------------------------------
        //  Request a temp file be created in HPCC to write to
        //------------------------------------------------------------------------------

        String eclRecordDefn = RecordDefinitionTranslator.toECLRecord(recordDef);

        System.out.println("Create Start");

        boolean compressed = false;
        CompressionAlgorithm compressionAlgorithm = CompressionAlgorithm.NONE;
        DFUCreateFileWrapper createResult = null;
        if (compressed)
        {
            createResult = dfuClient.createFile(fileName, clusterName, eclRecordDefn, 300, true, DFUFileTypeWrapper.Flat, "");
            compressionAlgorithm = CompressionAlgorithm.DEFAULT;
        }
        else
        {
            createResult = dfuClient.createFile(fileName, clusterName, eclRecordDefn, 300, false, DFUFileTypeWrapper.Flat, "");
        }
        System.out.println("Create Finished");

        DFUFilePartWrapper[] dfuFileParts = createResult.getFileParts();
        DataPartition[] hpccPartitions = DataPartition.createPartitions(dfuFileParts,
                new NullRemapper(new RemapInfo(), createResult.getFileAccessInfo()), dfuFileParts.length, createResult.getFileAccessInfoBlob());

        //------------------------------------------------------------------------------
        //  Write partitions to file parts
        //------------------------------------------------------------------------------

        int recordsPerPartition = records.size() / dfuFileParts.length;

        // These should be distributed evenly but we won't do this for the test
        int residualRecords = records.size() % dfuFileParts.length;

        int currentRecord = 0;
        long bytesWritten = 0;
        for (int partitionIndex = 0; partitionIndex < hpccPartitions.length; partitionIndex++)
        {
            int numRecordsInPartition = recordsPerPartition;
            if (partitionIndex == dfuFileParts.length - 1)
            {
                numRecordsInPartition += residualRecords;
            }

            HPCCRecordAccessor recordAccessor = new HPCCRecordAccessor(recordDef);
            HPCCRemoteFileWriter<HPCCRecord> fileWriter = null;
            if (connectTimeoutMs != null)
            {
                fileWriter=new HPCCRemoteFileWriter<HPCCRecord>(hpccPartitions[partitionIndex], recordDef,
                        recordAccessor, compressionAlgorithm,connectTimeoutMs);
                //wait a bit longer than the default timeout to ensure the override connect timeout
                //is being honoured
                if (connectTimeoutMs != null
                        && connectTimeoutMs > RowServiceOutputStream.DEFAULT_CONNECT_TIMEOUT_MILIS+1)
                {
                    Thread.sleep(RowServiceOutputStream.DEFAULT_CONNECT_TIMEOUT_MILIS+1);
                }
            } else {
                fileWriter=new HPCCRemoteFileWriter<HPCCRecord>(hpccPartitions[partitionIndex], recordDef,
                        recordAccessor, compressionAlgorithm);
            }

            for (int j = 0; j < numRecordsInPartition; j++, currentRecord++)
            {
                fileWriter.writeRecord(records.get(currentRecord));
            }
            fileWriter.close();
            bytesWritten += fileWriter.getBytesWritten();
        }

        //------------------------------------------------------------------------------
        //  Publish and finalize the temp file
        //------------------------------------------------------------------------------

        System.out.println("Publish Start");
        dfuClient.publishFile(createResult.getFileID(), eclRecordDefn, currentRecord, bytesWritten, true);
        System.out.println("Publish Finished");
    }
};