# Scripts 使用说明

## 1. 第三轮演示加固脚本

脚本：`scripts/ecommerce-demo-smoke.sh`

用途：为课程验收/截图/答辩生成并验证一条可复现演示路径，覆盖：
- 登录
- 数据集成保存为新数据集
- 治理执行成功
- 治理执行失败
- 失败任务触发并写入失败日志
- 日志详情结构化字段查看

## 2. 一键执行

```bash
bash scripts/ecommerce-demo-smoke.sh all
```

可选环境变量：
- `BASE_URL`（默认 `http://127.0.0.1:8080/api`）
- `USERNAME`（默认 `admin`）
- `PASSWORD`（默认 `admin123`）
- `STATE_FILE`（默认 `/tmp/ecommerce-demo-smoke-state.json`）

示例：

```bash
BASE_URL=http://127.0.0.1:18080/api \
USERNAME=admin \
PASSWORD=admin123 \
bash scripts/ecommerce-demo-smoke.sh all
```

## 3. 分步执行

```bash
bash scripts/ecommerce-demo-smoke.sh login
bash scripts/ecommerce-demo-smoke.sh dashboard-check
bash scripts/ecommerce-demo-smoke.sh integration-save
bash scripts/ecommerce-demo-smoke.sh governance-success
bash scripts/ecommerce-demo-smoke.sh governance-fail
bash scripts/ecommerce-demo-smoke.sh task-fail-log
bash scripts/ecommerce-demo-smoke.sh log-detail
```

说明：
- 分步执行会复用 `STATE_FILE` 中的关键 ID（如 `tradeDatasetId`、`failureTaskLogId`）。
- 如状态文件中的 ID 已失效，脚本会自动重新创建必要演示数据。

## 4. 现有脚本

- `scripts/api-smoke-test.sh`：通用主链路冒烟脚本
- `scripts/mysql-regression-check.sh`：MySQL 环境回归脚本
