package org.hjug.graphbuilder.metrics;

import static org.junit.jupiter.api.Assertions.*;

import org.hjug.graphbuilder.metrics.DisharmonyMetric.Direction;
import org.junit.jupiter.api.Test;

class DisharmonyMetricTest {

    @Test
    void storesNameValueAndDirection() {
        DisharmonyMetric metric = new DisharmonyMetric("WMC", 47.0, Direction.ASCENDING);

        assertEquals("WMC", metric.getName());
        assertEquals(47.0, metric.getValue());
        assertEquals(Direction.ASCENDING, metric.getDirection());
        assertNull(metric.getRank());
    }

    @Test
    void rankIsSettableAfterConstruction() {
        DisharmonyMetric metric = new DisharmonyMetric("TCC", 0.25, Direction.DESCENDING);
        metric.setRank(3);

        assertEquals(3, metric.getRank());
    }

    @Test
    void descendingDirectionSupported() {
        DisharmonyMetric metric = new DisharmonyMetric("LAA", 0.1, Direction.DESCENDING);
        assertEquals(Direction.DESCENDING, metric.getDirection());
    }
}
