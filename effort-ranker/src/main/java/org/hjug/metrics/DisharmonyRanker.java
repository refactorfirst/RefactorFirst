package org.hjug.metrics;

import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.hjug.graphbuilder.metrics.DisharmonyMetric;
import org.hjug.graphbuilder.metrics.DisharmonyMetric.Direction;

/**
 * Generic ranker for any disharmony type.
 *
 * <p>Algorithm (identical to the original GodClassRanker per-metric logic):
 * <ol>
 *   <li>For each metric in declaration order, sort the list by that metric's value in the metric's
 *       direction, then assign ordinal ranks with ties sharing the same rank.</li>
 *   <li>Sum the per-metric ranks for each item.</li>
 *   <li>Sort by the sum and assign the overall rank, again with ties shared.</li>
 * </ol>
 */
@Slf4j
public class DisharmonyRanker {

    public void rank(List<DisharmonyInstance> items) {
        if (items.isEmpty()) {
            return;
        }

        int metricCount = items.get(0).getMetrics().size();
        for (int i = 0; i < metricCount; i++) {
            final int idx = i;
            DisharmonyMetric sample = items.get(0).getMetrics().get(idx);
            log.info("Ranking metric: {}", sample.getName());

            Comparator<DisharmonyInstance> comparator = Comparator.comparingDouble(
                    item -> item.getMetrics().get(idx).getValue());
            if (sample.getDirection() == Direction.DESCENDING) {
                comparator = comparator.reversed();
            }
            items.sort(comparator);

            assignRanks(items, item -> item.getMetrics().get(idx).getValue(), (item, rank) -> item.getMetrics()
                    .get(idx)
                    .setRank(rank));
        }

        computeOverallRank(items);
    }

    private void computeOverallRank(List<DisharmonyInstance> items) {
        items.forEach(item -> {
            int sum = item.getMetrics().stream()
                    .mapToInt(DisharmonyMetric::getRank)
                    .sum();
            item.setSumOfRanks(sum);
        });

        items.sort(Comparator.comparingInt(DisharmonyInstance::getSumOfRanks));

        assignRanks(items, item -> (double) item.getSumOfRanks(), DisharmonyInstance::setOverallRank);
    }

    @FunctionalInterface
    private interface ValueExtractor<T> {
        double apply(T item);
    }

    @FunctionalInterface
    private interface RankSetter<T> {
        void set(T item, int rank);
    }

    private <T> void assignRanks(List<T> items, ValueExtractor<T> getter, RankSetter<T> setter) {
        int rank = 1;
        Double previousValue = null;

        for (T item : items) {
            double value = getter.apply(item);
            if (previousValue == null) {
                previousValue = value;
            }

            // Rank increments whenever the value changes (works for both ASC and DESC sorts).
            // Ties share a rank; the next distinct value gets the next rank number.
            if (Double.compare(value, previousValue) != 0) {
                rank++;
                previousValue = value;
            }
            setter.set(item, rank);
        }
    }
}
