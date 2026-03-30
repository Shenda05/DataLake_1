# 第七轮冻结前差距复审（Round 7）

更新时间：`2026-03-30`  
复审基线：当前仓库代码 + [README.md](/Users/xyd/Desktop/DataLake/README.md) + [api-contract.md](/Users/xyd/Desktop/DataLake/docs/api-contract.md) + [architecture.md](/Users/xyd/Desktop/DataLake/docs/architecture.md) + [user-manual.md](/Users/xyd/Desktop/DataLake/docs/user-manual.md) + 电商版设计文档/用户故事/迭代规划。

## 复审结论

- 主流程已闭环：登录 → 接入 → 数据集 → 查询分析 → 治理 → 调度 → 日志。
- 当前主要风险不在“缺功能”，而在“文档与实现口径漂移、回归资产入口分散、冻结交付材料不够集中”。
- 本轮目标是完成冻结级收口：文档对齐、脚本编排、演示路径固化、轻量 UI 文案一致性。

## 一、必须修（冻结阻断项）

| ID | 问题 | 风险等级 | 影响范围 | 证据 | 处置建议 | 截止标准 |
| --- | --- | --- | --- | --- | --- | --- |
| M1 | 接口文档未覆盖现网接口与参数（Phase A/B + 候选包2/3） | 高 | 答辩讲解、课程文档 | `docs/api-contract.md` 未覆盖 `queries/sql/page`、`queries/integration/save`、`analysis/charts`、`governance/operators/{key}/status`、`task-logs` 服务端筛选 | 全量更新 API 合同并新增接口清单 | 文档与 controller 清单逐条对应，无明显遗漏 |
| M2 | 架构文档任务类型口径错误 | 高 | 架构章节、答辩问答 | `docs/architecture.md` 仍写 `INTEGRATION` 任务类型；代码仅 `IMPORT/GOVERNANCE` | 修正文档并明确“数据集成在查询页子标签” | 文档口径与 `TaskService` 一致 |
| M3 | 测试计划与用户手册未同步最新能力 | 中高 | 功能完成度说明、验收单 | `docs/test-plan.md`、`docs/user-manual.md` 对多条件查询/SQL 分页排序/日志服务端筛选/治理算子启停描述滞后 | 更新测试计划与用户手册的能力说明和验收步骤 | 文档覆盖当前功能，便于复现 |
| M4 | 自动化回归入口分散，缺统一编排与覆盖矩阵 | 高 | 冻结验收效率、复测稳定性 | 现有 `api-smoke`/`demo-smoke`/`mysql-regression` 互相独立 | 新增统一入口 `scripts/regression-suite.sh` + 覆盖映射文档 | `bash scripts/regression-suite.sh all` 可一键跑通 |
| M5 | 运行说明带强本机路径依赖 | 中高 | 他机复现、课程检查 | `README.md` 含特定机器 `JAVA_HOME` 路径 | 改为通用 JDK17 命令与环境变量模板 | README 可在非作者机器直接照抄执行 |

### Round 7 闭环状态

- `M1` 已闭环：`docs/api-contract.md` 已补齐 Phase A/B + 候选包 2/3 接口。
- `M2` 已闭环：`docs/architecture.md` 已修正任务类型口径为 `IMPORT/GOVERNANCE`。
- `M3` 已闭环：`docs/test-plan.md`、`docs/user-manual.md` 已同步最新能力。
- `M4` 已闭环：新增 `scripts/regression-suite.sh` + `docs/regression-coverage-matrix.md`，并已跑通 `all/gate`。
- `M5` 已闭环：`README.md` 已去除本机硬编码路径，改为通用 JDK17 指引。

## 二、可忽略（本期可接受）

| ID | 问题 | 风险等级 | 说明 |
| --- | --- | --- | --- |
| I1 | 前端构建 chunk 体积偏大告警 | 低 | 不影响课程演示与验收，记录到后续优化 |
| I2 | 治理/查询内部仍有待优化注释（性能、映射引导） | 低 | 属于增强项，不阻断本期冻结 |
| I3 | 个别页面文案存在轻微风格差异 | 低 | 本轮统一关键页面即可，不做大面积重构 |

## 三、记录到文档说明（不改业务逻辑）

| ID | 内容 | 风险等级 | 记录方式 |
| --- | --- | --- | --- |
| D1 | 调度引擎当前为应用内轮询，不是 Quartz | 中 | README / 架构文档“当前约束” |
| D2 | SQL 仍限定单数据集单表；跨表通过数据集成子标签 | 中 | 用户手册 / API 合同说明 |
| D3 | 导入任务重跑依赖原文件/原库表仍可访问 | 中 | 用户手册 / MySQL 检查单说明 |
| D4 | 数据集成当前为基础 Join/Union（MVP） | 中 | 迭代范围说明与下阶段建议 |

## 四、冻结收口检查单

- [x] 文档口径与代码实现一致（接口、架构、测试、手册、README）。
- [x] 一键回归脚本可执行并输出分模块结果。
- [x] 演示路径可在空环境快速复现关键数据与日志。
- [ ] 后端编译、前端构建、回归脚本、浏览器检查单均通过（浏览器检查单待补签）。
- [x] 形成冻结说明，可直接用于课程文档附录。
