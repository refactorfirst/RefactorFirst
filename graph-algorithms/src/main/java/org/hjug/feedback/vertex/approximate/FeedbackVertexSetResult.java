package org.hjug.feedback.vertex.approximate;

import java.util.Set;

/**
 * Result container for the Feedback Vertex Set algorithm
 */
public class FeedbackVertexSetResult<V> {
    private final Set<V> feedbackVertices;

    public FeedbackVertexSetResult(Set<V> feedbackVertices) {
        this.feedbackVertices = feedbackVertices;
    }

    public Set<V> getFeedbackVertices() {
        return feedbackVertices;
    }

    public int size() {
        return feedbackVertices.size();
    }

    @Override
    public String toString() {
        return String.format(
                "FeedbackVertexSetResult{vertices=%s, size=%d}", feedbackVertices, feedbackVertices.size());
    }
}
