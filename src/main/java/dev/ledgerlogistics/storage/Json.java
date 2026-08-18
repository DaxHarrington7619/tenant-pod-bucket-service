package dev.ledgerlogistics.storage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Json {
    private Json() {}

    static Object parse(String source) {
        Parser parser = new Parser(source);
        Object value = parser.value();
        parser.space();
        if (!parser.done()) throw new IllegalArgumentException("Trailing JSON content");
        return value;
    }

    static String string(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '\"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('\"').toString();
    }

    private static final class Parser {
        private final String source;
        private int at;

        private Parser(String source) { this.source = source; }
        private boolean done() { return at == source.length(); }
        private void space() { while (!done() && Character.isWhitespace(source.charAt(at))) at++; }

        private Object value() {
            space();
            if (done()) throw new IllegalArgumentException("Empty JSON");
            return switch (source.charAt(at)) {
                case '{' -> object();
                case '[' -> array();
                case '\"' -> text();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            Map<String, Object> result = new LinkedHashMap<>();
            at++;
            space();
            if (take('}')) return result;
            do {
                space();
                String key = text();
                space();
                expect(':');
                result.put(key, value());
                space();
            } while (take(','));
            expect('}');
            return result;
        }

        private List<Object> array() {
            List<Object> result = new ArrayList<>();
            at++;
            space();
            if (take(']')) return result;
            do {
                result.add(value());
                space();
            } while (take(','));
            expect(']');
            return result;
        }

        private String text() {
            expect('\"');
            StringBuilder out = new StringBuilder();
            while (!done()) {
                char c = source.charAt(at++);
                if (c == '\"') return out.toString();
                if (c != '\\') { out.append(c); continue; }
                if (done()) throw new IllegalArgumentException("Bad JSON escape");
                char escaped = source.charAt(at++);
                switch (escaped) {
                    case '\"', '\\', '/' -> out.append(escaped);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (at + 4 > source.length()) throw new IllegalArgumentException("Bad unicode escape");
                        out.append((char) Integer.parseInt(source.substring(at, at + 4), 16));
                        at += 4;
                    }
                    default -> throw new IllegalArgumentException("Bad JSON escape");
                }
            }
            throw new IllegalArgumentException("Unclosed JSON string");
        }

        private Object number() {
            int start = at;
            while (!done() && "-+0123456789.eE".indexOf(source.charAt(at)) >= 0) at++;
            String token = source.substring(start, at);
            try {
                return token.contains(".") || token.contains("e") || token.contains("E")
                        ? Double.valueOf(token) : Long.valueOf(token);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Bad JSON number", e);
            }
        }

        private Object literal(String token, Object value) {
            if (!source.startsWith(token, at)) throw new IllegalArgumentException("Bad JSON literal");
            at += token.length();
            return value;
        }

        private boolean take(char expected) {
            if (!done() && source.charAt(at) == expected) { at++; return true; }
            return false;
        }

        private void expect(char expected) {
            if (!take(expected)) throw new IllegalArgumentException("Expected " + expected);
        }
    }
}
