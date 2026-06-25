# 测试报告

## 测试日期

- `2026-03-25`
- `2026-03-27`
- `2026-04-04`

## 本轮验证范围

- 前端生产构建
- 后端编译
- H2 默认配置端到端冒烟
- MySQL 本地连通性检查
- MySQL profile 真实启动与权限联调
- MySQL 回归辅助脚本验证
- 浏览器人工检查单执行
- 冻结前文档 / 脚本 / 演示资产一致性复审

## 实际执行结果

### 1. 构建验证

- `frontend`：`npm run build` 通过
- `backend`：历史记录中曾在 `JDK 18` 下执行 `mvn -DskipTests compile` 通过；当前冻结版本统一要求 `JDK 17+`

### 2. H2 端到端冒烟

使用临时端口启动后端，并执行 [api-smoke-test.sh](/Users/xyd/Desktop/DataLake/scripts/api-smoke-test.sh)。

已跑通链路：

1. 管理员登录
2. 创建文件数据源
3. 上传交易 CSV 文件并生成数据集
4. 条件查询、多条件查询、时间范围查询
5. SQL 查询与 SQL 分页排序查询
6. 创建治理流程
7. 创建并触发治理任务
8. 创建并触发导入任务
9. 获取任务日志列表

关键结果样本：

- `sourceId=1`
- `datasetId=1`
- `importId=1`
- `governanceTaskId=1`
- `importTaskId=2`
- `logCount=2`

结论：当前 `P0` 主链路的核心 API 在默认 H2 环境下可正常工作。

### 3. MySQL 连通性检查

已在本机尝试使用默认凭据：

```bash
mysql -h127.0.0.1 -P3306 -uroot -proot
```

结果：

- 可以确认本机 MySQL 监听可达
- 但 `root / root` 被拒绝，返回 `ERROR 1045 (28000): Access denied`

结论：

- `mysql profile` 的真实联调需要使用本机实际可用的 `MYSQL_USERNAME / MYSQL_PASSWORD`
- 当前阻塞点是账号权限，不是代码编译或服务监听问题

### 4. MySQL profile 真实联调

`2026-03-27` 已使用本机实际可用的 MySQL 账号完成一轮真实联调。

已执行动作：

1. 创建数据库 `data_lake_platform`
2. 导入 [schema.sql](/Users/xyd/Desktop/DataLake/database/schema.sql)
3. 使用 `mysql` profile 启动后端
4. 执行 [api-smoke-test.sh](/Users/xyd/Desktop/DataLake/scripts/api-smoke-test.sh)
5. 额外验证首页概览、用户与权限、日志接口和实时权限同步

关键结果：

- `api-smoke-test.sh` 在 MySQL 环境通过
- `sourceId=1`
- `datasetId=1`
- `importId=1`
- `governanceTaskId=1`
- `importTaskId=2`
- `logCount=2`
- 首页概览返回 `dataSources=1`、`totalTasks=2`、`successTasks=2`
- 权限页接口 `roles / users / auth/profile` 返回正常
- `operator` 角色在 MySQL 环境下已返回 `log.export`
- 管理员移除 `operator` 的 `log.export` 后，同一 `operator` token 的 `/auth/profile` 会实时移除该权限；验证后已恢复角色配置

结论：

- `mysql profile` 已经跑通到真实 MySQL 启动、主链路冒烟、首页 / 权限页 / 日志页接口验证和实时权限同步验证
- 当前剩余的 MySQL 侧缺口已不在后端联通，而主要是浏览器层人工 UI 验证与文档收口

### 5. MySQL 回归辅助脚本验证

`2026-03-27` 已继续执行 [mysql-regression-check.sh](/Users/xyd/Desktop/DataLake/scripts/mysql-regression-check.sh)，用于把以下高价值场景串成一条可重复脚本：

1. 复用 [api-smoke-test.sh](/Users/xyd/Desktop/DataLake/scripts/api-smoke-test.sh) 生成真实任务和日志数据
2. 创建 MySQL 数据源并测试连通性
3. 列出数据库 Schema、预览表数据并导入为数据集
4. 校验首页概览、用户与权限、日志页相关接口
5. 校验数据集 `xlsx` 导出与查询结果 `xlsx` 导出
6. 触发失败日志回放并确认日志条数增长
7. 切换 `OPERATOR` 的 `log.export` 权限并确认现有会话实时感知，再恢复原权限

关键结果样本：

- `sourceId=3`
- `importedSchema=data_lake_platform`
- `importedDatasetId=11`
- `importedTable=operator_def`
- `userCount=2`
- `logCountBeforeReplay=8`
- `logCountAfterReplay=9`
- 首页概览返回 `dataSources=3`、`datasets=11`、`totalTasks=4`、`successTasks=8`
- `operator` 角色当前返回菜单 `dashboard / data-sources / imports / datasets / queries / governance / tasks / logs`
- `operator` 角色当前返回操作权限 `governance.manage / governance.execute / task.manage / task.trigger / dataset.export / query.export / log.export / log.replay`

结论：

- MySQL 环境下的“数据库表导入 -> xlsx 导出 -> 日志回放 -> 权限实时同步”脚本化回归已经跑通
- 当前 MySQL 回归脚本已经与页面中的 Schema 自动识别流程保持一致

### 6. 冻结前收口复验（`2026-04-04`）

本轮未继续扩展业务能力，重点验证的是冻结前交付资产是否与当前实现保持一致。

实际执行命令：

```bash
BASE_URL=http://127.0.0.1:18080/api APP_USERNAME=admin APP_PASSWORD=admin123 \
bash scripts/ecommerce-demo-smoke.sh all

BASE_URL=http://127.0.0.1:18080/api APP_USERNAME=admin APP_PASSWORD=admin123 \
bash scripts/regression-suite.sh all

BASE_URL=http://127.0.0.1:18080/api APP_USERNAME=admin APP_PASSWORD=admin123 \
bash scripts/regression-suite.sh gate
```

已确认通过：

1. [browser-regression-checklist.md](/Users/xyd/Desktop/DataLake/docs/browser-regression-checklist.md) 已人工执行通过
2. [demo-runbook.md](/Users/xyd/Desktop/DataLake/docs/demo-runbook.md)、[demo-script.md](/Users/xyd/Desktop/DataLake/docs/demo-script.md)、[ppt-outline.md](/Users/xyd/Desktop/DataLake/docs/ppt-outline.md) 已统一为当前电商演示路径
3. [scripts/README.md](/Users/xyd/Desktop/DataLake/scripts/README.md) 与 [regression-coverage-matrix.md](/Users/xyd/Desktop/DataLake/docs/regression-coverage-matrix.md) 已明确脚本与人工资产边界
4. [api-contract.md](/Users/xyd/Desktop/DataLake/docs/api-contract.md)、[api-inventory.md](/Users/xyd/Desktop/DataLake/docs/api-inventory.md)、[db-schema-catalog.md](/Users/xyd/Desktop/DataLake/docs/db-schema-catalog.md)、[module-map.md](/Users/xyd/Desktop/DataLake/docs/module-map.md) 已同步当前冻结状态
5. 数据源重复策略、数据库 Schema 自动识别下拉、浏览器检查通过状态已同步进入测试计划、用户手册与 README
6. `demo-smoke` 已真实生成：首页指标、集成保存数据集、治理成功日志、失败任务日志与结构化日志详情
7. `regression-suite all / gate` 已真实通过，且新增的数据源重复策略检查已纳入 `extended` 验收

结论：

- 截至 `2026-04-04`，当前仓库已经进入“自动化回归通过 + 浏览器人工回归通过 + 文档与脚本资产一致”的冻结状态
- 当前剩余事项主要是课程文档排版与截图整理，不属于功能缺陷或验收阻断项

## 已覆盖的计划项

- 登录鉴权
- 数据源创建、编辑、启停、重复策略
- 文件导入与数据库表导入
- 数据集生成与多维筛选
- 条件查询、SQL 查询、SQL 分页排序
- 数据集成保存
- 治理流程保存与执行
- 任务创建与手动触发
- 日志列表、日志详情、日志回放、日志筛选
- Excel 导出代码与人工打开验证
- 用户管理与实时权限同步

## 当前剩余工作（非阻断）

- 课程报告中的截图归档与排版
- 答辩 PPT 内容精简与可视化呈现
- 如需在新机器复现 MySQL 联调，补充本机真实可用凭据
