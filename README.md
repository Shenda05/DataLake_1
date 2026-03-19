# 数据湖管理平台

这是一个面向课程项目的数据湖管理平台工程仓库，按 `frontend + backend + docs + database` 四层组织。当前 `develop` 已经把 P0 主链路基本接通，包含登录、数据接入、数据集管理、查询分析、治理执行、任务调度和日志监控。

## 当前已落地内容

- `frontend/`：Vue 3 + Element Plus + Pinia + ECharts 前端，登录、首页、数据源、文件导入、数据集、查询分析、治理、任务、日志页面都已接入真实 API。
- `backend/`：Spring Boot 后端，已实现 JWT 登录鉴权、仪表盘聚合、数据源持久化、文件导入、元数据生成、查询分析、治理执行、任务 CRUD、手动触发和任务日志。
- `database/`：包含 `sys_user`、`sys_role`、`data_source`、`import_record`、`data_set`、`meta_field` 等表结构。
- `docs/`：接口约定、架构说明、周计划与测试计划。
- `storage/`：运行时自动生成，用于保存上传原文件。

## 当前主链路
1. 使用默认账号登录，前端从后端获取 token 和菜单权限。
2. 首页从数据库读取统计卡片、近 7 天任务趋势和最近任务。
3. 管理员可以创建、测试和删除数据源。
4. 文件导入支持 `CSV / JSON / Excel`，导入后自动生成数据集和元数据。
5. 数据集页支持真实列表、元数据查看、分页预览、关键字筛选和 `CSV / JSON` 导出。
6. 查询分析页支持条件查询、单表 SQL 查询、分析摘要和图表展示。
7. 治理页支持保存治理流程、手动执行算子链并输出新数据集。
8. 任务页支持创建 `IMPORT / GOVERNANCE` 两类任务、暂停、恢复和立即执行。
9. 日志页支持查看执行摘要、耗时、错误信息和最近执行明细。

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

后端要求 `JDK 17+`。当前机器默认 `javac` 是 11，如需使用本机已安装的 JDK 18，可执行：

```bash
cd backend
env JAVA_HOME='/Users/xyd/Library/Java/JavaVirtualMachines/corretto-18.0.2/Contents/Home' PATH='/Users/xyd/Library/Java/JavaVirtualMachines/corretto-18.0.2/Contents/Home/bin':$PATH mvn spring-boot:run
```

后端默认使用 H2 内存库，启动时会自动初始化表结构和默认账号。

如果本机 `8080` 已被占用，可临时改端口启动：

```bash
env JAVA_HOME='/Users/xyd/Library/Java/JavaVirtualMachines/corretto-18.0.2/Contents/Home' PATH='/Users/xyd/Library/Java/JavaVirtualMachines/corretto-18.0.2/Contents/Home/bin':$PATH mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

如果需要切到 MySQL，可先创建数据库 `data_lake_platform`，再使用：

```bash
env JAVA_HOME='/Users/xyd/Library/Java/JavaVirtualMachines/corretto-18.0.2/Contents/Home' PATH='/Users/xyd/Library/Java/JavaVirtualMachines/corretto-18.0.2/Contents/Home/bin':$PATH mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

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
2. 进入“数据源管理”创建一个 `FILE` 类型数据源。
3. 进入“数据接入”上传 `CSV / JSON / Excel` 文件。
4. 导入完成后，到“数据集管理”确认输入数据集和元数据已生成。

### 2. 创建并执行治理流程

1. 进入“数据治理”页面。
2. 选择输入数据集，填写流程名称。
3. 添加算子步骤，例如：
   - `NULL_FILL`：`{"field":"industry","fillValue":"UNKNOWN"}`
   - `FILTER_KEEP`：`{"field":"industry","operator":"LIKE","value":"智能"}`
4. 点击“保存流程”。
5. 点击“执行流程”，系统会生成新的输出数据集，并返回日志编号。

### 3. 创建任务并手动触发

1. 进入“任务调度”页面。
2. 任务类型选 `GOVERNANCE` 时，目标选择已保存治理流程。
3. 任务类型选 `IMPORT` 时，目标选择已有导入历史记录。
4. 填写 `Cron` 表达式，例如 `0 */5 * * * *`。
5. 创建后可以点击“立即执行”，也可以保留给轮询调度器自动执行。

### 4. 查看日志与首页汇总

1. 进入“日志监控”页面查看最新执行记录。
2. 可按状态、类型、关键字筛选日志。
3. 选中某条日志后，右侧会显示执行摘要、错误信息、耗时和操作用户。
4. 返回首页可展示最近任务、最近日志和任务趋势，适合答辩演示。

## 当前约束

- 当前任务类型只启用了 `IMPORT` 和 `GOVERNANCE`。
- 当前调度实现为应用内轮询，不依赖 Quartz。
- 导入任务基于已有导入记录重跑原始文件，因此原始文件需要仍然存在于 `storage/` 目录。
- 当前导出仅支持 `CSV / JSON`，Excel 导出仍属于后续增强项。

## 下一步建议

- 在 MySQL 环境补一轮完整联调，确认时区、字符集和文件存储路径。
- 为任务页补自动刷新和最近执行结果提示，方便演示定时执行效果。
- 继续补充用户权限编辑、数据库表导入和 Excel 导出等 P1 能力。
