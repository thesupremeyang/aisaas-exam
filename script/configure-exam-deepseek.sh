#!/usr/bin/env bash
set -euo pipefail

MYSQL_HOST="${APBOA_EXAM_DB_HOST:-127.0.0.1}"
MYSQL_PORT="${APBOA_EXAM_DB_PORT:-3306}"
MYSQL_USER="${APBOA_EXAM_DB_USER:-root}"
MYSQL_PASSWORD="${APBOA_EXAM_DB_PASSWORD:-123456}"
DB_NAME="${APBOA_EXAM_DB_NAME:-apboa_exam}"
API_KEY="${APBOA_EXAM_DEEPSEEK_API_KEY:-}"

if [[ "$DB_NAME" != "apboa_exam" ]]; then
  echo "为避免误操作，本脚本只允许写入 apboa_exam。" >&2
  exit 1
fi
if [[ -z "$API_KEY" || ! "$API_KEY" =~ ^sk-[A-Za-z0-9_-]+$ ]]; then
  echo "请通过 APBOA_EXAM_DEEPSEEK_API_KEY 提供有效的 DeepSeek API Key。" >&2
  exit 1
fi

MYSQL=(mysql --protocol=TCP -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER")
MYSQL_PWD="$MYSQL_PASSWORD" "${MYSQL[@]}" "$DB_NAME" -e "
INSERT INTO model_provider
  (id, type, name, description, base_url, auth_type, api_key, env_var_name, enabled,
   config_meta, created_at, updated_at, created_by, updated_by, tenant_id)
VALUES
  (2090000000000000401, 'OPEN_AI', 'DeepSeek', 'DeepSeek 官方 API · 考试版独立配置',
   'https://api.deepseek.com', 'CONFIG', '${API_KEY}', NULL, 1, NULL,
   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1111111111111111111, 1111111111111111111, 1)
ON DUPLICATE KEY UPDATE
  type = VALUES(type), name = VALUES(name), description = VALUES(description),
  base_url = VALUES(base_url), auth_type = VALUES(auth_type), api_key = VALUES(api_key),
  enabled = VALUES(enabled), updated_at = CURRENT_TIMESTAMP;

INSERT INTO model_config
  (id, provider_id, name, model_id, model_type, description, streaming, thinking,
   context_window, max_tokens, temperature, top_p, top_k, repeat_penalty, seed, extend_config,
   connectivity_status, connectivity_message, last_connectivity_check, enabled, created_at,
   updated_at, created_by, updated_by, tenant_id)
VALUES
  (2090000000000000402, 2090000000000000401, 'deepseek-v4-flash', 'deepseek-v4-flash', '[\"CHAT\"]',
   '考试版独立算力', 1, 0, 1000000, 384000, 0.70, 0.90, 50, 1.00, 42, '{}',
   'NOT_CHECKED', NULL, NULL, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
   1111111111111111111, 1111111111111111111, 1)
ON DUPLICATE KEY UPDATE
  provider_id = VALUES(provider_id), name = VALUES(name), model_id = VALUES(model_id),
  model_type = VALUES(model_type), description = VALUES(description), streaming = VALUES(streaming),
  thinking = VALUES(thinking), context_window = VALUES(context_window), max_tokens = VALUES(max_tokens),
  temperature = VALUES(temperature), top_p = VALUES(top_p), top_k = VALUES(top_k),
  repeat_penalty = VALUES(repeat_penalty), seed = VALUES(seed), extend_config = VALUES(extend_config),
  connectivity_status = 'NOT_CHECKED', connectivity_message = NULL, last_connectivity_check = NULL,
  enabled = VALUES(enabled), updated_at = CURRENT_TIMESTAMP;
"

echo "考试版 DeepSeek 模型配置已写入 apboa_exam。"
