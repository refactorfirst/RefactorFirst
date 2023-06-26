package org.hjug.metrics;

import static org.junit.Assert.assertEquals;

import java.util.Locale;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CBOClassParsingTest {

    private Locale defaultLocale;

    @Before
    public void before() {
        defaultLocale = Locale.getDefault(Locale.Category.FORMAT);
        Locale.setDefault(Locale.Category.FORMAT, Locale.ENGLISH);
    }

    @After
    public void after() {
        Locale.setDefault(defaultLocale);
    }

    @Test
    public void test() {
        String result = "A value of 20 may denote a high amount of coupling within the class";
        CBOClass cboClass = new CBOClass("a", "a.txt", "org.hjug", result);
        assertEquals(Integer.valueOf(20), cboClass.getCouplingCount());
    }
}
