# 回归覆盖矩阵（冻结版）

更新时间：`2026-04-04`

## 1. 统一入口

```bash
bash scripts/regression-suite.sh all
```

## 2. 资产边界

| 资产 | 边界说明 | 主要输出 |
| --- | --- | --- |
| `api-smoke-test.sh` | 面向 API 主链路基线，不负责演示截图与双会话浏览器验证 | 冒烟通过 / 关键 ID |
| `ecommerce-demo-smoke.sh` | 面向答辩演示数据准备，不替代统一回归 | 首页指标、集成结果、治理成功/失败、结构化日志 |
| `mysql-regression-check.sh` | 面向 MySQL 环境专项校验，不要求 H2 环境运行 | 数据库表导入、`xlsx` 导出、日志回放、权限同步 |
| `regression-suite.sh` | 统一编排与冻结门禁，负责串联核心自动化资产 | all / gate 结果 |
| `browser-regression-checklist.md` | 面向人工 UI / 下载 / 权限实时同步验证 | 人工通过记录与截图证据 |

## 3. 覆盖映射

| 模块 | 覆盖方式 | 核心检查点 |
| --- | --- | --- |
| 登录鉴权 | `api-smoke` / `demo-smoke` / `extended` | `auth/login` 成功、token 可用 |
| 数据源管理 | `extended` / `mysql-regression` / 浏览器检查单 | 创建、启停、重复策略、测试连接 |
| 数据接入 | `api-smoke` / `demo-smoke` / `mysql-regression` / 浏览器检查单 | 文件导入、数据库表导入、Schema 自动识别、导入历史 |
| 数据集管理 | `extended` | 列表、详情、元数据、预览、导出 |
| 查询分析 | `api-smoke` | 条件查询、多条件、时间范围、SQL、SQL 分页 |
| 数据集成 | `demo-smoke` | `integration` + `integration/save` + 新数据集预览 |
| 数据治理 | `api-smoke` / `demo-smoke` / `extended` | 流程保存、治理成功、治理失败、算子启停拦截 |
| 任务调度 | `api-smoke` / `demo-smoke` | 任务创建、触发、失败任务场景 |
| 日志监控 | `api-smoke` / `demo-smoke` / `extended` / 浏览器检查单 | 列表、详情结构化字段、服务端筛选、失败回放 |

## 4. 门禁建议

- 日常提交前：`smoke + demo`
- 冻结前：`all`
- 发布候选：`gate`
- 有 MySQL 环境时：额外执行 `mysql`
- 提交课程文档前：补齐浏览器检查单截图与人工结果
