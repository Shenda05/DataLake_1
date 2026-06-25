# 模块与包结构说明（冻结版）

更新时间：`2026-04-04`

## 1. 仓库分层

- `frontend/`：Vue 3 管理台（路由、页面、权限交互、图表展示）
- `backend/`：Spring Boot API（认证、接入、资产、查询、治理、调度、日志）
- `database/`：数据库 schema
- `scripts/`：冒烟 / 回归 / 演示脚本
- `docs/`：接口、架构、测试、演示、冻结交付文档

## 2. 前端模块

### 入口与基础层

- `src/router`：页面路由和权限守卫
- `src/stores`：鉴权会话与权限状态（Pinia）
- `src/api`：接口类型与请求封装

### 业务页面层

- 仪表盘：`DashboardView.vue`
- 接入与资产：`DataSourcesView.vue`、`DataImportView.vue`、`DatasetsView.vue`
- 查询分析：`QueryAnalysisView.vue`
- 治理：`GovernanceView.vue`
- 调度与日志：`TasksView.vue`、`LogsView.vue`
- 权限：`UsersView.vue`

## 3. 后端模块

### 认证与通用

- `auth`：登录、用户、角色与权限
- `common`：统一响应、异常处理、安全上下文、启动初始化

### 业务能力

- `dashboard`：首页聚合指标
- `datasource`：数据源管理与导入
- `dataset`：数据集、元数据、预览、导出、查询分析、数据集成
- `governance`：算子管理、流程管理、执行
- `task`：任务管理、手动触发、调度执行、日志记录与回放

## 4. 主链路映射（课程演示）

1. 登录：`auth`
2. 电商数据接入：`datasource`
3. 数据集管理：`dataset`
4. 查询分析与集成：`dataset`（QueryAnalysis）
5. 治理执行：`governance`
6. 调度任务：`task`
7. 日志观测：`task`

## 5. 脚本与文档资产边界

### `scripts/`

- `api-smoke-test.sh`：主链路 API 基线
- `ecommerce-demo-smoke.sh`：演示数据与答辩路径准备
- `mysql-regression-check.sh`：MySQL 环境专项回归
- `regression-suite.sh`：统一编排与冻结门禁

### `docs/`

- `api-contract.md`：接口行为与字段约定
- `api-inventory.md`：接口目录清单
- `db-schema-catalog.md`：表结构与字段职责
- `user-manual.md`：用户操作路径
- `demo-runbook.md`：演示准备唯一口径
- `browser-regression-checklist.md`：人工浏览器回归记录
- `freeze-gap-review-round7.md` / `freeze-release-note.md`：冻结审计与发布结论

## 6. 当前边界说明

- 保留通用平台骨架，业务表达电商化。
- 当前任务类型固定 `IMPORT / GOVERNANCE`。
- 数据集成为查询页子标签能力，不是独立任务类型或独立菜单。
- 本轮不做治理 / 调度 / 查询引擎重构。
