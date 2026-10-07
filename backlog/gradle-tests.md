# Разобраться, почему не срабатывают Gradle-тесты

**Статус:** ✅ РЕШЕНО (проверено эмпирически, 2026-10-07)

## Итог: тесты РАБОТАЮТ при соблюдении окружения

Полный прогон `.\gradlew.bat test --no-daemon` (Gradle 8.7, `GRADLE_USER_HOME=C:\sdk\gradle-home`):

- `com.bank.controller.BankControllerTest`: tests=9, failures=0, errors=0, skipped=0
- `com.bank.service.ClientServiceImplTest`: tests=6, failures=0, errors=0, skipped=0
- `BUILD SUCCESSFUL`, EXIT=0

## Корневые причины «не срабатывают» (все устранены)

1. **Кириллический профиль Windows → Cp1251-чтение UTF-8 @argfile**: java-лаунчер тест-воркера читал UTF-8 `@argfile` как Cp1251 → `ClassNotFoundException`/сбой. Лечится ASCII `GRADLE_USER_HOME` (сейчас `C:\sdk\gradle-home`, перенесён с D: 2026-10-07).
2. **Демон Gradle виснет на этой машине** → обязателен `--no-daemon`.
3. **`java` не в PATH** → нужен `JAVA_HOME` (сейчас `c:\sdk\jdk-21.0.2`).
4. **Запуск не из `app/`**: gradlew лежит только в `app/` (репозиторий — в корне), сборка-тесты из корня репо не найдут проект.
5. **`test` исключает тэг `e2e`** (`excludeTags 'e2e'`): e2e-тесты живут только в задаче `e2eTest` и требуют поднятого Docker-стенда (Angie :82 + Keycloak :8081) — в обычном `test` их НЕТ ПО УМОЛЧАНИЮ. Это ожидаемо, не баг.
6. Версия Gradle поднята 8.5 → 8.7 (wrapper pinned, 2026-10-07) — тесты проходят на 8.7.

## Как пользоваться (выжимка)
```powershell
cd app
.\gradlew.bat test --no-daemon                                  # юнит-тесты (срезы)
.\gradlew.bat test --tests "com.bank.controller.BankControllerTest" --no-daemon
.\gradlew.bat e2eTest --no-daemon                               # e2e против живого стенда
```