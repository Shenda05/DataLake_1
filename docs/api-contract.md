# API 合同 V2（冻结版）

更新时间：`2026-04-04`

## 通用约定

- 基础路径：`/api`
- 统一响应：

```json
{
  "code": 0,
  "message": "OK",
  "data": {},
  "timestamp": "2026-03-18T23:00:00Z",
  "requestId": "req-demo-001"
}
```

- 分页结构：

```json
{
  "pageNum": 1,
  "pageSize": 10,
  "total": 2,
  "records": []
}
```

## 认证与权限

- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/auth/profile`
- `GET /api/roles`
- `PUT /api/roles/{roleId}`
- `GET /api/users`
- `POST /api/users`
- `PUT /api/users/{userId}`
- `DELETE /api/users/{userId}`

## 仪表盘

- `GET /api/dashboard/overview`
- `GET /api/dashboard/task-trend`
- `GET /api/dashboard/recent-tasks`
- `GET /api/dashboard/ecommerce`

## 数据源与接入

### 数据源

- `GET /api/data-sources`
- `POST /api/data-sources`
- `PUT /api/data-sources/{sourceId}`
- `DELETE /api/data-sources/{sourceId}`
- `POST /api/data-sources/{sourceId}/test`
- `POST /api/data-sources/{sourceId}/status`

`POST / PUT /api/data-sources` 可选字段：

- `duplicateConnectionStrategy`：`ALLOW | WARN | REJECT`

数据源保存响应可能补充：

- `warningMessage`：当策略为 `WARN` 且检测到相同 MYSQL 连接配置时返回

### 导入

- `POST /api/imports/file`
- `POST /api/imports/database`
- `GET /api/imports/database/schemas`（用于数据库导入页自动识别 `Schema / Database` 下拉）
- `GET /api/imports/database/tables`
- `GET /api/imports/database/preview`
- `GET /api/imports/history`  
  可选参数：`businessDomain/status/startTime/endTime`
- `GET /api/imports/{importId}`

## 数据集与元数据

- `GET /api/datasets`  
  可选参数：`keyword/sourceId/status/businessDomain`
- `GET /api/datasets/{datasetId}`
- `DELETE /api/datasets/{datasetId}`
- `GET /api/metadata/{datasetId}`
- `GET /api/preview/{datasetId}`  
  可选参数：`pageNum/pageSize/field/keyword`
- `GET /api/datasets/{datasetId}/export`

## 查询分析

### 条件查询 / SQL

- `POST /api/queries/filter`
- `POST /api/queries/filter/export`
- `POST /api/queries/sql`
- `POST /api/queries/sql/page`
- `POST /api/queries/sql/export`

`/queries/filter` 请求体支持：

- 旧口径：`field/operator/value`
- 新口径：`conditions[] + logic + sortField + sortOrder + pageNum + pageSize`

### 电商指标 / 图表

- `POST /api/queries/ecommerce-metric`
- `GET /api/analysis/{datasetId}/summary`
- `GET /api/analysis/{datasetId}/charts`
- `POST /api/analysis/charts`

`/queries/ecommerce-metric` 可选联动字段：

- `conditions[]`
- `logic`

### 数据集成（MVP）

- `POST /api/queries/integration`
- `POST /api/queries/integration/save`

## 数据治理

- `GET /api/governance/operators`  
  可选参数：`includeDisabled`
- `POST /api/governance/operators/{operatorKey}/status`
- `GET /api/governance/flows`
- `POST /api/governance/flows`
- `POST /api/governance/execute`

## 任务与日志

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
  可选参数：`taskType/status/operatorUser/startTime/endTime/keyword`
- `GET /api/task-logs/{logId}`
- `POST /api/task-logs/{logId}/replay`

## 当前关键枚举

- 任务类型：`IMPORT`、`GOVERNANCE`
- 算子状态：`ENABLED`、`DISABLED`
- 业务域：`USER/PRODUCT/TRADE/PAYMENT/INVENTORY/REVIEW/BEHAVIOR_LOG`

## 异常码建议

- `0`：成功
- `40001`：参数校验失败
- `40101`：未登录或 token 无效
- `40301`：无权限访问
- `40401`：资源不存在
- `40901`：资源冲突
- `50001`：系统内部异常
