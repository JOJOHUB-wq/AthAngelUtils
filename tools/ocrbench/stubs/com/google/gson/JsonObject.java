package com.google.gson;

import java.util.LinkedHashMap;
import java.util.Map;

/** Minimal offline gson stub for the OCR benchmark harness. Not shipped. */
public final class JsonObject extends JsonElement {
    final Map<String, JsonElement> map = new LinkedHashMap<>();

    public JsonElement get(String key) {
        return map.get(key);
    }

    public JsonArray getAsJsonArray(String key) {
        return (JsonArray) map.get(key);
    }

    public JsonObject getAsJsonObject(String key) {
        return (JsonObject) map.get(key);
    }

    public void add(String key, JsonElement value) {
        map.put(key, value);
    }
}
