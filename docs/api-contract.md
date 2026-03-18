# API 合同 V1

## 通用约定

- 基础路径：`/api`
- 返回结构：

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
- `GET /api/users`
- `GET /api/roles`

### 登录请求

```json
{
  "username": "admin",
  "password": "admin123"
}
```

## 首页仪表盘

- `GET /api/dashboard/overview`
- `GET /api/dashboard/task-trend`
- `GET /api/dashboard/recent-tasks`

## 数据源管理

- `GET /api/data-sources`
- `POST /api/data-sources`
- `PUT /api/data-sources/{sourceId}`
- `DELETE /api/data-sources/{sourceId}`
- `POST /api/data-sources/{sourceId}/test`

## 数据接入

- `POST /api/imports/file`
- `POST /api/imports/database`
- `GET /api/imports/history`
- `GET /api/imports/{importId}`

## 数据集与元数据

- `GET /api/datasets`
- `GET /api/datasets/{datasetId}`
- `DELETE /api/datasets/{datasetId}`
- `GET /api/metadata/{datasetId}`
- `GET /api/preview/{datasetId}`

## 查询分析

- `POST /api/queries/filter`
- `POST /api/queries/sql`
- `GET /api/analysis/{datasetId}/summary`
- `GET /api/analysis/{datasetId}/charts`

## 数据治理

- `GET /api/governance/operators`
- `GET /api/governance/flows`
- `POST /api/governance/flows`
- `POST /api/governance/execute`

### 治理执行请求

```json
{
  "datasetId": 1001,
  "operatorChain": [
    {
      "operatorKey": "NULL_FILL",
      "params": {
        "field": "industry",
        "value": "UNKNOWN"
      }
    },
    {
      "operatorKey": "DEDUPLICATE",
      "params": {
        "fields": ["company_name", "patent_code"]
      }
    }
  ]
}
```

## 任务与日志

- `GET /api/tasks`
- `POST /api/tasks`
- `PUT /api/tasks/{taskId}`
- `POST /api/tasks/{taskId}/trigger`
- `POST /api/tasks/{taskId}/pause`
- `POST /api/tasks/{taskId}/resume`
- `GET /api/task-logs`
- `GET /api/task-logs/{logId}`

## 异常码建议

- `0`：成功
- `40001`：参数校验失败
- `40101`：未登录或 token 无效
- `40301`：无权限访问
- `40401`：资源不存在
- `40901`：资源冲突
- `50001`：系统内部异常

