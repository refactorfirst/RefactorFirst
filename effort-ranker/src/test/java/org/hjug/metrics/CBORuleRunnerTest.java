package org.hjug.metrics;

import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CBORuleRunnerTest {

    private CBORuleRunner CBORuleRunner;

    @BeforeEach
    public void setUp() {
        CBORuleRunner = new CBORuleRunner();
    }

    @Test
    void testRuleRunnerExpectOneClass() throws Exception {
        String attributeHandler = "Console.java";
        Optional<CBOClass> optionalResult = CBORuleRunner.runCBOClassRule(
                attributeHandler, getClass().getClassLoader().getResourceAsStream(attributeHandler));

        CBOClass result = optionalResult.get();

        Assertions.assertEquals("Console.java", result.getFileName());
        Assertions.assertEquals("io.confluent.ksql.cli.console", result.getPackageName());
        Assertions.assertEquals(35, result.getCouplingCount().longValue());
    }

    @Test
    void testRuleRunnerExpectNoResults() throws Exception {
        String attributeHandler = "Attributes.java";
        Optional<CBOClass> result = CBORuleRunner.runCBOClassRule(
                attributeHandler, getClass().getClassLoader().getResourceAsStream(attributeHandler));

        Assertions.assertFalse(result.isPresent());
    }
}
