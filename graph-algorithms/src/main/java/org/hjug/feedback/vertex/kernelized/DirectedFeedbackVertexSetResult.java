package org.hjug.feedback.vertex.kernelized;

import java.util.Set;

/**
 * Result container for the Directed Feedback Vertex Set algorithm[1]
 */
public class DirectedFeedbackVertexSetResult<V> {
    private final Set<V> feedbackVertices;

    public DirectedFeedbackVertexSetResult(Set<V> feedbackVertices) {
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
                "DirectedFeedbackVertexSetResult{vertices=%s, size=%d}", feedbackVertices, feedbackVertices.size());
    }
}
