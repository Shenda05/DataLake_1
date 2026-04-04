# 演示与验收 Runbook（冻结版）

更新时间：`2026-04-04`

## 1. 定位

这是当前仓库唯一推荐的演示准备文档，用于在最短时间内复现可答辩状态。

目标结果：

- 首页有可展示指标
- 至少 1 个集成保存数据集
- 至少 1 次治理成功 + 1 次治理失败
- 日志详情含输入参数、执行步骤、结构化失败原因

## 2. 推荐执行顺序

### 步骤 1：启动服务

- 后端（H2 或 MySQL）
- 前端

### 步骤 2：一键准备演示数据

```bash
bash scripts/ecommerce-demo-smoke.sh all
```

可选环境变量：

```bash
BASE_URL=http://127.0.0.1:8080/api \
APP_USERNAME=admin \
APP_PASSWORD=admin123 \
STATE_FILE=/tmp/ecommerce-demo-smoke-state.json \
bash scripts/ecommerce-demo-smoke.sh all
```

### 步骤 3：执行统一回归套件

```bash
bash scripts/regression-suite.sh all
```

## 3. 分步演示命令（按页面链路）

```bash
bash scripts/ecommerce-demo-smoke.sh login
bash scripts/ecommerce-demo-smoke.sh dashboard-check
bash scripts/ecommerce-demo-smoke.sh integration-save
bash scripts/ecommerce-demo-smoke.sh governance-success
bash scripts/ecommerce-demo-smoke.sh governance-fail
bash scripts/ecommerce-demo-smoke.sh task-fail-log
bash scripts/ecommerce-demo-smoke.sh log-detail
```

## 4. `STATE_FILE` 用途

- 默认文件：`/tmp/ecommerce-demo-smoke-state.json`
- 主要保存：`tradeDatasetId / productDatasetId / inventoryDatasetId / integrationSavedDatasetId / governanceSuccessLogId / governanceFailureLogId / failureTaskLogId`
- 作用：支持分步命令复用前一步生成的关键 ID，避免每次都从头准备数据

## 5. 页面演示顺序

1. 首页：展示订单趋势、销售额趋势、Top5、低库存。
2. 数据源 / 数据接入：展示 `MYSQL` 数据源、Schema 自动识别、导入历史。
3. 数据集管理：展示业务域筛选、预览与导出。
4. 查询分析：展示条件查询、SQL 分页、数据集成并保存。
5. 数据治理：先展示成功执行，再展示失败案例与结构化日志。
6. 任务与日志：展示失败筛选、操作人、结构化失败原因、日志回放。

## 6. 失败回滚策略

- 演示数据异常：删除 `STATE_FILE` 后重跑 `ecommerce-demo-smoke.sh all`。
- 历史数据干扰：H2 可通过重启后端清空；MySQL 建议直接使用新的时间戳数据集名重跑。
- 局部失败：优先按子命令单独重跑，不需要全量重置。

## 7. 交付建议

- 把命令输出、关键 ID、截图统一归档到测试报告与答辩附录。
- 建议记录：`datasetId / flowId / taskId / logId`，便于现场快速定位页面。
- 若需要截图或课程附录引用，以本 Runbook 为准，不再额外维护第二套演示口径。
