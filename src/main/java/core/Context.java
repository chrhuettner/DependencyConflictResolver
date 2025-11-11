package core;

import com.github.dockerjava.api.DockerClient;
import provider.AIProvider;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

public class Context {
    private String project;
    private String previousVersion;
    private String newVersion;
    private String dependencyArtifactId;
    private String strippedFileName;
    private File outputDirClasses;

    private LogParser.CompileError compileError;

    private String brokenUpdateImage;
    private Path targetPathOld;
    private Path targetPathNew;

    private String targetDirectoryClasses;
    private File outputDirSrcFiles;

    private AIProvider activeProvider;
    private DockerClient dockerClient;
    private HashMap<String, ProposedChange> errorSet;
    private List<ProposedChange> proposedChanges;

    private String targetDirectoryLLMResponses;

    private String targetDirectoryPrompts;
    private String targetDirectoryFixedClasses;
    private String targetDirectoryFixedLogs;
    private String strippedClassName;

    public Context(String project, String previousVersion, String newVersion, String dependencyArtifactId, String strippedFileName,
                   File outputDirClasses, String brokenUpdateImage, Path targetPathOld, Path targetPathNew, String targetDirectoryClasses,
                   File outputDirSrcFiles, AIProvider activeProvider, DockerClient dockerClient, HashMap<String, ProposedChange> errorSet,
                   List<ProposedChange> proposedChanges, LogParser.CompileError compileError, String targetDirectoryLLMResponses,
                   String targetDirectoryPrompts, String targetDirectoryFixedClasses, String targetDirectoryFixedLogs, String strippedClassName) {
        this.project = project;
        this.previousVersion = previousVersion;
        this.newVersion = newVersion;
        this.dependencyArtifactId = dependencyArtifactId;
        this.strippedFileName = strippedFileName;
        this.outputDirClasses = outputDirClasses;
        this.brokenUpdateImage = brokenUpdateImage;
        this.targetPathOld = targetPathOld;
        this.targetPathNew = targetPathNew;
        this.targetDirectoryClasses = targetDirectoryClasses;
        this.outputDirSrcFiles = outputDirSrcFiles;
        this.activeProvider = activeProvider;
        this.dockerClient = dockerClient;
        this.errorSet = errorSet;
        this.proposedChanges = proposedChanges;
        this.compileError = compileError;
        this.targetDirectoryLLMResponses = targetDirectoryLLMResponses;
        this.targetDirectoryPrompts = targetDirectoryPrompts;
        this.targetDirectoryFixedClasses = targetDirectoryFixedClasses;
        this.targetDirectoryFixedLogs = targetDirectoryFixedLogs;
        this.strippedClassName = strippedClassName;
    }

    public String getStrippedClassName() {
        return strippedClassName;
    }

    public void setStrippedClassName(String strippedClassName) {
        this.strippedClassName = strippedClassName;
    }

    public String getTargetDirectoryFixedClasses() {
        return targetDirectoryFixedClasses;
    }

    public void setTargetDirectoryFixedClasses(String targetDirectoryFixedClasses) {
        this.targetDirectoryFixedClasses = targetDirectoryFixedClasses;
    }

    public String getTargetDirectoryFixedLogs() {
        return targetDirectoryFixedLogs;
    }

    public void setTargetDirectoryFixedLogs(String targetDirectoryFixedLogs) {
        this.targetDirectoryFixedLogs = targetDirectoryFixedLogs;
    }

    public String getTargetDirectoryPrompts() {
        return targetDirectoryPrompts;
    }

    public void setTargetDirectoryPrompts(String targetDirectoryPrompts) {
        this.targetDirectoryPrompts = targetDirectoryPrompts;
    }

    public String getTargetDirectoryLLMResponses() {
        return targetDirectoryLLMResponses;
    }

    public void setTargetDirectoryLLMResponses(String targetDirectoryLLMResponses) {
        this.targetDirectoryLLMResponses = targetDirectoryLLMResponses;
    }

    public LogParser.CompileError getCompileError() {
        return compileError;
    }

    public void setCompileError(LogParser.CompileError compileError) {
        this.compileError = compileError;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getPreviousVersion() {
        return previousVersion;
    }

    public void setPreviousVersion(String previousVersion) {
        this.previousVersion = previousVersion;
    }

    public String getNewVersion() {
        return newVersion;
    }

    public void setNewVersion(String newVersion) {
        this.newVersion = newVersion;
    }

    public String getDependencyArtifactId() {
        return dependencyArtifactId;
    }

    public void setDependencyArtifactId(String dependencyArtifactId) {
        this.dependencyArtifactId = dependencyArtifactId;
    }

    public String getStrippedFileName() {
        return strippedFileName;
    }

    public void setStrippedFileName(String strippedFileName) {
        this.strippedFileName = strippedFileName;
    }

    public File getOutputDirClasses() {
        return outputDirClasses;
    }

    public void setOutputDirClasses(File outputDirClasses) {
        this.outputDirClasses = outputDirClasses;
    }

    public String getBrokenUpdateImage() {
        return brokenUpdateImage;
    }

    public void setBrokenUpdateImage(String brokenUpdateImage) {
        this.brokenUpdateImage = brokenUpdateImage;
    }

    public Path getTargetPathOld() {
        return targetPathOld;
    }

    public void setTargetPathOld(Path targetPathOld) {
        this.targetPathOld = targetPathOld;
    }

    public Path getTargetPathNew() {
        return targetPathNew;
    }

    public void setTargetPathNew(Path targetPathNew) {
        this.targetPathNew = targetPathNew;
    }

    public String getTargetDirectoryClasses() {
        return targetDirectoryClasses;
    }

    public void setTargetDirectoryClasses(String targetDirectoryClasses) {
        this.targetDirectoryClasses = targetDirectoryClasses;
    }

    public File getOutputDirSrcFiles() {
        return outputDirSrcFiles;
    }

    public void setOutputDirSrcFiles(File outputDirSrcFiles) {
        this.outputDirSrcFiles = outputDirSrcFiles;
    }

    public AIProvider getActiveProvider() {
        return activeProvider;
    }

    public void setActiveProvider(AIProvider activeProvider) {
        this.activeProvider = activeProvider;
    }

    public DockerClient getDockerClient() {
        return dockerClient;
    }

    public void setDockerClient(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }

    public HashMap<String, ProposedChange> getErrorSet() {
        return errorSet;
    }

    public void setErrorSet(HashMap<String, ProposedChange> errorSet) {
        this.errorSet = errorSet;
    }

    public List<ProposedChange> getProposedChanges() {
        return proposedChanges;
    }

    public void setProposedChanges(List<ProposedChange> proposedChanges) {
        this.proposedChanges = proposedChanges;
    }
}
