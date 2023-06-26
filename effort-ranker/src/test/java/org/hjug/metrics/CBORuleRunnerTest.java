package org.hjug.metrics;

import java.util.Optional;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class CBORuleRunnerTest {

    private CBORuleRunner CBORuleRunner;

    @Before
    public void setUp() {
        CBORuleRunner = new CBORuleRunner();
    }

    @Test
    public void testRuleRunnerExpectOneClass() throws Exception {
        String attributeHandler = "Console.java";
        Optional<CBOClass> optionalResult = CBORuleRunner.runCBOClassRule(
                attributeHandler, getClass().getClassLoader().getResourceAsStream(attributeHandler));

        CBOClass result = optionalResult.get();

        Assert.assertEquals("Console.java", result.getFileName());
        Assert.assertEquals("io.confluent.ksql.cli.console", result.getPackageName());
        Assert.assertEquals(35, result.getCouplingCount().longValue());
    }

    @Test
    public void testRuleRunnerExpectNoResults() throws Exception {
        String attributeHandler = "Attributes.java";
        Optional<CBOClass> result = CBORuleRunner.runCBOClassRule(
                attributeHandler, getClass().getClassLoader().getResourceAsStream(attributeHandler));

        Assert.assertFalse(result.isPresent());
    }
}
