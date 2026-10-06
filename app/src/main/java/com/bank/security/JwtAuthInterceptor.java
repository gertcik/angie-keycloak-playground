package com.bank.security;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class JwtAuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_USERNAME = "auth.username";
    public static final String ATTR_CLIENT_ID = "auth.clientId";
    public static final String ATTR_TOKEN = "auth.token";

    private final JwtDecoder jwtDecoder = new JwtDecoder();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return reject(response, "Missing Bearer token");
        }
        String token = header.substring("Bearer ".length()).trim();
        JsonNode payload = jwtDecoder.decodePayload(token);
        if (payload == null) {
            return reject(response, "Invalid JWT token");
        }
        String username = payload.hasNonNull("preferred_username")
                ? payload.get("preferred_username").asText()
                : payload.hasNonNull("sub") ? payload.get("sub").asText() : null;
        String clientId = payload.hasNonNull("client_id") ? payload.get("client_id").asText() : null;
        if (username == null) {
            return reject(response, "JWT has no username claim");
        }
        request.setAttribute(ATTR_USERNAME, username);
        request.setAttribute(ATTR_CLIENT_ID, clientId);
        request.setAttribute(ATTR_TOKEN, token);
        return true;
    }

    private boolean reject(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
        return false;
    }
}