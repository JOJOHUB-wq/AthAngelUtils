package com.google.gson;

/** Minimal offline gson stub for the OCR benchmark harness. Not shipped. */
public final class JsonPrimitive extends JsonElement {
    private final String raw;

    JsonPrimitive(String raw) {
        this.raw = raw;
    }

    @Override
    public String getAsString() {
        return raw;
    }

    @Override
    public float getAsFloat() {
        return Float.parseFloat(raw);
    }
}
