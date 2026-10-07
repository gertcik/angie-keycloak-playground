# README: добавить ссылку на статью и другие материалы в раздел «Источники»

**Статус:** ✅ ВЫПОЛНЕНО (2026-10-07)

## Статья (проверена 2026-10-07)
- URL: https://fastfox.pro/blog/tutorials/nginx-njs-jwt-rs256-hs256/
- Заголовок: «JWT в Nginx с njs: проверка HS256 и RS256 на реверс-прокси»
- Автор: AI (GPT-5), дата: 10 ноя 2025, релевантность: прямая — та же схема, что у нас:
  - `auth_request /_auth` + `js_content jwt.auth` (у нас `/_jwt` + `jwt.handle`),
  - `js_import`, загрузка динамического модуля njs (у нас `load_module ngx_http_js_module.so`),
  - проброс клеймов в апстрим, `auth_request_set` (у нас `proxy_set_header Authorization $auth_token`),
  - чек `exp` (у нас `expires()` в `jwt.js`), `iss`/`aud`,
  - WebCrypto → `crypto.subtle`; статья упоминает `ngx.fetch`/JWKS (у нас подпись НЕ проверяется — демо; статья — справочник как это сделать «по-взрослому»).

## Что добавить в README.md
Новый раздел в конце (после «Документация», строка ~429):

```markdown
## Материалы / Источники

- [JWT в Nginx с njs: проверка HS256 и RS256 на реверс-прокси](https://fastfox.pro/blog/tutorials/nginx-njs-jwt-rs256-hs256/)
  — та же схема, что в стенде (`auth_request` + `js_content`), но с проверкой подписи (HS256/RS256,
  WebCrypto, JWKS) — справочник, как усилить наш `docker/angie/js/jwt.js` за пределы демо.
- [Документация njs](https://nginx.org/en/docs/njs/) — язык, `ngx.shared`, `js_content`/`js_set`.
- [ngx_http_auth_request_module](https://nginx.org/en/docs/http/ngx_http_auth_request_module.html)
  — `auth_request`/`auth_request_set` (у нас `/_jwt` в `default.conf`).
- [Angie — официальный сайт/документация](https://angie.software/en/) — конфиг, `/status/`.
- [Keycloak: Authorization Code + PKCE](https://www.keycloak.org/docs/latest/authorization_services/)
  и [Direct Access Grants](https://www.keycloak.org/docs/latest/securing_apps/) — флоу `/ui-keycloak/` и `/_kc`.
- [RFC 7519 (JWT)](https://www.rfc-editor.org/rfc/rfc7519) — структура токена, `exp`, `sub`, `aud`.
- [OIDC Discovery](https://openid.net/specs/openid-connect-discovery-1_0.html) — `/.well-known/openid-configuration`.
- [Spring Boot: slice-тесты (@WebMvcTest)](https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html#testing.spring-boot-applications.autoconfigured-tests)
  — как написаны `BankControllerTest`/`ClientServiceImplTest`.
```

## Доп. ссылки-кандидаты (по желанию)
- njs `js_shared_dict_zone` / `set` с TTL (зоной) — есть в доке njs на nginx.org (раздел shared dict).
- Spring Security / JWT — у нас в приложении подпись не проверяется, можно сослаться на intro.

## Ожидаемый результат
- Раздел «Материалы / Источники» в `README.md` с перечисленными ссылками и пояснением релевантности.
- НЕ выдумывать URL: все ссылки выше — официальные (nginx.org / angie.software / keycloak.org / rfc-editor.org / openid.net / docs.spring.io / fastfox.pro).