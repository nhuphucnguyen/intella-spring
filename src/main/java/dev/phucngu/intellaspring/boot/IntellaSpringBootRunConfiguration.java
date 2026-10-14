package dev.phucngu.intellaspring.boot;

import com.intellij.execution.CommonJavaRunConfigurationParameters;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.JavaCommandLineState;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.execution.util.ProgramParametersUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.execution.application.ApplicationConfiguration;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A Spring Boot run configuration: the stock application runner plus Boot's own launch
 * conventions, applied as VM parameters exactly like IDEA's gated plugin does — active
 * profiles, banner off, TieredStopAtLevel launch optimization, ANSI output always on.
 * Profiles/banner/optimization persist with the configuration and are edited alongside the
 * standard Application editor in S10's fragment pass.
 */
public class IntellaSpringBootRunConfiguration extends ApplicationConfiguration {
    private String activeProfiles = "";
    private boolean hideBanner = true;
    private boolean launchOptimization = true;

    public IntellaSpringBootRunConfiguration(Project project, ConfigurationFactory factory, String name) {
        super(name, project, factory);
    }

    public String getActiveProfiles() {
        return activeProfiles;
    }

    public void setActiveProfiles(String activeProfiles) {
        this.activeProfiles = activeProfiles != null ? activeProfiles : "";
    }

    public boolean isHideBanner() {
        return hideBanner;
    }

    public void setHideBanner(boolean hideBanner) {
        this.hideBanner = hideBanner;
    }

    public boolean isLaunchOptimization() {
        return launchOptimization;
    }

    public void setLaunchOptimization(boolean launchOptimization) {
        this.launchOptimization = launchOptimization;
    }

    @Override
    public String suggestedName() {
        String main = getMainClassName();
        return main != null ? main.substring(main.lastIndexOf('.') + 1) : null;
    }

    @Override
    public void checkConfiguration() throws RuntimeConfigurationException {
        Module module = getConfigurationModule().getModule();
        if (module == null) {
            throw new RuntimeConfigurationError("Module not specified");
        }
        String mainName = getMainClassName();
        if (mainName == null || mainName.isBlank()) {
            throw new RuntimeConfigurationError("Main class not specified");
        }
        var scope = com.intellij.psi.search.GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false);
        var main = com.intellij.psi.JavaPsiFacade.getInstance(getProject()).findClass(mainName, scope);
        if (main == null) {
            throw new RuntimeConfigurationError("Main class '" + mainName + "' not found on module classpath");
        }
        if (!SpringBootApplicationDetector.hasMainMethod(main)) {
            throw new RuntimeConfigurationError("Main class has no main(String[]) method");
        }
        if (!SpringBootApplicationDetector.isSpringBootApplication(main, scope)) {
            throw new RuntimeConfigurationError("Main class is not a @SpringBootApplication class");
        }
        JavaParametersUtil.checkAlternativeJRE(this);
        // only an explicitly set working directory is checked; the default (project dir) is the IDE's business
        if (getWorkingDirectory() != null) {
            ProgramParametersUtil.checkWorkingDirectoryExist(this, getProject(), module);
        }
    }

    @Override
    public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment)
            throws ExecutionException {
        return new IntellaSpringBootCommandLineState(environment);
    }

    @Override
    public void writeExternal(@NotNull Element element) {
        super.writeExternal(element);
        element.setAttribute("isb.activeProfiles", activeProfiles);
        element.setAttribute("isb.hideBanner", Boolean.toString(hideBanner));
        element.setAttribute("isb.launchOptimization", Boolean.toString(launchOptimization));
    }

    @Override
    public void readExternal(@NotNull Element element) {
        super.readExternal(element);
        activeProfiles = element.getAttributeValue("isb.activeProfiles", "");
        hideBanner = Boolean.parseBoolean(element.getAttributeValue("isb.hideBanner", "true"));
        launchOptimization = Boolean.parseBoolean(element.getAttributeValue("isb.launchOptimization", "true"));
    }

    /** IDEA's applySpringBootSettings equivalents, unit-testable on a plain parameter list. */
    public static void applyBootParameters(@NotNull ParametersList vmParameters, boolean hideBanner,
                                    boolean launchOptimization, @NotNull String activeProfiles) {
        if (launchOptimization) {
            vmParameters.add("-XX:TieredStopAtLevel=1");
        }
        if (hideBanner) {
            vmParameters.add("-Dspring.main.banner-mode=OFF");
        }
        if (!activeProfiles.isBlank()) {
            vmParameters.add("-Dspring.profiles.active=" + activeProfiles.trim());
        }
        vmParameters.add("-Dspring.output.ansi.enabled=always");
    }

    private final class IntellaSpringBootCommandLineState extends JavaCommandLineState {
        private IntellaSpringBootCommandLineState(@NotNull ExecutionEnvironment environment) {
            super(environment);
        }

        @Override
        protected JavaParameters createJavaParameters() throws ExecutionException {
            JavaParameters params = new JavaParameters();
            JavaParametersUtil.configureConfiguration(params, IntellaSpringBootRunConfiguration.this);
            JavaParametersUtil.configureModule(IntellaSpringBootRunConfiguration.this.getConfigurationModule(),
                    params, JavaParameters.JDK_AND_CLASSES, null);
            params.setMainClass(getMainClassName());
            applyBootParameters(params.getVMParametersList(), hideBanner, launchOptimization, activeProfiles);
            return params;
        }
    }
}
