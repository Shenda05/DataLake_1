import openpyxl
from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
from datetime import datetime, timedelta
import random
import os

wb = openpyxl.Workbook()
ws = wb.active
ws.title = "商品数据"

# 表头
headers = [
    "product_id",      # 商品ID
    "product_name",    # 商品名称
    "category",        # 商品分类
    "sub_category",    # 子分类
    "brand",           # 品牌
    "price",           # 单价(元)
    "cost",            # 成本(元)
    "stock_quantity",  # 库存数量
    "warehouse",       # 仓库
    "sales_volume",    # 月销量
    "sales_amount",    # 月销售额(元)
    "rating",          # 评分
    "review_count",    # 评价数
    "supplier",        # 供应商
    "launch_date",     # 上架日期
    "status",          # 状态
    "is_free_shipping",# 是否包邮
    "weight_kg",       # 重量(kg)
    "sku_code",        # SKU编码
    "remarks",         # 备注
]

# 表头样式
header_font = Font(name="微软雅黑", bold=True, color="FFFFFF", size=11)
header_fill = PatternFill(start_color="4472C4", end_color="4472C4", fill_type="solid")
header_alignment = Alignment(horizontal="center", vertical="center")
thin_border = Border(
    left=Side(style="thin"),
    right=Side(style="thin"),
    top=Side(style="thin"),
    bottom=Side(style="thin"),
)

for col_idx, header in enumerate(headers, 1):
    cell = ws.cell(row=1, column=col_idx, value=header)
    cell.font = header_font
    cell.fill = header_fill
    cell.alignment = header_alignment
    cell.border = thin_border

# 商品数据
products = [
    # 数码产品
    ["PROD-001", "iPhone 15 Pro Max 256GB", "数码", "智能手机", "Apple", 9999.00, 7200.00, 320, "华东仓", 1856, 18557344.00, 4.8, 3260, "苹果中国", "2025-09-22", "在售", "是", 0.22, "SKU-DG-001", "年度旗舰机"],
    ["PROD-002", "MacBook Pro 14英寸 M4芯片", "数码", "笔记本电脑", "Apple", 14999.00, 11000.00, 150, "华东仓", 890, 13329110.00, 4.9, 1520, "苹果中国", "2025-10-15", "在售", "是", 1.55, "SKU-DG-002", "高性能办公本"],
    ["PROD-003", "华为 Mate 70 Pro", "数码", "智能手机", "华为", 6999.00, 5000.00, 500, "华南仓", 2100, 14697900.00, 4.7, 4500, "华为终端", "2025-11-01", "在售", "是", 0.21, "SKU-DG-003", "国产旗舰"],
    ["PROD-004", "索尼 WH-1000XM6 降噪耳机", "数码", "耳机", "Sony", 2499.00, 1600.00, 800, "华东仓", 3200, 7996800.00, 4.8, 6800, "索尼中国", "2025-08-10", "在售", "否", 0.25, "SKU-DG-004", "头戴式降噪"],
    ["PROD-005", "iPad Air M2 11英寸", "数码", "平板电脑", "Apple", 4799.00, 3400.00, 400, "华北仓", 1500, 7198500.00, 4.7, 2900, "苹果中国", "2025-06-15", "在售", "是", 0.46, "SKU-DG-005", "轻薄学习办公"],
    ["PROD-006", "大疆 DJI Mini 4 Pro 无人机", "数码", "无人机", "DJI", 4788.00, 3200.00, 250, "华南仓", 980, 4692240.00, 4.9, 2100, "大疆创新", "2025-09-01", "在售", "是", 0.25, "SKU-DG-006", "249g免注册"],
    ["PROD-007", "小米 14 Ultra", "数码", "智能手机", "小米", 5999.00, 4200.00, 600, "华北仓", 1800, 10798200.00, 4.6, 3800, "小米科技", "2025-10-20", "在售", "是", 0.23, "SKU-DG-007", "徕卡影像"],
    ["PROD-008", "Apple Watch Ultra 3", "数码", "智能手表", "Apple", 6499.00, 4500.00, 350, "华东仓", 720, 4679280.00, 4.6, 1800, "苹果中国", "2025-09-10", "在售", "是", 0.06, "SKU-DG-008", "户外运动款"],

    # 家居产品
    ["PROD-009", "北欧简约三人沙发", "家居", "沙发", "宜家", 3999.00, 2400.00, 80, "华东仓", 450, 1799550.00, 4.5, 960, "宜家家居", "2025-03-15", "在售", "否", 45.00, "SKU-JJ-001", "可拆洗布艺"],
    ["PROD-010", "智能升降桌 1.4m", "家居", "办公桌", "乐歌", 2599.00, 1600.00, 200, "华北仓", 680, 1767320.00, 4.7, 1300, "乐歌股份", "2025-05-20", "在售", "否", 32.00, "SKU-JJ-002", "电动升降记忆"],
    ["PROD-011", "乳胶记忆棉床垫 1.8m", "家居", "床垫", "慕思", 4599.00, 2800.00, 120, "华南仓", 380, 1747620.00, 4.8, 750, "慕思股份", "2025-04-10", "在售", "否", 38.00, "SKU-JJ-003", "护脊款"],
    ["PROD-012", "实木书柜 六层", "家居", "柜子", "源氏木语", 1899.00, 1100.00, 60, "华东仓", 220, 417780.00, 4.4, 480, "源氏木语", "2025-02-28", "在售", "否", 55.00, "SKU-JJ-004", "北美橡木"],
    ["PROD-013", "极简落地灯", "家居", "灯具", "小米有品", 399.00, 200.00, 500, "华北仓", 1200, 478800.00, 4.5, 2200, "小米科技", "2025-07-05", "在售", "是", 3.20, "SKU-JJ-005", "米家智能控制"],
    ["PROD-014", "全棉四件套 1.8m", "家居", "床上用品", "水星家纺", 599.00, 320.00, 900, "华南仓", 2500, 1497500.00, 4.7, 5200, "水星家纺", "2025-06-20", "在售", "是", 2.10, "SKU-JJ-006", "60支长绒棉"],

    # 食品
    ["PROD-015", "有机坚果大礼包 1.2kg", "食品", "零食", "三只松鼠", 168.00, 98.00, 3000, "华东仓", 8500, 1428000.00, 4.6, 18000, "三只松鼠", "2025-11-10", "在售", "是", 1.20, "SKU-SP-001", "年货爆款"],
    ["PROD-016", "特级初榨橄榄油 500ml", "食品", "粮油调味", "金龙鱼", 89.90, 52.00, 2500, "华北仓", 4200, 377580.00, 4.4, 8500, "益海嘉里", "2025-08-01", "在售", "是", 0.50, "SKU-SP-002", "西班牙进口"],
    ["PROD-017", "冻干咖啡粉 30条装", "食品", "饮料冲调", "三顿半", 149.00, 85.00, 1800, "华南仓", 5600, 834400.00, 4.8, 12000, "三顿半", "2025-09-15", "在售", "是", 0.30, "SKU-SP-003", "冷萃即溶"],
    ["PROD-018", "有机纯牛奶 250ml*24盒", "食品", "乳制品", "蒙牛", 79.90, 48.00, 5000, "华北仓", 12000, 958800.00, 4.5, 25000, "蒙牛乳业", "2025-10-01", "在售", "是", 6.00, "SKU-SP-004", "3.6g蛋白质"],
    ["PROD-019", "五常大米 5kg", "食品", "米面杂粮", "十月稻田", 69.90, 40.00, 4000, "华东仓", 7800, 545220.00, 4.7, 16000, "十月稻田", "2025-09-25", "在售", "否", 5.00, "SKU-SP-005", "地理标志产品"],
    ["PROD-020", "黑巧克力礼盒 500g", "食品", "零食", "德芙", 129.00, 70.00, 2000, "华南仓", 3800, 490200.00, 4.5, 7500, "玛氏食品", "2025-11-20", "在售", "是", 0.50, "SKU-SP-006", "72%可可"],

    # 服装
    ["PROD-021", "男士轻薄羽绒服", "服装", "男装", "优衣库", 599.00, 350.00, 1500, "华东仓", 3200, 1916800.00, 4.6, 6800, "迅销中国", "2025-10-10", "在售", "是", 0.35, "SKU-FZ-001", "防泼水"],
    ["PROD-022", "女士羊绒大衣", "服装", "女装", "鄂尔多斯", 2599.00, 1500.00, 300, "华北仓", 450, 1169550.00, 4.7, 920, "鄂尔多斯", "2025-11-05", "在售", "是", 1.20, "SKU-FZ-002", "100%山羊绒"],
    ["PROD-023", "运动跑鞋 飞马40", "服装", "运动鞋", "Nike", 899.00, 520.00, 1200, "华南仓", 4200, 3775800.00, 4.8, 9500, "耐克中国", "2025-07-20", "在售", "是", 0.80, "SKU-FZ-003", "Zoom Air气垫"],
    ["PROD-024", "纯棉短袖T恤 3件装", "服装", "男装", "海澜之家", 199.00, 90.00, 3000, "华东仓", 6800, 1353200.00, 4.3, 14000, "海澜之家", "2025-04-01", "在售", "是", 0.50, "SKU-FZ-004", "新疆棉"],

    # 美妆
    ["PROD-025", "修护精华液 50ml", "美妆", "面部精华", "兰蔻", 1080.00, 580.00, 600, "华东仓", 2800, 3024000.00, 4.8, 5800, "欧莱雅中国", "2025-08-20", "在售", "是", 0.15, "SKU-MZ-001", "小黑瓶二代"],
    ["PROD-026", "防晒霜 SPF50+ 60ml", "美妆", "防晒", "安热沙", 238.00, 120.00, 2500, "华南仓", 6500, 1547000.00, 4.6, 14000, "资生堂", "2025-05-15", "在售", "是", 0.08, "SKU-MZ-002", "小金瓶"],
    ["PROD-027", "保湿面膜 10片装", "美妆", "面膜", "敷尔佳", 89.00, 38.00, 4000, "华北仓", 9500, 845500.00, 4.5, 20000, "敷尔佳", "2025-06-01", "在售", "是", 0.25, "SKU-MZ-003", "医美级补水"],
    ["PROD-028", "哑光口红礼盒 6支", "美妆", "口红", "完美日记", 199.00, 85.00, 1800, "华南仓", 5200, 1034800.00, 4.4, 11000, "逸仙电商", "2025-09-01", "在售", "是", 0.20, "SKU-MZ-004", "国货之光"],

    # 特殊状态商品
    ["PROD-029", "限量版机械键盘", "数码", "电脑配件", "Cherry", 1599.00, 900.00, 0, "华东仓", 320, 511680.00, 4.9, 680, "樱桃中国", "2025-12-01", "缺货", "是", 1.10, "SKU-DG-009", "补货中"],
    ["PROD-030", "夏季竹凉席 三件套", "家居", "床上用品", "", 299.00, 150.00, 0, "华北仓", 1200, 358800.00, 4.2, 2600, "暂未填写", "2025-05-01", "已下架", "否", 4.50, "SKU-JJ-007", "季节品已下架"],
    ["PROD-031", "进口巧克力礼盒", "食品", "零食", "费列罗", 0, 88.00, 1000, "华东仓", 0, 0, 4.3, 4200, "费列罗中国", "2025-12-15", "待定价", "是", 0.80, "SKU-SP-007", "春节新品待审核"],
    ["PROD-032", "儿童智能手表", "数码", "智能穿戴", "小天才", 799.00, 450.00, 450, "华南仓", 1500, 1198500.00, 4.5, 3200, "步步高", "2025-08-05", "在售", "是", 0.05, "SKU-DG-010", "防水定位"],
]

# 列宽
col_widths = [14, 28, 10, 14, 14, 12, 12, 14, 12, 14, 16, 8, 12, 16, 14, 10, 14, 10, 16, 22]
for i, width in enumerate(col_widths, 1):
    ws.column_dimensions[openpyxl.utils.get_column_letter(i)].width = width

# 数据样式
data_alignment = Alignment(vertical="center")
data_font = Font(name="微软雅黑", size=10)

for row_idx, product in enumerate(products, 2):
    for col_idx, value in enumerate(product, 1):
        cell = ws.cell(row=row_idx, column=col_idx, value=value)
        cell.font = data_font
        cell.alignment = data_alignment
        cell.border = thin_border
        # 特殊状态高亮
        if col_idx == 16:  # status列
            if value == "缺货":
                cell.fill = PatternFill(start_color="FFF2CC", end_color="FFF2CC", fill_type="solid")
            elif value == "已下架":
                cell.fill = PatternFill(start_color="F4CCCC", end_color="F4CCCC", fill_type="solid")
            elif value == "待定价":
                cell.fill = PatternFill(start_color="D9EAD3", end_color="D9EAD3", fill_type="solid")
        # 零值高亮
        elif col_idx in [6, 14] and value == 0:
            cell.fill = PatternFill(start_color="FCE4D6", end_color="FCE4D6", fill_type="solid")

# 冻结首行
ws.freeze_panes = "A2"
# 自动筛选
ws.auto_filter.ref = f"A1:T{len(products) + 1}"

# 添加第二个Sheet：分类说明
ws2 = wb.create_sheet("数据说明")
ws2.column_dimensions['A'].width = 20
ws2.column_dimensions['B'].width = 50

notes = [
    ("字段", "说明"),
    ("product_id", "商品唯一ID，格式 PROD-XXX"),
    ("product_name", "商品名称"),
    ("category", "一级分类：数码/家居/食品/服装/美妆"),
    ("sub_category", "二级分类"),
    ("brand", "品牌名称，部分数据可能为空"),
    ("price", "单价(元)，0表示待定价"),
    ("cost", "成本(元)"),
    ("stock_quantity", "当前库存数量，0表示缺货"),
    ("warehouse", "所在仓库：华东仓/华北仓/华南仓"),
    ("sales_volume", "近30天销量"),
    ("sales_amount", "近30天销售额(元)"),
    ("rating", "综合评分(1-5)"),
    ("review_count", "累计评价数"),
    ("supplier", "供应商名称"),
    ("launch_date", "上架日期"),
    ("status", "商品状态：在售/缺货/已下架/待定价"),
    ("is_free_shipping", "是否包邮：是/否"),
    ("weight_kg", "单件重量(kg)"),
    ("sku_code", "SKU编码"),
    ("remarks", "备注信息"),
    ("", ""),
    ("数据用途", "用于数据湖管理平台测试：数据接入→数据集管理→查询分析→数据治理"),
    ("特殊数据", "PROD-029(缺货/0库存)、PROD-030(已下架/品牌为空)、PROD-031(待定价/price=0)可用于测试治理算子"),
]

for i, (key, val) in enumerate(notes, 1):
    ws2.cell(row=i, column=1, value=key).font = Font(name="微软雅黑", bold=True, size=10)
    ws2.cell(row=i, column=2, value=val).font = Font(name="微软雅黑", size=10)

# 保存
output_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "storage", "product_data.xlsx")
output_path = os.path.normpath(output_path)
os.makedirs(os.path.dirname(output_path), exist_ok=True)
wb.save(output_path)
print(f"文件已生成: {output_path}")
print(f"共 {len(products)} 条商品数据, 20 个字段")
print(f"分类统计: 数码9款, 家居6款, 食品6款, 服装4款, 美妆4款, 外加3款特殊状态")
