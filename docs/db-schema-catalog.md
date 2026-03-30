# 数据库表结构清单（Round 7）

更新时间：`2026-03-30`  
来源：`database/schema.sql` 与 `backend/src/main/resources/schema.sql`。  
结论：两份 schema 当前保持一致，可作为 H2/MySQL 双环境基线。

## 1. 权限域

### `sys_role`

- 主键：`role_id`
- 核心字段：`role_name`（唯一）、`role_desc`
- 权限字段：`menu_permissions`、`action_permissions`
- 时间字段：`create_time`

### `sys_user`

- 主键：`user_id`
- 外键：`role_id -> sys_role.role_id`
- 核心字段：`username`（唯一）、`password`、`status`
- 时间字段：`create_time`、`update_time`

## 2. 接入与资产域

### `data_source`

- 主键：`source_id`
- 核心字段：`source_name`（唯一）、`source_type`、`status`
- 连接字段：`host/port/db_name/username/password`
- 审计字段：`create_user/create_time/update_time`

### `import_record`

- 主键：`import_id`
- 外键：`source_id -> data_source.source_id`
- 核心字段：`dataset_name`、`business_domain`、`format_type`
- 来源字段：`original_file_name`、`file_path`
- 结果字段：`status`、`record_count`、`error_message`
- 审计字段：`create_user/create_time`

### `data_set`

- 主键：`dataset_id`
- 外键：`source_id -> data_source.source_id`
- 核心字段：`dataset_name`（唯一）、`business_domain`、`format_type`
- 统计字段：`record_count`、`field_count`
- 存储字段：`storage_path`、`physical_table_name`
- 状态字段：`status`
- 审计字段：`creator/create_time/update_time`

### `meta_field`

- 主键：`field_id`
- 外键：`dataset_id -> data_set.dataset_id`
- 核心字段：`field_name`、`physical_column_name`、`field_type`
- 其他字段：`nullable`、`sample_value`、`field_order`

## 3. 治理域

### `operator_def`

- 主键：`operator_id`
- 核心字段：`operator_name`、`operator_key`（唯一）、`operator_type`
- 配置字段：`config_schema`、`description`
- 状态字段：`status`（`ENABLED/DISABLED`）
- 时间字段：`create_time`

### `governance_flow`

- 主键：`flow_id`
- 外键：
  - `input_dataset_id -> data_set.dataset_id`
  - `output_dataset_id -> data_set.dataset_id`
- 核心字段：`flow_name`（唯一）、`operator_chain`
- 审计字段：`creator/create_time/update_time`

## 4. 调度与日志域

### `task_def`

- 主键：`task_id`
- 核心字段：`task_name`（唯一）、`task_type`、`target_id`
- 调度字段：`cron_expr`、`status`、`retry_policy`
- 扩展字段：`payload`、`description`
- 运行字段：`next_run_time`、`last_run_time`
- 审计字段：`create_user/create_time/update_time`

### `task_log`

- 主键：`log_id`
- 外键：`task_id -> task_def.task_id`
- 核心字段：`task_type`、`target_id`、`status`
- 执行字段：`start_time`、`end_time`、`duration`
- 摘要字段：`execution_summary`、`error_message`
- 结构化字段：`input_params`、`execution_steps`、`failure_reason`
- 审计字段：`operator_user`、`create_time`

## 5. 说明

- 本轮未新增 schema 字段；重点是能力收口与交付增强。
- 若后续进入工业化阶段，建议补充：
  - 大文本日志分表或 JSON 列类型
  - 关键索引优化（日志筛选、任务查询、导入历史）
  - 审计与归档策略
