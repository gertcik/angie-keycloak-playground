package com.bank.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Диагностические эндпоинты cookies/сессий (backlog: jsessionid-lost-basic-jwt).
 * <p>
 * Апстрим в остальном stateless; единственное место, где Tomcat выдаёт
 * {@code Set-Cookie: JSESSIONID}, — {@link #session(HttpServletRequest)}.
 * Нужен, чтобы e2e-тестами доказать: Cookie инициатора проходит через Angie
 * (Basic -> JWT) неизменной, сессия переживает запросы и никогда не стирается
 * (в ответе нет {@code Set-Cookie: JSESSIONID=""}).
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Diagnostics", description = "Cookie/session diagnostics (requires Bearer JWT from Keycloak)")
public class DiagnosticsController {

    @GetMapping("/cookies")
    @Operation(summary = "Echo received cookies",
            description = "Возвращает cookies ровно так, как их получил апстрим — проверка, что Angie (Basic -> JWT) не затирает Cookie")
    public Map<String, String> cookies(HttpServletRequest request) {
        Map<String, String> out = new LinkedHashMap<>();
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                out.put(c.getName(), c.getValue());
            }
        }
        return out;
    }

    @GetMapping("/session")
    @Operation(summary = "Create or touch an HTTP session",
            description = "Стейтфул-эндпоинт: создаёт HttpSession (Tomcat отдаёт Set-Cookie: JSESSIONID) и увеличивает счётчик. Проверка, что сессия, созданная через Angie, переживает Basic -> JWT запросы и не стирается")
    public Map<String, Object> session(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        Integer hits = (Integer) session.getAttribute("hits");
        hits = (hits == null) ? 1 : hits + 1;
        session.setAttribute("hits", hits);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sessionId", session.getId());
        out.put("new", session.isNew());
        out.put("hits", hits);
        return out;
    }
}
