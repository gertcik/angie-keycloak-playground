package com.bank.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * E2E-тесты двух вариантов подключения к API через Angie.
 * <ul>
 *   <li>Вариант 1: {@code Authorization: Bearer <JWT>} — токен берётся из Keycloak (password grant).</li>
 *   <li>Вариант 2: {@code Authorization: Basic base64(login:password)} — Angie (njs) сама получает JWT
 *       из Keycloak, кэширует в shared-словаре и подкладывает Bearer.</li>
 * </ul>
 * Требуют поднятого стенда: {@code docker compose up -d --build} (или {@code start_all.cmd}).
 * Если стенд недоступен, тесты пропускаются (Assumption).
 */
@Epic("Авторизация банковского API")
@Feature("Подключение к /api/** через Angie")
@Tag("e2e")
class AuthFlowsE2ETest {

    private static final String ANGIe_PING = "/angie_status";

    private static final String TEST_USER = "testuser";
    private static final String TEST_USER_PASSWORD = "testpass123";
    private static final String PETROVA = "petrova";
    private static final String PETROVA_PASSWORD = "password456";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String angieBase;
    private static String keycloakUrl;

    @BeforeAll
    static void requireLiveStack() {
        angieBase = System.getProperty("e2e.angie.base", "http://localhost:82");
        keycloakUrl = System.getProperty("e2e.keycloak.url", "http://localhost:8081");
        boolean angieUp = ping(angieBase + ANGIe_PING) == 200;
        boolean keycloakUp = ping(keycloakUrl + "/realms/bank/.well-known/openid-configuration") == 200;
        Assumptions.assumeTrue(angieUp && keycloakUp,
                "Живой стенд недоступен (Angie :82 / Keycloak :8081). Запустите docker compose up -d --build или start_all.cmd");
    }

    // ------------------------------------------------------------------
    // Вариант 1 — подключение к API с JWT (Bearer из Keycloak)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("1. JWT: все /api/** отвечают 200 и читают пользователя из Bearer")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Вариант 1: подключение к API с JWT (Bearer)")
    void jwtBearerWorks() throws Exception {
        String token = accessToken(TEST_USER, TEST_USER_PASSWORD);

        CallResult me = call("/api/me", "Bearer " + token);
        assertEquals(200, me.code, "GET /api/me должен вернуть 200 с валидным JWT");
        assertJsonField(me.body, "username", TEST_USER);
        assertJsonField(me.body, "clientId", "1");

        assertEquals(200, call("/api/client/1", "Bearer " + token).code, "GET /api/client/1");
        assertEquals(200, call("/api/account/1", "Bearer " + token).code, "GET /api/account/1");
        assertEquals(200, call("/api/client/1/accounts", "Bearer " + token).code, "GET /api/client/1/accounts");
    }

    @Test
    @DisplayName("1. JWT: второй пользователь (petrova) отдаёт clientId=2")
    @Severity(SeverityLevel.NORMAL)
    @Story("Вариант 1: подключение к API с JWT (Bearer)")
    void jwtBearerSecondUserHasClientTwo() throws Exception {
        String token = accessToken(PETROVA, PETROVA_PASSWORD);

        CallResult me = call("/api/me", "Bearer " + token);
        assertEquals(200, me.code);
        assertJsonField(me.body, "username", PETROVA);
        assertJsonField(me.body, "clientId", "2");
    }

    // ------------------------------------------------------------------
    // Вариант 2 — подключение к API с Basic (Angie -> Keycloak -> JWT)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("2. Basic: Angie конвертирует логин/пароль в JWT и отвечает 200")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Вариант 2: подключение к API с Basic (JWT получает Angie через Keycloak)")
    void basicAuthWorks() throws Exception {
        String basic = "Basic " + base64(TEST_USER + ":" + TEST_USER_PASSWORD);

        CallResult me = call("/api/me", basic);
        assertEquals(200, me.code, "GET /api/me должен вернуть 200 с корректными Basic-учётными данными");
        assertJsonField(me.body, "username", TEST_USER);
        assertJsonField(me.body, "clientId", "1");

        assertEquals(200, call("/api/client/1", basic).code, "GET /api/client/1 (Basic)");
    }

    @Test
    @DisplayName("2. Basic: повторный запрос обслуживается из кэша Angie и тоже 200")
    @Severity(SeverityLevel.NORMAL)
    @Story("Вариант 2: подключение к API с Basic (JWT получает Angie через Keycloak)")
    void basicAuthCachedSecondRequestWorks() throws Exception {
        String basic = "Basic " + base64(TEST_USER + ":" + TEST_USER_PASSWORD);

        // Прогрев кэша
        assertEquals(200, call("/api/me", basic).code);
        // Повторный запрос — токен берётся из ngx.shared.jwt_cache, Keycloak не дёргается
        assertEquals(200, call("/api/account/1", basic).code, "повторный запрос с Basic (кэш)");
        assertEquals(200, call("/api/client/1/accounts", basic).code, "повторный запрос с Basic (кэш)");
    }

    @Test
    @DisplayName("2. Basic: неверный пароль → 401")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Вариант 2: подключение к API с Basic (JWT получает Angie через Keycloak)")
    void basicWrongPasswordIs401() {
        // petrova не закэширована ранее — Keycloak должен отвергнуть пароль
        CallResult res = call("/api/account/1", "Basic " + base64(PETROVA + ":WRONG"));
        assertEquals(401, res.code, "Неверные Basic-учётные данные должны давать 401");
    }

    // ------------------------------------------------------------------
    // Негативный сценарий (обе схемы)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("0. Без Authorization вообще → 401")
    @Severity(SeverityLevel.NORMAL)
    @Story("Общий негативный сценарий")
    void missingAuthIs401() {
        assertEquals(401, call("/api/me", null).code);
        assertEquals(401, call("/api/account/1", null).code);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    static final class CallResult {
        final int code;
        final String body;

        CallResult(int code, String body) {
            this.code = code;
            this.body = body;
        }
    }

    private static int ping(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            return CLIENT.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (Exception e) {
            return -1;
        }
    }

    private static CallResult call(String path, String authorization) {
        return Allure.step("GET " + path, () -> {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(angieBase + path))
                    .timeout(Duration.ofSeconds(15))
                    .GET();
            if (authorization != null) {
                builder.header("Authorization", authorization);
            }
            HttpResponse<String> res = CLIENT.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            Allure.addAttachment("response-" + path, "application/json", res.body());
            return new CallResult(res.statusCode(), res.body());
        });
    }

    private static String accessToken(String username, String password) {
        return Allure.step("Получить access token из Keycloak (password grant, " + username + ")", () -> {
            String body = "grant_type=password&client_id=bank-web"
                    + "&username=" + urlEncode(username)
                    + "&password=" + urlEncode(password);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(keycloakUrl + "/realms/bank/protocol/openid-connect/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> res = CLIENT.send(req,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(200, res.statusCode(), "Keycloak token endpoint должен вернуть 200: " + res.body());
            Allure.addAttachment("keycloak-token-response-" + username, "application/json", res.body());
            return MAPPER.readTree(res.body()).get("access_token").asText();
        });
    }

    private static void assertJsonField(String json, String field, String expected) throws Exception {
        JsonNode node = MAPPER.readTree(json).get(field);
        assertTrue(node != null, "В ответе должен быть JSON-поле '" + field + "': " + json);
        assertEquals(expected, node.asText(), "Значение поля '" + field + "'");
    }

    private static String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}