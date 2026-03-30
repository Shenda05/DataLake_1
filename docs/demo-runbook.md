# 演示与验收 Runbook（Round 7）

更新时间：`2026-03-30`

## 1. 目标

在最短时间内复现可答辩状态：

- 首页有可展示指标
- 至少 1 个集成保存数据集
- 至少 1 次治理成功 + 1 次治理失败
- 日志详情含输入参数、执行步骤、结构化失败原因

## 2. 推荐执行顺序

### 步骤 1：启动服务

- 后端（H2 或 MySQL）
- 前端

### 步骤 2：一键准备数据与主链路

```bash
bash scripts/ecommerce-demo-smoke.sh all
```

可选：

```bash
BASE_URL=http://127.0.0.1:8080/api \
USERNAME=admin \
PASSWORD=admin123 \
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

## 4. 页面演示建议

1. 首页：展示订单趋势、销售额趋势、Top5、低库存。
2. 查询分析：展示条件查询、SQL 分页、数据集成并保存。
3. 治理：先成功执行，再展示失败案例。
4. 任务与日志：展示失败筛选、操作人、结构化失败原因、日志回放。

## 5. 失败回滚策略

- 演示数据异常：删除 `STATE_FILE` 后重跑 `ecommerce-demo-smoke.sh all`。
- 历史数据干扰：重启后端（H2）可清空；MySQL 用新时间戳数据集名重跑。
- 局部失败：按子命令单独重跑，不需要全量重置。

## 6. 交付建议

- 把命令输出、关键 ID、截图统一归档到测试报告与答辩附录。
- 建议记录：`datasetId / flowId / taskId / logId`，便于现场快速定位页面。
