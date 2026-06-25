#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080/api}"
APP_USERNAME="${APP_USERNAME:-admin}"
APP_PASSWORD="${APP_PASSWORD:-${PASSWORD:-admin123}}"
STATE_FILE="${STATE_FILE:-/tmp/ecommerce-demo-smoke-state.json}"

COMMAND="${1:-all}"
TOKEN=""
TMP_DIR="$(mktemp -d /tmp/ecommerce-demo-smoke.XXXXXX)"
trap 'rm -rf "$TMP_DIR"' EXIT

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "[ERROR] 缺少必要命令: $1" >&2
    exit 1
  fi
}

for cmd in curl node mktemp; do
  require_command "$cmd"
done

state_init() {
  if [[ ! -f "$STATE_FILE" ]]; then
    echo '{}' > "$STATE_FILE"
  fi
}

state_set() {
  local key="$1"
  local raw_json="$2"
  node -e '
    const fs = require("fs");
    const file = process.argv[1];
    const key = process.argv[2];
    const raw = process.argv[3];
    const payload = fs.existsSync(file) ? JSON.parse(fs.readFileSync(file, "utf8") || "{}") : {};
    payload[key] = JSON.parse(raw);
    fs.writeFileSync(file, JSON.stringify(payload, null, 2));
  ' "$STATE_FILE" "$key" "$raw_json"
}

state_get() {
  local key="$1"
  node -e '
    const fs = require("fs");
    const file = process.argv[1];
    const key = process.argv[2];
    if (!fs.existsSync(file)) process.exit(0);
    const payload = JSON.parse(fs.readFileSync(file, "utf8") || "{}");
    const value = payload[key];
    if (value === undefined || value === null) process.exit(0);
    process.stdout.write(String(value));
  ' "$STATE_FILE" "$key"
}

json_quote() {
  node -e 'process.stdout.write(JSON.stringify(process.argv[1]));' "$1"
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

print_section() {
  echo
  echo "== $1 =="
}

login() {
  print_section "login"
  local response
  response="$(curl -sS -X POST -H 'Content-Type: application/json' -d "{\"username\":\"${APP_USERNAME}\",\"password\":\"${APP_PASSWORD}\"}" "${BASE_URL}/auth/login")"
  check_success "$response" "登录"
  TOKEN="$(json_get "$response" "data.token")"
  echo "[OK] 登录成功，user=${APP_USERNAME}"
}

ensure_file_source() {
  local response source_id
  response="$(api_request GET '/data-sources')"
  check_success "$response" "查询数据源"
  source_id="$(node -e '
    const payload = JSON.parse(process.argv[1]);
    const items = payload.data || [];
    const target = items.find(item => item.sourceType === "FILE") || items.find(item => item.sourceType === "MYSQL");
    if (target?.sourceId) process.stdout.write(String(target.sourceId));
  ' "$response")"
  if [[ -z "$source_id" ]]; then
    local source_name create_resp
    source_name="demo_file_source_$(date +%Y%m%d%H%M%S)"
    create_resp="$(api_request POST '/data-sources' "{\"sourceName\":\"${source_name}\",\"sourceType\":\"FILE\",\"description\":\"demo smoke auto source\"}")"
    check_success "$create_resp" "创建文件数据源"
    source_id="$(json_get "$create_resp" "data.sourceId")"
  fi
  state_set "fileSourceId" "$source_id"
  echo "[OK] fileSourceId=${source_id}"
}

dataset_exists() {
  local dataset_id="$1"
  if [[ -z "$dataset_id" ]]; then
    return 1
  fi
  local response code
  response="$(api_request GET "/datasets/${dataset_id}")"
  code="$(json_get "$response" "code" 2>/dev/null || true)"
  [[ "$code" == "0" ]]
}

dataset_matches_profile() {
  local dataset_id="$1"
  local expected_domain="$2"
  local required_fields_csv="$3"
  if ! dataset_exists "$dataset_id"; then
    return 1
  fi
  local detail_response detail_code actual_domain metadata_response metadata_code
  detail_response="$(api_request GET "/datasets/${dataset_id}")"
  detail_code="$(json_get "$detail_response" "code" 2>/dev/null || true)"
  if [[ "$detail_code" != "0" ]]; then
    return 1
  fi
  actual_domain="$(json_get "$detail_response" "data.businessDomain" 2>/dev/null || true)"
  if [[ -n "$expected_domain" && "$actual_domain" != "$expected_domain" ]]; then
    return 1
  fi
  metadata_response="$(api_request GET "/metadata/${dataset_id}")"
  metadata_code="$(json_get "$metadata_response" "code" 2>/dev/null || true)"
  if [[ "$metadata_code" != "0" ]]; then
    return 1
  fi
  node -e '
    const payload = JSON.parse(process.argv[1] || "{}");
    const required = (process.argv[2] || "")
      .split(",")
      .map(item => item.trim().toLowerCase())
      .filter(Boolean);
    const fields = new Set((payload.data || []).map(item => String(item.fieldName || "").toLowerCase()));
    const ok = required.every(field => fields.has(field));
    process.exit(ok ? 0 : 1);
  ' "$metadata_response" "$required_fields_csv"
}

import_dataset_from_file() {
  local key="$1"
  local dataset_name="$2"
  local business_domain="$3"
  local file_path="$4"
  local source_id import_response dataset_id
  source_id="$(state_get fileSourceId)"
  if [[ -z "$source_id" ]]; then
    ensure_file_source
    source_id="$(state_get fileSourceId)"
  fi
  import_response="$(curl -sS -X POST \
    -H "Authorization: Bearer ${TOKEN}" \
    -F "file=@${file_path}" \
    -F "datasetName=${dataset_name}" \
    -F "businessDomain=${business_domain}" \
    -F "sourceId=${source_id}" \
    "${BASE_URL}/imports/file")"
  check_success "$import_response" "导入数据集 ${dataset_name}"
  dataset_id="$(json_get "$import_response" "data.datasetId")"
  state_set "$key" "$dataset_id"
  echo "[OK] ${key}=${dataset_id}"
}

ensure_trade_dataset() {
  local day0 day1 day2 file_path suffix
  day0="$(node -e 'const d=new Date();process.stdout.write(d.toISOString().slice(0,10));')"
  day1="$(node -e 'const d=new Date();d.setDate(d.getDate()-1);process.stdout.write(d.toISOString().slice(0,10));')"
  day2="$(node -e 'const d=new Date();d.setDate(d.getDate()-2);process.stdout.write(d.toISOString().slice(0,10));')"
  suffix="$(date +%H%M%S)_$RANDOM"
  file_path="${TMP_DIR}/trade.csv"
  cat > "$file_path" <<CSV
order_id,product_id,product_name,category,quantity,amount,order_time,order_status
ORD-1001,SKU-1001,机械键盘,3C数码,2,299.00,${day0} 10:12:00,PAID
ORD-1002,SKU-1002,无线鼠标,3C数码,1,129.00,${day0} 13:45:00,PAID
ORD-1003,SKU-2001,牛奶,食品生鲜,3,78.00,${day1} 09:00:00,PAID
ORD-1004,SKU-3001,运动鞋,服饰鞋包,1,369.00,${day1} 18:22:00,PENDING
ORD-1005,SKU-1002,无线鼠标,3C数码,4,516.00,${day2} 16:20:00,PAID
CSV
  import_dataset_from_file "tradeDatasetId" "demo_trade_${day0}_${suffix}" "TRADE" "$file_path"
}

ensure_product_dataset() {
  local existing_id file_path suffix
  existing_id="$(state_get productDatasetId)"
  if dataset_matches_profile "$existing_id" "PRODUCT" "product_id,product_name"; then
    echo "[OK] 复用 productDatasetId=${existing_id}"
    return
  fi
  suffix="$(date +%H%M%S)_$RANDOM"
  file_path="${TMP_DIR}/product.csv"
  cat > "$file_path" <<CSV
product_id,product_name,category,status
SKU-1001,机械键盘,3C数码,ENABLED
SKU-1002,无线鼠标,3C数码,ENABLED
SKU-2001,牛奶,食品生鲜,ENABLED
SKU-3001,运动鞋,服饰鞋包,ENABLED
CSV
  import_dataset_from_file "productDatasetId" "demo_product_$(date +%Y%m%d_%H%M%S)_${suffix}" "PRODUCT" "$file_path"
}

ensure_inventory_dataset() {
  local existing_id file_path suffix
  existing_id="$(state_get inventoryDatasetId)"
  if dataset_matches_profile "$existing_id" "INVENTORY" "product_id,stock"; then
    echo "[OK] 复用 inventoryDatasetId=${existing_id}"
    return
  fi
  suffix="$(date +%H%M%S)_$RANDOM"
  file_path="${TMP_DIR}/inventory.csv"
  cat > "$file_path" <<CSV
product_id,product_name,stock,warehouse
SKU-1001,机械键盘,7,华东仓
SKU-1002,无线鼠标,5,华北仓
SKU-2001,牛奶,40,华南仓
SKU-3001,运动鞋,9,华东仓
CSV
  import_dataset_from_file "inventoryDatasetId" "demo_inventory_$(date +%Y%m%d_%H%M%S)_${suffix}" "INVENTORY" "$file_path"
}

prepare_demo_datasets() {
  print_section "prepare-demo-datasets"
  ensure_file_source
  ensure_trade_dataset
  ensure_product_dataset
  ensure_inventory_dataset
}

dashboard_check() {
  print_section "dashboard-ecommerce-check"
  prepare_demo_datasets
  local response
  response="$(api_request GET '/dashboard/ecommerce')"
  check_success "$response" "电商首页指标"
  node -e '
    const payload = JSON.parse(process.argv[1]);
    const data = payload.data || {};
    if (!Array.isArray(data.orderTrend) || data.orderTrend.length === 0) process.exit(2);
    if (!Array.isArray(data.salesTrend) || data.salesTrend.length === 0) process.exit(3);
    if (!Array.isArray(data.topProducts) || data.topProducts.length === 0) process.exit(4);
    if (typeof data.lowStockCount !== "number") process.exit(5);
  ' "$response" || {
    echo "[ERROR] 首页指标结构校验失败: $response" >&2
    exit 1
  }
  echo "[OK] dashboard/ecommerce 指标可展示"
}

integration_save() {
  print_section "integration-save"
  prepare_demo_datasets
  local left_id right_id integration_response save_response saved_dataset_id preview_response
  left_id="$(state_get tradeDatasetId)"
  right_id="$(state_get productDatasetId)"
  integration_response="$(api_request POST '/queries/integration' "{\"leftDatasetId\":${left_id},\"rightDatasetId\":${right_id},\"mode\":\"JOIN\",\"leftField\":\"product_id\",\"rightField\":\"product_id\",\"limit\":100}")"
  check_success "$integration_response" "数据集成查询"
  save_response="$(api_request POST '/queries/integration/save' "{\"leftDatasetId\":${left_id},\"rightDatasetId\":${right_id},\"mode\":\"JOIN\",\"leftField\":\"product_id\",\"rightField\":\"product_id\",\"limit\":100,\"outputDatasetName\":\"demo_integration_$(date +%Y%m%d%H%M%S)\",\"outputBusinessDomain\":\"TRADE\"}")"
  check_success "$save_response" "保存集成结果"
  saved_dataset_id="$(json_get "$save_response" "data.datasetId")"
  state_set "integrationDatasetId" "$saved_dataset_id"
  preview_response="$(api_request GET "/preview/${saved_dataset_id}?pageNum=1&pageSize=5")"
  check_success "$preview_response" "预览集成结果数据集"
  echo "[OK] integrationDatasetId=${saved_dataset_id}"
}

governance_success() {
  print_section "governance-success"
  prepare_demo_datasets
  local dataset_id response log_id
  dataset_id="$(state_get tradeDatasetId)"
  response="$(api_request POST '/governance/execute' "{\"datasetId\":${dataset_id},\"operatorChain\":[{\"operatorKey\":\"ORDER_DEDUP\",\"params\":{\"field\":\"order_id\"}},{\"operatorKey\":\"AMOUNT_NORMALIZE\",\"params\":{\"field\":\"amount\"}},{\"operatorKey\":\"TIME_NORMALIZE\",\"params\":{\"field\":\"order_time\"}}],\"executionName\":\"demo_governance_success\"}")"
  check_success "$response" "治理执行成功场景"
  log_id="$(json_get "$response" "data.logRef")"
  state_set "governanceSuccessLogId" "$log_id"
  echo "[OK] governanceSuccessLogId=${log_id}"
}

governance_fail() {
  print_section "governance-fail"
  prepare_demo_datasets
  local dataset_id response message failed_log_id
  dataset_id="$(state_get tradeDatasetId)"
  response="$(api_request POST '/governance/execute' "{\"datasetId\":${dataset_id},\"operatorChain\":[{\"operatorKey\":\"FIELD_CONVERT\",\"params\":{\"field\":\"product_name\",\"transform\":\"NUMBER\"}}],\"executionName\":\"demo_governance_fail\"}")"
  check_failed "$response" "治理执行失败场景"
  message="$(json_get "$response" "message" 2>/dev/null || true)"
  failed_log_id="$(node -e '
    const text = process.argv[1] || "";
    const matched = text.match(/日志编号[:：]\\s*(\\d+)/);
    if (matched) process.stdout.write(matched[1]);
  ' "$message")"
  if [[ -n "$failed_log_id" ]]; then
    state_set "governanceFailureLogId" "$failed_log_id"
  fi
  state_set "governanceFailureMessage" "$(json_quote "$message")"
  echo "[OK] 治理失败已触发${failed_log_id:+，governanceFailureLogId=${failed_log_id}}"
}

task_fail_log() {
  print_section "task-fail-log"
  prepare_demo_datasets
  local dataset_id flow_response flow_id task_response task_id trigger_response trigger_status trigger_log_id
  dataset_id="$(state_get tradeDatasetId)"
  flow_response="$(api_request POST '/governance/flows' "{\"flowName\":\"demo_fail_flow_$(date +%Y%m%d%H%M%S)\",\"datasetId\":${dataset_id},\"operatorChain\":[{\"operatorKey\":\"FIELD_CONVERT\",\"params\":{\"field\":\"product_name\",\"transform\":\"NUMBER\"}}]}")"
  check_success "$flow_response" "创建失败治理流程"
  flow_id="$(json_get "$flow_response" "data.flowId")"
  task_response="$(api_request POST '/tasks' "{\"taskName\":\"demo_fail_task_$(date +%Y%m%d%H%M%S)\",\"taskType\":\"GOVERNANCE\",\"targetId\":${flow_id},\"cronExpr\":\"0 */10 * * * *\",\"retryPolicy\":1,\"description\":\"demo fail task\",\"status\":\"ENABLED\"}")"
  check_success "$task_response" "创建失败任务"
  task_id="$(json_get "$task_response" "data.taskId")"
  trigger_response="$(api_request POST "/tasks/${task_id}/trigger")"
  check_success "$trigger_response" "触发失败任务"
  trigger_status="$(json_get "$trigger_response" "data.status")"
  if [[ "$trigger_status" != "FAILED" ]]; then
    echo "[ERROR] 失败任务触发后状态异常: ${trigger_response}" >&2
    exit 1
  fi
  trigger_log_id="$(json_get "$trigger_response" "data.logId")"
  state_set "failureTaskId" "$task_id"
  state_set "failureTaskLogId" "$trigger_log_id"
  echo "[OK] failureTaskId=${task_id}, failureTaskLogId=${trigger_log_id}"
}

log_detail() {
  print_section "log-detail"
  local log_id response
  log_id="$(state_get failureTaskLogId)"
  if [[ -z "$log_id" ]]; then
    task_fail_log
    log_id="$(state_get failureTaskLogId)"
  fi
  response="$(api_request GET "/task-logs/${log_id}")"
  check_success "$response" "日志详情查询"
  node -e '
    const payload = JSON.parse(process.argv[1]);
    const detail = payload.data || {};
    if (!detail.inputParams || Object.keys(detail.inputParams).length === 0) process.exit(2);
    if (!Array.isArray(detail.executionSteps) || detail.executionSteps.length === 0) process.exit(3);
    if (!detail.failureReason || !detail.failureReason.reason) process.exit(4);
  ' "$response" || {
    echo "[ERROR] 日志详情结构化字段缺失: ${response}" >&2
    exit 1
  }
  echo "[OK] logId=${log_id}，日志详情包含 inputParams/executionSteps/failureReason"
}

run_all() {
  login
  dashboard_check
  integration_save
  governance_success
  governance_fail
  task_fail_log
  log_detail
  echo
  echo "Smoke demo passed"
  echo "stateFile=${STATE_FILE}"
  echo "tradeDatasetId=$(state_get tradeDatasetId)"
  echo "productDatasetId=$(state_get productDatasetId)"
  echo "inventoryDatasetId=$(state_get inventoryDatasetId)"
  echo "integrationDatasetId=$(state_get integrationDatasetId)"
  echo "governanceSuccessLogId=$(state_get governanceSuccessLogId)"
  echo "failureTaskLogId=$(state_get failureTaskLogId)"
}

state_init

case "$COMMAND" in
  all)
    run_all
    ;;
  login)
    login
    ;;
  dashboard-check)
    login
    dashboard_check
    ;;
  integration-save)
    login
    integration_save
    ;;
  governance-success)
    login
    governance_success
    ;;
  governance-fail)
    login
    governance_fail
    ;;
  task-fail-log)
    login
    task_fail_log
    ;;
  log-detail)
    login
    log_detail
    ;;
  *)
    echo "Usage: $0 {all|login|dashboard-check|integration-save|governance-success|governance-fail|task-fail-log|log-detail}" >&2
    exit 1
    ;;
esac
