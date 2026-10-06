package com.bank.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Декодирует payload JWT без проверки подписи (демонстрационный режим).
 */
public class JwtDecoder {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public JsonNode decodePayload(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return null;
        }
        byte[] payload = base64UrlDecode(parts[1]);
        if (payload == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(payload);
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] base64UrlDecode(String segment) {
        String value = segment.replace('-', '+').replace('_', '/');
        int pad = (4 - value.length() % 4) % 4;
        for (int i = 0; i < pad; i++) {
            value += '=';
        }
        try {
            return java.util.Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}