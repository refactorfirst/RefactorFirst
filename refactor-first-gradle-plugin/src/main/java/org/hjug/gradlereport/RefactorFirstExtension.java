package org.hjug.gradlereport;

import java.io.File;

/**
 * Gradle extension to configure RefactorFirst plugin.<br>
 * Mirrors the Maven plugin parameters where possible.
 *
 * @author FX
 */
public class RefactorFirstExtension {
    private boolean showDetails = false;
    private int backEdgeAnalysisCount = 50;
    private boolean analyzeCycles = true;
    private boolean minifyHtml = false;
    private boolean excludeTests = true;
    /**
     * The test source directory containing test class sources (pattern).
     */
    private String testSourceDirectory; // e.g. "src/test"

    private String projectName;   // default to Gradle project name if null
    private String projectVersion; // default to Gradle project version if null

    /**
     * Output directory relative to project root for generated site files.
     * Defaults to target/site (to match Maven plugin output paths).
     */
    private String outputDirectory; // e.g. "target/site"

    public boolean isShowDetails() {
        return showDetails;
    }

    public void setShowDetails(boolean showDetails) {
        this.showDetails = showDetails;
    }

    public int getBackEdgeAnalysisCount() {
        return backEdgeAnalysisCount;
    }

    public void setBackEdgeAnalysisCount(int backEdgeAnalysisCount) {
        this.backEdgeAnalysisCount = backEdgeAnalysisCount;
    }

    public boolean isAnalyzeCycles() {
        return analyzeCycles;
    }

    public void setAnalyzeCycles(boolean analyzeCycles) {
        this.analyzeCycles = analyzeCycles;
    }

    public boolean isMinifyHtml() {
        return minifyHtml;
    }

    public void setMinifyHtml(boolean minifyHtml) {
        this.minifyHtml = minifyHtml;
    }

    public boolean isExcludeTests() {
        return excludeTests;
    }

    public void setExcludeTests(boolean excludeTests) {
        this.excludeTests = excludeTests;
    }

    public String getTestSourceDirectory() {
        return testSourceDirectory;
    }

    public void setTestSourceDirectory(String testSourceDirectory) {
        this.testSourceDirectory = testSourceDirectory;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getProjectVersion() {
        return projectVersion;
    }

    public void setProjectVersion(String projectVersion) {
        this.projectVersion = projectVersion;
    }

    public String getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(String outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    // Convenience to resolve output directory to an absolute File
    File resolveOutputDir(File projectDir) {
        String out = outputDirectory;
        if (out == null || out.trim().isEmpty()) {
            out = "target/site"; // match Maven default location
        }
        return new File(projectDir, out);
    }
}
