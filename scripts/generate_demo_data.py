import csv
import json
import os
import random
from datetime import datetime, timedelta

storage = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "storage"))
os.makedirs(storage, exist_ok=True)

# ============ 订单数据 CSV ============
products = ["SKU-DG-001","SKU-DG-002","SKU-DG-003","SKU-JJ-001","SKU-JJ-002",
            "SKU-SP-001","SKU-SP-002","SKU-FZ-001","SKU-FZ-003","SKU-MZ-001",
            "SKU-MZ-002","SKU-DG-004","SKU-JJ-005","SKU-SP-003","SKU-FZ-004"]
categories = ["数码","数码","数码","家居","家居","食品","食品","服装","服装","美妆","美妆","数码","家居","食品","服装"]
statuses = ["已完成","已完成","已完成","已完成","已完成","已完成","已完成","已退款","已完成","已完成","已完成","已完成","已完成","已完成","已完成","已完成"]

orders = []
base_date = datetime(2026, 5, 1)
for i in range(1, 201):
    idx = i % len(products)
    order_date = base_date + timedelta(days=random.randint(0, 40), hours=random.randint(0, 23), minutes=random.randint(0, 59))
    qty = random.randint(1, 5)
    price = random.choice([99.9, 168.0, 299.0, 599.0, 899.0, 1299.0, 2499.0, 4799.0, 89.9, 238.0])
    amount = round(qty * price, 2)
    status = random.choice(statuses)
    orders.append({
        "order_id": f"ORD-2026{order_date.strftime('%m%d')}{i:04d}",
        "product_sku": products[idx],
        "category": categories[idx],
        "quantity": qty,
        "unit_price": price,
        "total_amount": amount,
        "order_time": order_date.strftime("%Y-%m-%d %H:%M:%S"),
        "status": status,
        "payment_method": random.choice(["微信支付","支付宝","银行卡"]),
        "customer_city": random.choice(["上海","北京","深圳","广州","杭州","成都","武汉","南京"]),
        "is_first_order": random.choice(["是","否"])
    })

csv_path = os.path.join(storage, "orders_data.csv")
with open(csv_path, "w", newline="", encoding="utf-8-sig") as f:
    w = csv.DictWriter(f, fieldnames=orders[0].keys())
    w.writeheader()
    w.writerows(orders)
print(f"CSV: {csv_path} ({len(orders)} rows)")

# ============ 库存数据 JSON ============
warehouses = ["华东仓","华南仓","华北仓","华中仓","西南仓"]
inventory = []
for i in range(1, 51):
    idx = i % len(products)
    wh = random.choice(warehouses)
    qty = random.randint(0, 500)
    inventory.append({
        "product_sku": products[idx],
        "warehouse": wh,
        "stock_quantity": qty,
        "available_quantity": max(0, qty - random.randint(0, 50)),
        "reserved_quantity": random.randint(0, min(50, qty)),
        "last_restock_date": (datetime(2026, 6, 1) - timedelta(days=random.randint(1, 60))).strftime("%Y-%m-%d"),
        "safety_stock": random.choice([20, 30, 50, 100]),
        "turnover_rate": round(random.uniform(0.5, 5.0), 2)
    })

json_path = os.path.join(storage, "inventory_data.json")
with open(json_path, "w", encoding="utf-8") as f:
    json.dump(inventory, f, ensure_ascii=False, indent=2)
print(f"JSON: {json_path} ({len(inventory)} rows)")
print("Done!")
