# 回归覆盖矩阵（Round 7）

## 统一入口

```bash
bash scripts/regression-suite.sh all
```

## 覆盖映射

| 模块 | 覆盖方式 | 核心检查点 |
| --- | --- | --- |
| 登录鉴权 | `api-smoke` / `demo-smoke` / `extended` | `auth/login` 成功、token 可用 |
| 数据接入 | `api-smoke` / `demo-smoke` | 文件导入成功，导入历史可回查 |
| 数据集管理 | `extended` | 列表、详情、元数据、预览、导出 |
| 查询分析 | `api-smoke` | 条件查询、多条件、时间范围、SQL、SQL 分页 |
| 数据集成 | `demo-smoke` | `integration` + `integration/save` + 新数据集预览 |
| 数据治理 | `api-smoke` / `demo-smoke` / `extended` | 流程保存、治理成功、治理失败、算子启停拦截 |
| 任务调度 | `api-smoke` / `demo-smoke` | 任务创建、触发、失败任务场景 |
| 日志监控 | `api-smoke` / `demo-smoke` / `extended` | 列表、详情结构化字段、服务端筛选、失败回放 |

## 门禁建议

- 提交前至少执行：`smoke + demo + extended`
- 冻结前执行：`all`，MySQL 环境可追加 `RUN_MYSQL=true`
- 发布候选执行：`gate`（构建 + 回归）
