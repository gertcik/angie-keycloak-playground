# Кастомная страница входа вместо страницы Keycloak

**Статус:** ✅ ВЫПОЛНЕНО (2026-10-07, вариант A — тема логина Keycloak)

## Что сделано
- Тема `docker/keycloak/themes/bank/login/`:
  - `theme.properties` — `parent=keycloak`, `locales=ru,en`;
  - `template.ftl` — брендовый каркас: шапка «Bank API» + подзаголовок, остальные секции (`header`, `form`, `socialProviders`, `info`, сообщения) наследуются из `base/login.ftl` родителей;
  - `resources/css/bank.css` — зелёная тема поверх PatternFly/Keycloak (фон `#f4f6f8`, карточка скруглённая с тенью, кнопки/фокусы в фирменном `#2d7d46`).
- `docker/keycloak/bank-realm.json` — `"loginTheme": "bank"`.
- `docker/docker-compose.yml` — volume `./keycloak/themes:/opt/keycloak/themes:ro`.
- Keycloak пересоздан (`up -d --force-recreate keycloak`).

## Проверка
- Login-страница (PKCE `auth` endpoint + валидный code_challenge): HTTP 200, баннер `Bank API`, `css/bank.css` подключён, форма `username`/`password`/`kc-login` на месте, ошибок FreeMarker нет, логотип Keycloak не выводится.
- E2E 6/6 PASSED (тесты ходят в token endpoint, не в страницу — не затронуты).
- Остальные задачи (кроме неактуального Варианта B) закрыты.

## Анализ (открытые вопросы закрыты частично)

### Вариант A — Тема логина Keycloak (рекомендуется)
Стандартная тема логина Keycloak переопределяется через SPI `--spi-theme-login-theme=...`.
Для Keycloak 26 (docker `quay.io/keycloak/keycloak:26.1`) themes живут в `/opt/keycloak/themes` (в 26 каталог стандартный: `{KC_HOME}/themes`; volume поднять туда).

Шаги:
1. Создать `docker/keycloak/themes/bank/login/` (минимальная тема: `theme.properties`, `<realm name>="bank"`, родитель `base`; переопределить `template/login.ftl` — переписать фирменную страницу; можно взять `template.ftl` из keycloak:26 и оставить только форму).
2. В `docker/docker-compose.yml` сервису `keycloak`:
   - volume: `./keycloak/themes:/opt/keycloak/themes:ro`,
   - command: `start-dev --import-realm --spi-theme-login-theme=bank --spi-theme-admin-console-theme=keycloak` (логин-тему меняем, админ-тему не трогаем).
3. В `bank-realm.json` можно задать `"loginTheme": "bank"` на уровне realm (более явно, чем SPI).
4. `docker compose up -d --force-recreate keycloak` — контейнер пересоздать (realm импортируется заново).

Плюсы: сохраняется весь OIDC-флоу (`/ui-keycloak/` PKCE продолжает работать), меняется только внешний вид страницы входа. E2E не ломается (тесты ходят через `/realms/bank/protocol/openid-connect/token`, не через страницу). Минусы: работа с FreeMarker-шаблоном Keycloak.

### Вариант B — Своя форма на Angie (password grant, без редиректа на Keycloak)
По сути это уже есть в `/ui-basic/`. Если нужна одна «красивая» страница входа, достаточно доработать `docker/ui-basic/index.html`:
- инпут логина/пароля, кнопка «Войти»,
- `fetch(apiBase + '/api/me', { headers: { 'Authorization': 'Basic ' + btoa(login+':'+pass) } })` → Angie/njs сам делает password grant (кэш `jwt_cache`),
- при 200 сохранить ничего не надо (Angie кэширует по своему), перейти в дашборд, который уже есть в `/ui-keycloak/`? — нет, это ОТДЕЛЬНАЯ страница.
- проще: вынести общую дашбордовую часть (список счетов и т.д.) в общий JS или оставить как есть в `/ui-basic/`.

Плюсы: полный контроль вёрстки (HTML/CSS/JS, как уже в `ui-basic`), ноль работы с Keycloak-темами. Минусы: ломается демонстрационный смысл `/ui-keycloak/` (PKCE-редирект на Keycloak больше не показывается) — надо решить, оставляем ли этот флоу как отдельную демку.

### Вариант C — Hybrid: `/ui-keycloak/` остаётся, но точка входа `/` показывает «красивую» форму
- ссылка на `/ui-basic/` (Basic) и `/ui-keycloak/` (PKCE) уже есть в `docker/links.html` (страница `/`).

## Решение (принято владельцем)
Выбран **Вариант A** — тема логина Keycloak: PKCE-флоу `/ui-keycloak/` сохранён, изменился только внешний вид страницы входа (бренд «Bank API» поверх нативной формы).
Вариант B (своя форма на Angie) — не требуется: `/ui-basic/` уже даёт логин без редиректа на Keycloak.