@echo off
chcp 65001 >nul
title Ayudar Trazable - Backend Server

echo ========================================================
echo   Ayudar Trazable - Backend API (Java 21 + PostGIS)
echo ========================================================
echo.
echo Selecciona el modo de ejecucion:
echo  [1] Recomendado: Modo Hibrido (PostGIS en Podman + API local)
echo      - Permite editar codigo Java con recarga rapida.
echo  [2] Contenedores completos (Podman / Docker Compose)
echo      - Levanta BD y API en contenedores aislados.
echo  [3] Ejecutar pruebas unitarias e integracion (mvnw test)
echo.

set /p OPCION="Ingresa tu opcion (1, 2 o 3) [Default: 1]: "
if "%OPCION%"=="" set OPCION=1

if "%OPCION%"=="1" goto MODO_HIBRIDO
if "%OPCION%"=="2" goto MODO_COMPOSE
if "%OPCION%"=="3" goto MODO_TESTS
goto MODO_HIBRIDO

:MODO_HIBRIDO
echo.
echo [1/2] Verificando / Levantando base de datos PostGIS en Podman...
wsl -d podman-machine-default -u root podman run -d --name ayudar_trazable_db -p 5432:5432 -e POSTGRES_DB=ayudar_trazable -e POSTGRES_PASSWORD=postgres postgis/postgis:15-3.3-alpine 2>nul
wsl -d podman-machine-default -u root podman start ayudar_trazable_db 2>nul
echo Base de datos activa en localhost:5432.
echo.
echo [2/2] Iniciando API Spring Boot en http://localhost:3001 ...
echo Documentacion Swagger UI: http://localhost:3001/swagger-ui.html
echo OpenAPI Spec:            http://localhost:3001/v3/api-docs
echo.
call ./mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
pause
exit /b 0

:MODO_COMPOSE
echo.
echo Levantando stack completo con podman-compose...
wsl -d podman-machine-default -u root podman-compose -f /mnt/c/Users/alex0/Downloads/fqwwfq/cocode-back/compose.yaml up -d
echo.
echo Stack en ejecucion:
echo - API: http://localhost:3001
echo - Swagger: http://localhost:3001/swagger-ui.html
pause
exit /b 0

:MODO_TESTS
echo.
echo Ejecutando suite de 186 tests con Maven...
set PGPASSWORD=postgres
call ./mvnw.cmd test
pause
exit /b 0
