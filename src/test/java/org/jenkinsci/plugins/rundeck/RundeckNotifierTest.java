package org.jenkinsci.plugins.rundeck;

import hudson.FilePath;
import hudson.model.Action;
import hudson.model.Build;
import hudson.model.FreeStyleBuild;
import hudson.model.Result;
import hudson.model.Cause.UpstreamCause;
import hudson.model.FreeStyleProject;
import hudson.model.Run;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Date;
import java.util.Properties;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.time.DateUtils;
import org.jenkinsci.plugins.rundeck.RundeckNotifier.RundeckExecutionBuildBadgeAction;
import org.junit.Assert;
import org.jvnet.hudson.test.HudsonHomeLoader.CopyExisting;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.MockBuilder;
import org.rundeck.api.RundeckApiException;
import org.rundeck.api.RundeckClient;
import org.rundeck.api.domain.RundeckExecution;
import org.rundeck.api.domain.RundeckExecution.ExecutionStatus;
import org.rundeck.api.domain.RundeckJob;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.wc.SVNClientManager;

/**
 * Test the {@link RundeckNotifier}
 * 
 * @author Vincent Behar
 */
public class RundeckNotifierTest extends HudsonTestCase {

    public void testCommitWithoutTag() throws Exception {
        RundeckNotifier notifier = new RundeckNotifier("1", createOptions(), null, "", false, false);
        notifier.getDescriptor().setRundeckInstance(new MockRundeckClient());

        FreeStyleProject project = createFreeStyleProject();
        project.getBuildersList().add(new MockBuilder(Result.SUCCESS));
        project.getPublishersList().add(notifier);

        // first build
        FreeStyleBuild build = assertBuildStatusSuccess(project.scheduleBuild2(0).get());
        assertTrue(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        String s = FileUtils.readFileToString(build.getLogFile());
        assertTrue(s.contains("Notifying RunDeck..."));
        assertTrue(s.contains("Notification succeeded !"));

        // second build
        build = assertBuildStatusSuccess(project.scheduleBuild2(0).get());
        assertTrue(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        s = FileUtils.readFileToString(build.getLogFile());
        assertTrue(s.contains("Notifying RunDeck..."));
        assertTrue(s.contains("Notification succeeded !"));
    }

    public void testStandardCommit() throws Exception {
        RundeckNotifier notifier = new RundeckNotifier("1", null, null, "", false, false);
        notifier.getDescriptor().setRundeckInstance(new MockRundeckClient());

        FreeStyleProject project = createFreeStyleProject();
        project.getBuildersList().add(new MockBuilder(Result.SUCCESS));
        project.getPublishersList().add(notifier);

        FreeStyleBuild build = assertBuildStatusSuccess(project.scheduleBuild2(0).get());
        assertTrue(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        String s = FileUtils.readFileToString(build.getLogFile());
        assertTrue(s.contains("Notifying RunDeck"));
    }

    public void testDeployCommitWithTagWontBreakTheBuild() throws Exception {
        RundeckNotifier notifier = new RundeckNotifier("1", null, null, "", false, false);
        notifier.getDescriptor().setRundeckInstance(new MockRundeckClient());

        FreeStyleProject project = createFreeStyleProject();
        project.getBuildersList().add(new MockBuilder(Result.SUCCESS));
        project.getPublishersList().add(notifier);

        FreeStyleBuild build = assertBuildStatusSuccess(project.scheduleBuild2(0).get());
        assertTrue(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        String s = FileUtils.readFileToString(build.getLogFile());
        assertTrue(s.contains("Notifying RunDeck..."));
        assertTrue(s.contains("Notification succeeded !"));
    }

    public void testDeployCommitWithTagWillBreakTheBuild() throws Exception {
        RundeckNotifier notifier = new RundeckNotifier("1", null, null, "", false, true);
        notifier.getDescriptor().setRundeckInstance(new MockRundeckClient() {

            private static final long serialVersionUID = 1L;

            @Override
            public RundeckExecution triggerJob(String jobId, Properties options, Properties nodeFilters)
                    throws RundeckApiException {
                throw new RundeckApiException("Fake error for testing");
            }

        });

        FreeStyleProject project = createFreeStyleProject();
        project.getBuildersList().add(new MockBuilder(Result.SUCCESS));
        project.getPublishersList().add(notifier);

        // second build
        FreeStyleBuild build = assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0).get());
        assertFalse(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        String s = FileUtils.readFileToString(build.getLogFile());
        assertTrue(s.contains("Notifying RunDeck..."));
        assertTrue(s.contains("Error while talking to RunDeck's API"));
        assertTrue(s.contains("Fake error for testing"));
    }

    public void testExpandEnvVarsInOptions() throws Exception {
        RundeckNotifier notifier = new RundeckNotifier("1", createOptions(), null, null, false, true);
        notifier.getDescriptor().setRundeckInstance(new MockRundeckClient() {

            private static final long serialVersionUID = 1L;

            @Override
            public RundeckExecution triggerJob(String jobId, Properties options, Properties nodeFilters) {
                Assert.assertEquals(4, options.size());
                Assert.assertEquals("value 1", options.getProperty("option1"));
                Assert.assertEquals("1", options.getProperty("buildNumber"));
                Assert.assertEquals("my project name", options.getProperty("jobName"));
                return super.triggerJob(jobId, options, nodeFilters);
            }

        });

        FreeStyleProject project = createFreeStyleProject("my project name");
        project.getBuildersList().add(new MockBuilder(Result.SUCCESS));
        project.getPublishersList().add(notifier);

        // first build
        FreeStyleBuild build = assertBuildStatusSuccess(project.scheduleBuild2(0).get());
        assertTrue(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        String s = FileUtils.readFileToString(build.getLogFile());
        assertTrue(s.contains("Notifying RunDeck..."));
        assertTrue(s.contains("Notification succeeded !"));
    }

    public void testUpstreamBuildWithTag() throws Exception {
        RundeckNotifier notifier = new RundeckNotifier("1", null, null, "", false, false);
        notifier.getDescriptor().setRundeckInstance(new MockRundeckClient());

        FreeStyleProject upstream = createFreeStyleProject("upstream");
        upstream.getBuildersList().add(new MockBuilder(Result.SUCCESS));

        FreeStyleProject project = createFreeStyleProject();
        project.getBuildersList().add(new MockBuilder(Result.SUCCESS));
        project.getPublishersList().add(notifier);

        FreeStyleBuild upstreamBuild = assertBuildStatusSuccess(upstream.scheduleBuild2(0).get());
        FreeStyleBuild build = assertBuildStatusSuccess(project.scheduleBuild2(0, new UpstreamCause((Run<?, ?>) upstreamBuild)).get());
        assertTrue(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        String s = FileUtils.readFileToString(build.getLogFile());
        assertTrue(s.contains("Notifying RunDeck..."));
        assertTrue(s.contains("Notification succeeded !"));
    }

    public void testFailedBuild() throws Exception {
        RundeckNotifier notifier = new RundeckNotifier("1", createOptions(), null, "", false, false);
        notifier.getDescriptor().setRundeckInstance(new MockRundeckClient());

        FreeStyleProject project = createFreeStyleProject();
        project.getBuildersList().add(new MockBuilder(Result.FAILURE));
        project.getPublishersList().add(notifier);

        // first build
        FreeStyleBuild build = assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0).get());
        assertFalse(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        String s = FileUtils.readFileToString(build.getLogFile());
        assertFalse(s.contains("Notifying RunDeck"));

        // second build
        build = assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0).get());
        assertFalse(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        s = FileUtils.readFileToString(build.getLogFile());
        assertFalse(s.contains("Notifying RunDeck"));
    }

    public void testWaitForRundeckJob() throws Exception {
        RundeckNotifier notifier = new RundeckNotifier("1", createOptions(), null, "", true, false);
        notifier.getDescriptor().setRundeckInstance(new MockRundeckClient());

        FreeStyleProject project = createFreeStyleProject();
        project.getBuildersList().add(new MockBuilder(Result.SUCCESS));
        project.getPublishersList().add(notifier);

        // first build
        FreeStyleBuild build = assertBuildStatusSuccess(project.scheduleBuild2(0).get());
        assertTrue(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        String s = FileUtils.readFileToString(build.getLogFile());
        assertTrue(s.contains("Notifying RunDeck..."));
        assertTrue(s.contains("Notification succeeded !"));
        assertTrue(s.contains("Waiting for RunDeck execution to finish..."));
        assertTrue(s.contains("RunDeck execution #1 finished in 3 minutes 27 seconds, with status : SUCCEEDED"));
    }

    private String createOptions() {
        Properties options = new Properties();
        options.setProperty("option1", "value 1");
        options.setProperty("workspace", "$WORKSPACE");
        options.setProperty("jobName", "$JOB_NAME");
        options.setProperty("buildNumber", "$BUILD_NUMBER");

        StringWriter writer = new StringWriter();
        try {
            options.store(writer, "this is a comment line");
            return writer.toString();
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * @param build
     * @param actionClass
     * @return true if the given build contains an action of the given actionClass
     */
    private boolean buildContainsAction(Build<?, ?> build, Class<?> actionClass) {
        for (Action action : build.getActions()) {
            if (actionClass.isInstance(action)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Just a mock {@link RundeckClient} which is always successful
     */
    private static class MockRundeckClient extends RundeckClient {

        private static final long serialVersionUID = 1L;

        public MockRundeckClient() {
            super("http://localhost:4440", "admin", "admin");
        }

        @Override
        public void ping() {
            // successful
        }

        @Override
        public void testCredentials() {
            // successful
        }

        @Override
        public RundeckExecution triggerJob(String jobId, Properties options, Properties nodeFilters) {
            return initExecution(ExecutionStatus.RUNNING);
        }

        @Override
        public RundeckExecution getExecution(Long executionId) {
            return initExecution(ExecutionStatus.SUCCEEDED);
        }

        @Override
        public RundeckJob getJob(String jobId) {
            RundeckJob job = new RundeckJob();
            return job;
        }

        private RundeckExecution initExecution(ExecutionStatus status) {
            RundeckExecution execution = new RundeckExecution();
            execution.setId(1L);
            execution.setUrl("http://localhost:4440/execution/follow/1");
            execution.setStatus(status);
            execution.setStartedAt(new Date(1310159014640L));
            if (ExecutionStatus.SUCCEEDED.equals(status)) {
                Date endedAt = execution.getStartedAt();
                endedAt = DateUtils.addMinutes(endedAt, 3);
                endedAt = DateUtils.addSeconds(endedAt, 27);
                execution.setEndedAt(endedAt);
            }
            return execution;
        }

    }
}
