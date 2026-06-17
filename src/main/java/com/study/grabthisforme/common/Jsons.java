package com.study.grabthisforme.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;

public final class Jsons {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private Jsons() {
    }

    public static String writeStringList(List<String> values) {
        try {
            return OBJECT_MAPPER.writeValueAsString(values == null ? Collections.emptyList() : values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize json", exception);
        }
    }

    public static List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<List<String>>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize json", exception);
        }
    }
}
