# Angie Console Light: встроить бесплатную консоль мониторинга

**Статус:** 🟡 СДЕЛАНО, требует проверки на живом стенде (Docker daemon на машине не запущен)

## Что за консоль

- Продукт: **Angie Console Light** — бесплатный лёгкий SPA-дашборд мониторинга Angie.
- Демо: https://console.angie.software/ · документация: https://angie.software/en/console/ · GitHub: https://github.com/webserver-llc/angie-console-light
- Работает **поверх штатного REST API** (`/status/`): отдельного демона/метрик-экспортёра не нужно.
- Интерфейсные вкладки (Angie, HTTP Zones, Location Zones, Upstreams, TCP/UDP, Caches, Shared Zones,
  DNS Resolvers, Settings) показывают данные, если в конфиге заведены соответствующие `status_zone`/`zone`/
  `proxy_cache_path`/`resolver`. Для нашего стенда полезны: **About**, **Connections**,
  **HTTP Zones** (нужен `status_zone`), **HTTP Upstreams** (нужен `zone` в `upstream`), **Shared Zones**.

## Как устроена установка (изучено)

- Публикуется пакетами `angie-console-light` в репозиториях Angie и тарболом
  `https://download.angie.software/files/angie-console-light/angie-console-light-<version>.tar.gz`.
- Тарбол уже содержит **готовую статику** в `html/` (`index.html`, `assets/app.bundle.js`,
  `assets/editor.worker.bundle.js`) — сборка через yarn/webpack **не требуется**.
- Фронтенд обращается к API **относительным** путём `api` (`API_PREFIX = 'api'` в `src/constants.js`),
  поэтому, будучи отданным по `/console/`, он бьёт в `/console/api/...`.
- Штатный конфиг (из официальной документации):
  ```nginx
  location /console/ {
      alias /usr/share/angie-console-light/html/;
      index index.html;

      location /console/api/ {
          api /status/;
      }
  }
  ```
  Директива `api <path>` работает как `alias`: префикс location заменяется на указанный путь
  (внутри API-дерева), т.е. `/console/api/foo/` → `/status/foo/`.

## Что сделано в проекте

- `docker/angie/Dockerfile` — добавлена стадия `console-light` (alpine): скачивает тарбол
  (`ARG CONSOLE_VERSION=1.9.0`) и `COPY --from=console-light /console /usr/share/angie-console-light/html`.
- `docker/angie/conf.d/default.conf` — `location = /console` (редирект на `/console/`),
  `location /console/` (alias на статику, `index index.html`) + вложенный
  `location /console/api/ { api /status/; }`. Комментарий-подсказка про `allow 127.0.0.1; deny all;`.
- `docker/links.html` — ссылка на `/console/` в блоке «Мониторинг».
- `docker/docker-compose.yml` — изменений не требует (статика в образе, volume не нужен).
- `start_all.cmd` — smoke-строка `console light http://localhost:82/console/`.
- `README.md`, `AGENTS.md` — точки доступа, таблица контейнеров, раздел «Angie Console Light», источники.

## Внимание: ограничение доступа в проде

В документации консоль рекомендуют закрывать `allow 127.0.0.1; deny all;`. В нашем демо-стенде доступ
открыт (нужно смотреть с хоста через `:82`). Перед использованием вне демо — включить ACL.

## Как проверить (на живом стенде)

```powershell
cd docker
docker compose up -d --build angie-proxy      # образ пересобирается (скачивается консоль)
docker compose exec angie-proxy angie -t      # синтаксис конфига
docker compose exec angie-proxy ls /usr/share/angie-console-light/html
# открыть http://localhost:82/console/  -> HTTP 200, дашборд; /console/api/ -> JSON /status/
curl.exe -s -o NUL -w "%{http_code}\n" http://localhost:82/console/
curl.exe -s http://localhost:82/console/api/connections
```

## Замечания на будущее

- При подъёме версии консоли менять `ARG CONSOLE_VERSION` в `docker/angie/Dockerfile`.
- Чтобы в консоли появились HTTP Zones / Upstreams, в `default.conf` стоит завести
  `status_zone` в `server`/`location` и `zone` в `upstream` (сейчас конфиг их не задаёт — часть
  вкладок будет пустой, что нормально для демо).
