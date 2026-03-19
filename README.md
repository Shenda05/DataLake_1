# 数据湖管理平台

这是一个面向课程项目的数据湖管理平台工程仓库，按 `frontend + backend + docs + database` 四层组织。当前版本已经从“文档骨架”推进到“可运行主链路”，可以直接演示 `登录 -> 数据源 -> 文件导入 -> 数据集 -> 查询分析 -> 治理 -> 任务 -> 日志` 的 P0 闭环。

## 当前已落地内容

- `frontend/`：Vue 3 + Element Plus + Pinia + ECharts 的前端应用，页面已接入真实后端 API。
- `backend/`：Spring Boot 后端，已实现 JWT 鉴权、数据源持久化、文件导入、数据集管理、查询分析、治理执行、任务调度和日志落库。
- `database/`：核心业务表建表脚本，包含 `import_record`、`data_set.physical_table_name`、`task_log` 等 P0 所需字段。
- `docs/`：接口约定、架构说明、测试计划和周计划文档。
- `storage/`：运行时生成，保存上传原文件。

## 主链路能力

1. 登录后通过 JWT 访问业务接口，管理员与普通用户菜单不同。
2. 数据源支持新增、测试连接、删除和列表查看。
3. 文件导入支持 `CSV / JSON / Excel`，导入后自动生成数据集、元数据和物理表。
4. 数据集支持列表、详情、元数据和分页预览。
5. 查询分析支持结构化过滤、单数据集 SQL 查询、基础统计与图表。
6. 治理支持 4 类基础算子串联执行，输出新数据集。
7. 任务支持 `IMPORT / GOVERNANCE` 两类调度，日志统一落库并在首页聚合展示。

## 本地启动

### 前端

```bash
cd frontend
npm install
npm run dev
```

默认访问 `http://localhost:8080/api`。如果后端改了端口，可以在前端启动前指定：

```bash
VITE_API_BASE_URL=http://localhost:8081/api npm run dev
```

### 后端

后端要求 `JDK 17+`。当前机器默认 `javac` 是 11，如需使用本机已安装的 JDK 18，可执行：

```bash
cd backend
env JAVA_HOME='/Users/xyd/Library/Java/JavaVirtualMachines/corretto-18.0.2/Contents/Home' PATH='/Users/xyd/Library/Java/JavaVirtualMachines/corretto-18.0.2/Contents/Home/bin':$PATH mvn spring-boot:run
```

如果本机 `8080` 已被占用，可临时改端口启动：

```bash
env JAVA_HOME='/Users/xyd/Library/Java/JavaVirtualMachines/corretto-18.0.2/Contents/Home' PATH='/Users/xyd/Library/Java/JavaVirtualMachines/corretto-18.0.2/Contents/Home/bin':$PATH mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

默认使用 H2 内存库便于本地演示，启动时会自动初始化表结构和种子账号。

### MySQL 启动

如需切到 MySQL，请先手动创建数据库 `data_lake_platform`，再使用 MySQL profile：

```bash
cd backend
env JAVA_HOME='/Users/xyd/Library/Java/JavaVirtualMachines/corretto-18.0.2/Contents/Home' PATH='/Users/xyd/Library/Java/JavaVirtualMachines/corretto-18.0.2/Contents/Home/bin':$PATH mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

默认配置见 [application-mysql.yml](/Users/xyd/Desktop/DataLake/backend/src/main/resources/application-mysql.yml)。

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

## 当前限制

- 调度当前真实启用的任务类型只有 `IMPORT` 和 `GOVERNANCE`。
- SQL 查询限制为单数据集单表 `SELECT`，不支持多表 join。
- 导出当前优先保留在接口与数据模型层，前端下载入口仍可继续增强。

## 下一步建议

- 补一轮浏览器端到端验收，重点验证登录、导入、治理和任务执行。
- 在 MySQL 环境做一次完整联调，确认文件存储路径和字符集配置。
- 继续增强导出、系统设置和数据集成模块，作为 P1 迭代。
