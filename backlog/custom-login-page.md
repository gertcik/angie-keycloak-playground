# Кастомная страница входа вместо страницы Keycloak

**Статус:** backlog (не выполнено)

## Задача
Сделать пользовательскую страницу входа (hosted на Angie), чтобы пользователь не видел стандартную страницу Keycloak.

## Контекст стенда
- `/ui-basic/` (`docker/ui-basic/index.html`): vanilla JS, Basic login/password → Angie/njs конвертирует в JWT (кэш `jwt_cache`). Уже «без редиректа на Keycloak».
- `/ui-keycloak/` (`docker/ui-keycloak/index.html`): vanilla JS + PKCE S256 → редиректит на Keycloak `/realms/bank/protocol/openid-connect/auth`, где показывается фирменная страница входа Keycloak.
- Realm `bank`, клиент `bank-web` (`standardFlowEnabled: true`, password grant) — `docker/keycloak/bank-realm.json`.

## Что нужно проработать (варианты)
1. **Keycloak реализует темление темы (`themes`)**: Кастомизировать не применяя код — чистый UI. Для этого опции `--spi-theme-login-theme=custom` + mount кастомной темы в `${KC_HOME}/themes/...` в `docker/keycloak/`. Реализуется без изменения Spring.
   - Наша тема single-file: кастомизировать форму логина Keycloak легче всего через Keycloak-темы (FreeMarker/Velocity).
2. Заменить PKCE-флоу настройкой `login_theme` через Keycloak.
3. Либо уйти от стандартной страницы без исправления Keycloak: собрать свою форму входа на Angie, которая ведёт на password grant + сохранить токен в localStorage и затем `/ui-keycloak/` (без redirect). Это фактически дублирует `/ui-basic/` — надо решить, не противоречит ли задача цели стенда (демонстрация OIDC/PKCE через стандартную страницу Keycloak).

## Открытые вопросы
- Страница должна заменить логин И PKCE, или только быть просто формой с паролем?
- Нужно ли сохранить учебный смысл (демонстрация потока OIDC с редиректом)?
- Какие стили/логотипы у «кастомной» страницы?

## Ожидаемый результат
- Хостед-страница входа вместо стандартной Keycloak (в `docker/`, mounted в Angie или как theme в Keycloak)
- Документация (README) обновлена про новый поток
- E2E/smoke не должны сломаться (или обновлены под новый флоу)