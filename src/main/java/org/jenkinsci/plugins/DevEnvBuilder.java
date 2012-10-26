package org.jenkinsci.plugins;

import hudson.*;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tools.ToolInstallation;
import hudson.util.ArgumentListBuilder;
import java.io.IOException;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author ben.mcdonie@gmail.com
 */
public class DevEnvBuilder extends Builder {
    /**
     * GUI fields
     */
    private final String devEnvName;
    private final String solutionFile;
    private final String solutionConfigName;
    private final String projectFile;
    private final String projectConfigName;
    private final boolean performClean;
    private final boolean performRebuild;


    @DataBoundConstructor
    @SuppressWarnings("unused")
    public DevEnvBuilder(String devEnvName, String solutionFile, String solutionConfigName, String projectFile, String projectConfigName, boolean performClean, boolean performRebuild) {
        this.devEnvName = devEnvName;
        this.solutionFile = solutionFile;
        this.solutionConfigName = solutionConfigName;
        this.projectFile = projectFile;
        this.projectConfigName = projectConfigName;
        this.performClean = performClean;
        this.performRebuild = performRebuild;
    }

    @SuppressWarnings("unused")
    public String getSolutionFile() {
        return solutionFile;
    }

    @SuppressWarnings("unused")
    public String getSolutionConfigName() {
        return solutionConfigName;
    }

    @SuppressWarnings("unused")
    public String getProjectFile() {
        return projectFile;
    }

    @SuppressWarnings("unused")
    public String getProjectConfigName() {
        return projectConfigName;
    }
    
    @SuppressWarnings("unused")
    public String getDevEnvName() {
        return devEnvName;
    }

    public boolean isPerformClean() {
        return performClean;
    }

    public boolean isPerformRebuild() {
        return performRebuild;
    }
    
    public DevEnvInstallation getDevEnv() {
        for (DevEnvInstallation i : DESCRIPTOR.getInstallations()) {
            if (devEnvName != null && i.getName().equals(devEnvName))
                return i;
        }

        return null;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        ArgumentListBuilder cleanArgs = new ArgumentListBuilder();
        ArgumentListBuilder rebuildArgs = new ArgumentListBuilder();
        String execName = "devenv.com";
        DevEnvInstallation ai = getDevEnv();

        if (ai == null) {
            listener.getLogger().println("Path To devenv: " + execName);
            cleanArgs.add(execName);
            rebuildArgs.add(execName);
        } else {
            EnvVars env = build.getEnvironment(listener);
            ai = ai.forNode(Computer.currentComputer().getNode(), listener);
            ai = ai.forEnvironment(env);
            String pathToDevEnv = ai.getHome();
            FilePath exec = new FilePath(launcher.getChannel(), pathToDevEnv);

            try {
                if (!exec.exists()) {
                    listener.fatalError(pathToDevEnv + " doesn't exist");
                    return false;
                }
            } catch (IOException e) {
                listener.fatalError("Failed checking for existence of " + pathToDevEnv);
                return false;
            }

            listener.getLogger().println("Path To devenv: " + pathToDevEnv);
            cleanArgs.add(pathToDevEnv);
            cleanArgs.add("/clean");
            rebuildArgs.add(pathToDevEnv);
            rebuildArgs.add("/rebuild");
        }

        EnvVars env = build.getEnvironment(listener);

        String normalizedSolutionFile = normalize(solutionFile, build, env);
        String normalizedSolutionConfigName = normalize(solutionConfigName, build, env);
        String normalizedProjectFile = normalize(projectFile, build, env);
        String normalizedProjectConfigName = normalize(projectConfigName, build, env);
        
        if (normalizedSolutionConfigName != null && normalizedSolutionConfigName.length() > 0) {
            cleanArgs.add(normalizedSolutionConfigName);
            rebuildArgs.add(normalizedSolutionConfigName);
        }
        if (normalizedSolutionFile != null && normalizedSolutionFile.length() > 0) {
            cleanArgs.add(normalizedSolutionFile);
            rebuildArgs.add(normalizedSolutionFile);
        }

        if (normalizedProjectFile != null && normalizedProjectFile.length() > 0) {
            cleanArgs.add("/Project");
            rebuildArgs.add("/Project");
            cleanArgs.add(normalizedProjectFile);
            rebuildArgs.add(normalizedProjectFile);
        }
        if (normalizedProjectConfigName != null && normalizedProjectConfigName.length() > 0) {
            cleanArgs.add("/ProjectConfig");
            rebuildArgs.add("/ProjectConfig");
            cleanArgs.add(normalizedProjectConfigName);
            rebuildArgs.add(normalizedProjectConfigName);
        }
        
        FilePath pwd = build.getModuleRoot();
        if (normalizedSolutionFile != null) {
            FilePath msBuildFilePath = pwd.child(normalizedSolutionFile);
            if (!msBuildFilePath.exists()) {
                pwd = build.getWorkspace();
            }
        }

        if (!launcher.isUnix()) {
            cleanArgs = cleanArgs.toWindowsCommand(true);
            rebuildArgs = rebuildArgs.toWindowsCommand();
        }

        try {
            if (performClean) {
                listener.getLogger().println(String.format("Executing the command %s from %s", cleanArgs.toStringWithQuote(), pwd));
                int r = launcher.launch().cmds(cleanArgs).envs(env).stdout(listener).pwd(pwd).join();
                if (r != 0)
                    return false;
            }
            if (performClean) {
                listener.getLogger().println(String.format("Executing the command %s from %s", cleanArgs.toStringWithQuote(), pwd));
                int r = launcher.launch().cmds(rebuildArgs).envs(env).stdout(listener).pwd(pwd).join();
                if (r != 0)
                    return false;
            }
        } catch (IOException e) {
            Util.displayIOException(e, listener);
            build.setResult(Result.FAILURE);
            return false;
        }
        
        return true;
    }
    
    private String normalize(String str, AbstractBuild<?, ?> build, EnvVars env) {
        String normalizedSolutionFile = null;
        if (str != null && str.trim().length() != 0) {
            normalizedSolutionFile = str.replaceAll("[\t\r\n]+", " ");
            normalizedSolutionFile = Util.replaceMacro(normalizedSolutionFile, env);
            normalizedSolutionFile = Util.replaceMacro(normalizedSolutionFile, build.getBuildVariables());
        }
        
        return normalizedSolutionFile;
    }

    @Override
    public Descriptor<Builder> getDescriptor() {
        return DESCRIPTOR;
    }

    /**
     * Descriptor should be singleton.
     */
    @Extension
    public static final DevEnvBuilder.DescriptorImpl DESCRIPTOR = new DevEnvBuilder.DescriptorImpl();

    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        @CopyOnWrite
        private volatile DevEnvInstallation[] installations = new DevEnvInstallation[0];

        DescriptorImpl() {
            super(DevEnvBuilder.class);
            load();
        }

        public String getDisplayName() {
            return "Build a Visual Studio project or solution using devenv";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        public DevEnvInstallation[] getInstallations() {
            return installations;
        }

        public void setInstallations(DevEnvInstallation... devEnvInstallations) {
            this.installations = devEnvInstallations;
            save();
        }

        public DevEnvInstallation.DescriptorImpl getToolDescriptor() {
            return ToolInstallation.all().get(DevEnvInstallation.DescriptorImpl.class);
        }
    }
}
 