# MySQL 联调检查单

## 启动前

- 确认 MySQL 服务已启动
- 确认数据库 `data_lake_platform` 已创建
- 确认字符集为 `utf8mb4`
- 确认已执行 [schema.sql](/Users/xyd/Desktop/DataLake/database/schema.sql)

## 环境变量

- `MYSQL_URL`
- `MYSQL_USERNAME`
- `MYSQL_PASSWORD`
- `APP_STORAGE_ROOT`
- `APP_TASK_POLL_DELAY_MS`

## 启动命令

```bash
cd backend
env JAVA_HOME='/Users/xyd/Library/Java/JavaVirtualMachines/corretto-18.0.2/Contents/Home' \
PATH='/Users/xyd/Library/Java/JavaVirtualMachines/corretto-18.0.2/Contents/Home/bin':$PATH \
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

## 必查项

### 1. 连接与初始化

- 应用可成功启动
- 默认管理员账号可登录
- Swagger 可正常打开

### 2. 数据接入

- 文件导入成功
- 数据库表可列出、可预览、可导入
- 导入历史写入成功

### 3. 数据集与查询

- 数据集物理表已生成
- 元数据字段类型正常
- 条件查询正常
- SQL 查询正常
- `Excel` 导出文件可打开

### 4. 治理与调度

- 保存治理流程成功
- 手动执行治理成功
- 创建并触发任务成功
- 日志中可看到成功/失败摘要

## 当前已确认的问题

- 如果文件导入任务需要重跑，`APP_STORAGE_ROOT` 中原文件必须仍然存在

## 最近一次验证结果

- 验证日期：`2026-03-27`
- 验证环境：本机 MySQL，`mysql` profile，临时端口 `8093`
- 实际使用凭据：已通过环境变量覆盖默认值完成启动验证

### 已验证通过

- MySQL 库 `data_lake_platform` 可创建并成功导入 [schema.sql](/Users/xyd/Desktop/DataLake/database/schema.sql)
- 后端可在 `mysql` profile 下成功启动并连接本机 MySQL
- [api-smoke-test.sh](/Users/xyd/Desktop/DataLake/scripts/api-smoke-test.sh) 已在 MySQL 环境跑通
- 首页概览接口可返回真实统计
- 用户与权限页相关接口 `roles / users / auth/profile` 可正常返回
- 日志页相关接口 `task-logs / task-logs/{id}` 可正常返回
- 管理员修改 `OPERATOR` 角色操作权限后，现有 `operator` 会话可实时感知权限变化

### 本次关键结果

- `dataSources=1`
- `totalTasks=2`
- `successTasks=2`
- `userCount=2`
- `logCount=2`
- `operator` 角色在 MySQL 环境下已返回 `log.export`
- 去掉 `log.export` 后，同一个 `operator` token 的 `/auth/profile` 返回会立刻移除该权限
