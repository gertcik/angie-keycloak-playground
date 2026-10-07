# Разобраться, почему не срабатывают Gradle-тесты

**Статус:** backlog (не выполнено)

## Условия окружения (проверено)
- `JAVA_HOME=c:\sdk\jdk-21.0.2` — задан (User-переменная), JDK 21 на месте
- `GRADLE_USER_HOME=C:\sdk\gradle-home` — задан (ASCII-путь, обход проблемы Cyrillic-профиля)
- `java` НЕ в PATH — запуск тестов только через `app\gradlew.bat`
- Рабочий каталог для всех Gradle-команд: `app/`
- Обязателен флаг `--no-daemon` (демон виснет на этой машине)

## Что делаем (стартовые команды)
```powershell
cd app
.\gradlew.bat test --no-daemon
.\gradlew.bat test --tests "com.bank.controller.BankControllerTest" --no-daemon
```

## Известные факты из AGENTS.md/README (возможные причины)
- Тесты — чистые срезы (`@WebMvcTest` + Mockito), нет БД/Docker/сети
- `test` исключает тэг `e2e` (`excludeTags 'e2e'`), e2e-тесты живут только в `e2eTest`
- `e2eTest` через `start_all.cmd` работает (6/6 PASSED) — значит сам Gradle + форк даемона рабочие
- Причина «не срабатывают» НЕ установлена — надо воспроизвести и диагностировать:
  - NO-SOURCE / 0 тестов найдено?
  - ClassNotFoundException из-за UTF-8 @argfile (памятка: лечится ASCII GRADLE_USER_HOME)?
  - Зависание без `--no-daemon`?
  - Все тесты skipped через Assumption?
  - Ошибка компиляции тестов (Lombok annotationProcessor)?

## Ожидаемый результат
- Точная корневая причина + внести запись в AGENTS.md (если нюанс не задокументирован)