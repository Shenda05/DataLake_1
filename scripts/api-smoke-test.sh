#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080/api}"
APP_USERNAME="${APP_USERNAME:-admin}"
APP_PASSWORD="${APP_PASSWORD:-${PASSWORD:-admin123}}"

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

request() {
  local method="$1"
  local url="$2"
  local body="${3:-}"
  if [[ -n "${TOKEN:-}" ]]; then
    if [[ -n "$body" ]]; then
      curl -sS -X "$method" -H "Authorization: Bearer ${TOKEN}" -H 'Content-Type: application/json' -d "$body" "$url"
    else
      curl -sS -X "$method" -H "Authorization: Bearer ${TOKEN}" "$url"
    fi
  else
    if [[ -n "$body" ]]; then
      curl -sS -X "$method" -H 'Content-Type: application/json' -d "$body" "$url"
    else
      curl -sS -X "$method" "$url"
    fi
  fi
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

timestamp="$(date +%Y%m%d%H%M%S)"
tmp_csv="$(mktemp /tmp/data-lake-smoke.XXXXXX.csv)"
trap 'rm -f "$tmp_csv"' EXIT

cat > "$tmp_csv" <<'CSV'
order_id,product_id,product_name,category,quantity,amount,order_status,order_time
ORD-1001,SKU-1001,机械键盘,3C数码,2,299.90,paid,2026-03-05 10:00:00
ORD-1002,SKU-1002,无线鼠标,3C数码,1,129.00,completed,2026-03-12 09:30:00
ORD-1003,SKU-2001,牛奶,食品生鲜,3,78.00,pending,2026-02-25 08:10:00
CSV

echo "[1/8] 登录管理员账号"
login_response="$(request POST "${BASE_URL}/auth/login" "{\"username\":\"${APP_USERNAME}\",\"password\":\"${APP_PASSWORD}\"}")"
check_success "$login_response" "登录"
TOKEN="$(json_get "$login_response" "data.token")"

echo "[2/8] 创建文件数据源"
source_name="smoke_file_source_${timestamp}"
source_response="$(request POST "${BASE_URL}/data-sources" "{\"sourceName\":\"${source_name}\",\"sourceType\":\"FILE\",\"description\":\"Smoke test source\"}")"
check_success "$source_response" "创建数据源"
source_id="$(json_get "$source_response" "data.sourceId")"

echo "[3/8] 导入 CSV 文件"
dataset_name="smoke_dataset_${timestamp}"
import_response="$(curl -sS -X POST -H "Authorization: Bearer ${TOKEN}" -F "file=@${tmp_csv}" -F "datasetName=${dataset_name}" -F "businessDomain=TRADE" -F "sourceId=${source_id}" "${BASE_URL}/imports/file")"
check_success "$import_response" "文件导入"
dataset_id="$(json_get "$import_response" "data.datasetId")"
import_id="$(json_get "$import_response" "data.importId")"

echo "[4/8] 执行条件查询与 SQL 查询"
filter_response="$(request POST "${BASE_URL}/queries/filter" "{\"datasetId\":${dataset_id},\"field\":\"product_name\",\"operator\":\"LIKE\",\"value\":\"鼠标\",\"pageNum\":1,\"pageSize\":20}")"
check_success "$filter_response" "条件查询"
multi_filter_response="$(request POST "${BASE_URL}/queries/filter" "{\"datasetId\":${dataset_id},\"logic\":\"OR\",\"conditions\":[{\"field\":\"order_status\",\"operator\":\"EQ\",\"value\":\"completed\"},{\"field\":\"amount\",\"operator\":\"GTE\",\"value\":\"200\"}],\"sortField\":\"amount\",\"sortOrder\":\"DESC\",\"pageNum\":1,\"pageSize\":20}")"
check_success "$multi_filter_response" "多条件组合查询"
time_range_response="$(request POST "${BASE_URL}/queries/filter" "{\"datasetId\":${dataset_id},\"logic\":\"AND\",\"conditions\":[{\"field\":\"category\",\"operator\":\"LIKE\",\"value\":\"3C\"},{\"field\":\"order_time\",\"operator\":\"TIME_RANGE\",\"value\":\"2026-03-01 00:00:00\",\"valueTo\":\"2026-03-31 23:59:59\"}],\"pageNum\":1,\"pageSize\":20}")"
check_success "$time_range_response" "时间范围条件查询"
sql_response="$(request POST "${BASE_URL}/queries/sql" "{\"datasetId\":${dataset_id},\"sql\":\"SELECT * FROM dataset LIMIT 20\"}")"
check_success "$sql_response" "SQL 查询"
sql_page_response="$(request POST "${BASE_URL}/queries/sql/page" "{\"datasetId\":${dataset_id},\"sql\":\"SELECT product_name,amount,order_status FROM dataset\",\"sortField\":\"amount\",\"sortOrder\":\"DESC\",\"pageNum\":1,\"pageSize\":2}")"
check_success "$sql_page_response" "SQL 分页排序查询"

echo "[5/8] 创建治理流程"
flow_name="smoke_flow_${timestamp}"
flow_response="$(request POST "${BASE_URL}/governance/flows" "{\"flowName\":\"${flow_name}\",\"datasetId\":${dataset_id},\"operatorChain\":[{\"operatorKey\":\"ORDER_DEDUP\",\"params\":{\"field\":\"order_id\"}},{\"operatorKey\":\"AMOUNT_NORMALIZE\",\"params\":{\"field\":\"amount\"}},{\"operatorKey\":\"TIME_NORMALIZE\",\"params\":{\"field\":\"order_time\"}},{\"operatorKey\":\"STATUS_NORMALIZE\",\"params\":{\"field\":\"order_status\"}}]}")"
check_success "$flow_response" "创建治理流程"
flow_id="$(json_get "$flow_response" "data.flowId")"

echo "[6/8] 创建并触发治理任务"
governance_task_response="$(request POST "${BASE_URL}/tasks" "{\"taskName\":\"smoke_gov_task_${timestamp}\",\"taskType\":\"GOVERNANCE\",\"targetId\":${flow_id},\"cronExpr\":\"0 */10 * * * *\",\"retryPolicy\":1,\"description\":\"Smoke governance task\",\"status\":\"ENABLED\"}")"
check_success "$governance_task_response" "创建治理任务"
governance_task_id="$(json_get "$governance_task_response" "data.taskId")"
trigger_governance_response="$(request POST "${BASE_URL}/tasks/${governance_task_id}/trigger")"
check_success "$trigger_governance_response" "触发治理任务"

echo "[7/8] 创建并触发导入任务"
import_task_response="$(request POST "${BASE_URL}/tasks" "{\"taskName\":\"smoke_import_task_${timestamp}\",\"taskType\":\"IMPORT\",\"targetId\":${import_id},\"cronExpr\":\"0 */10 * * * *\",\"retryPolicy\":1,\"description\":\"Smoke import task\",\"status\":\"ENABLED\"}")"
check_success "$import_task_response" "创建导入任务"
import_task_id="$(json_get "$import_task_response" "data.taskId")"
trigger_import_response="$(request POST "${BASE_URL}/tasks/${import_task_id}/trigger")"
check_success "$trigger_import_response" "触发导入任务"

echo "[8/8] 获取日志列表"
logs_response="$(request GET "${BASE_URL}/task-logs")"
check_success "$logs_response" "获取日志列表"
log_count="$(node -e 'const data = JSON.parse(process.argv[1]); process.stdout.write(String((data.data || []).length));' "$logs_response")"

echo
echo "Smoke test passed"
echo "baseUrl=${BASE_URL}"
echo "sourceId=${source_id}"
echo "datasetId=${dataset_id}"
echo "importId=${import_id}"
echo "governanceTaskId=${governance_task_id}"
echo "importTaskId=${import_task_id}"
echo "logCount=${log_count}"
