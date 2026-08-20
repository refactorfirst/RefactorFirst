package org.hjug.graphbuilder.visitor.testclasses.anonymous;

/**
 * Fixture for the anonymous/synthetic rendering feature: an anonymous inner class
 * ({@code new Runnable() { ... }}) serialises as {@code AnonymousOwner$1} under OpenRewrite's
 * Java type attribution. Such {@code Outer$<digit>} FQNs are now <em>first-class graph members</em>
 * (they can contain antipatterns) and are rendered with {@code $} as the enclosing-class
 * separator. The owner below depends on {@link AnonymousTarget}; the anonymous classes
 * themselves ({@code AnonymousOwner$1} / {@code AnonymousOwner$2}) must appear as vertices in
 * the resulting graph.
 */
public class AnonymousOwner {

    private final AnonymousTarget target = new AnonymousTarget();

    public Runnable createAnonymousRunnable() {
        // anonymous inner class -> AnonymousOwner$1
        return new Runnable() {
            @Override
            public void run() {
                System.out.println(target.runIt());
            }
        };
    }

    public void useClashingAnonymousSubclass() {
        // anonymous subclass of a concrete type -> AnonymousOwner$2
        AnonymousTarget anon = new AnonymousTarget() {
            @Override
            public String runIt() {
                return "from-anonymous";
            }
        };
        anon.runIt();
    }
}
