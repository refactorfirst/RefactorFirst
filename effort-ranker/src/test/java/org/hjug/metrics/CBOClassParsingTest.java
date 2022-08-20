package org.hjug.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CBOClassParsingTest {

    private Locale defaultLocale;

    @BeforeEach
    public void before() {
        defaultLocale = Locale.getDefault(Locale.Category.FORMAT);
        Locale.setDefault(Locale.Category.FORMAT, Locale.ENGLISH);
    }

    @AfterEach
    public void after() {
        Locale.setDefault(defaultLocale);
    }

    @Test
    void test() {
        String result = "A value of 20 may denote a high amount of coupling within the class";
        CBOClass cboClass = new CBOClass("a", "a.txt", "org.hjug", result);
        assertEquals(Integer.valueOf(20), cboClass.getCouplingCount());
    }
}
