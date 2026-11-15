# Теряется JSESSIONID при Basic-авторизации через Angie (Basic → JWT)

**Статус:** ✅ РЕШЕНО (2026-11-13, тест + воспроизведение + анализ + фикс; сценарий «сервис гасит сессию сам» — вне стенда, задокументирован)

## Итог: Angie не теряет и не стирает JSESSIONID; «пустой JSESSIONID» в этом стенде невозможен

Вся цепочка **stateless**: `bank-app` не создаёт HTTP-сессий (в `app/src` ноль упоминаний session/cookie, нет Spring Security — только кастомный `JwtAuthInterceptor`), Angie/njs cookie не трогает. Tomcat эмитит `Set-Cookie: JSESSIONID` только при создании сессии — кода, создающего сессию, в стенде нет (добавлен диагностический `/api/session`).

**Ответ на вопрос «должен ли JSESSIONID возвращаться»: нет вообще** — ни настоящий, ни пустой. Наблюдаемый у репортёра пустой `JSESSIONID` генерируется их стейтфул Java-сервисом: сервис ждёт `Basic`, а апстрим получает `Bearer` (Angie подменяет заголовок) → auth-фильтр не распознаёт схему → сессия инвалидируется → стандартный паттерн стирания `Set-Cookie: JSESSIONID=""; Max-Age=0`. Это не потеря cookie, а активный сброс сессии апстримом; поездка в Keycloak тут ни при чём (заголовки ответа subrequest `/_jwt` отбрасываются, Keycloak-ответ не может внедрить `Set-Cookie` в основной ответ).

## Что сделано

### 1. Тесты
- **E2E** `app/src/test/java/com/bank/e2e/SessionCookieE2ETest.java` (тэг `@Tag("e2e")`, 4 сценария, Allure):
  1. Cookie инициатора (`JSESSIONID=test-session-1; X-Debug=probe`) с Basic проходит через Angie до апстрима неизменной (`/api/cookies` эхо);
  2. новая сессия (`/api/session`) отдаёт НЕпустой `Set-Cookie: JSESSIONID=...`;
  3. сессия переживает повторные Basic→JWT запросы (та же `sessionId`, `hits` растёт, `new:false`);
  4. сессия переживает смену схемы Basic → Bearer.
  Во всех сценариях ассерт `assertNoSessionCookieErasure`: нет `JSESSIONID=""` и нет `Max-Age=0` на JSESSIONID.
- **Результат:** `e2eTest` = 10/10 PASSED (4 новых + 6 старых `AuthFlowsE2ETest`). Юнит `test` — BUILD SUCCESSFUL. Allure: web (`app/build/reports/allure-report/allureReport`) + standalone (`app/build/reports/allure-report-standalone/index.html`).

### 2. Воспроизведение (curl против живого стенда)
```
1) Basic + Cookie: JSESSIONID=manual-test-9 → GET /api/cookies
   → HTTP 200, {"JSESSIONID":"manual-test-9"}, Set-Cookie НЕТ (ничего не стирается)
2) Basic → GET /api/session
   → HTTP 200, Set-Cookie: JSESSIONID=214E6DE1...; Path=/; HttpOnly  (непустой)
   → {"sessionId":"214E6DE1...","new":true,"hits":1}
3) Basic + Cookie: JSESSIONID=214E6DE1... → GET /api/session
   → HTTP 200, Set-Cookie НЕТ (стирания нет), {"sessionId":"214E6DE1...","new":false,"hits":2}
```
Ожидание задачи «воспроизвести потерю» НЕ подтвердилось: cookie не теряется ни в запросе, ни в ответе; сессия живёт.

### 3. Анализ гипотез (из постановки)
| Гипотеза | Вердикт |
|----------|---------|
| 1. Апстрим сам гасит сессию из-за смены `Basic`→`Bearer` | **Подтверждена как объяснение внешнего отчёта** — но вне стенда: bank-app принимает Bearer, сценарий здесь невоспроизводим; устраняется на стороне сервиса (принимать Bearer) |
| 2. Cookie уходит в Keycloak через `/_kc` | **Подтверждена конструктивно** (subrequest наследует заголовки родителя) — устранена фиксом |
| 3. `Set-Cookie` апстрима искажается прокси | **Опровергнута**: непустой JSESSIONID дошёл до клиента без искажений (e2e №2) |
| 4. `auth_request` портит ответ | **Опровергнута**: e2e прошёл с auth_request; заголовки subrequest отбрасываются, внедрить `Set-Cookie` в основной ответ он не может |

### 4. Фикс (устранение)
- `docker/angie/conf.d/default.conf`, `location = /_kc`: добавлен `proxy_set_header Cookie "";` — subrequest иначе наследовал бы cookie инициатора (`JSESSIONID`) и отправлял его в Keycloak (утечка в IdP). Password grant после фикса работает (smoke: Basic → 200, неверный пароль → 401).

## Изменённые файлы
- `app/src/main/java/com/bank/controller/DiagnosticsController.java` — **новый**: `GET /api/cookies` (эхо cookies), `GET /api/session` (создаёт HttpSession + счётчик; единственный источник `JSESSIONID` в стенде)
- `app/src/test/java/com/bank/e2e/SessionCookieE2ETest.java` — **новый** (4 e2e-сценария)
- `docker/angie/conf.d/default.conf` — `/_kc`: `proxy_set_header Cookie "";`
- `README.md` — API-таблица (+2 эндпоинта), описание `/_kc`, раздел «Важные ограничения» (+cookies/сессии)
- `AGENTS.md` — gotchas про Cookie/`/_kc`/диагностику

## Запуск
```powershell
cd app
.\gradlew.bat e2eTest --no-daemon     # нужен поднятый стенд (Angie :82 + Keycloak :8081)
```
Ручная проверка: `curl.exe -s -i -u testuser:testpass123 -H "Cookie: JSESSIONID=abc" http://localhost:82/api/cookies`
