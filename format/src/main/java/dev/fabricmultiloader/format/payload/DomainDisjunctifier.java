package dev.fabricmultiloader.format.payload;

import dev.fabricmultiloader.format.error.ErrorCode;
import dev.fabricmultiloader.format.error.Messages;
import dev.fabricmultiloader.format.manifest.PayloadDescriptor;
import dev.fabricmultiloader.format.version.VersionRange;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns declared payload domains into provably disjoint effective ones.
 *
 * <p>Fabric's solver picks the payload, and its optimisation objective ("load as many and as new
 * mods as possible") is not a specified tie-break. If two payloads were satisfiable at once, which
 * one wins would be undefined — and FabricMultiLoader cannot arbitrate, because selection happens
 * before any mod code runs. Determinism therefore has to be established <em>before</em> the jar
 * exists.
 *
 * <p>That is what this class does. Payloads are processed in priority order, and each one keeps
 * only what higher-priority payloads have not already claimed. The result is written into the
 * generated {@code depends} entries, so by the time the loader sees them, at most one can match.
 * {@code priority} therefore survives as a convenient way to express "catch-all plus special case"
 * while remaining entirely a build-time concept.
 *
 * @see <a href="https://github.com/CptGummiball/fabricmultiloader/blob/main/docs/design/part-03-container-format.md">Chapter 12.7</a>
 */
public final class DomainDisjunctifier {

    private static final Comparator<PayloadDescriptor> BY_PRIORITY_THEN_ID =
            new Comparator<PayloadDescriptor>() {
                @Override
                public int compare(PayloadDescriptor left, PayloadDescriptor right) {
                    if (left.priority() != right.priority()) {
                        return right.priority() - left.priority();
                    }
                    return left.id().compareTo(right.id());
                }
            };

    /**
     * Computes the effective domain of every payload.
     *
     * @param payloads the declared payloads, in any order
     * @return the effective domains plus any problems found; never throws, so a caller can report
     *     every problem at once instead of one per build
     */
    public static Result disjunctify(List<PayloadDescriptor> payloads) {
        List<PayloadDescriptor> ordered = new ArrayList<PayloadDescriptor>(payloads);
        Collections.sort(ordered, BY_PRIORITY_THEN_ID);

        List<Problem> problems = new ArrayList<Problem>();
        Map<String, Domain> effective = new LinkedHashMap<String, Domain>();
        Domain claimed = Domain.EMPTY;

        for (int i = 0; i < ordered.size(); i++) {
            PayloadDescriptor payload = ordered.get(i);
            Domain declared = Domain.of(payload.requires());

            for (int j = 0; j < i; j++) {
                PayloadDescriptor earlier = ordered.get(j);
                if (earlier.priority() == payload.priority()
                        && Domain.of(earlier.requires()).intersects(declared)) {
                    problems.add(overlapAtEqualPriority(earlier, payload, declared));
                }
            }

            Domain remaining = declared.subtract(claimed);
            if (remaining.isEmpty()) {
                problems.add(fullyShadowed(payload));
            } else if (!remaining.isExpressibleAsRequirements()) {
                problems.add(notExpressible(payload, remaining));
            }
            effective.put(payload.id(), remaining);
            claimed = claimed.union(remaining);
        }
        return new Result(effective, problems);
    }

    private static Problem overlapAtEqualPriority(
            PayloadDescriptor earlier, PayloadDescriptor later, Domain laterDomain) {
        Domain overlap = Domain.of(earlier.requires()).intersect(laterDomain);
        String report = Messages.report(ErrorCode.OMNI_1010)
                .detected("payload", earlier.id() + "  " + earlier.requires())
                .detected("payload", later.id() + "  " + later.requires())
                .detected("priority", earlier.priority() + " (both)")
                .detected("overlap", overlap.toString())
                .detail("Both payloads can be selected in the environments above.")
                .detail("Fabric Loader does not define which one wins, so this build is rejected.")
                .fix("narrow one of the two ranges in gradle/fabricmultiloader.toml")
                .fix("or give one a higher priority, so the overlap is subtracted automatically")
                .build();
        return new Problem(ErrorCode.OMNI_1010, later.id(), report);
    }

    private static Problem fullyShadowed(PayloadDescriptor payload) {
        String report = Messages.report(ErrorCode.OMNI_1015)
                .detected("payload", payload.id())
                .detected("declared", payload.requires().toString())
                .detail("Higher-priority payloads already cover every environment this one declares,")
                .detail("so it could never be selected and would ship as dead weight.")
                .fix("remove the payload, or widen its range")
                .fix("or lower the priority of the payloads shadowing it")
                .build();
        return new Problem(ErrorCode.OMNI_1015, payload.id(), report);
    }

    private static Problem notExpressible(PayloadDescriptor payload, Domain remaining) {
        String report = Messages.report(ErrorCode.OMNI_1016)
                .detected("payload", payload.id())
                .detected("effective domain", remaining.toString())
                .detail("After subtracting higher-priority payloads, what remains needs different")
                .detail("Java ranges or sides for different Minecraft ranges. A Fabric depends")
                .detail("block cannot express that, and widening it would break disjointness.")
                .fix("split this payload into one per Java range or side")
                .fix("or make the priorities and ranges of the payloads involved line up")
                .build();
        return new Problem(ErrorCode.OMNI_1016, payload.id(), report);
    }

    private DomainDisjunctifier() {
        throw new AssertionError("no instances");
    }

    /** A problem found while making domains disjoint. */
    public static final class Problem {

        private final ErrorCode code;
        private final String payloadId;
        private final String report;

        Problem(ErrorCode code, String payloadId, String report) {
            this.code = code;
            this.payloadId = payloadId;
            this.report = report;
        }

        /** The diagnostic code. */
        public ErrorCode code() {
            return code;
        }

        /** The payload the problem is attributed to. */
        public String payloadId() {
            return payloadId;
        }

        /** The rendered report. */
        public String report() {
            return report;
        }

        @Override
        public String toString() {
            return code.id() + " (" + payloadId + ")";
        }
    }

    /** Effective domains plus any problems. */
    public static final class Result {

        private final Map<String, Domain> effective;
        private final List<Problem> problems;

        Result(Map<String, Domain> effective, List<Problem> problems) {
            this.effective = Collections.unmodifiableMap(effective);
            this.problems = Collections.unmodifiableList(problems);
        }

        /** Effective domain per payload id, in priority order. */
        public Map<String, Domain> effectiveDomains() {
            return effective;
        }

        /** The effective domain of one payload, or {@link Domain#EMPTY}. */
        public Domain effectiveDomain(String payloadId) {
            Domain domain = effective.get(payloadId);
            return domain == null ? Domain.EMPTY : domain;
        }

        /**
         * The Minecraft range to write into a payload's generated {@code depends}.
         *
         * <p>Only meaningful when there are no problems; a non-expressible domain would otherwise
         * be silently widened here, which is exactly the failure this class exists to prevent.
         */
        public VersionRange effectiveMinecraft(String payloadId) {
            return effectiveDomain(payloadId).minecraftUnion();
        }

        /** Every problem found, in payload order. */
        public List<Problem> problems() {
            return problems;
        }

        /** Whether the configuration is provably deterministic. */
        public boolean isValid() {
            return problems.isEmpty();
        }

        /**
         * Independent check that the computed domains really are pairwise disjoint.
         *
         * <p>Cheap, and it guards the one property everything else assumes. If the subtraction ever
         * had a bug, this would catch it at build time rather than on a player's machine.
         */
        public boolean areEffectiveDomainsDisjoint() {
            List<Domain> domains = new ArrayList<Domain>(effective.values());
            for (int i = 0; i < domains.size(); i++) {
                for (int j = i + 1; j < domains.size(); j++) {
                    if (domains.get(i).intersects(domains.get(j))) {
                        return false;
                    }
                }
            }
            return true;
        }
    }
}
