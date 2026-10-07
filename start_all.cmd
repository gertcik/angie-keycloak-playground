@echo off
setlocal enabledelayedexpansion
title Bank API - Angie stack

if "%JAVA_HOME%"=="" set "JAVA_HOME=c:\sdk\jdk-21.0.2"
echo  Using JAVA_HOME=%JAVA_HOME%

if /I "%~1"=="clean" goto :clean

echo.
echo ====[1/6] Building backend JAR (gradlew assemble, app/)====
cd /d "%~dp0app"
call "%~dp0app\gradlew.bat" assemble --no-daemon || goto :fail_build

echo.
echo ====[2/6] Starting docker compose (bank-app + angie + keycloak)====
cd /d "%~dp0docker"
docker compose up -d --build || goto :fail_compose

echo.
echo ====[3/6] Waiting for bank-app and angie-proxy to be healthy====
set "health_file=%TEMP%\bank_stack_health.txt"
set "ok=0"
for /L %%i in (1,1,60) do (
  set "all_healthy=1"
  set "seen=0"
  docker inspect --format "{{.State.Health.Status}}" bank-app angie-proxy > "%health_file%" 2>nul
  for /f "usebackq delims=" %%h in ("%health_file%") do (
    set "seen=1"
    if /I not "%%h"=="healthy" set "all_healthy=0"
  )
  if "!seen!"=="0" set "all_healthy=0"
  if "!all_healthy!"=="1" (
    set "ok=1"
    echo  All containers healthy ^(check %%i^)
    goto :ready
  )
  ping -n 4 127.0.0.1 >nul
)
:ready
if not "%ok%"=="1" (
  echo.
  echo  TIMEOUT: containers did not become healthy.
  docker compose ps
  goto :fail
)

echo.
echo ====[4/6] Smoke tests (all access points)====
echo.
echo  Web endpoints:
curl.exe -s -o NUL -w "  angie_status      http://localhost:82/angie_status        -> HTTP %%{http_code}\n" http://localhost:82/angie_status
curl.exe -s -o NUL -w "  status JSON       http://localhost:82/status/             -> HTTP %%{http_code}\n" http://localhost:82/status/
curl.exe -s -o NUL -w "  links page        http://localhost:82/                    -> HTTP %%{http_code}\n" http://localhost:82/
curl.exe -s -o NUL -w "  swagger ui        http://localhost:82/swagger-ui.html     -> HTTP %%{http_code}\n" -L http://localhost:82/swagger-ui.html
curl.exe -s -o NUL -w "  bank-actuator   http://localhost:82/actuator/health     -> HTTP %%{http_code}\n" http://localhost:82/actuator/health
curl.exe -s -o NUL -w "  web ui keycloak   http://localhost:82/ui-keycloak/           -> HTTP %%{http_code}\n" http://localhost:82/ui-keycloak/
curl.exe -s -o NUL -w "  web ui basic      http://localhost:82/ui-basic/              -> HTTP %%{http_code}\n" http://localhost:82/ui-basic/
curl.exe -s -o NUL -w "  keycloak realm    http://localhost:8081/realms/bank/...    -> HTTP %%{http_code}\n" http://localhost:8081/realms/bank/.well-known/openid-configuration
echo.
echo  API via Angie (Keycloak user testuser, clientId=1, JWT bearer):
echo   waiting for Keycloak realm 'bank' (well-known endpoint)...
set "kc_ready=0"
set "token_file=%TEMP%\kc_token.json"
del "%token_file%" 2>nul
for /L %%i in (1,1,60) do (
  curl.exe -s -o NUL -w "%%{http_code}" "http://localhost:8081/realms/bank/.well-known/openid-configuration" > "%token_file%"
  set "kc_code="
  set /p kc_code=<"%token_file%"
  if "!kc_code!"=="200" (
    set "kc_ready=1"
    goto :kc_ok
  )
  ping -n 4 127.0.0.1 >nul
)
:kc_ok
if not "%kc_ready%"=="1" (
  echo  WARN: Keycloak realm not ready after 180s, token fetch may fail.
)
echo   GET access token (password grant, client bank-web):
curl.exe -s -X POST "http://localhost:8081/realms/bank/protocol/openid-connect/token" -H "Content-Type: application/x-www-form-urlencoded" -d "grant_type=password&client_id=bank-web&username=testuser&password=testpass123" -o "%token_file%"
for /f "usebackq delims=" %%t in (`powershell -NoProfile -ExecutionPolicy Bypass -Command "$j = Get-Content -Raw '%token_file%' | ConvertFrom-Json; if ($j.access_token) { Write-Output $j.access_token }"`) do set "TOKEN=%%t"
if not defined TOKEN (
  echo  ERROR: could not obtain Keycloak access token.
  type "%token_file%"
  goto :fail
)
echo   got access_token (%TOKEN:~0,12%...)
echo   GET /api/me
curl.exe -s -w "                          -> HTTP %%{http_code}\n" -H "Authorization: Bearer %TOKEN%" http://localhost:82/api/me
echo   GET /api/client/1
curl.exe -s -o NUL -w "                          -> HTTP %%{http_code}\n" -H "Authorization: Bearer %TOKEN%" http://localhost:82/api/client/1
echo   GET /api/account/1
curl.exe -s -o NUL -w "                          -> HTTP %%{http_code}\n" -H "Authorization: Bearer %TOKEN%" http://localhost:82/api/account/1
echo   GET /api/client/1/accounts
curl.exe -s -o NUL -w "                          -> HTTP %%{http_code}\n" -H "Authorization: Bearer %TOKEN%" http://localhost:82/api/client/1/accounts
del "%token_file%" 2>nul
echo.
echo  API via Angie (Basic -> njs -> JWT cache, no Keycloak redirect):
echo  GET /api/me (Basic testuser:testpass123)
curl.exe -s -o NUL -w "                          -> HTTP %%{http_code}\n" -u testuser:testpass123 http://localhost:82/api/me
echo   GET /api/client/1 (Basic testuser:testpass123, second hit - served from Angie cache)
curl.exe -s -o NUL -w "                          -> HTTP %%{http_code}\n" -u testuser:testpass123 http://localhost:82/api/client/1
echo   GET /api/account/1 (Basic petrova:WRONG - must be 401, petrova not cached yet)
curl.exe -s -o NUL -w "                          -> HTTP %%{http_code}\n" -u petrova:WRONG http://localhost:82/api/account/1
echo.

echo ====[5/6] Containers====
docker compose ps
echo.

echo ====[6/6] E2E tests and Allure reports====
cd /d "%~dp0app"
echo  Running e2e tests (JWT + Basic against live stack)...
call "%~dp0app\gradlew.bat" e2eTest --no-daemon || goto :fail_tests
echo  Generating Allure report (web version)...
call "%~dp0app\gradlew.bat" allureReport --no-daemon || goto :fail_tests
echo  Generating standalone Allure report (single HTML, works from disk)...
call "%~dp0app\gradlew.bat" allureStandalone --no-daemon || goto :fail_tests
echo  Allure report (web):        %~dp0app\build\reports\allure-report\allureReport
echo  Allure report (standalone): %~dp0app\build\reports\allure-report-standalone\index.html
echo.

if /I "%~1"=="test" (
  echo  Extra: K6 load test...
  cd /d "%~dp0docker"
  docker compose --profile test run --rm --build k6-load-test
  echo.
)
echo  Done. Unexpected codes: me/client/account must be 200; the single 401 (petrova wrong pass) is expected. ASCII + CRLF kept.
exit /b 0

:clean
echo.
echo ====[CLEAN] Stopping docker compose and removing all build results====
cd /d "%~dp0docker"
docker compose down
cd /d "%~dp0app"
call "%~dp0app\gradlew.bat" clean --no-daemon
if errorlevel 1 exit /b 1
if exist "%~dp0app\.gradle" rmdir /s /q "%~dp0app\.gradle"
if not exist "%~dp0app\build" goto :clean_ok
echo  WARN: app\build still exists.
exit /b 1

:clean_ok
echo  Clean: docker compose stopped; removed app\build - JAR, test reports, Allure reports - and app\.gradle.
exit /b 0

:fail_build
echo.
echo  ERROR: JAR build failed. Need JDK 21: JAVA_HOME=c:\sdk\jdk-21.0.2 (see AGENTS.md).
exit /b 1

:fail_compose
echo.
echo  ERROR: docker compose up -d failed.
exit /b 1

:fail_tests
echo.
echo  ERROR: e2e tests or Allure report generation failed.
exit /b 1

:fail
exit /b 1
