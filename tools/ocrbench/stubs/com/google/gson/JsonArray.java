package com.google.gson;

import java.util.ArrayList;
import java.util.List;

/** Minimal offline gson stub for the OCR benchmark harness. Not shipped. */
public final class JsonArray extends JsonElement {
    final List<JsonElement> list = new ArrayList<>();

    public int size() {
        return list.size();
    }

    public JsonElement get(int i) {
        return list.get(i);
    }

    public void add(JsonElement e) {
        list.add(e);
    }
}
