#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080/api}"
USERNAME="${USERNAME:-admin}"
PASSWORD="${PASSWORD:-admin123}"
STATE_FILE="${STATE_FILE:-/tmp/ecommerce-demo-smoke-state.json}"
RUN_MYSQL="${RUN_MYSQL:-false}"
COMMAND="${1:-all}"

TOKEN=""
TMP_DIR="$(mktemp -d /tmp/data-lake-regression-suite.XXXXXX)"
trap 'rm -rf "$TMP_DIR"' EXIT

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "[ERROR] 缺少必要命令: $1" >&2
    exit 1
  fi
}

for cmd in curl node bash mktemp; do
  require_command "$cmd"
done

print_section() {
  echo
  echo "==================== $1 ===================="
}

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

check_success() {
  local response="$1"
  local context="$2"
  local code
  code="$(json_get "$response" "code" 2>/dev/null || true)"
  if [[ "$code" != "0" ]]; then
    echo "[ERROR] ${context} 失败: ${response}" >&2
    exit 1
  fi
}

check_failed() {
  local response="$1"
  local context="$2"
  local code
  code="$(json_get "$response" "code" 2>/dev/null || true)"
  if [[ "$code" == "0" ]]; then
    echo "[ERROR] ${context} 预期失败但返回成功: ${response}" >&2
    exit 1
  fi
}

api_request() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  if [[ -n "$body" ]]; then
    curl -sS -X "$method" -H "Authorization: Bearer ${TOKEN}" -H 'Content-Type: application/json' -d "$body" "${BASE_URL}${path}"
  else
    curl -sS -X "$method" -H "Authorization: Bearer ${TOKEN}" "${BASE_URL}${path}"
  fi
}

login() {
  print_section "login"
  local response
  response="$(curl -sS -X POST -H 'Content-Type: application/json' -d "{\"username\":\"${USERNAME}\",\"password\":\"${PASSWORD}\"}" "${BASE_URL}/auth/login")"
  check_success "$response" "登录"
  TOKEN="$(json_get "$response" "data.token")"
  echo "[OK] 登录成功"
}

run_api_smoke() {
  print_section "api-smoke"
  BASE_URL="$BASE_URL" USERNAME="$USERNAME" PASSWORD="$PASSWORD" bash scripts/api-smoke-test.sh
  echo "[OK] api-smoke 通过"
}

run_demo_smoke() {
  print_section "demo-smoke"
  BASE_URL="$BASE_URL" USERNAME="$USERNAME" PASSWORD="$PASSWORD" STATE_FILE="$STATE_FILE" bash scripts/ecommerce-demo-smoke.sh all
  echo "[OK] demo-smoke 通过"
}

run_mysql_regression() {
  print_section "mysql-regression"
  BASE_URL="$BASE_URL" bash scripts/mysql-regression-check.sh
  echo "[OK] mysql-regression 通过"
}

extended_dataset_checks() {
  print_section "extended-dataset-checks"
  local datasets_response dataset_id detail_response metadata_response preview_response export_header export_file
  datasets_response="$(api_request GET '/datasets')"
  check_success "$datasets_response" "数据集列表"
  dataset_id="$(node -e '
    const payload = JSON.parse(process.argv[1]);
    const first = (payload.data || [])[0];
    if (first?.datasetId) process.stdout.write(String(first.datasetId));
  ' "$datasets_response")"
  if [[ -z "$dataset_id" ]]; then
    echo "[ERROR] 扩展检查失败：当前无可用数据集" >&2
    exit 1
  fi

  detail_response="$(api_request GET "/datasets/${dataset_id}")"
  check_success "$detail_response" "数据集详情"
  metadata_response="$(api_request GET "/metadata/${dataset_id}")"
  check_success "$metadata_response" "元数据查询"
  preview_response="$(api_request GET "/preview/${dataset_id}?pageNum=1&pageSize=5")"
  check_success "$preview_response" "数据预览"

  export_header="${TMP_DIR}/dataset-export-header.txt"
  export_file="${TMP_DIR}/dataset-export.csv"
  curl -sS -D "$export_header" -o "$export_file" \
    -H "Authorization: Bearer ${TOKEN}" \
    "${BASE_URL}/datasets/${dataset_id}/export?format=csv"
  if [[ ! -s "$export_file" ]]; then
    echo "[ERROR] 数据集导出文件为空" >&2
    exit 1
  fi
  if ! rg -qi "content-disposition:.*filename\\*=" "$export_header"; then
    echo "[ERROR] 数据集导出缺少标准下载文件名头" >&2
    exit 1
  fi
  echo "[OK] 数据集管理专项检查通过（datasetId=${dataset_id}）"
}

extended_governance_operator_checks() {
  print_section "extended-governance-operator-checks"
  local datasets_response dataset_id operators_response target_key original_status disable_response fail_response restore_response
  datasets_response="$(api_request GET '/datasets')"
  check_success "$datasets_response" "治理检查-数据集列表"
  dataset_id="$(node -e '
    const payload = JSON.parse(process.argv[1]);
    const first = (payload.data || [])[0];
    if (first?.datasetId) process.stdout.write(String(first.datasetId));
  ' "$datasets_response")"
  if [[ -z "$dataset_id" ]]; then
    echo "[ERROR] 治理检查失败：当前无可用数据集" >&2
    exit 1
  fi

  operators_response="$(api_request GET '/governance/operators?includeDisabled=true')"
  check_success "$operators_response" "治理算子列表"
  target_key="$(node -e '
    const payload = JSON.parse(process.argv[1]);
    const items = payload.data || [];
    const preferred = items.find(item => item.operatorKey === "TIME_NORMALIZE");
    const picked = preferred || items[0];
    if (picked?.operatorKey) process.stdout.write(picked.operatorKey);
  ' "$operators_response")"
  if [[ -z "$target_key" ]]; then
    echo "[ERROR] 治理检查失败：未找到可用算子" >&2
    exit 1
  fi
  original_status="$(node -e '
    const payload = JSON.parse(process.argv[1]);
    const key = process.argv[2];
    const items = payload.data || [];
    const matched = items.find(item => item.operatorKey === key);
    process.stdout.write((matched?.status || "ENABLED").toUpperCase());
  ' "$operators_response" "$target_key")"

  disable_response="$(api_request POST "/governance/operators/${target_key}/status" '{"status":"DISABLED"}')"
  check_success "$disable_response" "停用治理算子"

  fail_response="$(api_request POST '/governance/execute' "{\"datasetId\":${dataset_id},\"operatorChain\":[{\"operatorKey\":\"${target_key}\",\"params\":{\"field\":\"order_time\"}}],\"executionName\":\"round7_operator_disabled_check\"}")"
  check_failed "$fail_response" "禁用算子执行拦截"

  restore_response="$(api_request POST "/governance/operators/${target_key}/status" "{\"status\":\"${original_status}\"}")"
  check_success "$restore_response" "恢复治理算子状态"
  echo "[OK] 治理算子启停检查通过（operatorKey=${target_key}）"
}

extended_log_filter_checks() {
  print_section "extended-log-filter-checks"
  local base_logs governance_failed_logs first_operator_user by_operator_logs range_logs detail_response log_id
  base_logs="$(api_request GET '/task-logs')"
  check_success "$base_logs" "日志列表"
  log_id="$(node -e '
    const payload = JSON.parse(process.argv[1]);
    const first = (payload.data || [])[0];
    if (first?.logId) process.stdout.write(String(first.logId));
  ' "$base_logs")"
  if [[ -z "$log_id" ]]; then
    echo "[INFO] 当前无日志，尝试用 demo 脚本补数据"
    BASE_URL="$BASE_URL" USERNAME="$USERNAME" PASSWORD="$PASSWORD" STATE_FILE="$STATE_FILE" bash scripts/ecommerce-demo-smoke.sh task-fail-log
    base_logs="$(api_request GET '/task-logs')"
    check_success "$base_logs" "补数后日志列表"
  fi

  governance_failed_logs="$(api_request GET '/task-logs?taskType=GOVERNANCE&status=FAILED')"
  check_success "$governance_failed_logs" "日志筛选（类型+状态）"
  node -e '
    const payload = JSON.parse(process.argv[1]);
    const items = payload.data || [];
    if (!items.length) process.exit(0);
    const invalid = items.find(item => item.taskType !== "GOVERNANCE" || item.status !== "FAILED");
    if (invalid) process.exit(2);
  ' "$governance_failed_logs" || {
    echo "[ERROR] 日志筛选返回了不符合条件的数据" >&2
    exit 1
  }

  first_operator_user="$(node -e '
    const payload = JSON.parse(process.argv[1]);
    const items = payload.data || [];
    const matched = items.find(item => item.operatorUser !== null && item.operatorUser !== undefined);
    if (matched) process.stdout.write(String(matched.operatorUser));
  ' "$base_logs")"
  if [[ -n "$first_operator_user" ]]; then
    by_operator_logs="$(api_request GET "/task-logs?operatorUser=${first_operator_user}")"
    check_success "$by_operator_logs" "日志筛选（操作人）"
  fi

  range_logs="$(api_request GET '/task-logs?startTime=2020-01-01&endTime=2099-12-31')"
  check_success "$range_logs" "日志筛选（时间范围）"

  log_id="$(node -e '
    const payload = JSON.parse(process.argv[1]);
    const first = (payload.data || [])[0];
    if (first?.logId) process.stdout.write(String(first.logId));
  ' "$base_logs")"
  if [[ -n "$log_id" ]]; then
    detail_response="$(api_request GET "/task-logs/${log_id}")"
    check_success "$detail_response" "日志详情"
    node -e '
      const payload = JSON.parse(process.argv[1]);
      const detail = payload.data || {};
      if (!("operatorName" in detail)) process.exit(2);
    ' "$detail_response" || {
      echo "[ERROR] 日志详情缺少 operatorName 字段" >&2
      exit 1
    }
  fi

  echo "[OK] 日志服务端筛选检查通过"
}

run_extended_checks() {
  login
  extended_dataset_checks
  extended_governance_operator_checks
  extended_log_filter_checks
  echo "[OK] extended checks 全部通过"
}

run_gate() {
  print_section "freeze-gate-build"
  (cd backend && mvn -q -DskipTests compile)
  (cd frontend && npm run build >/dev/null)
  echo "[OK] 构建门禁通过"
  run_all
}

run_all() {
  run_api_smoke
  run_demo_smoke
  run_extended_checks
  if [[ "${RUN_MYSQL}" == "true" ]]; then
    run_mysql_regression
  fi
  print_section "summary"
  echo "[OK] regression-suite 执行完成"
}

case "$COMMAND" in
  all)
    run_all
    ;;
  smoke)
    run_api_smoke
    ;;
  demo)
    run_demo_smoke
    ;;
  extended)
    run_extended_checks
    ;;
  mysql)
    run_mysql_regression
    ;;
  gate)
    run_gate
    ;;
  *)
    echo "用法: bash scripts/regression-suite.sh [all|smoke|demo|extended|mysql|gate]" >&2
    exit 1
    ;;
esac
