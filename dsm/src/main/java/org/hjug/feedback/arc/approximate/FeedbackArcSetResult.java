package org.hjug.feedback.arc.approximate;

import java.util.List;
import java.util.Set;

/**
 * Result container for the Feedback Arc Set algorithm
 */
public class FeedbackArcSetResult<V, E> {
    private final List<V> vertexSequence;
    private final Set<E> feedbackArcs;

    public FeedbackArcSetResult(List<V> vertexSequence, Set<E> feedbackArcs) {
        this.vertexSequence = vertexSequence;
        this.feedbackArcs = feedbackArcs;
    }

    public List<V> getVertexSequence() {
        return vertexSequence;
    }

    public Set<E> getFeedbackArcs() {
        return feedbackArcs;
    }

    public int getFeedbackArcCount() {
        return feedbackArcs.size();
    }

    @Override
    public String toString() {
        return String.format(
                "FeedbackArcSetResult{vertexSequence=%s, feedbackArcCount=%d}", vertexSequence, feedbackArcs.size());
    }
}
