package com.google.gson;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;

/** Minimal offline gson stub (recursive-descent JSON parser) for the OCR benchmark. Not shipped. */
public final class JsonParser {

    private JsonParser() {
    }

    public static JsonElement parseReader(Reader reader) {
        try {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = reader.read(buf)) > 0) {
                sb.append(buf, 0, n);
            }
            Parser p = new Parser(sb.toString());
            JsonElement e = p.parseValue();
            p.skipWs();
            return e;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        void skipWs() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        JsonElement parseValue() {
            skipWs();
            char c = s.charAt(i);
            if (c == '{') {
                return parseObject();
            }
            if (c == '[') {
                return parseArray();
            }
            if (c == '"') {
                return new JsonPrimitive(parseString());
            }
            if (c == 't' || c == 'f') {
                boolean t = c == 't';
                i += t ? 4 : 5;
                return new JsonPrimitive(String.valueOf(t));
            }
            if (c == 'n') {
                i += 4;
                return new JsonPrimitive("null");
            }
            return parseNumber();
        }

        private JsonObject parseObject() {
            JsonObject o = new JsonObject();
            i++; // {
            skipWs();
            if (s.charAt(i) == '}') {
                i++;
                return o;
            }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                i++; // :
                JsonElement v = parseValue();
                o.add(key, v);
                skipWs();
                char c = s.charAt(i++);
                if (c == '}') {
                    return o;
                }
            }
        }

        private JsonArray parseArray() {
            JsonArray a = new JsonArray();
            i++; // [
            skipWs();
            if (s.charAt(i) == ']') {
                i++;
                return a;
            }
            while (true) {
                a.add(parseValue());
                skipWs();
                char c = s.charAt(i++);
                if (c == ']') {
                    return a;
                }
            }
        }

        private String parseString() {
            StringBuilder sb = new StringBuilder();
            i++; // "
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n' -> sb.append('\n');
                        case 't' -> sb.append('\t');
                        case 'r' -> sb.append('\r');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> {
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                        }
                        default -> sb.append(e);
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        private JsonPrimitive parseNumber() {
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
                i++;
            }
            return new JsonPrimitive(s.substring(start, i));
        }
    }
}
