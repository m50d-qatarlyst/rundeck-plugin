package org.jenkinsci.plugins.rundeck;

import hudson.EnvVars;
import hudson.model.EnvironmentContributingAction;
import hudson.model.AbstractBuild;
import hudson.model.Cause;
import org.rundeck.api.domain.RundeckExecution;

/**
 * The cause of a RunDeck initiated build (encapsulates the {@link RundeckExecution} at the origin of the RunDeck
 * notification).
 * 
 * @author Vincent Behar
 */
public class RundeckCause extends Cause {

    private final RundeckExecution execution;

    /**
     * @param execution
     */
    public RundeckCause(RundeckExecution execution) {
        super();
        this.execution = execution;
    }

    @Override
    public String getShortDescription() {
        return "Started by a RunDeck Notification";
    }

    @Override
    public void onAddedTo(AbstractBuild build) {
        super.onAddedTo(build);
        build.addAction(new RundeckExecutionEnvironmentContributingAction(execution));
    }

    /**
     * {@link EnvironmentContributingAction} used to make information about the {@link RundeckExecution} available to
     * the build (as environment variables)
     */
    public static class RundeckExecutionEnvironmentContributingAction implements EnvironmentContributingAction {

        private final RundeckExecution execution;

        /**
         * @param execution
         */
        public RundeckExecutionEnvironmentContributingAction(RundeckExecution execution) {
            super();
            this.execution = execution;
        }

        public String getIconFileName() {
            return null;
        }

        public String getDisplayName() {
            return null;
        }

        public String getUrlName() {
            return null;
        }

        public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
            if (execution != null) {
                if (execution.getJob() != null) {
                    env.put("RDECK_JOB_ID", String.valueOf(execution.getJob().getId()));
                    env.put("RDECK_JOB_NAME", String.valueOf(execution.getJob().getName()));
                    env.put("RDECK_JOB_GROUP", String.valueOf(execution.getJob().getGroup()));
                    env.put("RDECK_JOB_DESCRIPTION", String.valueOf(execution.getJob().getDescription()));
                    env.put("RDECK_PROJECT", String.valueOf(execution.getJob().getProject()));
                }
                env.put("RDECK_EXEC_ID", String.valueOf(execution.getId()));
                env.put("RDECK_EXEC_STATUS", String.valueOf(execution.getStatus()));
                env.put("RDECK_EXEC_STARTED_BY", String.valueOf(execution.getStartedBy()));
                env.put("RDECK_EXEC_STARTED_AT", String.valueOf(execution.getStartedAt()));
                env.put("RDECK_EXEC_ENDED_AT", String.valueOf(execution.getEndedAt()));
                env.put("RDECK_EXEC_ABORTED_BY", String.valueOf(execution.getAbortedBy()));
                env.put("RDECK_EXEC_DURATION_MILLIS", String.valueOf(execution.getDurationInMillis()));
                env.put("RDECK_EXEC_DURATION_SECONDS", String.valueOf(execution.getDurationInSeconds()));
                env.put("RDECK_EXEC_DURATION", String.valueOf(execution.getDuration()));
                env.put("RDECK_EXEC_SHORT_DURATION", String.valueOf(execution.getShortDuration()));
                env.put("RDECK_EXEC_URL", String.valueOf(execution.getUrl()));
                env.put("RDECK_EXEC_DESCRIPTION", String.valueOf(execution.getDescription()));
            }
        }
    }

}
