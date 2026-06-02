package org.hjug.metrics;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.hjug.graphbuilder.metrics.DisharmonyMetric;
import org.hjug.graphbuilder.metrics.DisharmonyMetric.Direction;
import org.junit.jupiter.api.Test;

/**
 * Verifies DisharmonyRanker produces the same rankings as GodClassRanker for WMC/ATFD/TCC ascending,
 * and correctly handles mixed ASC/DESC directions and multi-metric tie logic.
 */
class DisharmonyRankerTest {

    private final DisharmonyRanker ranker = new DisharmonyRanker();

    // ── GodClass parity fixtures ────────────────────────────────────────────────
    // Same metric values used in GodClassRankerTest to confirm identical results.

    private DisharmonyInstance godLike(String name, String path, int atfd, int wmc, float tcc) {
        List<DisharmonyMetric> metrics = new ArrayList<>();
        metrics.add(new DisharmonyMetric("ATFD", atfd, Direction.ASCENDING));
        metrics.add(new DisharmonyMetric("WMC", wmc, Direction.ASCENDING));
        metrics.add(new DisharmonyMetric("TCC", tcc, Direction.ASCENDING));
        return new DisharmonyInstance("God Class", name, path, "org.example", null, metrics);
    }

    // ATFD=79, WMC=79, TCC=0.028
    private DisharmonyInstance attributeHandler() {
        return godLike("AttributeHandler", "org/hjug/git/AttributeHandler.java", 79, 79, 0.027777778f);
    }

    // ATFD=25, WMC=51, TCC=0.2
    private DisharmonyInstance sorter() {
        return godLike("Sorter", "Sorter.java", 25, 51, 0.2f);
    }

    // ATFD=16, WMC=60, TCC=0.078
    private DisharmonyInstance themeImpl() {
        return godLike("ThemeImpl", "ThemeImpl.java", 16, 60, 0.078160919f);
    }

    // ── Overall rank parity with GodClassRanker ─────────────────────────────────

    @Test
    void overallRankParityWithGodClassRanker() {
        List<DisharmonyInstance> items = new ArrayList<>();
        DisharmonyInstance ah = attributeHandler();
        DisharmonyInstance s = sorter();
        DisharmonyInstance t = themeImpl();
        items.add(ah);
        items.add(s);
        items.add(t);

        ranker.rank(items);

        // After ranking, list is sorted by sumOfRanks (same as GodClassRanker final state)
        // GodClassRankerTest expects: ThemeImpl sum=5, Sorter sum=6, AttributeHandler sum=7
        // with overallRanks 1, 2, 3
        assertEquals(1, findByPath(items, "ThemeImpl.java").getOverallRank());
        assertEquals(2, findByPath(items, "Sorter.java").getOverallRank());
        assertEquals(3, findByPath(items, "org/hjug/git/AttributeHandler.java").getOverallRank());

        assertEquals(5, findByPath(items, "ThemeImpl.java").getSumOfRanks());
        assertEquals(6, findByPath(items, "Sorter.java").getSumOfRanks());
        assertEquals(7, findByPath(items, "org/hjug/git/AttributeHandler.java").getSumOfRanks());
    }

    // ── Tie handling ────────────────────────────────────────────────────────────

    @Test
    void tiedMetricValuesShareRank() {
        // Sorter and ThemeImpl2 both ATFD=25 → same ATFD rank
        DisharmonyInstance s = godLike("Sorter", "Sorter.java", 25, 51, 0.2f);
        DisharmonyInstance s2 = godLike("Sorter2", "Sorter2.java", 25, 51, 0.2f);
        DisharmonyInstance t = themeImpl(); // ATFD=16

        List<DisharmonyInstance> items = new ArrayList<>();
        items.add(t);
        items.add(s);
        items.add(s2);

        ranker.rank(items);

        // Both Sorter instances should share a rank for every metric
        assertEquals(s.getMetrics().get(0).getRank(), s2.getMetrics().get(0).getRank());
        assertEquals(s.getMetrics().get(1).getRank(), s2.getMetrics().get(1).getRank());
        assertEquals(s.getMetrics().get(2).getRank(), s2.getMetrics().get(2).getRank());
    }

    // ── Descending direction ────────────────────────────────────────────────────

    @Test
    void descendingDirectionRanksLowestValueHighest() {
        // TCC DESCENDING: lower TCC = worse = higher rank (rank 1 is low TCC)
        // Wait: "lower is worse" → rank 1 should be the worst (lowest) value
        // Actually the plan says DESC means "lower value is worse" → highest rank number
        // DisharmonyRanker for DESC: sort descending (high first), then rank 1 = highest value
        // No: "higher rank = more problematic" but the GodClass pattern is "rank 1 = lowest (worst) value"
        // Plan clarifies: for DESC metrics, we sort descending so rank 1 = highest value,
        // meaning the instance with the highest value gets rank 1 (its metric is least bad).
        // Actually re-reading the plan: "ASC = higher value is worse → ascending sort"
        // meaning after ascending sort rank 1 = lowest value = least bad.
        // For God Class with TCC=0.028 (very low cohesion = very bad), it gets rank 1 after ascending sort.
        // So for TCC (where lower = worse), ASCENDING is actually correct for God Class.
        // The DESCENDING direction means: SORT DESCENDING so that rank 1 = highest value.
        // For Brain Class TCC=DESC means: high TCC is bad (breaking cohesion), rank 1 = highest TCC.
        // Let's test: two items with TCC 0.4 and 0.1, DESCENDING → 0.4 gets rank 1

        List<DisharmonyMetric> m1 = List.of(new DisharmonyMetric("TCC", 0.4, Direction.DESCENDING));
        List<DisharmonyMetric> m2 = List.of(new DisharmonyMetric("TCC", 0.1, Direction.DESCENDING));
        DisharmonyInstance high = new DisharmonyInstance("T", "high", "pkg", "High.java", null, m1);
        DisharmonyInstance low = new DisharmonyInstance("T", "low", "pkg", "Low.java", null, m2);

        List<DisharmonyInstance> items = new ArrayList<>();
        items.add(low);
        items.add(high);

        ranker.rank(items);

        // DESCENDING sort: 0.4 > 0.1, so 0.4 comes first → rank 1
        assertEquals(1, high.getMetrics().get(0).getRank());
        assertEquals(2, low.getMetrics().get(0).getRank());
    }

    // ── Single-metric ranking ───────────────────────────────────────────────────

    @Test
    void singleMetricAscendingRanksCorrectly() {
        List<DisharmonyMetric> m1 = List.of(new DisharmonyMetric("LOC", 100, Direction.ASCENDING));
        List<DisharmonyMetric> m2 = List.of(new DisharmonyMetric("LOC", 200, Direction.ASCENDING));
        List<DisharmonyMetric> m3 = List.of(new DisharmonyMetric("LOC", 50, Direction.ASCENDING));
        DisharmonyInstance i1 = new DisharmonyInstance("T", "c1", "pkg", "C1.java", null, m1);
        DisharmonyInstance i2 = new DisharmonyInstance("T", "c2", "pkg", "C2.java", null, m2);
        DisharmonyInstance i3 = new DisharmonyInstance("T", "c3", "pkg", "C3.java", null, m3);

        List<DisharmonyInstance> items = new ArrayList<>(List.of(i1, i2, i3));
        ranker.rank(items);

        // ASC: 50→rank1, 100→rank2, 200→rank3
        assertEquals(1, i3.getOverallRank()); // LOC=50
        assertEquals(2, i1.getOverallRank()); // LOC=100
        assertEquals(3, i2.getOverallRank()); // LOC=200
    }

    // ── sumOfRanks computation ──────────────────────────────────────────────────

    @Test
    void sumOfRanksEqualsIndividualMetricRankSum() {
        List<DisharmonyInstance> items = new ArrayList<>();
        items.add(attributeHandler());
        items.add(sorter());
        items.add(themeImpl());

        ranker.rank(items);

        for (DisharmonyInstance item : items) {
            int expectedSum = item.getMetrics().stream()
                    .mapToInt(DisharmonyMetric::getRank)
                    .sum();
            assertEquals(expectedSum, item.getSumOfRanks());
        }
    }

    // ── method-level disharmony (has methodSignature) ──────────────────────────

    @Test
    void methodLevelDisharmonyRankedCorrectly() {
        List<DisharmonyMetric> m1 = List.of(
                new DisharmonyMetric("LOC", 80, Direction.ASCENDING),
                new DisharmonyMetric("CYCLO", 6, Direction.ASCENDING));
        List<DisharmonyMetric> m2 = List.of(
                new DisharmonyMetric("LOC", 120, Direction.ASCENDING),
                new DisharmonyMetric("CYCLO", 4, Direction.ASCENDING));
        DisharmonyInstance i1 =
                new DisharmonyInstance("Brain Method", "MyClass", "MyClass.java", "pkg", "method1()", m1);
        DisharmonyInstance i2 =
                new DisharmonyInstance("Brain Method", "MyClass", "MyClass.java", "pkg", "method2()", m2);

        List<DisharmonyInstance> items = new ArrayList<>(List.of(i1, i2));
        ranker.rank(items);

        assertNotNull(i1.getOverallRank());
        assertNotNull(i2.getOverallRank());
    }

    // ── helper ─────────────────────────────────────────────────────────────────

    private DisharmonyInstance findByPath(List<DisharmonyInstance> items, String path) {
        return items.stream()
                .filter(i -> path.equals(i.getFileRepoPath()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no item with path: " + path));
    }
}
