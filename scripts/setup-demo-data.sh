#!/bin/bash
# ============================================================
# 数据湖管理平台 - 演示数据一键初始化脚本
# 适用于 Windows Git Bash / Linux / macOS
#
# 用法:
#   bash scripts/setup-demo-data.sh [MySQL密码]
#   bash scripts/setup-demo-data.sh root
#
# 前置条件:
#   - MySQL 已安装并运行 (默认 root@localhost)
#   - Python 3 已安装 (需 openpyxl 库: pip install openpyxl)
#   - Java 17+ 和 Maven 已安装
#   - 已执行过 npm install (前端依赖)
# ============================================================

set -e

MYSQL_PASS="${1:-root}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
STORAGE_DIR="$PROJECT_DIR/storage"
API_BASE="http://localhost:8080/api"

echo "========================================"
echo " 数据湖管理平台 - 演示数据初始化"
echo "========================================"
echo ""
echo "MySQL root 密码: $MYSQL_PASS"
echo "项目目录: $PROJECT_DIR"
echo ""

# ---------- Step 1: 生成数据文件 ----------
echo "[1/6] 生成演示数据文件..."
mkdir -p "$STORAGE_DIR"

python3 << PYEOF
import csv, json, os, random
from datetime import datetime, timedelta

storage = "$STORAGE_DIR"
storage = os.path.normpath(storage)
os.makedirs(storage, exist_ok=True)

# ==================== 商品数据 CSV ====================
print("  -> 商品数据 CSV (32条)...")
product_headers = [
    "product_id","product_name","category","sub_category","brand",
    "price","cost","stock_quantity","warehouse","sales_volume",
    "sales_amount","rating","review_count","supplier","launch_date",
    "status","is_free_shipping","weight_kg","sku_code","remarks"
]
products = [
    ["PROD-001","iPhone 15 Pro Max 256GB","数码","智能手机","Apple",9999.00,7200.00,320,"华东仓",1856,18557344.00,4.8,3260,"苹果中国","2025-09-22","在售","是",0.22,"SKU-DG-001","年度旗舰机"],
    ["PROD-002","MacBook Pro 14英寸 M4芯片","数码","笔记本电脑","Apple",14999.00,11000.00,150,"华东仓",890,13329110.00,4.9,1520,"苹果中国","2025-10-15","在售","是",1.55,"SKU-DG-002","高性能办公本"],
    ["PROD-003","华为 Mate 70 Pro","数码","智能手机","华为",6999.00,5000.00,500,"华南仓",2100,14697900.00,4.7,4500,"华为终端","2025-11-01","在售","是",0.21,"SKU-DG-003","国产旗舰"],
    ["PROD-004","索尼 WH-1000XM6 降噪耳机","数码","耳机","Sony",2499.00,1600.00,800,"华东仓",3200,7996800.00,4.8,6800,"索尼中国","2025-08-10","在售","否",0.25,"SKU-DG-004","头戴式降噪"],
    ["PROD-005","iPad Air M2 11英寸","数码","平板电脑","Apple",4799.00,3400.00,400,"华北仓",1500,7198500.00,4.7,2900,"苹果中国","2025-06-15","在售","是",0.46,"SKU-DG-005","轻薄学习办公"],
    ["PROD-006","大疆 DJI Mini 4 Pro 无人机","数码","无人机","DJI",4788.00,3200.00,250,"华南仓",980,4692240.00,4.9,2100,"大疆创新","2025-09-01","在售","是",0.25,"SKU-DG-006","249g免注册"],
    ["PROD-007","小米 14 Ultra","数码","智能手机","小米",5999.00,4200.00,600,"华北仓",1800,10798200.00,4.6,3800,"小米科技","2025-10-20","在售","是",0.23,"SKU-DG-007","徕卡影像"],
    ["PROD-008","Apple Watch Ultra 3","数码","智能手表","Apple",6499.00,4500.00,350,"华东仓",720,4679280.00,4.6,1800,"苹果中国","2025-09-10","在售","是",0.06,"SKU-DG-008","户外运动款"],
    ["PROD-009","北欧简约三人沙发","家居","沙发","宜家",3999.00,2400.00,80,"华东仓",450,1799550.00,4.5,960,"宜家家居","2025-03-15","在售","否",45.00,"SKU-JJ-001","可拆洗布艺"],
    ["PROD-010","智能升降桌 1.4m","家居","办公桌","乐歌",2599.00,1600.00,200,"华北仓",680,1767320.00,4.7,1300,"乐歌股份","2025-05-20","在售","否",32.00,"SKU-JJ-002","电动升降记忆"],
    ["PROD-011","乳胶记忆棉床垫 1.8m","家居","床垫","慕思",4599.00,2800.00,120,"华南仓",380,1747620.00,4.8,750,"慕思股份","2025-04-10","在售","否",38.00,"SKU-JJ-003","护脊款"],
    ["PROD-012","实木书柜 六层","家居","柜子","源氏木语",1899.00,1100.00,60,"华东仓",220,417780.00,4.4,480,"源氏木语","2025-02-28","在售","否",55.00,"SKU-JJ-004","北美橡木"],
    ["PROD-013","极简落地灯","家居","灯具","小米有品",399.00,200.00,500,"华北仓",1200,478800.00,4.5,2200,"小米科技","2025-07-05","在售","是",3.20,"SKU-JJ-005","米家智能控制"],
    ["PROD-014","全棉四件套 1.8m","家居","床上用品","水星家纺",599.00,320.00,900,"华南仓",2500,1497500.00,4.7,5200,"水星家纺","2025-06-20","在售","是",2.10,"SKU-JJ-006","60支长绒棉"],
    ["PROD-015","有机坚果大礼包 1.2kg","食品","零食","三只松鼠",168.00,98.00,3000,"华东仓",8500,1428000.00,4.6,18000,"三只松鼠","2025-11-10","在售","是",1.20,"SKU-SP-001","年货爆款"],
    ["PROD-016","特级初榨橄榄油 500ml","食品","粮油调味","金龙鱼",89.90,52.00,2500,"华北仓",4200,377580.00,4.4,8500,"益海嘉里","2025-08-01","在售","是",0.50,"SKU-SP-002","西班牙进口"],
    ["PROD-017","冻干咖啡粉 30条装","食品","饮料冲调","三顿半",149.00,85.00,1800,"华南仓",5600,834400.00,4.8,12000,"三顿半","2025-09-15","在售","是",0.30,"SKU-SP-003","冷萃即溶"],
    ["PROD-018","有机纯牛奶 250ml*24盒","食品","乳制品","蒙牛",79.90,48.00,5000,"华北仓",12000,958800.00,4.5,25000,"蒙牛乳业","2025-10-01","在售","是",6.00,"SKU-SP-004","3.6g蛋白质"],
    ["PROD-019","五常大米 5kg","食品","米面杂粮","十月稻田",69.90,40.00,4000,"华东仓",7800,545220.00,4.7,16000,"十月稻田","2025-09-25","在售","否",5.00,"SKU-SP-005","地理标志产品"],
    ["PROD-020","黑巧克力礼盒 500g","食品","零食","德芙",129.00,70.00,2000,"华南仓",3800,490200.00,4.5,7500,"玛氏食品","2025-11-20","在售","是",0.50,"SKU-SP-006","72%可可"],
    ["PROD-021","男士轻薄羽绒服","服装","男装","优衣库",599.00,350.00,1500,"华东仓",3200,1916800.00,4.6,6800,"迅销中国","2025-10-10","在售","是",0.35,"SKU-FZ-001","防泼水"],
    ["PROD-022","女士羊绒大衣","服装","女装","鄂尔多斯",2599.00,1500.00,300,"华北仓",450,1169550.00,4.7,920,"鄂尔多斯","2025-11-05","在售","是",1.20,"SKU-FZ-002","100%山羊绒"],
    ["PROD-023","运动跑鞋 飞马40","服装","运动鞋","Nike",899.00,520.00,1200,"华南仓",4200,3775800.00,4.8,9500,"耐克中国","2025-07-20","在售","是",0.80,"SKU-FZ-003","Zoom Air气垫"],
    ["PROD-024","纯棉短袖T恤 3件装","服装","男装","海澜之家",199.00,90.00,3000,"华东仓",6800,1353200.00,4.3,14000,"海澜之家","2025-04-01","在售","是",0.50,"SKU-FZ-004","新疆棉"],
    ["PROD-025","修护精华液 50ml","美妆","面部精华","兰蔻",1080.00,580.00,600,"华东仓",2800,3024000.00,4.8,5800,"欧莱雅中国","2025-08-20","在售","是",0.15,"SKU-MZ-001","小黑瓶二代"],
    ["PROD-026","防晒霜 SPF50+ 60ml","美妆","防晒","安热沙",238.00,120.00,2500,"华南仓",6500,1547000.00,4.6,14000,"资生堂","2025-05-15","在售","是",0.08,"SKU-MZ-002","小金瓶"],
    ["PROD-027","保湿面膜 10片装","美妆","面膜","敷尔佳",89.00,38.00,4000,"华北仓",9500,845500.00,4.5,20000,"敷尔佳","2025-06-01","在售","是",0.25,"SKU-MZ-003","医美级补水"],
    ["PROD-028","哑光口红礼盒 6支","美妆","口红","完美日记",199.00,85.00,1800,"华南仓",5200,1034800.00,4.4,11000,"逸仙电商","2025-09-01","在售","是",0.20,"SKU-MZ-004","国货之光"],
    # 脏数据：用于测试治理算子
    ["PROD-029","限量版机械键盘","数码","电脑配件","Cherry",1599.00,900.00,0,"华东仓",320,511680.00,4.9,680,"樱桃中国","2025-12-01","缺货","是",1.10,"SKU-DG-009","补货中"],
    ["PROD-030","夏季竹凉席 三件套","家居","床上用品","",299.00,150.00,0,"华北仓",1200,358800.00,4.2,2600,"暂未填写","2025-05-01","已下架","否",4.50,"SKU-JJ-007","季节品已下架"],
    ["PROD-031","进口巧克力礼盒","食品","零食","费列罗",0,88.00,1000,"华东仓",0,0,4.3,4200,"费列罗中国","2025-12-15","待定价","是",0.80,"SKU-SP-007","春节新品待审核"],
    ["PROD-032","儿童智能手表","数码","智能穿戴","小天才",799.00,450.00,450,"华南仓",1500,1198500.00,4.5,3200,"步步高","2025-08-05","在售","是",0.05,"SKU-DG-010","防水定位"],
]
with open(os.path.join(storage, "product_data.csv"), "w", newline="", encoding="utf-8-sig") as f:
    w = csv.writer(f)
    w.writerow(product_headers)
    w.writerows(products)

# ==================== 订单数据 CSV (近7天) ====================
print("  -> 订单数据 CSV (300条, 近7天)...")
pinfo = [
    ("SKU-DG-001","iPhone 15 Pro Max"), ("SKU-DG-002","MacBook Pro 14 M4"),
    ("SKU-DG-003","华为 Mate 70 Pro"), ("SKU-JJ-001","北欧简约沙发"),
    ("SKU-JJ-002","智能升降桌"), ("SKU-SP-001","有机坚果大礼包"),
    ("SKU-SP-002","特级初榨橄榄油"), ("SKU-FZ-001","男士轻薄羽绒服"),
    ("SKU-FZ-003","运动跑鞋飞马40"), ("SKU-MZ-001","修护精华液50ml"),
    ("SKU-MZ-002","安热沙防晒霜"), ("SKU-DG-004","索尼降噪耳机"),
    ("SKU-JJ-005","极简落地灯"), ("SKU-SP-003","冻干咖啡粉"),
    ("SKU-FZ-004","纯棉短袖T恤"),
]
cats = ["数码","数码","数码","家居","家居","食品","食品","服装","服装","美妆","美妆","数码","家居","食品","服装"]
order_headers = ["order_id","product_sku","product_name","category","quantity","unit_price","total_amount","order_time","status","payment_method","customer_city","is_first_order"]
orders = []
for i in range(1, 301):
    idx = i % len(pinfo)
    sku, pname = pinfo[idx]
    hour_offset = random.randint(0, 155)
    order_dt = datetime(2026, 6, 12, 23, 59, 59) - timedelta(hours=hour_offset)
    qty = random.randint(1, 5)
    price = random.choice([99.9,168.0,299.0,599.0,899.0,1299.0,2499.0,4799.0,89.9,238.0])
    amount = round(qty * price, 2)
    orders.append([
        f"ORD-26{order_dt.strftime('%m%d')}{i:04d}",
        sku, pname, cats[idx], qty, price, amount,
        order_dt.strftime("%Y-%m-%d %H:%M:%S"),
        random.choice(["已完成","已完成","已完成","已完成","已完成","已退款"]),
        random.choice(["微信支付","支付宝","银行卡"]),
        random.choice(["上海","北京","深圳","广州","杭州","成都","武汉","南京"]),
        random.choice(["是","否"])
    ])
with open(os.path.join(storage, "orders_recent.csv"), "w", newline="", encoding="utf-8-sig") as f:
    w = csv.writer(f)
    w.writerow(order_headers)
    w.writerows(orders)

# ==================== 库存数据 JSON ====================
print("  -> 库存数据 JSON (50条)...")
whs = ["华东仓","华南仓","华北仓","华中仓","西南仓"]
inventory = []
for i in range(1, 51):
    idx = i % len(pinfo)
    sku, _ = pinfo[idx]
    wh = random.choice(whs)
    qty = random.randint(0, 500)
    inventory.append({
        "product_sku": sku,
        "warehouse": wh,
        "stock_quantity": qty,
        "available_quantity": max(0, qty - random.randint(0, 50)),
        "reserved_quantity": random.randint(0, min(50, qty)),
        "last_restock_date": (datetime(2026, 6, 1) - timedelta(days=random.randint(1, 60))).strftime("%Y-%m-%d"),
        "safety_stock": random.choice([20, 30, 50, 100]),
        "turnover_rate": round(random.uniform(0.5, 5.0), 2)
    })
with open(os.path.join(storage, "inventory_data.json"), "w", encoding="utf-8") as f:
    json.dump(inventory, f, ensure_ascii=False, indent=2)

print("  数据文件生成完成!")
PYEOF

echo ""

# ---------- Step 2: 初始化数据库 ----------
echo "[2/6] 初始化 MySQL 数据库..."
mysql -u root -p"$MYSQL_PASS" --default-character-set=utf8mb4 << SQL
DROP DATABASE IF EXISTS data_lake_platform;
CREATE DATABASE data_lake_platform DEFAULT CHARACTER SET utf8mb4;
SQL
echo "  数据库 data_lake_platform 已重建"

# ---------- Step 3: 启动后端 ----------
echo "[3/6] 启动后端 (Spring Boot)..."
cd "$PROJECT_DIR/backend"

# 先确保 application.yml 密码正确
if command -v python3 &>/dev/null; then
  python3 -c "
import sys
yml_path = '$PROJECT_DIR/backend/src/main/resources/application.yml'
with open(yml_path, 'r') as f:
    content = f.read()
# 替换空密码为指定密码
import re
content = re.sub(r'(password:\s*)$', r'password: $MYSQL_PASS', content, flags=re.MULTILINE)
with open(yml_path, 'w') as f:
    f.write(content)
print('application.yml password updated')
" 2>/dev/null || true
fi

# 杀掉旧进程
jps -l 2>/dev/null | grep DataLakePlatformApplication | awk '{print $1}' | xargs -r kill 2>/dev/null || true
sleep 2

# 后台启动
mvn spring-boot:run -q > /tmp/datalake-backend.log 2>&1 &
BACKEND_PID=$!
echo "  后端 PID: $BACKEND_PID, 等待启动..."

# 等待后端就绪
for i in $(seq 1 30); do
  if curl -s http://localhost:8080/api/auth/login -X POST -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}' 2>/dev/null | grep -q '"code":0'; then
    echo "  后端启动成功! (${i}s)"
    break
  fi
  sleep 1
done

# ---------- Step 4: 获取 Token ----------
echo "[4/6] 登录获取 Token..."
TOKEN=$(curl -s -X POST "$API_BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])" 2>/dev/null)

if [ -z "$TOKEN" ]; then
  echo "  ERROR: 登录失败, 请检查后端是否正常运行"
  exit 1
fi
echo "  Token: ${TOKEN:0:30}..."

# ---------- Step 5: 创建数据源并导入数据 ----------
echo "[5/6] 创建数据源并导入数据集..."

# 创建数据源
cat > /tmp/ds.json << JSONEOF
{"sourceName":"ecommerce_file_upload","sourceType":"FILE","description":"Ecommerce demo file source"}
JSONEOF

DS_RESP=$(curl -s -X POST "$API_BASE/data-sources" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d @/tmp/ds.json)
SOURCE_ID=$(echo "$DS_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['sourceId'])" 2>/dev/null)
echo "  数据源 ID: $SOURCE_ID"

# 导入商品数据
echo "  -> 导入商品数据 (CSV)..."
curl -s -X POST "$API_BASE/imports/file" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@$STORAGE_DIR/product_data.csv" \
  -F "datasetName=product_data" \
  -F "sourceId=$SOURCE_ID" \
  -F "headerRow=true" > /dev/null
echo "    完成: product_data (32条)"

# 导入订单数据
echo "  -> 导入订单数据 (CSV)..."
curl -s -X POST "$API_BASE/imports/file" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@$STORAGE_DIR/orders_recent.csv" \
  -F "datasetName=orders_recent" \
  -F "sourceId=$SOURCE_ID" \
  -F "headerRow=true" > /dev/null
echo "    完成: orders_recent (300条)"

# 导入库存数据 (INVENTORY domain)
echo "  -> 导入库存数据 (JSON)..."
curl -s -X POST "$API_BASE/imports/file" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@$STORAGE_DIR/inventory_data.json" \
  -F "datasetName=inventory_data" \
  -F "businessDomain=INVENTORY" \
  -F "sourceId=$SOURCE_ID" \
  -F "headerRow=true" > /dev/null
echo "    完成: inventory_data (50条)"

# ---------- Step 6: SQL 插入任务/日志/治理流程 ----------
echo "[6/6] 插入任务、日志、治理流程..."

mysql -u root -p"$MYSQL_PASS" --default-character-set=utf8mb4 data_lake_platform << 'SQLEOF'

-- 调度任务
INSERT INTO task_def (task_name, task_type, target_id, cron_expr, status, retry_policy, create_user, payload, description, next_run_time, last_run_time, create_time, update_time) VALUES
('Daily Order Import', 'IMPORT', 2, '0 0 2 * * ?', 'ENABLED', 3, 1, '{"sourceId":1,"fileName":"orders.csv"}', 'Auto import orders at 2am daily', '2026-06-13 02:00:00', '2026-06-12 02:00:00', NOW(), NOW()),
('Product Data Governance', 'GOVERNANCE', 1, '0 30 3 * * ?', 'ENABLED', 2, 1, '{"datasetId":1,"flowId":1}', 'Daily product data cleaning at 3:30am', '2026-06-13 03:30:00', '2026-06-12 03:30:00', NOW(), NOW()),
('Inventory Snapshot Sync', 'INTEGRATION', 3, '0 0 6 * * ?', 'ENABLED', 1, 1, '{"sourceId":1,"targetId":3}', 'Daily inventory sync at 6am', '2026-06-13 06:00:00', '2026-06-12 06:00:00', NOW(), NOW()),
('Order Dedup Task', 'GOVERNANCE', 2, '0 0 8 * * 1', 'DISABLED', 2, 1, '{"datasetId":2}', 'Weekly order dedup on Monday (paused)', '2026-06-15 08:00:00', '2026-06-08 08:00:00', NOW(), NOW()),
('Category Normalization', 'GOVERNANCE', 1, '0 0 12 * * 5', 'ENABLED', 1, 1, '{"datasetId":1}', 'Weekly category normalization on Friday', '2026-06-12 12:00:00', '2026-06-05 12:00:00', NOW(), NOW());

-- 任务执行日志（12条，成功+失败混合）
INSERT INTO task_log (task_id, task_type, target_id, start_time, end_time, status, execution_summary, error_message, input_params, execution_steps, failure_reason, duration, operator_user, create_time) VALUES
(1,'IMPORT',2,'2026-06-12 02:00:00','2026-06-12 02:03:15','SUCCESS','Imported 200 orders, 0 failed',NULL,'{"source":"orders.csv"}','["Parse file","Validate data","Batch insert","Update metadata"]',NULL,195,1,NOW()),
(1,'IMPORT',2,'2026-06-11 02:00:00','2026-06-11 02:02:48','SUCCESS','Imported 198 orders, 0 failed',NULL,'{"source":"orders.csv"}','["Parse file","Validate data","Batch insert"]',NULL,168,1,NOW()),
(1,'IMPORT',2,'2026-06-10 02:00:01','2026-06-10 02:05:22','SUCCESS','Imported 205 orders, 0 failed',NULL,'{"source":"orders.csv"}','["Parse file","Validate data","Batch insert"]',NULL,321,1,NOW()),
(2,'GOVERNANCE',1,'2026-06-12 03:30:00','2026-06-12 03:31:10','SUCCESS','Governance done: 0 dedup, 2 null-fill, 3 transform',NULL,'{"operatorChain":["NULL_FILL","CATEGORY_NORMALIZE"]}','["Null fill - done","Category normalize - done"]',NULL,70,1,NOW()),
(2,'GOVERNANCE',1,'2026-06-11 03:30:00','2026-06-11 03:31:45','FAILED','Governance failed: null ratio too high','Null ratio in field exceeded 30% threshold','{"operatorChain":["NULL_FILL","DEDUPLICATE"]}','["Null fill - done","Dedup - failed"]','Null value in dedup field',105,1,NOW()),
(2,'GOVERNANCE',1,'2026-06-10 03:30:00','2026-06-10 03:30:55','SUCCESS','Governance done: 1 dedup, 0 null-fill',NULL,'{"operatorChain":["DEDUPLICATE","AMOUNT_NORMALIZE"]}','["Dedup - done","Amount normalize - done"]',NULL,55,1,NOW()),
(3,'INTEGRATION',3,'2026-06-12 06:00:00','2026-06-12 06:01:30','SUCCESS','Inventory sync done: 50 records updated',NULL,NULL,'["Extract","Compare","Write"]',NULL,90,1,NOW()),
(3,'INTEGRATION',3,'2026-06-11 06:00:00','2026-06-11 06:00:42','SUCCESS','Inventory sync done: 48 records updated',NULL,NULL,'["Extract","Compare"]',NULL,42,1,NOW()),
(4,'GOVERNANCE',2,'2026-06-08 08:00:00','2026-06-08 08:01:20','SUCCESS','Order dedup done: 3 duplicates removed',NULL,'{"operatorKey":"ORDER_DEDUP"}','["Dedup - done"]',NULL,80,1,NOW()),
(5,'GOVERNANCE',1,'2026-06-05 12:00:00','2026-06-05 12:00:35','SUCCESS','Category normalization done: 5 records fixed',NULL,'{"operatorKey":"CATEGORY_NORMALIZE"}','["Category normalize - done"]',NULL,35,1,NOW()),
(1,'IMPORT',2,'2026-06-09 02:00:00','2026-06-09 02:04:10','SUCCESS','Imported 190 orders',NULL,'{"source":"orders.csv"}','["Parse","Validate","Insert"]',NULL,250,1,NOW()),
(1,'IMPORT',2,'2026-06-08 02:00:00','2026-06-08 02:03:55','SUCCESS','Imported 210 orders',NULL,'{"source":"orders.csv"}','["Parse","Insert"]',NULL,235,1,NOW());

-- 治理流程模板
INSERT INTO governance_flow (flow_name, input_dataset_id, operator_chain, output_dataset_id, creator, create_time, update_time) VALUES
('Product Standard Governance', 1, '[{"operatorKey":"NULL_FILL","params":{"field":"brand","fillValue":"Unknown"}},{"operatorKey":"CATEGORY_NORMALIZE","params":{}},{"operatorKey":"AMOUNT_NORMALIZE","params":{"field":"price"}}]', NULL, 1, NOW(), NOW()),
('Order Dedup & Clean', 2, '[{"operatorKey":"ORDER_DEDUP","params":{}},{"operatorKey":"TIME_NORMALIZE","params":{"field":"order_time"}},{"operatorKey":"STATUS_NORMALIZE","params":{"field":"status"}}]', NULL, 1, NOW(), NOW());

SQLEOF

echo ""
echo "========================================"
echo " 初始化完成!"
echo "========================================"
echo ""
echo "数据概览:"
echo "  - 数据集: 3个 (商品32条 / 订单300条 / 库存50条)"
echo "  - 数据源: 1个 (FILE)"
echo "  - 调度任务: 5个 (3启用 / 1暂停)"
echo "  - 执行日志: 12条 (2失败 / 10成功)"
echo "  - 治理算子: 9个"
echo "  - 治理流程: 2个"
echo "  - 用户: admin/admin123, operator/operator123"
echo ""
echo "访问地址:"
echo "  前端: http://localhost:5173/"
echo "  后端: http://localhost:8080/"
echo "  Swagger: http://localhost:8080/swagger-ui.html"
echo ""
echo "启动前端: cd frontend && npm run dev"
echo ""
