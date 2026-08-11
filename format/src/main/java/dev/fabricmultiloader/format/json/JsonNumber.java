package dev.fabricmultiloader.format.json;

/**
 * A JSON number, kept as the literal text from the source.
 *
 * <p>Storing the raw lexeme rather than a {@code double} is a reproducibility decision: the
 * container manifest must round-trip byte-for-byte through read → write → read, and converting
 * {@code 1.0} into a {@code double} and back would silently produce {@code 1.0} for some values and
 * {@code 1} for others depending on the platform's formatting. Manifests only ever contain small
 * integers, but the guarantee is worth more than the micro-optimisation.
 */
public final class JsonNumber extends JsonValue {

    private final String raw;

    JsonNumber(String raw, JsonLocation location) {
        super(location);
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("a JSON number must have a literal representation");
        }
        this.raw = raw;
    }

    /** Creates a constructed integral number. */
    public static JsonNumber of(long value) {
        return new JsonNumber(Long.toString(value), JsonLocation.UNKNOWN);
    }

    /** Creates a constructed floating-point number. */
    public static JsonNumber of(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("JSON cannot represent " + value);
        }
        return new JsonNumber(Double.toString(value), JsonLocation.UNKNOWN);
    }

    @Override
    public JsonType type() {
        return JsonType.NUMBER;
    }

    @Override
    public boolean isNumber() {
        return true;
    }

    /** The literal text as it appeared in the source, e.g. {@code "1.20e3"}. */
    public String raw() {
        return raw;
    }

    @Override
    String asRawNumber() {
        return raw;
    }

    /** Whether the literal has no fraction and no exponent. */
    public boolean isIntegral() {
        return raw.indexOf('.') < 0 && raw.indexOf('e') < 0 && raw.indexOf('E') < 0;
    }

    @Override
    public int asInt() {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw JsonMessages.numberNotIntegral(this, "int", e);
        }
    }

    @Override
    public long asLong() {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw JsonMessages.numberNotIntegral(this, "long", e);
        }
    }

    @Override
    public double asDouble() {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            throw JsonMessages.numberNotIntegral(this, "double", e);
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof JsonNumber && ((JsonNumber) other).raw.equals(raw);
    }

    @Override
    public int hashCode() {
        return raw.hashCode();
    }
}
