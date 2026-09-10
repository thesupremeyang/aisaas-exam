#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MYSQL_HOST="${APBOA_EXAM_DB_HOST:-127.0.0.1}"
MYSQL_PORT="${APBOA_EXAM_DB_PORT:-3306}"
MYSQL_USER="${APBOA_EXAM_DB_USER:-root}"
MYSQL_PASSWORD="${APBOA_EXAM_DB_PASSWORD:-123456}"
DB_NAME="${APBOA_EXAM_DB_NAME:-apboa_exam}"
VECTOR_HOST="${APBOA_EXAM_VECTOR_DB_HOST:-127.0.0.1}"
VECTOR_PORT="${APBOA_EXAM_VECTOR_DB_PORT:-5432}"
VECTOR_USER="${APBOA_EXAM_VECTOR_DB_USER:-postgres}"
VECTOR_PASSWORD="${APBOA_EXAM_VECTOR_DB_PASSWORD:-123456}"
VECTOR_DB_NAME="${APBOA_EXAM_VECTOR_DB_NAME:-apboa_exam_vector}"
VECTOR_ADMIN_USER="${APBOA_EXAM_VECTOR_ADMIN_USER:-apple}"
VECTOR_ADMIN_PASSWORD="${APBOA_EXAM_VECTOR_ADMIN_PASSWORD:-}"
EXAM_SQL="$ROOT_DIR/sql/once_db_init/apboa_exam.sql"

if [[ "$DB_NAME" != "apboa_exam" || "$VECTOR_DB_NAME" != "apboa_exam_vector" ]]; then
  echo "为避免误操作，本脚本只允许初始化 apboa_exam 和 apboa_exam_vector。" >&2
  exit 1
fi

if [[ ! -f "$EXAM_SQL" ]]; then
  echo "找不到考试版数据库快照：$EXAM_SQL" >&2
  exit 1
fi

MYSQL=(mysql --protocol=TCP -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER")
mysql_exec() {
  MYSQL_PWD="$MYSQL_PASSWORD" "${MYSQL[@]}" "$@"
}

echo "初始化考试版 MySQL 数据库：$DB_NAME"
mysql_exec -e "CREATE DATABASE IF NOT EXISTS \`$DB_NAME\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql_exec "$DB_NAME" < "$EXAM_SQL"

echo "初始化考试版 PostgreSQL 向量数据库：$VECTOR_DB_NAME"
if ! PGPASSWORD="$VECTOR_PASSWORD" psql --host="$VECTOR_HOST" --port="$VECTOR_PORT" --username="$VECTOR_USER" --dbname=postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '$VECTOR_DB_NAME'" | grep -q 1; then
  PGPASSWORD="$VECTOR_ADMIN_PASSWORD" createdb --host="$VECTOR_HOST" --port="$VECTOR_PORT" --username="$VECTOR_ADMIN_USER" --owner="$VECTOR_USER" "$VECTOR_DB_NAME"
fi

echo "考试版数据库初始化完成。SaaS 数据库 apboa_next 未被修改。"
