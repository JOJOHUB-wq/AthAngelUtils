package com.google.gson;

/** Minimal offline gson stub for the OCR benchmark harness. Not shipped. */
public abstract class JsonElement {
    public boolean isJsonObject() {
        return this instanceof JsonObject;
    }

    public boolean isJsonArray() {
        return this instanceof JsonArray;
    }

    public boolean isJsonPrimitive() {
        return this instanceof JsonPrimitive;
    }

    public JsonObject getAsJsonObject() {
        return (JsonObject) this;
    }

    public JsonArray getAsJsonArray() {
        return (JsonArray) this;
    }

    public String getAsString() {
        throw new UnsupportedOperationException("not primitive: " + getClass().getSimpleName());
    }

    public float getAsFloat() {
        throw new UnsupportedOperationException("not primitive: " + getClass().getSimpleName());
    }
}
