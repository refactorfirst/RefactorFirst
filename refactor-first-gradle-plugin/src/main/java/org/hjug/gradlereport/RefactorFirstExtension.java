package org.hjug.gradlereport;

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

    private String projectName; // default to Gradle project name if null
    private String projectVersion; // default to Gradle project version if null

    /**
     * Output directory for the HTML / simple HTML / CSV reports, relative to the project
     * directory or absolute. Defaults to {@code <projectDir>/build/reports/refactorfirst}.
     * Does not apply to the JSON report, which always writes to {@code <projectDir>/.refactorfirst}.
     */
    private String outputDirectory; // e.g. "build/rf"

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
}
