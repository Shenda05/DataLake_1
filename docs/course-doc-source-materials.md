# 课程文档原始材料汇总

更新时间：`2026-04-05`

## 用途说明

本文档用于进入“课程文档原始材料补齐”阶段后，集中整理后续编写以下文档所需的基础素材：

1. 白盒测试文档
2. 黑盒测试文档
3. 软件使用说明

整理原则如下：

1. 只复用当前仓库已经存在的文档、脚本和代码实现。
2. 不新增业务逻辑，不扩写不存在的模块、流程或接口。
3. 如现有文档口径与代码边界存在差异，以当前冻结代码和脚本行为为准。
4. 本文档定位为“原始材料汇总”，不是老师模板式正式交付文档。

主要事实源：

- `docs/user-manual.md`
- `docs/demo-runbook.md`
- `docs/demo-script.md`
- `docs/regression-coverage-matrix.md`
- `docs/browser-regression-checklist.md`
- `docs/test-plan.md`
- `docs/test-report.md`
- `scripts/api-smoke-test.sh`
- `scripts/ecommerce-demo-smoke.sh`
- `scripts/regression-suite.sh`
- 当前前后端代码实现

# 1. 关键页面清单与推荐截图顺序

建议截图顺序按“登录入口 → 首页总览 → 接入 → 资产 → 分析 → 治理 → 调度 → 日志 → 权限”展开，这一顺序同时兼容软件使用说明、答辩展示和黑盒测试截图归档。

| 顺序 | 页面名称 | 路由 | 推荐截图内容 | 截图目的 | 来源依据 |
| --- | --- | --- | --- | --- | --- |
| 1 | 登录页 | `/login` | 用户名密码输入框、登录按钮、系统标题 | 展示系统入口与账号登录方式 | `docs/user-manual.md`、`router/index.ts` |
| 2 | 电商仪表盘 | `/dashboard` | 统计卡片、趋势图、最近任务、最近日志、快捷入口 | 作为系统总览截图 | `docs/demo-runbook.md`、`DashboardView.vue` |
| 3 | 数据源管理 | `/data-sources` | `FILE / MYSQL` 数据源列表、连接测试/启停按钮、编辑弹窗 | 展示接入前的数据源准备能力 | `docs/user-manual.md`、`DataSourcesView.vue` |
| 4 | 电商数据接入-文件导入 | `/imports` | 文件导入页、文件格式识别、编码方式、首行为表头 | 展示文件导入入口和解析参数 | `docs/user-manual.md`、`browser-regression-checklist.md` |
| 5 | 电商数据接入-数据库表导入 | `/imports` | `Schema / Database` 下拉、数据库表列表、样例预览 | 展示 `MYSQL` 表导入入口 | `docs/user-manual.md`、`browser-regression-checklist.md` |
| 6 | 电商数据接入-导入历史/详情 | `/imports` | 导入历史表格、状态筛选、详情抽屉 | 展示导入结果可追踪 | `docs/user-manual.md`、`DataImportView.vue` |
| 7 | 电商数据集管理-列表 | `/datasets` | 数据集列表、业务域/状态/来源筛选 | 展示资产列表视图 | `docs/demo-script.md`、`DatasetsView.vue` |
| 8 | 电商数据集管理-详情/预览/元数据 | `/datasets` | 已选数据集详情、元数据表、分页预览 | 展示数据集管理深度能力 | `docs/user-manual.md`、`DatasetsView.vue` |
| 9 | 电商查询分析-条件查询结果 | `/queries` | 多条件查询区、结果表格、基础分布图 | 展示条件查询能力 | `docs/demo-script.md`、`QueryAnalysisView.vue` |
| 10 | 电商查询分析-SQL 分页排序结果 | `/queries` | SQL 输入区、分页结果、排序字段 | 展示单表 SQL 查询能力 | `docs/demo-script.md`、`api-smoke-test.sh` |
| 11 | 电商查询分析-电商指标图表 | `/queries` | 指标类型选择、趋势图或占比图、指标表格 | 展示电商化分析视图 | `docs/user-manual.md`、`QueryAnalysisView.vue` |
| 12 | 电商查询分析-数据集成与保存结果 | `/queries` | `JOIN / UNION` 配置、结果预览、保存成功反馈 | 展示集成结果沉淀为数据集 | `docs/demo-runbook.md`、`ecommerce-demo-smoke.sh` |
| 13 | 电商数据治理-流程编排 | `/governance` | 输入数据集、算子步骤、JSON 参数、模板入口 | 展示治理编排界面 | `docs/demo-script.md`、`GovernanceView.vue` |
| 14 | 电商数据治理-成功执行结果 | `/governance` | 输出数据集名称、输入/输出条数、异常处理条数 | 展示治理成功闭环 | `docs/demo-runbook.md`、`ecommerce-demo-smoke.sh` |
| 15 | 电商数据治理-失败执行提示 | `/governance` | 错误提示、失败原因、失败步骤信息 | 展示失败场景和日志价值 | `docs/demo-script.md`、`ecommerce-demo-smoke.sh` |
| 16 | 电商任务调度 | `/tasks` | 任务列表、状态统计、任务模板、表单区 | 展示调度配置和立即执行入口 | `docs/user-manual.md`、`TasksView.vue` |
| 17 | 任务与日志监控-日志列表 | `/logs` | 日志筛选区、列表、失败统计卡片 | 展示监控总览能力 | `docs/demo-script.md`、`LogsView.vue` |
| 18 | 任务与日志监控-日志详情 | `/logs` | 输入参数、执行步骤、结构化失败原因、回放按钮 | 展示结构化日志价值 | `docs/demo-runbook.md`、`ecommerce-demo-smoke.sh` |
| 19 | 用户与权限 | `/users` | 用户列表、角色选择、菜单权限、操作权限分组 | 展示权限配置能力 | `docs/user-manual.md`、`UsersView.vue` |

补充建议：

1. 如果只保留一套“最短答辩截图”，建议优先保留顺序 `1 / 2 / 4 / 8 / 9 / 12 / 14 / 16 / 18 / 19`。
2. 如果要为软件使用说明配图，建议按照上表完整保留 19 张页面截图。
3. 日志相关截图建议同时保留“日志列表”和“日志详情”两张，便于分别支撑黑盒和白盒文档。

# 2. 关键主流程清单

下表用于后续编写“系统主流程”“黑盒主链路验收流程”“软件使用说明章节目录”。

| 主流程 | 用途 | 起点页面 | 终点结果 | 主要关联模块 | 可截图节点 |
| --- | --- | --- | --- | --- | --- |
| 登录 | 进入系统并获得菜单/操作权限 | 登录页 | 成功进入首页并显示当前可见菜单 | 登录权限、路由、会话权限 | 登录页、首页 |
| 数据导入 | 把文件或数据库表转为平台数据集 | 电商数据接入 | 生成导入历史和新数据集 | 数据源管理、数据接入、数据集管理 | 文件导入页、数据库表导入页、导入历史 |
| 数据集管理 | 查看和维护已有数据资产 | 电商数据集管理 | 查看详情、元数据、预览、导出或删除校验 | 数据集管理 | 数据集列表、详情预览 |
| 查询分析 | 执行查询、图表分析和集成保存 | 电商查询分析 | 得到结果表、图表或新集成数据集 | 查询分析、数据集管理 | 条件查询结果、SQL 结果、指标图表、集成结果 |
| 数据治理 | 对数据集执行治理算子链 | 电商数据治理 | 生成新输出数据集或返回失败步骤 | 数据治理、数据集管理、日志 | 流程编排、成功结果、失败提示 |
| 任务调度 | 配置和执行导入/治理任务 | 电商任务调度 | 创建任务、立即执行、更新状态和最近日志 | 任务调度、数据接入、数据治理 | 任务列表、任务触发结果 |
| 日志监控 | 查看执行历史、失败原因和回放结果 | 任务与日志监控 | 查看日志详情并回放失败任务 | 日志监控、任务调度、数据治理 | 日志列表、日志详情、回放结果 |

# 3. 各主流程的实际操作步骤

本节只整理“已实现且已被现有文档/脚本证明”的实际操作路径，便于直接改写为软件使用说明和黑盒测试步骤。

## 3.1 登录流程

### 前置条件

1. 前后端服务已启动。
2. 可用账号：
   - 管理员：`admin / admin123`
   - 普通用户：`operator / operator123`

### 实际操作步骤

1. 打开系统登录页 `/login`。
2. 输入用户名和密码。
3. 点击“登录”按钮。
4. 系统返回 token、角色信息和菜单/操作权限。
5. 页面自动跳转到当前角色首个有权限的菜单页，通常为首页。

### 页面可见结果

1. 登录成功后可看到首页或首个有权限页面。
2. 左侧菜单会按角色权限收口。
3. 顶部栏会显示当前账号的展示名称和角色标签。

### 可作为截图证据的节点

1. 登录页输入状态
2. 登录成功后的首页
3. 不同角色的菜单收口差异

### 注意事项

1. 管理员修改当前账号的角色、状态、菜单权限或操作权限后，现有会话会在 profile 同步后自动更新。
2. 未登录访问业务页会被路由守卫拦截并跳回登录页。

## 3.2 数据导入流程

### 3.2.1 文件导入

#### 前置条件

1. 已存在可用 `FILE` 数据源；如没有，也可在数据源管理页先创建。
2. 已准备好 `CSV / JSON / Excel` 文件。

#### 实际操作步骤

1. 进入“电商数据接入”页，停留在“文件导入”子页。
2. 选择数据源。
3. 输入数据集名称。
4. 选择业务域。
5. 上传文件。
6. 根据文件格式配置解析参数：
   - `CSV`：设置编码方式、是否首行为表头
   - `Excel`：设置是否首行为表头
   - `JSON`：编码方式和表头选项自动忽略
7. 点击“导入”。
8. 导入成功后进入导入历史查看对应记录。

#### 页面可见结果

1. 成功提示生成的数据集名称。
2. 导入历史新增一条记录。
3. 记录中可查看业务域、格式、状态、记录数、操作人和创建时间。

#### 可作为截图证据的节点

1. 文件导入表单完整状态
2. 文件格式自动识别后参数变化
3. 导入历史新增成功记录
4. 导入详情抽屉

#### 注意事项

1. `CSV / JSON / Excel` 是当前已支持的文件格式。
2. 非法文件格式和解析失败会返回明确错误信息。

### 3.2.2 数据库表导入

#### 前置条件

1. 当前账号具有数据库表导入权限，通常为管理员。
2. 已存在启用中的 `MYSQL` 数据源，且连接可用。

#### 实际操作步骤

1. 进入“电商数据接入”页，切换到“数据库表导入”子页。
2. 选择一个 `MYSQL` 数据源。
3. 等待系统自动加载 `Schema / Database` 下拉。
4. 选择目标 `Schema / Database`。
5. 等待系统刷新数据库表列表。
6. 选择一个数据库表。
7. 点击或等待“预览数据”，查看字段和样例行。
8. 输入数据集名称与业务域，必要时填写描述。
9. 点击“导入为数据集”。

#### 页面可见结果

1. 可看到 Schema 列表自动加载。
2. 切换 Schema 后数据库表列表同步变化。
3. 预览区显示字段名、字段类型和样例记录。
4. 导入成功后，导入历史新增一条 `MYSQL_TABLE` 记录。

#### 可作为截图证据的节点

1. Schema 自动识别下拉
2. 数据库表列表
3. 表结构与样例预览
4. 导入成功后的历史记录

#### 注意事项

1. 当前数据库表导入只支持选表导入，不支持自定义 SQL 导入。
2. 这是浏览器人工回归重点场景之一。

## 3.3 数据集管理流程

### 前置条件

1. 已至少存在一个导入成功或集成/治理生成的数据集。

### 实际操作步骤

1. 进入“电商数据集管理”页。
2. 通过关键字、来源数据源、状态、业务域筛选数据集。
3. 在列表中选择一个数据集。
4. 查看右侧详情、元数据和分页预览。
5. 按字段与关键字过滤预览结果。
6. 根据需要导出 `CSV / JSON / Excel`。
7. 如为管理员，尝试删除未被引用的数据集。

### 页面可见结果

1. 列表中显示数据集名称、业务域、格式、记录数、字段数、状态。
2. 详情区显示 `physicalTableName`、来源路径、描述、创建更新时间等信息。
3. 元数据区显示字段名、物理列名、字段类型、可空、样例值、字段顺序。
4. 预览区支持分页。

### 可作为截图证据的节点

1. 数据集列表筛选结果
2. 选中数据集后的详情/元数据/预览三块内容
3. 数据导出操作
4. 删除被引用数据集的失败提示

### 注意事项

1. 被治理流程、日志或导入记录引用的数据集不可删除。
2. 数据集导出属于后端文件流导出能力。

## 3.4 查询分析流程

查询分析页是复合页面，后续写使用说明和黑盒测试时建议拆成四条子路径：条件查询、SQL 查询、电商指标、数据集成。

### 3.4.1 条件查询

#### 前置条件

1. 已存在至少一个可查询数据集。

#### 实际操作步骤

1. 进入“电商查询分析”页。
2. 选择数据集。
3. 添加一个或多个查询条件。
4. 设置 `AND / OR` 逻辑、排序字段、排序顺序、分页参数。
5. 点击“查询”。
6. 根据需要导出当前页或按当前筛选导出全量命中结果。

#### 推荐真实样本输入

- `product_name LIKE 鼠标`
- `order_status = completed OR amount >= 200`
- `category LIKE 3C AND order_time TIME_RANGE 2026-03-01 00:00:00 ~ 2026-03-31 23:59:59`

#### 页面可见结果

1. 返回分页结果表。
2. 更新记录总数、当前页条数、基础分布图等信息。

#### 可作为截图证据的节点

1. 多条件查询表单
2. 查询结果表格
3. 分布图与结果联动

### 3.4.2 SQL 查询

#### 实际操作步骤

1. 在查询分析页输入单表 SQL。
2. 推荐使用 `SELECT * FROM dataset LIMIT 20` 或带排序字段的单表查询。
3. 点击执行 SQL 查询。
4. 如需分页结果，使用页面中的 SQL 分页视图并配置排序字段。

#### 推荐真实样本输入

- `SELECT * FROM dataset LIMIT 20`
- `SELECT product_name,amount,order_status FROM dataset`

#### 页面可见结果

1. 返回 SQL 结果表。
2. 支持分页与排序展示。
3. 支持导出 `CSV / JSON / Excel`。

#### 注意事项

1. 当前 SQL 只允许查询当前数据集单表。
2. 非 `SELECT`、带分号、注释或跨表的 SQL 会被拒绝。

### 3.4.3 电商指标

#### 实际操作步骤

1. 切换到电商指标子页。
2. 选择指标类型，如订单量趋势、销售额趋势、热销商品排行、分类占比、低库存预警。
3. 根据指标类型选择时间字段、数值字段、分类字段等。
4. 点击查询或等待刷新图表。

#### 页面可见结果

1. 展示折线图、柱状图或饼图。
2. 同时返回对应的指标表格。

#### 可作为截图证据的节点

1. 指标选择区
2. 订单趋势/销售趋势图
3. 分类占比图或低库存结果表

### 3.4.4 数据集成

#### 实际操作步骤

1. 切换到数据集成子页。
2. 选择左、右两个数据集。
3. 选择集成模式：`JOIN` 或 `UNION`。
4. 若为 `JOIN`，选择左右关联字段。
5. 点击预览集成结果。
6. 输入输出数据集名称和输出业务域。
7. 点击“保存为新数据集”。

#### 页面可见结果

1. 返回集成结果预览。
2. 保存成功后生成新的数据集。
3. 数据集管理页可看到新的集成数据集。

#### 可作为截图证据的节点

1. 集成参数配置
2. 集成结果预览
3. 保存成功提示

## 3.5 数据治理流程

### 前置条件

1. 已存在一个可治理的数据集，推荐使用交易数据集。
2. 当前角色具备治理执行权限；若需要保存流程，还需具备治理管理权限。

### 实际操作步骤

1. 进入“电商数据治理”页。
2. 选择输入数据集。
3. 通过模板或手动方式配置算子链。
4. 编辑每一步的 JSON 参数。
5. 根据需要调整步骤顺序。
6. 可选择先保存治理流程。
7. 点击“执行治理”。
8. 查看成功结果或失败提示。

### 推荐真实样本输入

成功链路：

- `ORDER_DEDUP` + `AMOUNT_NORMALIZE` + `TIME_NORMALIZE`

失败链路：

- `FIELD_CONVERT`，参数示例：`{"field":"product_name","transform":"NUMBER"}`

### 页面可见结果

1. 成功时返回输出数据集名称、输入记录数、输出记录数、异常处理条数、日志编号。
2. 失败时返回失败原因，并写入结构化日志。

### 可作为截图证据的节点

1. 算子步骤编排界面
2. 成功执行结果卡片
3. 失败执行提示

### 注意事项

1. 当前算子支持启用/停用，禁用算子不可保存或执行。
2. 算子参数当前为 JSON 文本，而非可视化字段映射界面。
3. 治理输出始终生成新数据集。

## 3.6 任务调度流程

### 前置条件

1. 已存在可用的导入记录或治理流程。

### 实际操作步骤

1. 进入“电商任务调度”页。
2. 选择任务类型：`IMPORT` 或 `GOVERNANCE`。
3. 选择目标对象。
4. 输入任务名称、Cron 表达式、重试策略和描述。
5. 点击保存创建任务。
6. 在列表中执行立即触发、暂停、恢复或删除。
7. 观察最近日志与状态统计变化。

### 页面可见结果

1. 任务列表新增一条任务。
2. 立即执行后可看到状态反馈与日志引用。
3. 暂停/恢复后状态和下次执行时间变化。

### 可作为截图证据的节点

1. 任务表单与模板入口
2. 任务列表
3. 立即执行后的反馈

### 注意事项

1. 当前只支持 `IMPORT / GOVERNANCE` 两类任务。
2. 任务页支持保存常用筛选视图和自动刷新。

## 3.7 日志监控流程

### 前置条件

1. 系统中已存在任务执行日志或治理执行日志。

### 实际操作步骤

1. 进入“任务与日志监控”页。
2. 按任务类型、状态、操作人、时间范围、关键字筛选日志。
3. 在列表中选中一条日志。
4. 查看详情中的输入参数、执行步骤、失败原因和操作人信息。
5. 如果该日志来自调度任务且当前角色具备权限，可执行“一键回放”。
6. 如具备日志导出权限，可导出当前筛选结果为 `CSV / JSON`。

### 页面可见结果

1. 日志列表按条件收敛。
2. 日志详情显示结构化字段：
   - `inputParams`
   - `executionSteps`
   - `failureReason`
3. 回放成功后日志列表条数增加。

### 可作为截图证据的节点

1. 日志筛选区和列表
2. 失败日志详情
3. 一键回放后的页面反馈

### 注意事项

1. 当前日志页导出是前端基于当前筛选结果本地生成文件，不依赖独立后端导出接口。
2. 不是由调度任务生成的手动日志，不能直接回放。
3. 权限实时同步、日志按钮级授权和日志回放反馈是浏览器人工回归重点场景。

# 4. 自动化脚本覆盖测试点清单

## 4.1 统一入口与关系

| 命令 | 定位 | 关系说明 | 主要输出证据 |
| --- | --- | --- | --- |
| `bash scripts/api-smoke-test.sh` | API 主链路冒烟 | 最基础自动化主链路 | `sourceId / datasetId / importId / taskId / logCount` |
| `bash scripts/ecommerce-demo-smoke.sh all` | 演示数据准备与答辩可见结果生成 | 负责生成截图和演示所需数据 | 首页指标、集成数据集、治理成功/失败日志 |
| `bash scripts/regression-suite.sh smoke` | 统一回归中的冒烟模式 | 等价于执行 `api-smoke` | 冒烟通过 |
| `bash scripts/regression-suite.sh demo` | 统一回归中的演示模式 | 等价于执行 `demo-smoke` | 演示数据准备通过 |
| `bash scripts/regression-suite.sh extended` | 扩展验收 | 负责重复策略、治理算子、日志筛选等高价值检查 | 扩展场景通过 |
| `bash scripts/regression-suite.sh all` | 冻结前全量自动化回归 | `api-smoke + demo-smoke + extended` | all 通过 |
| `bash scripts/regression-suite.sh gate` | 冻结门禁 | 构建 + `all` | build + gate 通过 |

## 4.2 `api-smoke-test.sh` 覆盖点

直接覆盖：

1. 管理员登录
2. 创建文件数据源
3. 上传交易 `CSV` 文件并生成数据集
4. 条件查询
5. 多条件组合查询
6. 时间范围查询
7. SQL 查询
8. SQL 分页排序查询
9. 创建治理流程
10. 创建并触发治理任务
11. 创建并触发导入任务
12. 获取任务日志列表

适合在测试文档中写成的检查点：

| 检查点 | 实际样本 |
| --- | --- |
| 登录成功 | `admin / admin123` |
| 文件导入 | 交易 `CSV` 文件 |
| 条件查询 | `product_name LIKE 鼠标` |
| 多条件查询 | `order_status = completed OR amount >= 200` |
| 时间范围查询 | `category LIKE 3C AND order_time TIME_RANGE 2026-03-01 ~ 2026-03-31` |
| SQL 查询 | `SELECT * FROM dataset LIMIT 20` |
| SQL 分页排序 | `SELECT product_name,amount,order_status FROM dataset` + `sortField=amount DESC` |
| 治理流程 | `ORDER_DEDUP + AMOUNT_NORMALIZE + TIME_NORMALIZE + STATUS_NORMALIZE` |
| 任务触发 | 导入任务 + 治理任务 |
| 日志列表 | 至少返回 2 条日志 |

## 4.3 `ecommerce-demo-smoke.sh` 覆盖点

### 一键模式 `all`

覆盖：

1. 登录
2. 首页电商指标准备
3. 演示用交易/商品/库存数据集准备
4. 数据集成并保存为新数据集
5. 治理成功场景
6. 治理失败场景
7. 失败任务触发并写入失败日志
8. 日志详情结构化字段校验

### 分步子命令

| 子命令 | 覆盖点 |
| --- | --- |
| `login` | 登录成功 |
| `dashboard-check` | 首页 `dashboard/ecommerce` 指标结构可展示 |
| `integration-save` | `JOIN` 集成预览、集成结果保存、预览新数据集 |
| `governance-success` | 治理成功，写入成功日志 |
| `governance-fail` | 治理失败，返回失败信息 |
| `task-fail-log` | 创建失败治理流程、创建失败任务、触发失败任务、生成失败日志 |
| `log-detail` | 校验日志详情含 `inputParams / executionSteps / failureReason` |

### 适合写入测试文档的检查点

1. 首页必须有订单趋势、销售趋势、Top5 和低库存统计。
2. 数据集成保存后必须生成新的数据集并可预览。
3. 治理成功后必须生成新的输出数据集。
4. 治理失败后必须保留失败信息或失败日志编号。
5. 任务失败后日志详情必须包含结构化字段。

## 4.4 `regression-suite.sh` 覆盖点

### `smoke`

等价于 `api-smoke`，主要覆盖 API 主链路基线。

### `demo`

等价于 `demo-smoke`，主要覆盖演示数据和答辩可见结果。

### `extended`

细分覆盖如下：

#### 数据源重复策略

1. 同名数据源拒绝
2. 同连接 `WARN` 允许保存且返回 `warningMessage`
3. 同连接 `REJECT` 阻止保存

#### 数据集管理

1. 数据集列表
2. 数据集详情
3. 元数据查询
4. 分页预览
5. 数据集导出响应头校验

#### 治理算子

1. 查询算子列表
2. 禁用治理算子
3. 禁用算子执行拦截
4. 恢复算子状态

#### 日志服务端筛选

1. 按任务类型和状态筛选
2. 按操作人筛选
3. 按时间范围筛选
4. 查询日志详情并校验 `operatorName`

### `all`

执行：

1. `api-smoke`
2. `demo-smoke`
3. `extended`
4. 如开启 `RUN_MYSQL=true`，再执行 MySQL 专项回归

### `gate`

执行：

1. 后端编译
2. 前端构建
3. 全量自动化回归

## 4.5 浏览器人工检查补充项

以下场景不完全依赖脚本，需要浏览器人工验证：

1. 权限实时同步
2. 首页快捷入口与菜单收口
3. 日志按钮级授权
4. 数据库表导入页面交互
5. Excel 导出文件人工打开验证
6. 日志回放后的页面反馈

这些场景的现成操作步骤和期望结果已收录在 `docs/browser-regression-checklist.md`。

# 5. 白盒测试原始材料

本节只整理“最适合写白盒测试”的后端类、关键方法和关键分支，不直接展开完整白盒测试设计。

## 5.1 `AuthService`

- 文件：
  `backend/src/main/java/com/datalake/platform/auth/AuthService.java`
- 关键方法：
  - `login`
- 建议重点分支：
  - 用户存在且状态为 `ENABLED`，密码正确
  - 用户不存在
  - 用户被停用
  - 密码错误
  - 登录成功后 token 生成
  - 登录成功后角色菜单与操作权限装载
- 适合写的白盒点：
  - `BadCredentialsException` 触发路径
  - 角色权限序列化结果装载到 `LoginResponse`

## 5.2 `DataSourceService`

- 文件：
  `backend/src/main/java/com/datalake/platform/datasource/DataSourceService.java`
- 关键方法：
  - `create`
  - `update`
  - `test`
  - `delete`
  - `evaluateDuplicateConnectionStrategy`
  - `normalizeDuplicateConnectionStrategy`
  - `normalizeSourceStatus`
- 建议重点分支：
  - 数据源类型仅允许 `FILE / MYSQL`
  - 同名数据源拒绝
  - 同连接 `ALLOW`
  - 同连接 `WARN`
  - 同连接 `REJECT`
  - `FILE` 数据源测试连接直接成功
  - `MYSQL` 测试连接成功
  - `MYSQL` 测试连接失败
  - 已关联数据集时删除失败
  - 状态仅允许 `ENABLED / DISABLED`

## 5.3 `DataImportService`

- 文件：
  `backend/src/main/java/com/datalake/platform/datasource/DataImportService.java`
- 关键方法：
  - `importFile`
  - `importDatabaseTable`
  - `rerunImport`
  - `detail`
- 建议重点分支：
  - 文件导入成功
  - 文件导入解析失败
  - 数据库表导入成功
  - 数据库表导入失败
  - `rerunImport` 走文件导入重跑
  - `rerunImport` 走数据库导入重跑
  - 原始文件不存在
  - 数据源不可用或未启用
  - `FILE_IMPORT_PREFIX / DATABASE_IMPORT_PREFIX` 分支判断

## 5.4 `DatasetService`

- 文件：
  `backend/src/main/java/com/datalake/platform/dataset/DatasetService.java`
- 关键方法：
  - `createDatasetFromRows`
  - `list`
  - `detail`
  - `delete`
  - `validateDelete`
- 建议重点分支：
  - `businessDomain` 规范化
  - 动态物理表名生成
  - 列表筛选条件为空与非空
  - 数据集不存在
  - 被治理流程输入引用时删除失败
  - 被治理流程输出引用时删除失败
  - 被治理日志引用时删除失败
  - 被导入记录引用时删除失败

## 5.5 `DatasetTableService`

- 文件：
  `backend/src/main/java/com/datalake/platform/dataset/DatasetTableService.java`
- 关键方法：
  - `createPhysicalTable`
  - `insertRows`
  - `preview`
  - `filter`
  - `executeSqlWithColumns`
  - `dropTable`
- 建议重点分支：
  - 动态列类型映射
  - 预览分页
  - 预览字段关键字筛选
  - 过滤条件逻辑组合
  - SQL 执行结果列提取
  - 动态表删除

## 5.6 `QueryAnalysisService`

- 文件：
  `backend/src/main/java/com/datalake/platform/dataset/QueryAnalysisService.java`
- 关键方法：
  - `filter`
  - `sql`
  - `sqlPage`
  - `exportFilter`
  - `exportSql`
  - `integration`
  - `saveIntegrationResult`
  - `normalizeSql`
  - `computeIntegration`
- 建议重点分支：
  - 查询条件为空时报错
  - 旧口径单条件与 `conditions[]` 多条件兼容
  - `AND / OR` 逻辑
  - `TIME_RANGE / BETWEEN`
  - `pageNum / pageSize` 默认值与边界
  - SQL 非 `SELECT`
  - SQL 中包含分号或注释
  - SQL 非当前单表
  - SQL 排序字段不存在
  - 导出 `PAGE / ALL`
  - 集成模式 `JOIN / UNION`
  - 输出数据集名为空
  - 集成结果无字段时报错
  - `JOIN` 冲突列自动重命名 `right_`

## 5.7 `GovernanceService`

- 文件：
  `backend/src/main/java/com/datalake/platform/governance/GovernanceService.java`
- 关键方法：
  - `operators`
  - `updateOperatorStatus`
  - `createFlow`
  - `execute`
  - `executeSavedFlow`
  - `validateOperatorChain`
- 建议重点分支：
  - 查询启用算子与包含停用算子两种列表
  - 算子状态仅允许 `ENABLED / DISABLED`
  - 算子链为空
  - 算子不存在
  - 算子已停用
  - 治理步骤全部成功
  - 治理中间步骤失败
  - 保存流程与直接执行差异
  - 保存流程后回写 `output_dataset_id`

## 5.8 `TaskService`

- 文件：
  `backend/src/main/java/com/datalake/platform/task/TaskService.java`
- 关键方法：
  - `create`
  - `update`
  - `pause`
  - `resume`
  - `delete`
  - `trigger`
  - `replayFromLog`
  - `executeDueTasks`
  - `executeTask`
  - `validateTask`
- 建议重点分支：
  - `IMPORT / GOVERNANCE` 两类任务分支
  - `taskType` 为空
  - `CronExpression` 非法
  - `status` 为空时默认 `ENABLED`
  - `status` 仅允许 `ENABLED / PAUSED`
  - 任务正在运行时重复触发
  - 手动触发成功后状态恢复
  - 失败重试逻辑
  - 运行中任务禁止删除
  - 有日志任务禁止删除
  - 手动日志不可回放
  - 到期任务轮询执行

## 5.9 `TaskLogService`

- 文件：
  `backend/src/main/java/com/datalake/platform/task/TaskLogService.java`
- 关键方法：
  - `list`
  - `detail`
  - `record`
  - `parseInputParams`
  - `parseExecutionSteps`
  - `parseFailureReason`
- 建议重点分支：
  - 按类型筛选
  - 按状态筛选
  - 按操作人筛选
  - 按时间范围筛选
  - 按关键字筛选
  - 日志不存在
  - `inputParams` 解析成功/失败
  - `executionSteps` 解析成功/失败
  - `failureReason` 解析成功/失败
  - 结构化 JSON 过长截断

# 6. 黑盒测试原始材料

本节采用“功能点 / 前置条件 / 输入 / 预期输出 / 来源依据”的写法，后续可直接拆成黑盒测试用例。

## 6.1 认证与权限

| 功能点 | 前置条件 | 输入 | 预期输出 | 来源依据 |
| --- | --- | --- | --- | --- |
| 登录成功 | 服务已启动 | `admin / admin123` 或 `operator / operator123` | 登录成功，进入首页或首个有权限菜单页 | `docs/user-manual.md` |
| 登录失败 | 服务已启动 | 错误用户名或错误密码 | 返回明确失败提示，不能进入系统 | `AuthService.login`、`docs/test-plan.md` |
| 未登录拦截 | 未登录状态 | 直接访问业务页 | 路由被拦截并回到 `/login` | `router/index.ts`、`docs/test-plan.md` |
| 菜单权限收口 | 已登录普通用户 | 移除某菜单权限后刷新或等待同步 | 左侧菜单与首页快捷入口同步收口 | `docs/browser-regression-checklist.md` |
| 操作权限实时同步 | 管理员与普通用户双会话 | 管理员修改 `log.export` 或 `log.replay` | 普通用户无需重新登录即可看到按钮状态变化 | `docs/browser-regression-checklist.md` |

## 6.2 数据源与数据接入

| 功能点 | 前置条件 | 输入 | 预期输出 | 来源依据 |
| --- | --- | --- | --- | --- |
| 新增 `FILE` 数据源 | 管理员已登录 | `sourceName + sourceType=FILE` | 保存成功，列表新增数据源 | `docs/user-manual.md`、`DataSourceService` |
| 新增 `MYSQL` 数据源 | 管理员已登录 | `sourceName + sourceType=MYSQL + host/port/dbName/username/password` | 保存成功，可用于数据库表导入 | `docs/user-manual.md` |
| 同名数据源校验 | 已存在同名数据源 | 再次保存相同 `sourceName` | 保存失败，提示“数据源名称已存在” | `docs/test-plan.md`、`regression-suite.sh` |
| 同连接 `WARN` 策略 | 已存在相同 MYSQL 连接 | `duplicateConnectionStrategy=WARN` | 允许保存，并返回 `warningMessage` | `regression-suite.sh` |
| 同连接 `REJECT` 策略 | 已存在相同 MYSQL 连接 | `duplicateConnectionStrategy=REJECT` | 拒绝保存，并提示已有相同连接配置 | `regression-suite.sh` |
| 数据源测试连接 | 已存在启用中的数据源 | 点击测试连接 | `FILE` 数据源直接成功；`MYSQL` 返回连接结果 | `docs/user-manual.md` |
| 数据源启停 | 已存在数据源 | 点击启用/停用 | 状态变更成功 | `DataSourcesView.vue` |
| 数据源删除校验 | 数据源下已有数据集 | 点击删除 | 删除失败，提示已有数据集不能删除 | `DataSourceService.delete` |
| 文件导入 CSV | 已存在可用数据源 | 上传交易 `CSV`，设置编码和首行为表头 | 生成导入记录和新数据集 | `docs/user-manual.md`、`api-smoke-test.sh` |
| 文件导入 JSON | 已存在可用数据源 | 上传 `JSON` 对象数组文件 | 成功导入，编码/表头选项被忽略 | `docs/user-manual.md` |
| 文件导入 Excel | 已存在可用数据源 | 上传 `xls/xlsx` 文件 | 成功导入，可切换首行为表头 | `docs/user-manual.md` |
| 数据库表导入 | 已存在可用 `MYSQL` 数据源 | 选择 `schema / table` 并导入 | 生成新数据集和导入历史 | `docs/user-manual.md`、`browser-regression-checklist.md` |
| 导入历史筛选 | 已存在多条导入记录 | 按业务域、状态、时间范围筛选 | 历史列表按条件收敛 | `DataImportView.vue` |
| 导入详情查看 | 已存在导入记录 | 打开详情 | 可看到源文件名/表名、业务域、导入参数、状态、错误信息 | `DataImportView.vue` |

## 6.3 数据集管理与查询分析

| 功能点 | 前置条件 | 输入 | 预期输出 | 来源依据 |
| --- | --- | --- | --- | --- |
| 数据集列表筛选 | 已存在多个数据集 | 按关键字、来源、状态、业务域筛选 | 列表按条件收敛 | `docs/user-manual.md` |
| 数据集详情查看 | 已存在数据集 | 选中一个数据集 | 显示详情、元数据和预览 | `DatasetsView.vue` |
| 数据预览字段筛选 | 已存在数据集 | 输入字段和关键字 | 预览列表按条件收敛 | `DatasetsView.vue` |
| 数据集导出 | 已存在数据集且有导出权限 | 导出 `CSV / JSON / Excel` | 下载成功 | `docs/user-manual.md` |
| 数据集删除成功 | 数据集未被引用且管理员有权限 | 点击删除 | 删除成功 | `DatasetService.delete` |
| 数据集删除失败 | 数据集被流程/日志/导入记录引用 | 点击删除 | 返回被引用不能删除提示 | `DatasetService.validateDelete` |
| 条件查询-单条件 | 已选择数据集 | `product_name LIKE 鼠标` | 返回匹配记录 | `api-smoke-test.sh` |
| 条件查询-多条件 OR | 已选择数据集 | `order_status = completed OR amount >= 200` | 返回满足任一条件的记录 | `api-smoke-test.sh` |
| 条件查询-时间范围 | 已选择数据集 | `category LIKE 3C AND order_time TIME_RANGE ...` | 返回时间范围内的匹配记录 | `api-smoke-test.sh` |
| 条件查询排序分页 | 已选择数据集 | 指定 `sortField / sortOrder / pageNum / pageSize` | 返回分页结果 | `docs/test-plan.md` |
| SQL 查询成功 | 已选择数据集 | `SELECT * FROM dataset LIMIT 20` | 返回单表查询结果 | `api-smoke-test.sh` |
| SQL 分页排序成功 | 已选择数据集 | `SELECT product_name,amount,order_status FROM dataset` + `sortField=amount DESC` | 返回排序分页结果 | `api-smoke-test.sh` |
| SQL 非法输入拦截 | 已选择数据集 | 非 `SELECT`、含分号/注释或跨表 SQL | 返回友好错误提示 | `QueryAnalysisService.normalizeSql` |
| 电商指标查询 | 已选择数据集 | 订单趋势/销售趋势/热销排行/分类占比/低库存 | 返回图表和表格 | `docs/user-manual.md` |
| 数据集成预览-JOIN | 已准备两个数据集 | `JOIN + leftField/rightField` | 返回集成预览结果 | `ecommerce-demo-smoke.sh` |
| 数据集成预览-UNION | 已准备两个数据集 | `UNION` | 返回合并结果预览 | `QueryAnalysisService.computeIntegration` |
| 数据集成保存 | 已有集成预览结果 | 输出数据集名称 + 业务域 | 生成新的集成数据集 | `ecommerce-demo-smoke.sh` |

## 6.4 数据治理

| 功能点 | 前置条件 | 输入 | 预期输出 | 来源依据 |
| --- | --- | --- | --- | --- |
| 查询治理算子 | 已登录 | 打开治理页 | 显示治理算子列表 | `GovernanceView.vue` |
| 停用治理算子 | 有治理管理权限 | 把算子状态改为 `DISABLED` | 状态变更成功 | `GovernanceService.updateOperatorStatus` |
| 禁用算子执行拦截 | 某算子已停用 | 使用该算子执行治理 | 返回拦截错误 | `regression-suite.sh` |
| 保存治理流程 | 有治理管理权限 | `flowName + datasetId + operatorChain` | 流程保存成功 | `docs/user-manual.md` |
| 治理执行成功 | 已存在输入数据集 | `ORDER_DEDUP + AMOUNT_NORMALIZE + TIME_NORMALIZE` | 生成新数据集，返回统计结果 | `ecommerce-demo-smoke.sh` |
| 治理执行失败 | 已存在输入数据集 | `FIELD_CONVERT(product_name -> NUMBER)` | 返回失败提示并写入失败日志 | `ecommerce-demo-smoke.sh` |

## 6.5 任务调度与日志监控

| 功能点 | 前置条件 | 输入 | 预期输出 | 来源依据 |
| --- | --- | --- | --- | --- |
| 创建导入任务 | 已存在导入记录 | `taskType=IMPORT + targetId + cronExpr` | 任务保存成功 | `api-smoke-test.sh` |
| 创建治理任务 | 已存在治理流程 | `taskType=GOVERNANCE + targetId + cronExpr` | 任务保存成功 | `api-smoke-test.sh` |
| 立即执行任务 | 已存在任务 | 点击“立即执行” | 返回执行结果并生成日志 | `TasksView.vue` |
| 暂停任务 | 已存在启用任务 | 点击“暂停” | 状态变为 `PAUSED` | `TaskService.pause` |
| 恢复任务 | 已存在暂停任务 | 点击“恢复” | 状态变为 `ENABLED`，下次执行时间更新 | `TaskService.resume` |
| 失败任务重试 | 已存在失败任务 | 立即执行或回放 | 重新触发执行 | `TaskService.executeTask` |
| 日志列表筛选-类型状态 | 已存在日志 | `taskType=GOVERNANCE & status=FAILED` | 只返回治理失败日志 | `regression-suite.sh` |
| 日志列表筛选-操作人 | 已存在日志 | `operatorUser=某用户` | 返回该操作人日志 | `regression-suite.sh` |
| 日志列表筛选-时间范围 | 已存在日志 | `startTime / endTime` | 返回时间范围内日志 | `regression-suite.sh` |
| 日志详情查看 | 已存在日志 | 打开某条日志 | 显示 `inputParams / executionSteps / failureReason / operatorName` | `ecommerce-demo-smoke.sh` |
| 失败日志回放 | 日志来自调度任务且有权限 | 点击“一键回放” | 页面给出成功反馈，日志条数增加 | `docs/browser-regression-checklist.md` |
| 日志导出当前筛选结果 | 有 `log.export` 权限 | 点击导出 `CSV / JSON` | 当前筛选结果被导出为文件 | `LogsView.vue`、`browser-regression-checklist.md` |

## 6.6 用户与权限

| 功能点 | 前置条件 | 输入 | 预期输出 | 来源依据 |
| --- | --- | --- | --- | --- |
| 新增用户 | 管理员已登录 | 用户名、密码、角色、状态 | 用户创建成功 | `docs/user-manual.md` |
| 编辑用户 | 管理员已登录 | 修改用户名、角色、状态或密码 | 用户信息更新成功 | `UsersView.vue` |
| 停用用户 | 管理员已登录 | 把状态改为 `DISABLED` | 用户被停用；被停用用户不能正常使用登录态 | `AuthService`、`docs/test-plan.md` |
| 删除用户 | 管理员已登录 | 删除非当前登录用户 | 删除成功 | `AuthService.deleteUser` |
| 当前登录用户自删/自停用拦截 | 管理员已登录 | 尝试删除自己或停用自己 | 返回明确错误提示 | `AuthService.updateUser/deleteUser` |
| 角色描述修改 | 管理员已登录 | 更新 `roleDesc` | 角色说明更新成功 | `UsersView.vue` |
| 菜单权限修改 | 管理员已登录 | 勾选/取消某菜单权限 | 角色菜单权限更新成功 | `UsersView.vue` |
| 操作权限修改 | 管理员已登录 | 勾选/取消某操作权限 | 角色操作权限更新成功 | `UsersView.vue` |
| 权限实时同步 | 双会话浏览器 | 修改 `OPERATOR` 菜单或操作权限 | 现有会话自动感知变化 | `docs/browser-regression-checklist.md` |

# 附录：材料来源索引

| 材料类别 | 主要来源 |
| --- | --- |
| 页面清单与截图顺序 | `docs/demo-runbook.md`、`docs/demo-script.md`、`router/index.ts`、各 `View` |
| 登录与使用步骤 | `docs/user-manual.md` |
| 浏览器交互与人工截图点 | `docs/browser-regression-checklist.md` |
| 自动化脚本覆盖 | `scripts/api-smoke-test.sh`、`scripts/ecommerce-demo-smoke.sh`、`scripts/regression-suite.sh`、`docs/regression-coverage-matrix.md` |
| 测试计划口径 | `docs/test-plan.md` |
| 测试执行结果样本 | `docs/test-report.md` |
| 白盒候选类与方法 | 后端 `Service` 实现 |
| 黑盒功能点口径 | `docs/test-plan.md`、`docs/user-manual.md`、现有脚本和页面代码 |

## 建议后续拆分方式

1. 写《软件使用说明》时：
   直接复用第 1 章和第 3 章。
2. 写《黑盒测试文档》时：
   直接复用第 2 章、第 3 章、第 4 章和第 6 章。
3. 写《白盒测试文档》时：
   直接复用第 4 章和第 5 章，并补充控制流图、判定条件和覆盖策略。
