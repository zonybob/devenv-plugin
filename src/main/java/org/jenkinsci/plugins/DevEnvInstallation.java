package org.jenkinsci.plugins;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.model.EnvironmentSpecific;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import jenkins.model.Jenkins;

/**
 * @author Ben McDonie
 */
public final class DevEnvInstallation extends ToolInstallation implements NodeSpecific<DevEnvInstallation>, EnvironmentSpecific<DevEnvInstallation> {

    @SuppressWarnings("unused")
    /**
     * Backward compatibility
     */
    private transient String pathToDevEnv;


    @DataBoundConstructor
    public DevEnvInstallation(String name, String home) {
        super(name, home, null);
    }

    public DevEnvInstallation forNode(Node node, TaskListener log) throws IOException, InterruptedException {
        return new DevEnvInstallation(getName(), translateFor(node, log));
    }

    public DevEnvInstallation forEnvironment(EnvVars environment) {
        return new DevEnvInstallation(getName(), environment.expand(getHome()));
    }

    @Extension
    public static class DescriptorImpl extends ToolDescriptor<DevEnvInstallation> {

        public String getDisplayName() {
            return "Microsoft Visual Studio (devenv)";
        }

        @Override
        public DevEnvInstallation[] getInstallations() {
            return Jenkins.getInstance().getDescriptorByType(DevEnvBuilder.DescriptorImpl.class).getInstallations();
        }

        @Override
        public void setInstallations(DevEnvInstallation... installations) {
            Jenkins.getInstance().getDescriptorByType(DevEnvBuilder.DescriptorImpl.class).setInstallations(installations);
        }

    }

    /**
     * Used for backward compatibility
     *
     * @return the new object, an instance of MsBuildInstallation
     */
    protected Object readResolve() {
        if (this.pathToDevEnv != null) {
            return new DevEnvInstallation(this.getName(), this.pathToDevEnv);
        }
        return this;
    }
}