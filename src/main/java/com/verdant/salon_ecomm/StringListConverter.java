package com.verdant.salon_ecomm;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

@Converter
public class StringListConverter implements AttributeConverter<List, String> {

    @Override
    public String convertToDatabaseColumn(List list) {
        if (list == null) return "[]";
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(list);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List convertToEntityAttribute(String json) {
        if (json == null) return new ArrayList<>();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, List.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}