package org.hjug.feedback.arc.exact;

import java.util.Set;

/**
 * Result container for the minimum feedback arc set algorithm [2]
 */
public class FeedbackArcSetResult<V, E> {
    private final Set<E> feedbackArcSet;
    private final double objectiveValue;

    public FeedbackArcSetResult(Set<E> feedbackArcSet, double objectiveValue) {
        this.feedbackArcSet = feedbackArcSet;
        this.objectiveValue = objectiveValue;
    }

    public Set<E> getFeedbackArcSet() {
        return feedbackArcSet;
    }

    public double getObjectiveValue() {
        return objectiveValue;
    }

    public int size() {
        return feedbackArcSet.size();
    }

    @Override
    public String toString() {
        return String.format(
                "FeedbackArcSetResult{arcSet=%s, objective=%.2f, size=%d}",
                feedbackArcSet, objectiveValue, feedbackArcSet.size());
    }
}
