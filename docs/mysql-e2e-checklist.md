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

- 本机默认 `root / root` 凭据可能不适用，需要用实际 MySQL 账号覆盖
- 如果文件导入任务需要重跑，`APP_STORAGE_ROOT` 中原文件必须仍然存在
