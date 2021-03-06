/* (c) 2014 LinkedIn Corp. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied.
 */

package gobblin.runtime.mapreduce;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.hadoop.fs.Path;

import org.jboss.byteman.contrib.bmunit.BMNGRunner;
import org.jboss.byteman.contrib.bmunit.BMRule;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import gobblin.configuration.ConfigurationKeys;
import gobblin.metastore.FsStateStore;
import gobblin.metastore.StateStore;
import gobblin.runtime.JobLauncherTestHelper;
import gobblin.runtime.JobState;
import gobblin.writer.Destination;
import gobblin.writer.WriterOutputFormat;


/**
 * Unit test for {@link MRJobLauncher}.
 */
@Test(groups = { "gobblin.runtime.mapreduce" })
public class MRJobLauncherTest extends BMNGRunner {

  private Properties launcherProps;
  private JobLauncherTestHelper jobLauncherTestHelper;

  @BeforeClass
  public void startUp() throws Exception {
    this.launcherProps = new Properties();
    this.launcherProps.load(new FileReader("gobblin-test/resource/gobblin.mr-test.properties"));
    this.launcherProps.setProperty(ConfigurationKeys.JOB_HISTORY_STORE_ENABLED_KEY, "true");
    this.launcherProps.setProperty(ConfigurationKeys.METRICS_ENABLED_KEY, "true");
    this.launcherProps.setProperty(ConfigurationKeys.METRICS_REPORTING_FILE_ENABLED_KEY, "false");
    this.launcherProps.setProperty(ConfigurationKeys.JOB_HISTORY_STORE_JDBC_DRIVER_KEY,
        "org.apache.derby.jdbc.EmbeddedDriver");
    this.launcherProps.setProperty(ConfigurationKeys.JOB_HISTORY_STORE_URL_KEY,
        "jdbc:derby:memory:gobblin2;create=true");

    StateStore<JobState> jobStateStore = new FsStateStore<JobState>(
        this.launcherProps.getProperty(ConfigurationKeys.STATE_STORE_FS_URI_KEY),
        this.launcherProps.getProperty(ConfigurationKeys.STATE_STORE_ROOT_DIR_KEY),
        JobState.class);

    this.jobLauncherTestHelper = new JobLauncherTestHelper(this.launcherProps, jobStateStore);
    this.jobLauncherTestHelper.prepareJobHistoryStoreDatabase(this.launcherProps);
  }

  @Test
  public void testLaunchJob() throws Exception {
    this.jobLauncherTestHelper.runTest(loadJobProps());
  }

  @Test
  public void testLaunchJobWithConcurrencyLimit() throws Exception {
    Properties jobProps = loadJobProps();
    jobProps.setProperty(ConfigurationKeys.MR_JOB_MAX_MAPPERS_KEY, "2");
    this.jobLauncherTestHelper.runTest(jobProps);
    jobProps.setProperty(ConfigurationKeys.MR_JOB_MAX_MAPPERS_KEY, "3");
    this.jobLauncherTestHelper.runTest(jobProps);
    jobProps.setProperty(ConfigurationKeys.MR_JOB_MAX_MAPPERS_KEY, "5");
    this.jobLauncherTestHelper.runTest(jobProps);
  }

  @Test
  public void testLaunchJobWithPullLimit() throws Exception {
    Properties jobProps = loadJobProps();
    jobProps.setProperty(ConfigurationKeys.EXTRACT_PULL_LIMIT, "10");
    this.jobLauncherTestHelper.runTestWithPullLimit(jobProps);
  }

  @Test
  public void testLaunchJobWithMultiWorkUnit() throws Exception {
    Properties jobProps = loadJobProps();
    jobProps.setProperty("use.multiworkunit", Boolean.toString(true));
    this.jobLauncherTestHelper.runTest(jobProps);
  }

  @Test(groups = { "ignore" })
  public void testCancelJob() throws Exception {
    this.jobLauncherTestHelper.runTestWithCancellation(loadJobProps());
  }

  @Test
  public void testLaunchJobWithFork() throws Exception {
    Properties jobProps = loadJobProps();
    jobProps.setProperty(ConfigurationKeys.CONVERTER_CLASSES_KEY, "gobblin.test.TestConverter2");
    jobProps.setProperty(ConfigurationKeys.FORK_BRANCHES_KEY, "2");
    jobProps
        .setProperty(ConfigurationKeys.ROW_LEVEL_POLICY_LIST + ".0", "gobblin.policies.schema.SchemaRowCheckPolicy");
    jobProps
        .setProperty(ConfigurationKeys.ROW_LEVEL_POLICY_LIST + ".1", "gobblin.policies.schema.SchemaRowCheckPolicy");
    jobProps.setProperty(ConfigurationKeys.ROW_LEVEL_POLICY_LIST_TYPE + ".0", "OPTIONAL");
    jobProps.setProperty(ConfigurationKeys.ROW_LEVEL_POLICY_LIST_TYPE + ".1", "OPTIONAL");
    jobProps.setProperty(ConfigurationKeys.TASK_LEVEL_POLICY_LIST + ".0",
        "gobblin.policies.count.RowCountPolicy,gobblin.policies.schema.SchemaCompatibilityPolicy");
    jobProps.setProperty(ConfigurationKeys.TASK_LEVEL_POLICY_LIST + ".1",
        "gobblin.policies.count.RowCountPolicy,gobblin.policies.schema.SchemaCompatibilityPolicy");
    jobProps.setProperty(ConfigurationKeys.TASK_LEVEL_POLICY_LIST_TYPE + ".0", "OPTIONAL,OPTIONAL");
    jobProps.setProperty(ConfigurationKeys.TASK_LEVEL_POLICY_LIST_TYPE + ".1", "OPTIONAL,OPTIONAL");
    jobProps.setProperty(ConfigurationKeys.WRITER_OUTPUT_FORMAT_KEY + ".0", WriterOutputFormat.AVRO.name());
    jobProps.setProperty(ConfigurationKeys.WRITER_OUTPUT_FORMAT_KEY + ".1", WriterOutputFormat.AVRO.name());
    jobProps.setProperty(ConfigurationKeys.WRITER_DESTINATION_TYPE_KEY + ".0", Destination.DestinationType.HDFS.name());
    jobProps.setProperty(ConfigurationKeys.WRITER_DESTINATION_TYPE_KEY + ".1", Destination.DestinationType.HDFS.name());
    jobProps.setProperty(ConfigurationKeys.WRITER_STAGING_DIR + ".0", "gobblin-test/basicTest/tmp/taskStaging");
    jobProps.setProperty(ConfigurationKeys.WRITER_STAGING_DIR + ".1", "gobblin-test/basicTest/tmp/taskStaging");
    jobProps.setProperty(ConfigurationKeys.WRITER_OUTPUT_DIR + ".0", "gobblin-test/basicTest/tmp/taskOutput");
    jobProps.setProperty(ConfigurationKeys.WRITER_OUTPUT_DIR + ".1", "gobblin-test/basicTest/tmp/taskOutput");
    jobProps.setProperty(ConfigurationKeys.DATA_PUBLISHER_FINAL_DIR + ".0", "gobblin-test/jobOutput");
    jobProps.setProperty(ConfigurationKeys.DATA_PUBLISHER_FINAL_DIR + ".1", "gobblin-test/jobOutput");
    this.jobLauncherTestHelper.runTestWithFork(jobProps);
  }

  /**
   * Byteman test that ensures the {@link MRJobLauncher} successfully cleans up all staging data even when
   * an exception is thrown in the {@link MRJobLauncher#collectOutput(Path)} method. The {@link BMRule} is
   * to inject an {@link IOException} when the {@link MRJobLauncher#collectOutput(Path)} method is called.
   */
  @Test()
  @BMRule(name = "testJobCleanupOnError",
          targetClass = "gobblin.runtime.mapreduce.MRJobLauncher",
          targetMethod = "collectOutput(Path)",
          targetLocation = "AT ENTRY",
          condition = "true",
          action = "throw new IOException(\"Exception for testJobCleanupOnError\")")
  public void testJobCleanupOnError() throws IOException {
    Properties props = loadJobProps();
    try {
      this.jobLauncherTestHelper.runTest(props);
      Assert.fail("Byteman is not configured properly, the runTest method should have throw an exception");
    } catch (Exception e) {
      // The job should throw an exception, ignore it
    }

    Assert.assertTrue(props.containsKey(ConfigurationKeys.WRITER_STAGING_DIR));
    Assert.assertTrue(props.containsKey(ConfigurationKeys.WRITER_OUTPUT_DIR));

    File stagingDir = new File(props.getProperty(ConfigurationKeys.WRITER_STAGING_DIR));
    File outputDir = new File(props.getProperty(ConfigurationKeys.WRITER_OUTPUT_DIR));

    Assert.assertEquals(FileUtils.listFiles(stagingDir, null, true).size(), 0);
    Assert.assertEquals(FileUtils.listFiles(outputDir, null, true).size(), 0);
  }

  @AfterClass
  public void tearDown() {
    try {
      DriverManager.getConnection("jdbc:derby:memory:gobblin2;shutdown=true");
    } catch (SQLException se) {
      // An exception is expected when shutting down the database
    }
  }

  public Properties loadJobProps() throws IOException {
    Properties jobProps = new Properties();
    jobProps.load(new FileReader("gobblin-test/resource/mr-job-conf/GobblinMRTest.pull"));
    jobProps.putAll(this.launcherProps);
    jobProps.setProperty(JobLauncherTestHelper.SOURCE_FILE_LIST_KEY, "gobblin-test/resource/source/test.avro.0,"
        + "gobblin-test/resource/source/test.avro.1," + "gobblin-test/resource/source/test.avro.2,"
        + "gobblin-test/resource/source/test.avro.3");

    return jobProps;
  }
}
