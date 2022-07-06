package org.hjug.metrics;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.pmd.*;
import net.sourceforge.pmd.lang.Language;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.java.JavaLanguageModule;

// based on http://sdoulger.blogspot.com/2010/12/call-pmd-from-your-code-with-you-custom.html
@Slf4j
public class PMDGodClassRuleRunner {

    private SourceCodeProcessor sourceCodeProcessor;
    private Language java = LanguageRegistry.getLanguage(JavaLanguageModule.NAME);
    private RuleSets ruleSets;

    public PMDGodClassRuleRunner() {
        PMD pmd = new PMD();
        sourceCodeProcessor = pmd.getSourceCodeProcessor();

        RuleSet ruleSet = new RuleSetLoader().loadFromResource("category/java/design.xml");
        Rule godClassRule = ruleSet.getRuleByName("GodClass");
        godClassRule.setLanguage(java);

        // add your rule to the ruleset
        RuleSetFactory ruleSetFactory = new RuleSetFactory();
        RuleSet ruleSet2 = ruleSetFactory.createSingleRuleRuleSet(godClassRule);
        ruleSets = new RuleSets(ruleSet2);
    }

    public Optional<GodClass> runGodClassRule(File file) {
        // TODO: Capture file path and file ref?
        return runPMD(file);
    }

    public Optional<GodClass> runGodClassRule(String name, InputStream fis) {
        return runPMD(name, fis);
    }

    /**
     * Runs PMD on the specific file with the specific rule.
     * @param file target file
     * @return  List with errors. If empty then no error
     */
    public Optional<GodClass> runPMD(File file) {

        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
        } catch (FileNotFoundException ignore) {
            log.warn("{} was not found", file.getName());
        }

        return runPMD(file.getName(), fis);
    }

    public Optional<GodClass> runPMD(String sourceCodeFileName, InputStream inputStream) {
        GodClass godClass = null;
        try {
            // Set the javaVersion you are using. (*1)
            // pmd.setJavaVersion(SourceType.JAVA_16); -- MAY NEED TO SPECIFY THIS...
            // Get a context and initialize it with The Report that PMD will return
            final RuleContext ctx = new RuleContext();
            ctx.setReport(new Report());
            // target filename
            ctx.setSourceCodeFile(new File(sourceCodeFileName));
            sourceCodeProcessor.processSourceCode(inputStream, ruleSets, ctx);

            // write results
            if (!ctx.getReport().isEmpty()) {
                for (final RuleViolation violation : ctx.getReport()) {
                    godClass = new GodClass(
                            violation.getClassName(),
                            sourceCodeFileName,
                            violation.getPackageName(),
                            violation.getDescription());
                }
            }
        } catch (PMDException ignore) {
            log.warn("runPMD failed", ignore);
        }
        return Optional.ofNullable(godClass);
    }
}
