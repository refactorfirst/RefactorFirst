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
import org.hjug.metrics.rules.CBORule;

// based on http://sdoulger.blogspot.com/2010/12/call-pmd-from-your-code-with-you-custom.html
@Slf4j
public class CBORuleRunner {

    private SourceCodeProcessor sourceCodeProcessor;
    private RuleSets ruleSets;
    private Language java = LanguageRegistry.getLanguage(JavaLanguageModule.NAME);

    public CBORuleRunner() {
        PMD pmd = new PMD();
        sourceCodeProcessor = pmd.getSourceCodeProcessor();

        Rule cboClassRule = new CBORule();
        cboClassRule.setLanguage(java);

        // add your rule to the ruleset
        RuleSetFactory ruleSetFactory = new RuleSetFactory();
        RuleSet ruleSet2 = ruleSetFactory.createSingleRuleRuleSet(cboClassRule);

        ruleSets = new RuleSets(ruleSet2);
    }

    public Optional<CBOClass> runCBOClassRule(File file) {
        // TODO: Capture file path and file ref?
        return runPMD(file);
    }

    public Optional<CBOClass> runCBOClassRule(String name, InputStream fis) {
        return runPMD(name, fis);
    }

    /**
     * Runs PMD on the specific file with the specific rule.
     * @param file target file
     * @return  List with errors. If empty then no error
     */
    public Optional<CBOClass> runPMD(File file) {

        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
        } catch (FileNotFoundException ignore) {
            log.warn("{} was not found", file.getName());
        }

        return runPMD(file.getName(), fis);
    }

    public Optional<CBOClass> runPMD(String sourceCodeFileName, InputStream inputStream) {
        CBOClass cboClass = null;
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
                    cboClass = new CBOClass(
                            violation.getClassName(),
                            sourceCodeFileName,
                            violation.getPackageName(),
                            violation.getDescription());
                }
            }
        } catch (PMDException ignore) {
            log.warn("runPMD failed", ignore);
        }
        return Optional.ofNullable(cboClass);
    }
}
