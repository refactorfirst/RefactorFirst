package org.hjug.graphbuilder.graphbuilder;

import java.util.List;

/**
 * List defaults for {@link CSharpFacts}. The extractor may leave a list out of the JSON;
 * the records then hold an empty list instead of null.
 *
 * Kept outside CSharpFacts on purpose: its nested records use this too, and a helper
 * on the outer record would make them depend on the record that contains them (a cycle).
 */
final class CSharpFactLists {

    private CSharpFactLists() {}

    static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }
}
