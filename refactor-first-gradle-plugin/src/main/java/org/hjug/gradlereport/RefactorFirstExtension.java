package org.hjug.gradlereport;

import java.io.File;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

public class RefactorFirstExtension {
    private final Property<Boolean> showDetails;
    private final Property<Integer> backEdgeAnalysisCount;
    private final Property<Boolean> analyzeCycles;
    private final Property<Boolean> excludeTests;
    private final Property<Boolean> minifyHtml;
    private final Property<File> testSourceDirectory;
    private final Property<File> outputDirectory;

    /** Creates the extension and assigns its default conventions. */
    public RefactorFirstExtension(ObjectFactory objects) {
        this.showDetails = objects.property(Boolean.class).convention(false);
        this.backEdgeAnalysisCount = objects.property(Integer.class).convention(50);
        this.analyzeCycles = objects.property(Boolean.class).convention(true);
        this.excludeTests = objects.property(Boolean.class).convention(true);
        this.minifyHtml = objects.property(Boolean.class).convention(false);
        this.testSourceDirectory = objects.property(File.class);
        this.outputDirectory = objects.property(File.class).convention(new File("build/reports/refactor-first"));
    }

    /** Returns the property controlling detailed report content. */
    public Property<Boolean> getShowDetails() {
        return showDetails;
    }

    /** Returns the maximum cycle back-edge count property. */
    public Property<Integer> getBackEdgeAnalysisCount() {
        return backEdgeAnalysisCount;
    }

    /** Returns the property controlling cycle analysis. */
    public Property<Boolean> getAnalyzeCycles() {
        return analyzeCycles;
    }

    /** Returns the property controlling test-source exclusion. */
    public Property<Boolean> getExcludeTests() {
        return excludeTests;
    }

    /** Returns the property controlling HTML minification. */
    public Property<Boolean> getMinifyHtml() {
        return minifyHtml;
    }

    /** Returns the optional test source directory property. */
    public Property<File> getTestSourceDirectory() {
        return testSourceDirectory;
    }

    /** Returns the report output directory property. */
    public Property<File> getOutputDirectory() {
        return outputDirectory;
    }

    /** Returns whether detailed report content is enabled. */
    public boolean isShowDetails() {
        return getShowDetails().get();
    }

    /** Enables or disables detailed report content. */
    public void setShowDetails(boolean showDetails) {
        getShowDetails().set(showDetails);
    }

    /** Returns the configured cycle back-edge limit. */
    public int getBackEdgeAnalysisCountValue() {
        return getBackEdgeAnalysisCount().get();
    }

    /** Sets the maximum number of cycle back edges to analyze. */
    public void setBackEdgeAnalysisCount(int count) {
        getBackEdgeAnalysisCount().set(count);
    }

    /** Returns whether cycle analysis is enabled. */
    public boolean isAnalyzeCycles() {
        return getAnalyzeCycles().get();
    }

    /** Enables or disables cycle analysis. */
    public void setAnalyzeCycles(boolean analyzeCycles) {
        getAnalyzeCycles().set(analyzeCycles);
    }

    /** Returns whether test sources are excluded. */
    public boolean isExcludeTests() {
        return getExcludeTests().get();
    }

    /** Enables or disables test-source exclusion. */
    public void setExcludeTests(boolean excludeTests) {
        getExcludeTests().set(excludeTests);
    }

    /** Returns whether HTML minification is enabled. */
    public boolean isMinifyHtml() {
        return getMinifyHtml().get();
    }

    /** Enables or disables HTML minification. */
    public void setMinifyHtml(boolean minifyHtml) {
        getMinifyHtml().set(minifyHtml);
    }

    /** Sets the test source directory. */
    public void setTestSourceDirectory(File testSourceDirectory) {
        getTestSourceDirectory().set(testSourceDirectory);
    }

    /** Sets the report output directory. */
    public void setOutputDirectory(File outputDirectory) {
        getOutputDirectory().set(outputDirectory);
    }

    /** Validates the configured extension values. */
    public void validate() {
        if (getBackEdgeAnalysisCount().get() < 0) {
            throw new RefactorFirstPluginException("backEdgeAnalysisCount must be >= 0, got: "
                    + getBackEdgeAnalysisCount().get());
        }
    }
}
