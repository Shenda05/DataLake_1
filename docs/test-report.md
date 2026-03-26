# 测试报告

## 测试日期

- `2026-03-25`

## 本轮验证范围

- 前端生产构建
- 后端编译
- H2 默认配置端到端冒烟
- MySQL 本地连通性检查

## 实际执行结果

### 1. 构建验证

- `frontend`：`npm run build` 通过
- `backend`：在 `JDK 18` 下执行 `mvn -DskipTests compile` 通过

### 2. H2 端到端冒烟

使用 `8086` 端口临时启动后端，并执行 [api-smoke-test.sh](/Users/xyd/Desktop/DataLake/scripts/api-smoke-test.sh)。

已跑通链路：

1. 管理员登录
2. 创建文件数据源
3. 上传 CSV 文件并生成数据集
4. 条件查询
5. SQL 查询
6. 创建治理流程
7. 创建并触发治理任务
8. 创建并触发导入任务
9. 获取任务日志列表

关键结果：

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

- `mysql profile` 的真实联调还需要使用你本机实际可用的 `MYSQL_USERNAME / MYSQL_PASSWORD`
- 当前阻塞点是账号权限，不是代码编译或服务监听问题

## 已覆盖的计划项

- 登录鉴权
- 数据源创建
- 文件导入
- 数据集生成
- 条件查询与 SQL 查询
- 治理流程保存与执行
- 任务创建与手动触发
- 日志列表查看
- Excel 导出代码已实现并完成构建验证
- 用户管理代码已实现并完成构建验证
- 数据库表导入代码已实现并完成编译验证

## 尚未完成的实机验证

- 使用真实 MySQL 账号跑完整 `mysql profile` 端到端链路
- 浏览器层面的人工 UI 回归
- 失败日志回放的完整人工场景验证
- 数据库表导入和 Excel 导出的人工打开验证

## 建议的下一轮验证顺序

1. 使用真实 MySQL 账号启动 `mysql` profile
2. 跑一遍 [api-smoke-test.sh](/Users/xyd/Desktop/DataLake/scripts/api-smoke-test.sh)
3. 手工验证数据库表导入
4. 手工下载并打开 `xlsx`
5. 在前端验证用户管理、筛选视图和日志回放
