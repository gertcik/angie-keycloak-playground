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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * E2E-тесты поведения cookies/сессий при Basic -> JWT через Angie
 * (backlog: jsessionid-lost-basic-jwt).
 * <p>
 * Проверяемая гипотеза репортёра: «Angie, получая токен в Keycloak и подменяя
 * Authorization на Bearer, теряет/стирает JSESSIONID инициатора».
 * <p>
 * Фактически доказывается обратное (апстрим принимает Bearer и держит сессию):
 * <ol>
 *   <li>Cookie инициатора проходит через Angie до апстрима неизменной;</li>
 *   <li>новая сессия на апстриме выдаёт НЕпустой {@code Set-Cookie: JSESSIONID=...};</li>
 *   <li>сессия переживает повторные Basic-запросы и смену схемы Basic -> Bearer;</li>
 *   <li>ни в одном ответе нет стирания {@code JSESSIONID=""} / {@code Max-Age=0}.</li>
 * </ol>
 * Сценарий «стейтфул-апстрим сам гасит сессию из-за смены схемы» здесь
 * невоспроизводим: bank-app принимает Bearer (см. backlog, гипотеза 1 — вне стенда).
 * <p>
 * Требуют поднятого стенда; иначе пропускаются (Assumption).
 */
@Epic("Авторизация банковского API")
@Feature("JSESSIONID/cookies при Basic -> JWT через Angie")
@Tag("e2e")
class SessionCookieE2ETest {

    private static final String TEST_USER = "testuser";
    private static final String TEST_USER_PASSWORD = "testpass123";

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
        boolean angieUp = ping(angieBase + "/angie_status") == 200;
        boolean keycloakUp = ping(keycloakUrl + "/realms/bank/.well-known/openid-configuration") == 200;
        Assumptions.assumeTrue(angieUp && keycloakUp,
                "Живой стенд недоступен (Angie :82 / Keycloak :8081). Запустите docker compose up -d --build или start_all.cmd");
    }

    @Test
    @DisplayName("1. Cookie инициатора проходит через Angie (Basic -> JWT) до апстрима неизменной")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Cookie не теряется в цепочке Angie -> апстрим")
    void initiatorCookieReachesUpstreamUnchanged() {
        Map<String, String> headers = Map.of("Cookie", "JSESSIONID=test-session-1; X-Debug=probe");

        CallResult res = call("/api/cookies", basicAuth(), headers);

        assertEquals(200, res.code, "GET /api/cookies с Basic + Cookie должен вернуть 200");
        assertEquals("test-session-1", jsonField(res.body, "JSESSIONID"),
                "Cookie JSESSIONID инициатора должен дойти до апстрима неизменной");
        assertEquals("probe", jsonField(res.body, "X-Debug"), "Второй cookie тоже должен дойти");
        assertNoSessionCookieErasure(res.setCookie, "/api/cookies");
    }

    @Test
    @DisplayName("2. Новая сессия на апстриме: Tomcat выдаёт НЕпустой JSESSIONID, стирания нет")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Set-Cookie апстрима проходит через Angie без искажений")
    void newSessionGetsNonEmptySessionCookie() {
        CallResult res = call("/api/session", basicAuth(), null);

        assertEquals(200, res.code, "GET /api/session с Basic должен вернуть 200");
        assertTrue(nonBlank(jsonField(res.body, "sessionId")),
                "sessionId должен быть непустым: " + res.body);
        assertTrue(res.setCookie.stream().anyMatch(SessionCookieE2ETest::isNonEmptySessionCookie),
                "Ожидался Set-Cookie: JSESSIONID=<непустое значение>, получено: " + res.setCookie);
        assertNoSessionCookieErasure(res.setCookie, "/api/session");
    }

    @Test
    @DisplayName("3. Сессия переживает повторные Basic -> JWT запросы (та же сессия, счётчик растёт)")
    @Severity(SeverityLevel.CRITICAL)
    @Story("Сессия, созданная через Angie, не сбрасывается Basic -> JWT запросами")
    void sessionSurvivesRepeatedBasicRequests() {
        CallResult first = call("/api/session", basicAuth(), null);
        assertEquals(200, first.code);
        String sid = jsonField(first.body, "sessionId");
        int hitsFirst = Integer.parseInt(jsonField(first.body, "hits"));
        assertEquals("true", jsonField(first.body, "new"),
                "Запрос без Cookie должен создать новую сессию: " + first.body);
        assertEquals(1, hitsFirst, "Первая сессия должна иметь hits=1: " + first.body);

        CallResult second = call("/api/session", basicAuth(),
                Map.of("Cookie", "JSESSIONID=" + sid));
        assertEquals(200, second.code);
        assertEquals("false", jsonField(second.body, "new"),
                "Повторный запрос с валидным JSESSIONID не должен создавать новую сессию");
        assertEquals(sid, jsonField(second.body, "sessionId"),
                "sessionId должен совпадать — та же сессия");
        assertEquals(hitsFirst + 1, Integer.parseInt(jsonField(second.body, "hits")),
                "Счётчик сессии должен сохраниться (тот же session object): " + second.body);
        assertNoSessionCookieErasure(second.setCookie, "/api/session (повторный)");
    }

    @Test
    @DisplayName("4. Сессия переживает смену схемы у инициатора: Basic -> Bearer")
    @Severity(SeverityLevel.NORMAL)
    @Story("Подмена Authorization на Bearer не влияет на сессию апстрима")
    void sessionSurvivesSchemeSwitchBasicToBearer() {
        CallResult first = call("/api/session", basicAuth(), null);
        assertEquals(200, first.code);
        String sid = jsonField(first.body, "sessionId");
        int hitsFirst = Integer.parseInt(jsonField(first.body, "hits"));

        String token = accessToken(TEST_USER, TEST_USER_PASSWORD);
        CallResult second = call("/api/session", "Bearer " + token,
                Map.of("Cookie", "JSESSIONID=" + sid));
        assertEquals(200, second.code);
        assertEquals(sid, jsonField(second.body, "sessionId"),
                "Сессия должна пережить обращение с Bearer (инициатор сменил Basic -> Bearer)");
        assertEquals(hitsFirst + 1, Integer.parseInt(jsonField(second.body, "hits")));
        assertNoSessionCookieErasure(second.setCookie, "/api/session (Bearer)");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    static final class CallResult {
        final int code;
        final String body;
        final List<String> setCookie;

        CallResult(int code, String body, List<String> setCookie) {
            this.code = code;
            this.body = body;
            this.setCookie = setCookie;
        }
    }

    private static String basicAuth() {
        return "Basic " + Base64.getEncoder()
                .encodeToString((TEST_USER + ":" + TEST_USER_PASSWORD).getBytes(StandardCharsets.UTF_8));
    }

    private static CallResult call(String path, String authorization, Map<String, String> extraHeaders) {
        return Allure.step("GET " + path + (extraHeaders != null ? " (+Cookie)" : ""), () -> {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(angieBase + path))
                    .timeout(Duration.ofSeconds(15))
                    .GET();
            if (authorization != null) {
                builder.header("Authorization", authorization);
            }
            if (extraHeaders != null) {
                extraHeaders.forEach(builder::header);
            }
            HttpResponse<String> res = CLIENT.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            List<String> setCookie = res.headers().allValues("set-cookie");
            Allure.addAttachment("response-" + path, "application/json", res.body());
            Allure.addAttachment("set-cookie-" + path, "text/plain", String.join("\n", setCookie));
            return new CallResult(res.statusCode(), res.body(), setCookie);
        });
    }

    /** Проверка «пустого JSESSIONID» — симптома сброса сессии: пустое значение и/или Max-Age=0. */
    private static void assertNoSessionCookieErasure(List<String> setCookie, String where) {
        for (String value : setCookie) {
            if (!value.contains("JSESSIONID=")) {
                continue;
            }
            String cookieValue = value.substring(value.indexOf("JSESSIONID=") + "JSESSIONID=".length())
                    .split(";", 2)[0].trim().replace("\"", "");
            assertTrue(!cookieValue.isEmpty(),
                    "Пустое значение JSESSIONID (сброс сессии) в ответе " + where + ": " + value);
            assertTrue(!value.matches("(?i).*max-age\\s*=\\s*0.*"),
                    "Max-Age=0 на JSESSIONID (сброс сессии) в ответе " + where + ": " + value);
        }
    }

    private static boolean isNonEmptySessionCookie(String value) {
        return value != null && value.matches("(?s).*JSESSIONID\\s*=\\s*[^;\"].*");
    }

    private static String jsonField(String json, String field) {
        try {
            JsonNode node = MAPPER.readTree(json).get(field);
            return node == null ? null : node.asText();
        } catch (Exception e) {
            throw new IllegalStateException("Неожиданное тело ответа: " + json, e);
        }
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.isBlank();
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
            return MAPPER.readTree(res.body()).get("access_token").asText();
        });
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

    private static String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
