# Bank API - Agent Instructions

Educational Spring Boot API load-balanced by a single Angie proxy (all other balancers — Apache, NGINX, HAProxy — were removed). Keycloak lives in `docker/` (OIDC IdP + njs Basic→JWT bridge in Angie), not in the Spring code.

## Build & Test

- Java 21 + Gradle 8.7 (wrapper included). `jdk-21.0.2/` is **gitignored and often absent** — `java` is not on PATH in a fresh clone. Install any JDK 21 first (or set `JAVA_HOME` to a local copy).
- Required persistence environment vars (already set as User vars on this machine): `JAVA_HOME=c:\sdk\jdk-21.0.2` and **`GRADLE_USER_HOME=C:\sdk\gradle-home`**. The latter is mandatory: the Windows profile is Cyrillic (`Владимир`), so Gradle's test-worker `@argfile` (UTF-8) gets read by the `java` launcher as Cp1251 → `ClassNotFoundException`; pointing Gradle at an ASCII path fixes it. The `18EE~1` short path does NOT help (`getCanonicalPath()` resolves it back). It lives on the system drive (next to the JDK, `C:\sdk\`) — not on `D:\`.
- Tests hang with the Gradle daemon on this machine — always pass `--no-daemon`. All Gradle commands run from `app/` (project moved out of the repo root):

```powershell
cd app
.\gradlew.bat clean assemble --no-daemon
.\gradlew.bat test --no-daemon                                  # all tests
.\gradlew.bat test --tests "com.bank.controller.BankControllerTest" --no-daemon   # single test
```

- Tests are pure slices (`@WebMvcTest` + Mockito): no database, Docker, or network needed.
- **E2E-тесты аутентификации** (`app/src/test/java/com/bank/e2e/AuthFlowsE2ETest.java`, тэг `@Tag("e2e")`, Java `HttpClient`, Allure `@Epic`/`@Feature`/`@Story`): проверяют оба варианта подключения к `/api/**` через живой стенд —
  1. `Authorization: Bearer <JWT>` (токен из Keycloak password grant),
  2. `Authorization: Basic login:password` (JWT получает и кэширует Angie/njs, `jwt_cache`),
  плюс негативы: неверный пароль → 401, без заголовка → 401.
  Нужен поднятый Docker-стенд (Angie :82 + Keycloak :8081); если он недоступен — тесты пропускаются (Assumption). Исключены из обычного `test` (excludeTags), поэтому `gradlew test` работает без Docker:

```powershell
cd app
.\gradlew.bat e2eTest --no-daemon                                  # e2e против живого стенда
.\gradlew.bat e2eTest --no-daemon -De2e.angie.base=http://localhost:82 -De2e.keycloak.url=http://localhost:8081
.\gradlew.bat allureReport --no-daemon                             # HTML-отчёт в app/build/reports/allure-report/allureReport
.\gradlew.bat allureStandalone --no-daemon                         # standalone-отчёт: ОДИН index.html (app/build/reports/allure-report-standalone) со вшитыми данными — открывается с диска без HTTP-сервера/браузера-фетчей
.\gradlew.bat allureServe                                          # открыть отчёт локально (блокирует терминал)
```
- No linter, formatter, or CI config exists — `test` is the only verification step.

## Docker (order matters)

`docker/app/Dockerfile` (build context = repo root) copies `app/build/libs/bank-api-1.0.0.jar` (no build stage), and that JAR is gitignored. **Run `gradlew assemble` (from `app/`) before `docker compose up --build`** or the app image build fails.

Compose file is `docker/docker-compose.yml`, so run all commands from `docker/`:

```powershell
cd docker
docker compose up -d --build
docker compose down
docker compose logs -f bank-app
```

- One-shot runner (builds JAR, starts compose, waits for healthy, smoke-tests every endpoint, **затем всегда прогоняет `e2eTest` и собирает ОБА Allure-отчёта: web (`allureReport`) и standalone (`allureStandalone`)**): `.\start_all.cmd` from the repo root (`start_all.cmd test` additionally runs the k6 load test; `start_all.cmd clean` removes all build outputs — `app\build` + `app\.gradle`). It builds the JAR in `app/` (cd's into it internally) and sets `JAVA_HOME`/`GRADLE_USER_HOME` defaults if the env is missing, so it works even when `java` isn't on PATH.
- The script is pure ASCII (any Cyrillic in a `.cmd` shifts cmd.exe's byte-offset parsing and garbles commands) and must keep CRLF line endings.

- `k6-load-test` is behind the `test` profile. The script reads `BASE_URL` env (compose sets `http://angie-proxy:82`), the Dockerfile CMD is just `run /load-test.js` — do NOT pass `--BASE_URL` as a CLI flag (k6 errors `unknown flag`). Because the script is baked into the image, rebuild it: `docker compose --profile test run --rm --build k6-load-test`.
- `bank-app` and `angie-proxy` have healthchecks (busybox `wget` `/actuator/health` and `/angie_status` on `127.0.0.1`) and `k6-load-test`/`angie-proxy` use `depends_on: ... condition: service_healthy`. **Use `127.0.0.1` in healthchecks, not `localhost`** — angie binds IPv4 only and `::1` gets `Connection refused`.
- `load-test.js` samples account IDs 1–3 (only 3 accounts exist; `/api/account/4` is a 404 by design).
- Angie will be recreated (brief downtime) whenever the `bank-api:1.0.0` image is rebuilt — that's what previously caused 50×502 in the load test (k6 started while Tomcat was still booting). Healthcheck gating fixes it.

## Containers / Ports

| Container | Port | Purpose |
|-----------|------|---------|
| bank-app | 8080 (внутр.) | Spring Boot app — `/api/**` требует авторизацию (`Bearer` напрямую либо `Basic` → JWT через njs в Angie), подпись JWT не проверяется. **Порт наружу не публикуется: доступ к API только через Angie `:82`**; все обращения к `/api/**` логируются в логе `bank-app` |
| angie-proxy | 82 | balancer + `/angie_status`, `/status/` (JSON), Web UI `/ui-keycloak/` (PKCE), `/ui-basic/` (Basic), links page `/` |
| keycloak | 8081 | OIDC IdP, realm `bank`, клиент `bank-web` (PKCE + password grant, `start-dev --import-realm`) |

JWT: UI и тесты получают access token из Keycloak (демо `ivanov`/`password123` → claim `client_id=1`, `petrova`/`password456` → `2`; **тесты — `testuser`/`testpass123` → `client_id=1`**); API берёт из Bearer `preferred_username`/`sub` и `client_id` (демо — без проверки подписи). В лог `bank-app` на каждый `/api/**` выводится и сам JWT (`token=...`). Бывший `POST /api/auth` удалён.

Basic → JWT в Angie (njs): `location /api/` использует `auth_request /_jwt` (`js_content jwt.handle`) + `proxy_set_header Authorization $auth_token` (`js_set jwt.lookup`). Bearer из клиента проходит как есть; Basic → проверка кэша (`js_shared_dict_zone jwt_cache`, таймаут зоны = 270 с, `exp` токена 300 с), при отсутствии — внутренний `/_kc` (password grant в Keycloak `keycloak:8080`, обязателен `proxy_set_header Content-Type application/x-www-form-urlencoded;`), неверные учётные данные → 401. `$upstream_http_*` НЕ работает для `js_content` — только для реального апстрима, поэтому токен шарится через shared-словарь, а не через заголовок subrequest. Модуль: `/usr/lib/angie/modules/ngx_http_js_module.so` (`load_module`, ES5, нет `fetch`/`for..of`/`ngx.encodeBase64`). Перечисление ключей shared-словаря — метод `keys(1000)` (в этой сборке Angie нет `get_keys`); отладка кэша — `GET /_jwt_stats` (`js_content jwt.stats`, ф-ция `stats()` в `jwt.js`).

**ДВА РЕАЛЬНЫХ ПОДВОДНЫХ КАМНЯ njs в этой сборке Angie (проверено эмпирически):**
1. **`set()` — НЕЛЬЗЯ передавать 3-й аргумент (timeout).** В этой сборке он трактуется как **миллисекунды**, а не секунды: `set(k, v, 270)` держит запись ~270 мс (проверено probe-ом: `alive`→`+0.6s gone`), поэтому прежний `set(..., exp−30)` (из удалённого `decodeExp`) разбивал кэш в хлам — каждый следующий запрос перевыпускал токен. TTL задаётся таймаутом самой зоны: `js_shared_dict_zone zone=jwt_cache:1m timeout=270s`.
2. **Запись из контекста auth_request-subrequest живёт ~0,3 с** (даже без 3-го аргумента): `handle()` (это subrequest `/_jwt`) пишет токен, но к следующему запросу запись уже исчезает. Поэтому долговечная запись делается в `lookup()` — `js_set`-переменная выполняется в контексте **основного** запроса: на каждый промах/хит кэш пере-пишется (`ngx.shared.jwt_cache.set(user, ...)`), а когда токену осталось ≤60 с — запись гасится (`set(user,'')`), чтобы следующий Basic-запрос перевыпустил свежий токен (`expires()` датой из JWT).
3. `worker_processes auto` (на этой машине = 20 воркеров) — **НОРМАЛЬНО**: shared-словарь реально шарится между воркерами (проверено: 100 запросов на 20 воркерах → 1 токен, `_jwt_stats` достоверен). Прежнее «worker_processes 1 обязателен» было ложным выводом — все тогдашние симптомы («30 запросов → 4 токена», `total:0`) объяснялись п.1 (TTL в мс).

All balancers proxy the same API: `GET /api/me` (из JWT), `GET /api/client/{id}`, `GET /api/account/{id}`, `GET /api/client/{id}/accounts`. Swagger UI: `http://localhost:82/swagger-ui.html`. Links page: `docker/links.html` mounted into angie at `/` as `index.html`. Web UI: `docker/ui-keycloak/index.html` (vanilla JS, PKCE S256) mounted into angie at `/ui-keycloak/`; `docker/ui-basic/index.html` (no Keycloak redirect, Basic → JWT) mounted at `/ui-basic/`.

Keycloak config is `docker/keycloak/bank-realm.json` (realm import; no data volume — realm пересоздаётся при рестарте). Admin console: `http://localhost:8081` (`admin`/`admin`). Keycloak поднимается дольше остальных и без healthcheck — `start_all.cmd` сам ждёт `/.well-known/openid-configuration` перед smoke-тестами, k6 ретраит token fetch в `setup()`.

## Codebase gotchas

- Source: `app/src/main/java/com/bank/` (entrypoint `BankApiApplication`), tests: `app/src/test/java/com/bank/`. Config: `config/`, `controller/BankController` (all endpoints), `service/ClientServiceImpl`, `config/DataInitializer` seeds users on startup.
- **Both `application.properties` and `application.yml` exist** with overlapping keys; Spring Boot gives precedence to `application.properties` (`show-sql=false`, actuator exposure). Edit the right one.
- Test users: `ivanov`/`password123` (client 1), `petrova`/`password456` (client 2) — демо/UI; `testuser`/`testpass123` (client 1) — **только для автотестов** (e2e, smoke, k6). Passwords live in `DataInitializer.java` (H2, `create-drop`) — но `ivanov`/`petrova`/`testuser` в H2 отдельны: данные клиентов по id, JWT-пользователь из realm не сверяется с H2-логином. Realm user — в `docker/keycloak/bank-realm.json`.

## Docs

Repo layout: `app/` — Spring Boot приложение и инструменты сборки (Gradle wrapper, `src/`, `build/`); `docker/` — стек (compose, angie, keycloak, ui, k6); корень — `README.md`, `AGENTS.md` и лаунчер `start_all.cmd` (`links.html` живёт в `docker/`).

`README.md` (repo root) — единственный человекочитаемый документ стенда: точки доступа, быстрый старт, контейнеры, API и авторизация, текстовые схемы, тестирование, troubleshooting. Источник истины — `docker-compose.yml`, конфиги и код.

Basic → JWT в Angie: в `jwt_cache` (shared dict) хранится `token|hash(пароль)`; при попадании в кэш njs сравнивает хэш переданного пароля — **неверный пароль → 401 даже для закэшированного пользователя** (не только при промахе кэша).
