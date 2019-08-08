/*-
 * Copyright 2009-2015 © Meikel Brandmeyer.
 * All rights reserved.
 *
 * Licensed under the EUPL V.1.1 (cf. file EUPL-1.1 distributed with the
 * source code.) Translations in other european languages available at
 * https://joinup.ec.europa.eu/software/page/eupl.
 *
 * Alternatively, you may choose to use the software under the MIT license
 * (cf. file MIT distributed with the source code).
 */

package nebula.plugin.clojuresque.tasks;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.tasks.TaskAction
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.JavaExecSpec;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.ProcessForkOptions;

class ClojureExec extends ConventionTask implements JavaExecSpec {
    private ClojureExecAction clojureExecAction

    ClojureExec() {
        super()

        FileResolver fileResolver = ((ProjectInternal)getProject()).getFileResolver()
        clojureExecAction = new ClojureExecAction(fileResolver, getProject().getGradle().getGradleVersion())
    }

    @TaskAction
    void exec() {
        clojureExecAction.execute();
    }

    public List<String> getAllJvmArgs() {
        return clojureExecAction.getAllJvmArgs();
    }

    @Override
    public void setAllJvmArgs(List<String> list) {
        clojureExecAction.setAllJvmArgs(list);
    }

    public void setAllJvmArgs(Iterable<?> arguments) {
        clojureExecAction.setAllJvmArgs(arguments);
    }

    public List<String> getJvmArgs() {
        return clojureExecAction.getJvmArgs();
    }

    @Override
    public void setJvmArgs(List<String> list) {
        clojureExecAction.setJvmArgs(list);
    }

    public void setJvmArgs(Iterable<?> arguments) {
        clojureExecAction.setJvmArgs(arguments);
    }

    public ClojureExec jvmArgs(Iterable<?> arguments) {
        clojureExecAction.jvmArgs(arguments);
        return this;
    }

    @Override
    public ClojureExec jvmArgs(Object... arguments) {
        clojureExecAction.jvmArgs(arguments);
        return this;
    }

    @Override
    List<CommandLineArgumentProvider> getJvmArgumentProviders() {
        return clojureExecAction.getJvmArgumentProviders()
    }

    public Map<String, Object> getSystemProperties() {
        return clojureExecAction.getSystemProperties();
    }

    public void setSystemProperties(Map<String, ?> properties) {
        clojureExecAction.setSystemProperties(properties);
    }

    public ClojureExec systemProperties(Map<String, ?> properties) {
        clojureExecAction.systemProperties(properties);
        return this;
    }

    public ClojureExec systemProperty(String name, Object value) {
        clojureExecAction.systemProperty(name, value);
        return this;
    }

    public FileCollection getBootstrapClasspath() {
        return clojureExecAction.getBootstrapClasspath();
    }

    public void setBootstrapClasspath(FileCollection classpath) {
        clojureExecAction.setBootstrapClasspath(classpath);
    }

    public ClojureExec bootstrapClasspath(Object... classpath) {
        clojureExecAction.bootstrapClasspath(classpath);
        return this;
    }

    public String getMaxHeapSize() {
        return clojureExecAction.getMaxHeapSize();
    }

    public void setMaxHeapSize(String heapSize) {
        clojureExecAction.setMaxHeapSize(heapSize);
    }

    public String getMinHeapSize() {
        return clojureExecAction.getMinHeapSize();
    }

    public void setMinHeapSize(String heapSize) {
        clojureExecAction.setMinHeapSize(heapSize);
    }

    public boolean getEnableAssertions() {
        return clojureExecAction.getEnableAssertions();
    }

    public void setEnableAssertions(boolean enabled) {
        clojureExecAction.setEnableAssertions(enabled);
    }

    public boolean getDebug() {
        return clojureExecAction.getDebug();
    }

    public void setDebug(boolean enabled) {
        clojureExecAction.setDebug(enabled);
    }

    public String getMain() {
        return clojureExecAction.getMain();
    }

    public ClojureExec setMain(String mainClassName) {
        clojureExecAction.setMain(mainClassName);
        return this;
    }

    public List<String> getArgs() {
        return clojureExecAction.getArgs();
    }

    public ClojureExec setArgs(Iterable<?> applicationArgs) {
        clojureExecAction.setArgs(applicationArgs);
        return this;
    }

    @Override
    List<CommandLineArgumentProvider> getArgumentProviders() {
        return clojureExecAction.getArgumentProviders()
    }

    public ClojureExec args(Object... args) {
        clojureExecAction.args(args);
        return this;
    }

    public JavaExecSpec args(Iterable<?> args) {
        clojureExecAction.args(args);
        return this;
    }

    @Override
    public JavaExecSpec setArgs(List<String> list) {
        clojureExecAction.setArgs(list);
        return this;
    }

    public ClojureExec setClasspath(FileCollection classpath) {
        clojureExecAction.setClasspath(classpath);
        return this;
    }

    public ClojureExec classpath(Object... paths) {
        clojureExecAction.classpath(paths);
        return this;
    }

    public FileCollection getClasspath() {
        return clojureExecAction.getClasspath();
    }

    public ClojureExec copyTo(JavaForkOptions options) {
        clojureExecAction.copyTo(options);
        return this;
    }

    public String getExecutable() {
        return clojureExecAction.getExecutable();
    }

    @Override
    public void setExecutable(String s) {
        clojureExecAction.setExecutable(s);
    }

    public void setExecutable(Object executable) {
        clojureExecAction.setExecutable(executable);
    }

    public ClojureExec executable(Object executable) {
        clojureExecAction.executable(executable);
        return this;
    }

    public File getWorkingDir() {
        return clojureExecAction.getWorkingDir();
    }

    @Override
    public void setWorkingDir(File file) {
        clojureExecAction.setWorkingDir(file);
    }

    public void setWorkingDir(Object dir) {
        clojureExecAction.setWorkingDir(dir);
    }

    public ClojureExec workingDir(Object dir) {
        clojureExecAction.workingDir(dir);
        return this;
    }

    public Map<String, Object> getEnvironment() {
        return clojureExecAction.getEnvironment();
    }

    public void setEnvironment(Map<String, ?> environmentVariables) {
        clojureExecAction.setEnvironment(environmentVariables);
    }

    public ClojureExec environment(String name, Object value) {
        clojureExecAction.environment(name, value);
        return this;
    }

    public ClojureExec environment(Map<String, ?> environmentVariables) {
        clojureExecAction.environment(environmentVariables);
        return this;
    }

    public ClojureExec copyTo(ProcessForkOptions target) {
        clojureExecAction.copyTo(target);
        return this;
    }

    public ClojureExec setStandardInput(InputStream inputStream) {
        clojureExecAction.setStandardInput(inputStream);
        return this;
    }

    public InputStream getStandardInput() {
        return clojureExecAction.getStandardInput();
    }

    public ClojureExec setStandardOutput(OutputStream outputStream) {
        clojureExecAction.setStandardOutput(outputStream);
        return this;
    }

    public OutputStream getStandardOutput() {
        return clojureExecAction.getStandardOutput();
    }

    public ClojureExec setErrorOutput(OutputStream outputStream) {
        clojureExecAction.setErrorOutput(outputStream);
        return this;
    }

    public OutputStream getErrorOutput() {
        return clojureExecAction.getErrorOutput();
    }

    public JavaExecSpec setIgnoreExitValue(boolean ignoreExitValue) {
        clojureExecAction.setIgnoreExitValue(ignoreExitValue);
        return this;
    }

    public boolean isIgnoreExitValue() {
        return clojureExecAction.isIgnoreExitValue();
    }

    public List<String> getCommandLine() {
        return clojureExecAction.getCommandLine();
    }

    public void setDefaultCharacterEncoding(String defaultCharacterEncoding) {
        clojureExecAction.setDefaultCharacterEncoding(defaultCharacterEncoding);
    }

    public String getDefaultCharacterEncoding() {
        return clojureExecAction.getDefaultCharacterEncoding();
    }
}
