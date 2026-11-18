package org.hjug.graphbuilder.graphbuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class CSharpFactListsTest {

    @Test
    void aMissingListBecomesEmpty() {
        assertEquals(List.of(), CSharpFactLists.orEmpty(null));
    }

    @Test
    void aPresentListIsKeptAsIs() {
        List<String> names = List.of("a", "b");
        assertSame(names, CSharpFactLists.orEmpty(names));
    }

    @Test
    void theRecordsStillDefaultTheirLists() {
        CSharpFacts facts = new CSharpFacts(1, "root", null, null);
        assertTrue(facts.namespaces().isEmpty());
        assertTrue(facts.types().isEmpty());
    }

    /** The nested records must not call back into the record that contains them. */
    @Test
    void theHelperDoesNotLiveOnTheOuterRecord() throws IOException {
        String source = Files.readString(
                Path.of("src/main/java/org/hjug/graphbuilder/graphbuilder/CSharpFacts.java"), StandardCharsets.UTF_8);
        assertTrue(!source.contains("List<T> orEmpty("), "orEmpty is declared in CSharpFacts again");
    }
}
