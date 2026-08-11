package dev.fabricmultiloader.format.json;

/**
 * A strict recursive-descent RFC 8259 parser.
 *
 * <p>Strict means: no comments, no trailing commas, no unquoted keys, no single quotes, no
 * {@code NaN} or {@code Infinity}, no leading zeroes, no trailing content. Manifests are machine
 * generated, so tolerance would only ever hide a generator bug — and a permissive parser is exactly
 * how a subtly wrong artifact reaches a player.
 *
 * <p>Every value records its line, column and JSON pointer as it is parsed, which is what lets a
 * diagnostic quote the offending source line with a caret while also naming the logical path.
 */
final class JsonReader {

    private final char[] input;
    private final JsonLimits limits;

    private int pos;
    private int line = 1;
    private int column = 1;
    private int depth;

    JsonReader(String text, JsonLimits limits) {
        if (text == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        this.limits = limits == null ? JsonLimits.DEFAULT : limits;
        if (text.length() > this.limits.maxDocumentChars()) {
            throw JsonMessages.limitExceeded(
                    "document size (characters)", this.limits.maxDocumentChars(), text.length(),
                    1, 1, JsonPointer.ROOT);
        }
        this.input = text.toCharArray();
    }

    JsonValue parseDocument() {
        skipWhitespace();
        if (isAtEnd()) {
            throw syntax("the document is empty");
        }
        JsonValue value = parseValue(JsonPointer.ROOT);
        skipWhitespace();
        if (!isAtEnd()) {
            throw syntax("unexpected trailing content after the top-level value");
        }
        return value;
    }

    // ------------------------------------------------------------------ values

    private JsonValue parseValue(String pointer) {
        JsonLocation start = here(pointer);
        char c = peek();
        switch (c) {
            case '{':
                return parseObject(pointer, start);
            case '[':
                return parseArray(pointer, start);
            case '"':
                return new JsonString(parseString(pointer), start);
            case 't':
                expectLiteral("true");
                return new JsonBool(true, start);
            case 'f':
                expectLiteral("false");
                return new JsonBool(false, start);
            case 'n':
                expectLiteral("null");
                return new JsonNull(start);
            default:
                if (c == '-' || (c >= '0' && c <= '9')) {
                    return new JsonNumber(parseNumber(), start);
                }
                throw syntax("expected a JSON value, found '" + describeChar(c) + "'");
        }
    }

    private JsonObject parseObject(String pointer, JsonLocation start) {
        enterContainer();
        JsonObject object = new JsonObject(start);
        advance(); // '{'
        skipWhitespace();
        if (peekOrThrow("'}' or a member key") == '}') {
            advance();
            depth--;
            return object;
        }
        int entries = 0;
        while (true) {
            skipWhitespace();
            if (peekOrThrow("a member key") != '"') {
                throw syntax("expected a quoted member key");
            }
            JsonLocation keyLocation = here(pointer);
            String key = parseString(pointer);
            if (object.has(key)) {
                throw syntaxAt(keyLocation, "duplicate member key \"" + key + "\"");
            }
            skipWhitespace();
            if (peekOrThrow("':'") != ':') {
                throw syntax("expected ':' after the member key");
            }
            advance();
            skipWhitespace();
            object.set(key, parseValue(JsonPointer.child(pointer, key)));

            if (++entries > limits.maxContainerEntries()) {
                throw limit("members in one object", limits.maxContainerEntries(), entries, pointer);
            }

            skipWhitespace();
            char next = peekOrThrow("',' or '}'");
            if (next == ',') {
                advance();
                skipWhitespace();
                if (peekOrThrow("a member key") == '}') {
                    throw syntax("trailing comma before '}'");
                }
                continue;
            }
            if (next == '}') {
                advance();
                depth--;
                return object;
            }
            throw syntax("expected ',' or '}' after a member");
        }
    }

    private JsonArray parseArray(String pointer, JsonLocation start) {
        enterContainer();
        JsonArray array = new JsonArray(start);
        advance(); // '['
        skipWhitespace();
        if (peekOrThrow("']' or a value") == ']') {
            advance();
            depth--;
            return array;
        }
        int index = 0;
        while (true) {
            skipWhitespace();
            array.add(parseValue(JsonPointer.index(pointer, index)));
            index++;

            if (index > limits.maxContainerEntries()) {
                throw limit("elements in one array", limits.maxContainerEntries(), index, pointer);
            }

            skipWhitespace();
            char next = peekOrThrow("',' or ']'");
            if (next == ',') {
                advance();
                skipWhitespace();
                if (peekOrThrow("a value") == ']') {
                    throw syntax("trailing comma before ']'");
                }
                continue;
            }
            if (next == ']') {
                advance();
                depth--;
                return array;
            }
            throw syntax("expected ',' or ']' after an element");
        }
    }

    private String parseString(String pointer) {
        advance(); // opening quote
        StringBuilder out = new StringBuilder();
        while (true) {
            if (isAtEnd()) {
                throw syntax("unterminated string — the document ends before the closing quote");
            }
            char c = peek();
            if (c == '"') {
                advance();
                if (out.length() > limits.maxStringLength()) {
                    throw limit("characters in one string", limits.maxStringLength(), out.length(), pointer);
                }
                return out.toString();
            }
            if (c == '\\') {
                advance();
                out.append(parseEscape());
                continue;
            }
            if (c < 0x20) {
                throw syntax("unescaped control character U+"
                        + String.format("%04X", (int) c) + " in a string");
            }
            out.append(c);
            advance();
            if (out.length() > limits.maxStringLength()) {
                throw limit("characters in one string", limits.maxStringLength(), out.length(), pointer);
            }
        }
    }

    private char parseEscape() {
        if (isAtEnd()) {
            throw syntax("the document ends inside an escape sequence");
        }
        char c = peek();
        advance();
        switch (c) {
            case '"':
                return '"';
            case '\\':
                return '\\';
            case '/':
                return '/';
            case 'b':
                return '\b';
            case 'f':
                return '\f';
            case 'n':
                return '\n';
            case 'r':
                return '\r';
            case 't':
                return '\t';
            case 'u':
                return parseUnicodeEscape();
            default:
                throw syntax("invalid escape sequence '\\" + describeChar(c) + "'");
        }
    }

    private char parseUnicodeEscape() {
        int value = 0;
        for (int i = 0; i < 4; i++) {
            if (isAtEnd()) {
                throw syntax("the document ends inside a \\u escape");
            }
            char c = peek();
            int digit = Character.digit(c, 16);
            if (digit < 0) {
                throw syntax("invalid hex digit '" + describeChar(c) + "' in a \\u escape");
            }
            value = (value << 4) | digit;
            advance();
        }
        return (char) value;
    }

    private String parseNumber() {
        int startPos = pos;
        if (peek() == '-') {
            advance();
        }
        if (isAtEnd()) {
            throw syntax("the document ends inside a number");
        }
        if (peek() == '0') {
            advance();
            if (!isAtEnd() && peek() >= '0' && peek() <= '9') {
                throw syntax("leading zeroes are not allowed in JSON numbers");
            }
        } else {
            requireDigit("a digit after '-'");
            while (!isAtEnd() && peek() >= '0' && peek() <= '9') {
                advance();
            }
        }
        if (!isAtEnd() && peek() == '.') {
            advance();
            requireDigit("a digit after the decimal point");
            while (!isAtEnd() && peek() >= '0' && peek() <= '9') {
                advance();
            }
        }
        if (!isAtEnd() && (peek() == 'e' || peek() == 'E')) {
            advance();
            if (!isAtEnd() && (peek() == '+' || peek() == '-')) {
                advance();
            }
            requireDigit("a digit in the exponent");
            while (!isAtEnd() && peek() >= '0' && peek() <= '9') {
                advance();
            }
        }
        return new String(input, startPos, pos - startPos);
    }

    private void requireDigit(String expectation) {
        if (isAtEnd() || peek() < '0' || peek() > '9') {
            throw syntax("expected " + expectation);
        }
        advance();
    }

    private void expectLiteral(String literal) {
        for (int i = 0; i < literal.length(); i++) {
            if (isAtEnd() || peek() != literal.charAt(i)) {
                throw syntax("expected the literal '" + literal + "'");
            }
            advance();
        }
    }

    // ------------------------------------------------------------------ scanning

    private void enterContainer() {
        if (++depth > limits.maxDepth()) {
            throw limit("nesting depth", limits.maxDepth(), depth, JsonPointer.ROOT);
        }
    }

    private void skipWhitespace() {
        while (!isAtEnd()) {
            char c = peek();
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                advance();
            } else {
                return;
            }
        }
    }

    private boolean isAtEnd() {
        return pos >= input.length;
    }

    private char peek() {
        return input[pos];
    }

    private char peekOrThrow(String expectation) {
        if (isAtEnd()) {
            throw syntax("the document ends where " + expectation + " was expected");
        }
        return input[pos];
    }

    private void advance() {
        char c = input[pos++];
        if (c == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }
    }

    private JsonLocation here(String pointer) {
        return new JsonLocation(line, column, pointer);
    }

    // ------------------------------------------------------------------ diagnostics

    private JsonFormatException syntax(String problem) {
        return JsonMessages.syntax(line, column, JsonPointer.ROOT, problem, sourceLine(line));
    }

    private JsonFormatException syntaxAt(JsonLocation location, String problem) {
        return JsonMessages.syntax(
                location.line(), location.column(), location.pointer(), problem,
                sourceLine(location.line()));
    }

    private JsonFormatException limit(String limitName, long max, long actual, String pointer) {
        return JsonMessages.limitExceeded(limitName, max, actual, line, column, pointer);
    }

    /** Extracts a source line for the caret display, truncating very long lines. */
    private String sourceLine(int wanted) {
        int currentLine = 1;
        int start = 0;
        for (int i = 0; i < input.length; i++) {
            if (currentLine == wanted && input[i] == '\n') {
                return truncate(new String(input, start, i - start));
            }
            if (input[i] == '\n') {
                currentLine++;
                start = i + 1;
            }
        }
        if (currentLine == wanted) {
            return truncate(new String(input, start, input.length - start));
        }
        return null;
    }

    private static String truncate(String text) {
        String stripped = text.replace("\r", "");
        return stripped.length() <= 160 ? stripped : stripped.substring(0, 157) + "...";
    }

    private static String describeChar(char c) {
        if (c < 0x20) {
            return "U+" + String.format("%04X", (int) c);
        }
        return String.valueOf(c);
    }
}
