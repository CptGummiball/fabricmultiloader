package dev.fabricmultiloader.format.payload;

import dev.fabricmultiloader.format.manifest.PayloadDescriptor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Whether one payload applies to one environment, and if not, every reason it does not.
 *
 * <p>All failing constraints are collected rather than short-circuiting at the first. A user whose
 * Fabric API <em>and</em> Cloth Config are both too old should be told both at once; making them
 * launch three times to discover three problems is the failure mode this whole diagnostic layer
 * exists to avoid.
 */
public final class MatchResult {

    private final PayloadDescriptor payload;
    private final List<Rejection> rejections;

    private MatchResult(PayloadDescriptor payload, List<Rejection> rejections) {
        this.payload = payload;
        this.rejections = Collections.unmodifiableList(new ArrayList<Rejection>(rejections));
    }

    /** The payload applies. */
    public static MatchResult matched(PayloadDescriptor payload) {
        return new MatchResult(payload, Collections.<Rejection>emptyList());
    }

    /** The payload does not apply, for the given reasons. */
    public static MatchResult rejected(PayloadDescriptor payload, List<Rejection> rejections) {
        if (rejections.isEmpty()) {
            throw new IllegalArgumentException("a rejection must state at least one reason");
        }
        return new MatchResult(payload, rejections);
    }

    /** The payload this result is about. */
    public PayloadDescriptor payload() {
        return payload;
    }

    /** Whether the payload applies. */
    public boolean isMatch() {
        return rejections.isEmpty();
    }

    /** Every failed requirement, in evaluation order; empty on a match. */
    public List<Rejection> rejections() {
        return rejections;
    }

    /**
     * Whether the payload failed on a domain constraint rather than a filter.
     *
     * <p>Drives the wording of a diagnostic: a Minecraft or Java mismatch means "this build does not
     * cover your setup", whereas a filter failure means "your setup is close, fix this one thing".
     */
    public boolean failedOnDomain() {
        for (Rejection rejection : rejections) {
            switch (rejection.constraint()) {
                case MINECRAFT:
                case JAVA:
                case ENVIRONMENT:
                    return true;
                default:
                    break;
            }
        }
        return false;
    }

    /** The first rejection, or {@code null} on a match. Convenience for concise log lines. */
    public Rejection primaryRejection() {
        return rejections.isEmpty() ? null : rejections.get(0);
    }

    @Override
    public String toString() {
        return payload.id() + (isMatch() ? " matched" : " rejected: " + rejections);
    }
}
