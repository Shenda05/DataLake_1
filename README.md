# 电商数据湖管理平台（Web）

这是一个面向课程项目的电商数据湖管理平台工程仓库，按 `frontend + backend + docs + database` 四层组织。当前版本在保留通用平台骨架的前提下，已完成订单/商品/库存优先的电商化 MVP 改造，主链路覆盖登录、接入、数据集、查询分析、治理、调度和日志监控。

## 当前已落地内容

- `frontend/`：Vue 3 + Element Plus + Pinia + ECharts 前端，已完成“电商仪表盘、数据接入、数据集管理、查询分析（含电商指标与数据集成子标签）、治理、调度、日志、用户管理”页面与真实 API 对接。
- 用户与权限页已经从静态样例改成真实管理页，支持管理员新增、编辑、停用和删除用户，并可按分组权限树调整角色说明、菜单权限和关键操作权限。
- 前端当前会话会定时同步后端 profile；用户被停用、角色被切换、菜单或操作权限被调整后，现有会话会自动感知并更新可见导航与可执行按钮。
- 首页和日志页已经补齐跨模块快捷入口；首页新增电商核心指标（最近失败任务数、7 天订单/销售趋势、热销 Top5、低库存数量），日志页新增导入/治理/调度分类统计与失败原因聚合。
- 任务页和日志页现在支持筛选条件记忆、URL 查询参数回填，并补齐了执行目标、时间范围、近 7 天失败记录等组合筛选；两页都支持保存常用筛选视图，日志页额外支持失败日志一键回放和按当前筛选结果导出。
- 数据源管理已补齐编辑、启停、友好查重与同连接处理策略；`MYSQL` 数据源支持 `ALLOW / WARN / REJECT` 三种重复连接策略，保存成功时可返回 `warningMessage` 提示复用已有连接。
- `backend/`：Spring Boot 后端，已实现 JWT 登录鉴权、仪表盘聚合、数据源持久化、文件/数据库表导入、元数据生成、查询分析、治理执行、任务 CRUD、手动触发、任务日志和失败日志回放；并新增电商指标 API、基础 Join/Union 集成 API 与业务域字段贯穿。
- `database/`：包含 `sys_user`、`sys_role`、`data_source`、`import_record`、`data_set`、`meta_field` 等表结构；`import_record` 与 `data_set` 已新增 `business_domain` 字段。
- `docs/`：接口约定、架构说明、测试计划/报告、用户手册、演示脚本、冻结审计、接口清单、表结构清单、模块清单和浏览器回归检查单。
- `scripts/`：提供主链路冒烟、演示冒烟、MySQL 回归和统一回归入口脚本。
- `storage/`：运行时自动生成，用于保存上传原文件。

## 当前主链路
1. 使用默认账号登录，前端从后端获取 token 和菜单权限。
2. 电商仪表盘从数据库读取统计卡片、最近失败任务、近 7 天订单/销售趋势、热销商品 Top5 和低库存数量。
3. 管理员可以创建、编辑、测试、启停和删除数据源；`MYSQL` 数据源保存时支持同连接 `ALLOW / WARN / REJECT` 策略。
4. 电商数据接入页支持文件导入和 `MYSQL` 数据库表导入，导入时可选择业务域（用户/商品/交易/支付/库存/评价/行为日志）；数据库导入会自动识别 `Schema / Database` 下拉，切换后同步刷新表列表与预览，导入后自动生成数据集和元数据。
5. 电商数据集页支持按业务域筛选、元数据查看、分页预览、关键字筛选、`CSV / JSON / Excel` 导出和删除校验。
6. 电商查询分析页支持条件查询、单表 SQL、电商指标查询（订单量/销售额趋势、热销排行、分类占比、库存预警）和数据集成子标签（Join/Union）。
7. 电商治理页支持保存治理流程、手动执行算子链并输出新数据集，已补齐订单去重/金额标准化/时间标准化/分类标准化/状态标准化算子。
8. 电商任务调度页支持创建 `IMPORT / GOVERNANCE` 两类任务、暂停、恢复和立即执行，并提供每日订单导入等电商模板任务入口。
9. 日志页支持查看执行摘要、耗时、错误信息、最近执行明细、失败日志一键回放，以及导入/治理/调度分类统计与失败原因 Top5 聚合。

## 电商 MVP 关键变更

- 业务域枚举：`USER / PRODUCT / TRADE / PAYMENT / INVENTORY / REVIEW / BEHAVIOR_LOG`。
- 新增字段：`data_set.business_domain`、`import_record.business_domain`。
- 新增接口：
  - `GET /api/dashboard/ecommerce`
  - `POST /api/queries/ecommerce-metric`
  - `POST /api/queries/integration`
- 保留兼容：任务引擎仍保持 `IMPORT / GOVERNANCE`，通过电商模板任务名承载场景语义。

## 本地启动

### 前端

```bash
cd frontend
npm install
npm run dev
```

默认访问 `http://localhost:8080/api`。如果后端改了端口，可以这样启动：

```bash
VITE_API_BASE_URL=http://localhost:8081/api npm run dev
```

### 后端

后端要求 `JDK 17+`。建议先确认当前 Java 版本：

```bash
java -version
javac -version
```

如果默认版本不是 `17+`，请先切换到本机可用的 JDK 17（或更高）后再启动。示例：

```bash
export JAVA_HOME=/path/to/your/jdk17
export PATH="$JAVA_HOME/bin:$PATH"
cd backend
mvn spring-boot:run
```

后端默认使用 H2 内存库，启动时会自动初始化表结构和默认账号。

如果本机 `8080` 已被占用，可临时改端口启动：

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

如果需要切到 MySQL，可先创建数据库 `data_lake_platform`，再使用：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

推荐先准备 MySQL 库和字符集：

```sql
CREATE DATABASE IF NOT EXISTS data_lake_platform
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;
```

然后执行建表脚本：

```bash
/usr/local/mysql/bin/mysql -uroot -proot --default-character-set=utf8mb4 data_lake_platform < database/schema.sql
```

如果你不想把账号密码写死在配置里，可以用环境变量覆盖：

```bash
export MYSQL_URL='jdbc:mysql://127.0.0.1:3306/data_lake_platform?useUnicode=true&characterEncoding=utf8&connectionTimeZone=Asia/Shanghai&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
export MYSQL_USERNAME='root'
export MYSQL_PASSWORD='root'
export APP_STORAGE_ROOT='/absolute/path/to/DataLake/storage'
export APP_TASK_POLL_DELAY_MS='15000'
export APP_DATA_SOURCE_DUPLICATE_CONNECTION_STRATEGY='WARN'
```

MySQL 联调时建议重点确认三件事：

1. 时区：数据库、JDBC URL 和应用输出都统一到 `Asia/Shanghai`。
2. 字符集：数据库和导入终端都使用 `utf8mb4`，避免中文字段或日志摘要乱码。
3. 存储路径：`APP_STORAGE_ROOT` 尽量配置为稳定的绝对路径，避免相对路径随着启动目录变化。

### 最小冒烟验证

后端启动后，可以直接运行仓库里的接口冒烟脚本：

```bash
bash scripts/api-smoke-test.sh
```

如果后端不是跑在默认 `8080`，可以覆盖 `BASE_URL`：

```bash
BASE_URL=http://127.0.0.1:8086/api bash scripts/api-smoke-test.sh
```

也可以运行统一回归入口（推荐冻结前执行）：

```bash
bash scripts/regression-suite.sh all
```

如果要补齐 MySQL 环境下的数据库表导入、`xlsx` 导出、日志回放和权限同步回归，可执行：

```bash
BASE_URL=http://127.0.0.1:8093/api \
MYSQL_SOURCE_USER='root' \
MYSQL_SOURCE_PASSWORD='你的 MySQL 密码' \
bash scripts/mysql-regression-check.sh
```

最近一次已验证通过的回归结果见 [mysql-e2e-checklist.md](/Users/xyd/Desktop/DataLake/docs/mysql-e2e-checklist.md) 和 [test-report.md](/Users/xyd/Desktop/DataLake/docs/test-report.md)。`2026-03-27` 的样本结果包含：

- `importedTable=operator_def`
- `importedDatasetId=11`
- `logCountBeforeReplay=8`
- `logCountAfterReplay=9`
- `operatorActions` 已覆盖 `dataset.export / query.export / log.export / log.replay`

冻结交付资料建议从以下文档查看：

- [freeze-gap-review-round7.md](/Users/xyd/Desktop/DataLake/docs/freeze-gap-review-round7.md)
- [api-inventory.md](/Users/xyd/Desktop/DataLake/docs/api-inventory.md)
- [db-schema-catalog.md](/Users/xyd/Desktop/DataLake/docs/db-schema-catalog.md)
- [module-map.md](/Users/xyd/Desktop/DataLake/docs/module-map.md)
- [demo-runbook.md](/Users/xyd/Desktop/DataLake/docs/demo-runbook.md)
- [regression-coverage-matrix.md](/Users/xyd/Desktop/DataLake/docs/regression-coverage-matrix.md)
- [freeze-release-note.md](/Users/xyd/Desktop/DataLake/docs/freeze-release-note.md)

## 目录结构

```text
.
├── backend
├── database
├── docs
├── frontend
├── 产品功能设计文档.md
├── 小组成员分工.md
└── 数据湖管理平台_选题与功能.md
```

## 默认账号

- 管理员：`admin / admin123`
- 普通用户：`operator / operator123`

## 治理、任务、日志演示流程

### 1. 导入一份输入数据

1. 登录管理员账号。
2. 进入“数据源管理”创建一个 `FILE` 或 `MYSQL` 类型数据源。
3. 进入“电商数据接入”，上传 `CSV / JSON / Excel` 文件或从数据库数据源中选择表导入，并指定业务域（优先 `TRADE/PRODUCT/INVENTORY`）。
4. 导入完成后，到“电商数据集管理”确认输入数据集、元数据和预览数据已生成。

### 2. 创建并执行治理流程

1. 进入“电商数据治理”页面。
2. 选择输入数据集，填写流程名称。
3. 添加算子步骤，例如：
   - `ORDER_DEDUP`：`{"field":"order_id"}`
   - `AMOUNT_NORMALIZE`：`{"field":"amount"}`
   - `TIME_NORMALIZE`：`{"field":"order_time"}`
   - `STATUS_NORMALIZE`：`{"field":"order_status"}`
4. 点击“保存流程”。
5. 点击“执行流程”，系统会生成新的输出数据集，并返回日志编号。

### 3. 创建任务并手动触发

1. 进入“电商任务调度”页面。
2. 任务类型选 `GOVERNANCE` 时，目标选择已保存治理流程。
3. 任务类型选 `IMPORT` 时，目标选择已有导入历史记录。
4. 可直接套用电商模板任务：每日订单导入、订单自动治理、商品销量统计、库存预警统计。
5. 填写 `Cron` 表达式，例如 `0 0 2 * * *`。
6. 创建后可以点击“立即执行”，也可以保留给轮询调度器自动执行。
7. 当前任务页已支持自动刷新和最近执行结果提示，适合现场观察状态变化。

### 4. 查看日志与首页汇总

1. 进入“任务与日志监控”页面查看最新执行记录。
2. 可按状态、类型、关键字、执行目标、执行时间范围以及“近 7 天失败”快速筛选日志，并查看导入/治理/调度分类统计。
3. 当前任务页和日志页筛选都会自动记忆，并支持保存常用筛选视图，同步到地址栏。
4. 需要留档时可直接导出当前筛选结果；数据集页和查询页额外支持 `Excel` 导出。
5. 选中某条日志后，右侧会显示执行摘要、错误信息、耗时和操作用户，并可直接跳回任务页、来源模块或一键回放；失败原因 Top5 可用于快速排障。
6. 返回电商仪表盘可展示最近任务、最近日志、订单/销售趋势、热销商品和低库存指标，适合答辩演示。

## 当前约束

- 当前任务类型只启用了 `IMPORT` 和 `GOVERNANCE`。
- 当前调度实现为应用内轮询，不依赖 Quartz。
- 导入任务重跑依赖原始导入源仍然可访问：文件导入需要 `storage/` 中原文件仍存在，数据库表导入需要原 `MYSQL` 数据源和表仍可连接。
- 数据库表导入当前只支持 `MYSQL` 类型数据源。
- 数据源测试连接是显式校验动作，平台不维护“已测试通过”持久状态；同连接重复策略只影响保存动作，不影响已保存数据源的测试连接、启停和导入。
- SQL 查询仍限定为当前数据集对应的单表；跨表分析目前仅支持查询页“数据集成”子标签中的基础 `JOIN / UNION` 能力。
- 当前权限模型已支持“角色驱动菜单 + 接口鉴权 + 分组权限树 + 关键按钮级操作权限”，已覆盖治理执行、治理保存、任务配置、任务触发、数据集导出、查询导出、日志导出和日志回放等高频操作；首页快捷入口也会随菜单权限自动收口，但尚未扩展到全量页面的每一个按钮。

## 下一步建议

- 在角色基础上继续补齐剩余页面的按钮级授权，例如首页快捷操作、日志导出和部分只读页中的辅助动作。
- 将数据库表导入从 `MYSQL` 扩展到更多关系型数据源，并补充更完整的元数据映射策略。
- 为日志回放补“按原始执行参数重放”和“失败原因聚类”。
- 如果后续要长期运行演示环境，可以把当前应用内轮询调度平滑切到 Quartz 或独立调度器。
