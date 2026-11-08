#!/bin/sh
set -e

echo "============================================="
echo "Inicializando Base de Datos Ayudar Trazable..."
echo "============================================="

# 1. Base de datos principal: ayudar_trazable
echo "-> Aplicando db.sql en ayudar_trazable..."
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "ayudar_trazable" -f /docker-entrypoint-initdb.d/sql/db.sql

echo "-> Aplicando procedimientos almacenados en ayudar_trazable..."
for f in /docker-entrypoint-initdb.d/sql/sp/*.sql; do
    echo "   Cargando $(basename "$f")..."
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "ayudar_trazable" -f "$f"
done

echo "-> Aplicando semillas en ayudar_trazable..."
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "ayudar_trazable" -f /docker-entrypoint-initdb.d/sql/seed.sql
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "ayudar_trazable" -f /docker-entrypoint-initdb.d/sql/seed_dev.sql

echo "-> Configurando rol y permisos para app_ayudar..."
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "ayudar_trazable" <<-EOSQL
    ALTER ROLE app_ayudar WITH PASSWORD 'app_ayudar';
    GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO app_ayudar;
    GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO app_ayudar;
    GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO app_ayudar;
EOSQL

# 2. Base de datos para tests automáticos: ayudar_trazable_pruebas
echo "-> Creando base de datos ayudar_trazable_pruebas..."
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "postgres" <<-EOSQL
    CREATE DATABASE ayudar_trazable_pruebas;
EOSQL

echo "-> Aplicando db.sql en ayudar_trazable_pruebas..."
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "ayudar_trazable_pruebas" -f /docker-entrypoint-initdb.d/sql/db.sql

echo "-> Aplicando procedimientos almacenados en ayudar_trazable_pruebas..."
for f in /docker-entrypoint-initdb.d/sql/sp/*.sql; do
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "ayudar_trazable_pruebas" -f "$f"
done

echo "-> Aplicando semillas en ayudar_trazable_pruebas..."
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "ayudar_trazable_pruebas" -f /docker-entrypoint-initdb.d/sql/seed.sql

echo "-> Configurando permisos en ayudar_trazable_pruebas..."
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "ayudar_trazable_pruebas" <<-EOSQL
    GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO app_ayudar;
    GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO app_ayudar;
    GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO app_ayudar;
EOSQL

echo "============================================="
echo "Inicializacion de PostGIS completada con exito!"
echo "============================================="
