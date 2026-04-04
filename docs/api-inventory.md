# 接口清单（冻结版）

更新时间：`2026-04-04`
来源：后端 `Controller` 扫描结果（`backend/src/main/java/com/datalake/platform/**`）。

## 1. 认证与权限

- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/auth/profile`
- `GET /api/roles`
- `PUT /api/roles/{roleId}`
- `GET /api/users`
- `POST /api/users`
- `PUT /api/users/{userId}`
- `DELETE /api/users/{userId}`

## 2. 仪表盘

- `GET /api/dashboard/overview`
- `GET /api/dashboard/task-trend`
- `GET /api/dashboard/recent-tasks`
- `GET /api/dashboard/ecommerce`

## 3. 数据源与接入

### 数据源

- `GET /api/data-sources`
- `POST /api/data-sources`
- `PUT /api/data-sources/{sourceId}`
- `DELETE /api/data-sources/{sourceId}`
- `POST /api/data-sources/{sourceId}/test`
- `POST /api/data-sources/{sourceId}/status`

补充说明：

- `POST / PUT /api/data-sources` 可选字段：`duplicateConnectionStrategy`
- 数据源保存响应可能补充：`warningMessage`

### 数据导入

- `POST /api/imports/file`
- `POST /api/imports/database`
- `GET /api/imports/database/schemas`
- `GET /api/imports/database/tables`
- `GET /api/imports/database/preview`
- `GET /api/imports/history`  
  支持可选筛选：`businessDomain/status/startTime/endTime`
- `GET /api/imports/{importId}`

## 4. 数据集与元数据

- `GET /api/datasets`  
  支持可选筛选：`keyword/sourceId/status/businessDomain`
- `GET /api/datasets/{datasetId}`
- `DELETE /api/datasets/{datasetId}`
- `GET /api/metadata/{datasetId}`
- `GET /api/preview/{datasetId}`  
  支持：`pageNum/pageSize/field/keyword`
- `GET /api/datasets/{datasetId}/export`

## 5. 查询分析

### 条件查询 / SQL

- `POST /api/queries/filter`  
  支持：`conditions + logic + sortField/sortOrder + pageNum/pageSize`
- `POST /api/queries/filter/export`
- `POST /api/queries/sql`
- `POST /api/queries/sql/page`
- `POST /api/queries/sql/export`

### 电商指标与图表

- `POST /api/queries/ecommerce-metric`  
  支持联动条件：`conditions/logic`
- `GET /api/analysis/{datasetId}/summary`
- `GET /api/analysis/{datasetId}/charts`
- `POST /api/analysis/charts`  
  支持按当前筛选条件聚合图表

### 数据集成（MVP）

- `POST /api/queries/integration`
- `POST /api/queries/integration/save`

## 6. 治理

- `GET /api/governance/operators`  
  支持：`includeDisabled`
- `POST /api/governance/operators/{operatorKey}/status`
- `GET /api/governance/flows`
- `POST /api/governance/flows`
- `POST /api/governance/execute`

## 7. 任务与日志

### 任务

- `GET /api/tasks`
- `POST /api/tasks`
- `PUT /api/tasks/{taskId}`
- `DELETE /api/tasks/{taskId}`
- `POST /api/tasks/{taskId}/trigger`
- `POST /api/tasks/{taskId}/pause`
- `POST /api/tasks/{taskId}/resume`

### 日志

- `GET /api/task-logs`  
  支持可选筛选：`taskType/status/operatorUser/startTime/endTime/keyword`
- `GET /api/task-logs/{logId}`
- `POST /api/task-logs/{logId}/replay`

## 8. 当前统一约定

- 统一响应结构：`code/message/data/timestamp/requestId`
- 统一分页结构：`pageNum/pageSize/total/records`
- 当前任务类型：`IMPORT / GOVERNANCE`
- 当前业务域枚举：`USER/PRODUCT/TRADE/PAYMENT/INVENTORY/REVIEW/BEHAVIOR_LOG`
