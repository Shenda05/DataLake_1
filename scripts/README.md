# Scripts 使用说明（冻结版）

更新时间：`2026-04-04`

## 1. 资产边界

| 资产 | 作用 | 适用环境 | 是否推荐冻结前执行 |
| --- | --- | --- | --- |
| `scripts/api-smoke-test.sh` | H2 友好的主链路 API 基线冒烟 | H2 / MySQL | 是 |
| `scripts/ecommerce-demo-smoke.sh` | 准备演示数据并生成答辩可见结果 | H2 / MySQL | 是 |
| `scripts/mysql-regression-check.sh` | MySQL 环境专项回归（数据库表导入、`xlsx` 导出、日志回放、权限同步） | MySQL | 有 MySQL 时执行 |
| `scripts/regression-suite.sh` | 统一编排、冻结门禁、扩展验收 | H2 / MySQL | 是 |
| `docs/browser-regression-checklist.md` | 人工 UI / 下载文件 / 双会话验证 | 浏览器 | 是 |

## 2. `api-smoke-test.sh`

用途：验证通用主链路 API 是否可用，覆盖：

- 登录
- 创建文件数据源
- 文件导入交易数据集
- 条件查询 / 多条件查询 / 时间范围查询
- SQL 查询 / SQL 分页排序
- 创建治理流程
- 创建并触发治理任务
- 创建并触发导入任务
- 获取日志列表

执行：

```bash
bash scripts/api-smoke-test.sh
```

可选环境变量：

- `BASE_URL`
- `APP_USERNAME`
- `APP_PASSWORD`

## 3. `ecommerce-demo-smoke.sh`

用途：为课程验收 / 截图 / 答辩生成可复现演示路径，覆盖：

- 首页指标准备
- 数据集成保存为新数据集
- 治理执行成功
- 治理执行失败
- 失败任务触发并写入失败日志
- 日志详情结构化字段验证

一键执行：

```bash
bash scripts/ecommerce-demo-smoke.sh all
```

分步执行：

```bash
bash scripts/ecommerce-demo-smoke.sh login
bash scripts/ecommerce-demo-smoke.sh dashboard-check
bash scripts/ecommerce-demo-smoke.sh integration-save
bash scripts/ecommerce-demo-smoke.sh governance-success
bash scripts/ecommerce-demo-smoke.sh governance-fail
bash scripts/ecommerce-demo-smoke.sh task-fail-log
bash scripts/ecommerce-demo-smoke.sh log-detail
```

可选环境变量：

- `BASE_URL`（默认 `http://127.0.0.1:8080/api`）
- `APP_USERNAME`（默认 `admin`，推荐使用，避免和 zsh 内置 `USERNAME` 冲突）
- `APP_PASSWORD`（默认 `admin123`）
- `STATE_FILE`（默认 `/tmp/ecommerce-demo-smoke-state.json`）

## 4. `mysql-regression-check.sh`

用途：在 MySQL 环境验证以下高价值场景：

- MySQL 数据源创建与测试连接
- 数据库 Schema 自动识别与表导入
- 数据集 / 查询结果 `xlsx` 导出
- 日志回放
- 权限实时同步

执行示例：

```bash
BASE_URL=http://127.0.0.1:8093/api \
MYSQL_SOURCE_USER='root' \
MYSQL_SOURCE_PASSWORD='你的 MySQL 密码' \
bash scripts/mysql-regression-check.sh
```

## 5. `regression-suite.sh`

用途：统一编排自动化回归与冻结门禁。

支持子命令：

```bash
bash scripts/regression-suite.sh all
bash scripts/regression-suite.sh smoke
bash scripts/regression-suite.sh demo
bash scripts/regression-suite.sh extended
bash scripts/regression-suite.sh mysql
bash scripts/regression-suite.sh gate
```

其中：

- `smoke`：执行 `api-smoke`
- `demo`：执行 `demo-smoke`
- `extended`：执行数据集管理、治理算子、日志筛选、数据源重复策略等扩展验收
- `mysql`：执行 MySQL 专项回归
- `gate`：执行构建 + 全量自动化回归

## 6. 推荐执行顺序

冻结前建议至少执行：

```bash
bash scripts/ecommerce-demo-smoke.sh all
bash scripts/regression-suite.sh all
bash scripts/regression-suite.sh gate
```

如有 MySQL 环境，再追加：

```bash
bash scripts/mysql-regression-check.sh
```

浏览器层验证请配合：

- [docs/browser-regression-checklist.md](/Users/xyd/Desktop/DataLake/docs/browser-regression-checklist.md)
