package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CsvReportTest {

    @Test
    void sanitizeCsvCell_quotesEscapesAndNeutralizesEveryLine() {
        assertEquals("\"plain\"", CsvReport.sanitizeCsvCell("plain"));
        assertEquals("\"a,b \"\"quoted\"\"\"", CsvReport.sanitizeCsvCell("a,b \"quoted\""));
        assertEquals("\"'=formula\"", CsvReport.sanitizeCsvCell("=formula"));
        assertEquals("\"safe\n'+formula\"", CsvReport.sanitizeCsvCell("safe\n+formula"));
        assertEquals("\"'\tformula\"", CsvReport.sanitizeCsvCell("\tformula"));
        assertEquals("\"'\r'=formula\"", CsvReport.sanitizeCsvCell("\r=formula"));
    }

    @Test
    void addsRow_usesCanonicalEncoderOnceForEveryCell() {
        StringBuilder row = new StringBuilder();

        new CsvReport().addsRow(row, new String[] {"=project", "1,2", "a\"b"});

        assertEquals("\"'=project\",\"1,2\",\"a\"\"b\",", row.toString());
    }
}
