#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080/api}"
ADMIN_USERNAME="${ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin123}"
OPERATOR_USERNAME="${OPERATOR_USERNAME:-operator}"
OPERATOR_PASSWORD="${OPERATOR_PASSWORD:-operator123}"
MYSQL_SOURCE_HOST="${MYSQL_SOURCE_HOST:-127.0.0.1}"
MYSQL_SOURCE_PORT="${MYSQL_SOURCE_PORT:-3306}"
MYSQL_SOURCE_DB="${MYSQL_SOURCE_DB:-${MYSQL_DATABASE:-data_lake_platform}}"
MYSQL_SOURCE_SCHEMA="${MYSQL_SOURCE_SCHEMA:-${MYSQL_SOURCE_DB}}"
MYSQL_SOURCE_USER="${MYSQL_SOURCE_USER:-${MYSQL_USERNAME:-root}}"
MYSQL_SOURCE_PASSWORD="${MYSQL_SOURCE_PASSWORD:-${MYSQL_PASSWORD:-}}"

TMP_DIR="$(mktemp -d /tmp/data-lake-mysql-regression.XXXXXX)"
trap 'rm -rf "$TMP_DIR"' EXIT

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "[ERROR] 缺少必要命令: $1" >&2
    exit 1
  fi
}

for command_name in curl node unzip mktemp rg; do
  require_command "$command_name"
done

json_get() {
  node -e '
    const payload = JSON.parse(process.argv[1]);
    const path = process.argv[2].split(".");
    let current = payload;
    for (const key of path) {
      current = current?.[key];
    }
    if (current === undefined || current === null) {
      process.exit(2);
    }
    process.stdout.write(typeof current === "object" ? JSON.stringify(current) : String(current));
  ' "$1" "$2"
}

json_eval() {
  local payload="$1"
  local script="$2"
  node -e "$script" "$payload"
}

check_success() {
  local response="$1"
  local context="$2"
  local code
  code="$(json_get "$response" "code" 2>/dev/null || true)"
  if [[ "$code" != "0" ]]; then
    echo "[ERROR] ${context} 失败: $response" >&2
    exit 1
  fi
}

request() {
  local token="$1"
  local method="$2"
  local url="$3"
  local body="${4:-}"
  if [[ -n "$body" ]]; then
    curl -fsS -X "$method" -H "Authorization: Bearer ${token}" -H 'Content-Type: application/json' -d "$body" "$url"
  else
    curl -fsS -X "$method" -H "Authorization: Bearer ${token}" "$url"
  fi
}

login() {
  local username="$1"
  local password="$2"
  curl -fsS -X POST -H 'Content-Type: application/json' \
    -d "{\"username\":\"${username}\",\"password\":\"${password}\"}" \
    "${BASE_URL}/auth/login"
}

download_with_auth() {
  local token="$1"
  local method="$2"
  local url="$3"
  local output_file="$4"
  local body="${5:-}"
  if [[ -n "$body" ]]; then
    curl -fsS -X "$method" -H "Authorization: Bearer ${token}" -H 'Content-Type: application/json' \
      -d "$body" "$url" -o "$output_file"
  else
    curl -fsS -X "$method" -H "Authorization: Bearer ${token}" "$url" -o "$output_file"
  fi
}

validate_xlsx() {
  local file_path="$1"
  local label="$2"
  local listing_file="$TMP_DIR/${label}.listing"
  if [[ ! -s "$file_path" ]]; then
    echo "[ERROR] ${label} 文件为空: $file_path" >&2
    exit 1
  fi
  unzip -l "$file_path" > "$listing_file"
  if ! rg -q '\[Content_Types\]\.xml' "$listing_file"; then
    echo "[ERROR] ${label} 不是有效的 xlsx 文件：缺少 [Content_Types].xml" >&2
    exit 1
  fi
  if ! rg -q 'xl/workbook\.xml' "$listing_file"; then
    echo "[ERROR] ${label} 不是有效的 xlsx 文件：缺少 xl/workbook.xml" >&2
    exit 1
  fi
  if ! rg -q 'xl/worksheets/sheet1\.xml' "$listing_file"; then
    echo "[ERROR] ${label} 不是有效的 xlsx 文件：缺少工作表 sheet1.xml" >&2
    exit 1
  fi
}

echo "[1/9] 登录管理员与普通用户"
admin_login_response="$(login "$ADMIN_USERNAME" "$ADMIN_PASSWORD")"
check_success "$admin_login_response" "管理员登录"
operator_login_response="$(login "$OPERATOR_USERNAME" "$OPERATOR_PASSWORD")"
check_success "$operator_login_response" "普通用户登录"
admin_token="$(json_get "$admin_login_response" "data.token")"
operator_token="$(json_get "$operator_login_response" "data.token")"

echo "[2/9] 运行主链路冒烟，准备真实任务与日志数据"
smoke_output="$(BASE_URL="$BASE_URL" USERNAME="$ADMIN_USERNAME" PASSWORD="$ADMIN_PASSWORD" bash scripts/api-smoke-test.sh)"
printf '%s\n' "$smoke_output"

echo "[3/9] 创建并验证 MySQL 数据源"
timestamp="$(date +%Y%m%d%H%M%S)"
source_name="mysql_regression_source_${timestamp}"
source_payload="$(
  SOURCE_NAME="$source_name" \
  MYSQL_SOURCE_HOST="$MYSQL_SOURCE_HOST" \
  MYSQL_SOURCE_PORT="$MYSQL_SOURCE_PORT" \
  MYSQL_SOURCE_DB="$MYSQL_SOURCE_DB" \
  MYSQL_SOURCE_USER="$MYSQL_SOURCE_USER" \
  MYSQL_SOURCE_PASSWORD="$MYSQL_SOURCE_PASSWORD" \
  node - <<'NODE'
const payload = {
  sourceName: process.env.SOURCE_NAME,
  sourceType: 'MYSQL',
  host: process.env.MYSQL_SOURCE_HOST,
  port: Number(process.env.MYSQL_SOURCE_PORT || '3306'),
  dbName: process.env.MYSQL_SOURCE_DB,
  username: process.env.MYSQL_SOURCE_USER,
  password: process.env.MYSQL_SOURCE_PASSWORD,
  description: 'MySQL regression validation source'
};
process.stdout.write(JSON.stringify(payload));
NODE
)"
source_response="$(request "$admin_token" POST "${BASE_URL}/data-sources" "$source_payload")"
check_success "$source_response" "创建 MySQL 数据源"
source_id="$(json_get "$source_response" "data.sourceId")"
test_response="$(request "$admin_token" POST "${BASE_URL}/data-sources/${source_id}/test")"
check_success "$test_response" "测试 MySQL 数据源"

echo "[4/9] 验证数据库 Schema、表列出、预览与导入"
schemas_response="$(request "$admin_token" GET "${BASE_URL}/imports/database/schemas?sourceId=${source_id}")"
check_success "$schemas_response" "列出数据库 Schema"
selected_schema="$(PREFERRED_SCHEMA="$MYSQL_SOURCE_SCHEMA" json_eval "$schemas_response" '
  const payload = JSON.parse(process.argv[1]);
  const schemas = payload.data || [];
  const preferred = process.env.PREFERRED_SCHEMA;
  const matched = schemas.find(item => item === preferred) || schemas[0];
  if (!matched) process.exit(2);
  process.stdout.write(matched);
')"
tables_response="$(request "$admin_token" GET "${BASE_URL}/imports/database/tables?sourceId=${source_id}&schemaName=${selected_schema}")"
check_success "$tables_response" "列出数据库表"
selected_table="$(json_eval "$tables_response" '
  const payload = JSON.parse(process.argv[1]);
  const tables = payload.data || [];
  const preferred = ["operator_def", "sys_user", "sys_role"];
  const matched = preferred
    .map(name => tables.find(item => item.tableName === name))
    .find(Boolean) || tables[0];
  if (!matched) process.exit(2);
  process.stdout.write(matched.tableName);
')"
preview_response="$(request "$admin_token" GET "${BASE_URL}/imports/database/preview?sourceId=${source_id}&schemaName=${selected_schema}&tableName=${selected_table}&limit=5")"
check_success "$preview_response" "预览数据库表"
dataset_name="mysql_import_dataset_${timestamp}"
import_payload="$(
  SOURCE_ID="$source_id" \
  MYSQL_SOURCE_SCHEMA="$selected_schema" \
  SELECTED_TABLE="$selected_table" \
  DATASET_NAME="$dataset_name" \
  node - <<'NODE'
const payload = {
  sourceId: Number(process.env.SOURCE_ID),
  schemaName: process.env.MYSQL_SOURCE_SCHEMA,
  tableName: process.env.SELECTED_TABLE,
  datasetName: process.env.DATASET_NAME,
  description: 'MySQL regression imported dataset'
};
process.stdout.write(JSON.stringify(payload));
NODE
)"
import_response="$(request "$admin_token" POST "${BASE_URL}/imports/database" "$import_payload")"
check_success "$import_response" "导入数据库表"
imported_dataset_id="$(json_get "$import_response" "data.datasetId")"

echo "[5/9] 验证首页、权限页与日志页核心接口"
dashboard_admin_response="$(request "$admin_token" GET "${BASE_URL}/dashboard/overview")"
check_success "$dashboard_admin_response" "管理员首页概览"
dashboard_operator_response="$(request "$operator_token" GET "${BASE_URL}/dashboard/overview")"
check_success "$dashboard_operator_response" "普通用户首页概览"
roles_response="$(request "$admin_token" GET "${BASE_URL}/roles")"
check_success "$roles_response" "角色列表"
users_response="$(request "$admin_token" GET "${BASE_URL}/users")"
check_success "$users_response" "用户列表"
logs_before_response="$(request "$admin_token" GET "${BASE_URL}/task-logs")"
check_success "$logs_before_response" "日志列表"
operator_profile_before="$(request "$operator_token" GET "${BASE_URL}/auth/profile")"
check_success "$operator_profile_before" "普通用户 profile"

echo "[6/9] 验证数据集 Excel 导出"
dataset_xlsx="$TMP_DIR/dataset-export.xlsx"
download_with_auth "$operator_token" GET "${BASE_URL}/datasets/${imported_dataset_id}/export?format=xlsx" "$dataset_xlsx"
validate_xlsx "$dataset_xlsx" "数据集导出"

echo "[7/9] 验证查询结果 Excel 导出"
query_export_payload="$(
  DATASET_ID="$imported_dataset_id" \
  node - <<'NODE'
const payload = {
  datasetId: Number(process.env.DATASET_ID),
  sql: 'SELECT * FROM dataset LIMIT 5'
};
process.stdout.write(JSON.stringify(payload));
NODE
)"
query_xlsx="$TMP_DIR/query-export.xlsx"
download_with_auth "$operator_token" POST "${BASE_URL}/queries/sql/export?format=xlsx" "$query_xlsx" "$query_export_payload"
validate_xlsx "$query_xlsx" "查询结果导出"

echo "[8/9] 验证日志回放"
replay_log_id="$(json_eval "$logs_before_response" '
  const payload = JSON.parse(process.argv[1]);
  const matched = (payload.data || []).find(item => item.taskId);
  if (!matched) process.exit(2);
  process.stdout.write(String(matched.logId));
')"
replay_response="$(request "$operator_token" POST "${BASE_URL}/task-logs/${replay_log_id}/replay")"
check_success "$replay_response" "日志回放"
logs_after_response="$(request "$admin_token" GET "${BASE_URL}/task-logs")"
check_success "$logs_after_response" "回放后日志列表"

echo "[9/9] 验证 MySQL 环境下权限实时同步"
operator_role_before="$(json_eval "$roles_response" '
  const payload = JSON.parse(process.argv[1]);
  const role = (payload.data || []).find(item => item.roleName === "OPERATOR");
  if (!role) process.exit(2);
  process.stdout.write(JSON.stringify(role));
')"
role_id="$(json_eval "$operator_role_before" 'const role = JSON.parse(process.argv[1]); process.stdout.write(String(role.roleId));')"
toggle_payload="$(json_eval "$operator_role_before" '
  const role = JSON.parse(process.argv[1]);
  const actions = Array.isArray(role.actionPermissions) ? [...role.actionPermissions] : [];
  const hasLogExport = actions.includes("log.export");
  const nextActions = hasLogExport ? actions.filter(item => item !== "log.export") : [...actions, "log.export"];
  process.stdout.write(JSON.stringify({
    expectActionPresent: !hasLogExport,
    payload: {
      roleDesc: role.roleDesc,
      menus: role.menuPermissions,
      actions: nextActions
    }
  }));
')"
toggle_request_payload="$(json_get "$toggle_payload" "payload")"
toggle_expected_present="$(json_get "$toggle_payload" "expectActionPresent")"
toggle_response="$(request "$admin_token" PUT "${BASE_URL}/roles/${role_id}" "$toggle_request_payload")"
check_success "$toggle_response" "切换 OPERATOR log.export 权限"
operator_profile_after_toggle="$(request "$operator_token" GET "${BASE_URL}/auth/profile")"
check_success "$operator_profile_after_toggle" "切换权限后的普通用户 profile"
SHOULD_HAVE="$toggle_expected_present" json_eval "$operator_profile_after_toggle" '
  const payload = JSON.parse(process.argv[1]);
  const actions = payload.data?.actions || [];
  const shouldHave = process.env.SHOULD_HAVE === "true";
  const hasAction = actions.includes("log.export");
  if (shouldHave !== hasAction) {
    console.error(`权限同步校验失败，期望 log.export=${shouldHave}，实际=${hasAction}`);
    process.exit(1);
  }
'
restore_payload="$(json_eval "$operator_role_before" '
  const role = JSON.parse(process.argv[1]);
  process.stdout.write(JSON.stringify({
    roleDesc: role.roleDesc,
    menus: role.menuPermissions,
    actions: role.actionPermissions || []
  }));
')"
restore_response="$(request "$admin_token" PUT "${BASE_URL}/roles/${role_id}" "$restore_payload")"
check_success "$restore_response" "恢复 OPERATOR 原始权限"
operator_profile_restored="$(request "$operator_token" GET "${BASE_URL}/auth/profile")"
check_success "$operator_profile_restored" "恢复权限后的普通用户 profile"

before_log_count="$(json_eval "$logs_before_response" 'const payload = JSON.parse(process.argv[1]); process.stdout.write(String((payload.data || []).length));')"
after_log_count="$(json_eval "$logs_after_response" 'const payload = JSON.parse(process.argv[1]); process.stdout.write(String((payload.data || []).length));')"
if (( after_log_count <= before_log_count )); then
  echo "[ERROR] 日志回放后日志数未增长: before=${before_log_count}, after=${after_log_count}" >&2
  exit 1
fi
summary_json="$(
  DASHBOARD_ADMIN="$dashboard_admin_response" \
  DASHBOARD_OPERATOR="$dashboard_operator_response" \
  USERS_RESPONSE="$users_response" \
  OPERATOR_PROFILE="$operator_profile_restored" \
  BEFORE_LOG_COUNT="$before_log_count" \
  AFTER_LOG_COUNT="$after_log_count" \
  IMPORTED_DATASET_ID="$imported_dataset_id" \
  SOURCE_ID="$source_id" \
  SELECTED_SCHEMA="$selected_schema" \
  SELECTED_TABLE="$selected_table" \
  node - <<'NODE'
const dashboardAdmin = JSON.parse(process.env.DASHBOARD_ADMIN);
const dashboardOperator = JSON.parse(process.env.DASHBOARD_OPERATOR);
const users = JSON.parse(process.env.USERS_RESPONSE);
const operatorProfile = JSON.parse(process.env.OPERATOR_PROFILE);
const summary = {
  sourceId: Number(process.env.SOURCE_ID),
  importedDatasetId: Number(process.env.IMPORTED_DATASET_ID),
  importedSchema: process.env.SELECTED_SCHEMA,
  importedTable: process.env.SELECTED_TABLE,
  adminOverview: dashboardAdmin.data,
  operatorOverview: dashboardOperator.data,
  userCount: (users.data || []).length,
  logCountBeforeReplay: Number(process.env.BEFORE_LOG_COUNT),
  logCountAfterReplay: Number(process.env.AFTER_LOG_COUNT),
  operatorMenus: operatorProfile.data?.menus || [],
  operatorActions: operatorProfile.data?.actions || []
};
process.stdout.write(JSON.stringify(summary, null, 2));
NODE
)"

printf '\nMySQL regression check passed\n'
printf '%s\n' "$summary_json"
