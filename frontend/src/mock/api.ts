export type UserProfile = {
  id: number;
  username: string;
  role: 'ADMIN' | 'OPERATOR';
  displayName: string;
};

export type DashboardOverview = {
  dataSources: number;
  datasets: number;
  newDatasetsToday: number;
  totalTasks: number;
  runningTasks: number;
  successTasks: number;
  failedTasks: number;
  recentFailedTasks: number;
};

export const demoUsers: Record<string, { password: string; profile: UserProfile }> = {
  admin: {
    password: 'admin123',
    profile: { id: 1, username: 'admin', role: 'ADMIN', displayName: '平台管理员' }
  },
  operator: {
    password: 'operator123',
    profile: { id: 2, username: 'operator', role: 'OPERATOR', displayName: '数据操作员' }
  }
};

export const menuPermissions = {
  ADMIN: ['dashboard', 'data-sources', 'imports', 'datasets', 'queries', 'governance', 'tasks', 'logs', 'users'],
  OPERATOR: ['dashboard', 'imports', 'datasets', 'queries', 'governance', 'tasks', 'logs']
} as const;

export const overview: DashboardOverview = {
  dataSources: 4,
  datasets: 18,
  newDatasetsToday: 3,
  totalTasks: 26,
  runningTasks: 2,
  successTasks: 21,
  failedTasks: 3,
  recentFailedTasks: 2
};

// [已改造完成] 查询分析 Phase A：多条件/逻辑组合/排序分页口径（用于 mock 口径说明）
export const queryFilterOperators = ['LIKE', 'EQ', 'GT', 'GTE', 'LT', 'LTE', 'BETWEEN', 'TIME_RANGE'] as const;

export const queryPayloadExamples = {
  filter: {
    datasetId: 2001,
    logic: 'AND',
    conditions: [
      { field: 'order_status', operator: 'EQ', value: 'PAID' },
      { field: 'order_time', operator: 'TIME_RANGE', value: '2026-03-01 00:00:00', valueTo: '2026-03-31 23:59:59' }
    ],
    sortField: 'order_time',
    sortOrder: 'DESC',
    pageNum: 1,
    pageSize: 20,
    exportScope: 'ALL'
  },
  chartByFilter: {
    datasetId: 2001,
    dimensionField: 'order_status',
    logic: 'AND',
    conditions: [
      { field: 'order_status', operator: 'EQ', value: 'PAID' },
      { field: 'order_time', operator: 'TIME_RANGE', value: '2026-03-01 00:00:00', valueTo: '2026-03-31 23:59:59' }
    ]
  },
  ecommerceMetric: {
    datasetId: 2001,
    metricType: 'SALES_TREND',
    timeField: 'order_time',
    valueField: 'amount',
    logic: 'AND',
    conditions: [
      { field: 'order_status', operator: 'EQ', value: 'PAID' },
      { field: 'order_time', operator: 'TIME_RANGE', value: '2026-03-01 00:00:00', valueTo: '2026-03-31 23:59:59' }
    ]
  },
  sqlPage: {
    datasetId: 2001,
    sql: 'SELECT order_id, amount, order_time FROM dataset',
    sortField: 'order_time',
    sortOrder: 'DESC',
    pageNum: 1,
    pageSize: 20
  }
} as const;

export const taskTrend = [
  { day: '03-21', total: 3 },
  { day: '03-22', total: 4 },
  { day: '03-23', total: 5 },
  { day: '03-24', total: 4 },
  { day: '03-25', total: 6 },
  { day: '03-26', total: 5 },
  { day: '03-27', total: 7 }
];

export const dataSources = [
  { sourceId: 1001, sourceName: '电商文件上传目录', sourceType: 'FILE', status: 'ENABLED', description: '订单/商品/库存演示文件源' },
  { sourceId: 1002, sourceName: '电商业务 MySQL', sourceType: 'MYSQL', status: 'ENABLED', description: '订单库表导入来源' }
];

export const datasets = [
  { datasetId: 2001, datasetName: '订单明细数据', businessDomain: 'TRADE', formatType: 'CSV', recordCount: 1280, fieldCount: 12, status: 'READY', creator: 1, physicalTableName: 'dl_dataset_2001' },
  { datasetId: 2002, datasetName: '库存快照数据', businessDomain: 'INVENTORY', formatType: 'JSON', recordCount: 640, fieldCount: 8, status: 'READY', creator: 2, physicalTableName: 'dl_dataset_2002' }
];

export const metadata = [
  { fieldId: 1, datasetId: 2001, fieldName: 'order_id', physicalColumnName: 'order_id', fieldType: 'STRING', nullable: false, sampleValue: 'ORD-20260327001', fieldOrder: 1 },
  { fieldId: 2, datasetId: 2001, fieldName: 'product_id', physicalColumnName: 'product_id', fieldType: 'STRING', nullable: false, sampleValue: 'SKU-1001', fieldOrder: 2 },
  { fieldId: 3, datasetId: 2001, fieldName: 'amount', physicalColumnName: 'amount', fieldType: 'DOUBLE', nullable: true, sampleValue: '129.90', fieldOrder: 3 }
];

export const recentTasks = [
  { taskId: 3001, taskName: '每日订单导入任务', taskType: 'IMPORT', status: 'SUCCESS', nextRunTime: '2026-03-28 02:00' },
  { taskId: 3002, taskName: '订单自动治理任务', taskType: 'GOVERNANCE', status: 'RUNNING', nextRunTime: '2026-03-28 02:30' }
];

export const taskLogs = [
  {
    logId: 4001,
    taskId: 3001,
    taskName: '每日订单导入任务',
    taskType: 'IMPORT',
    targetId: 5001,
    status: 'SUCCESS',
    startTime: '2026-03-27 02:00',
    endTime: '2026-03-27 02:03',
    duration: 180,
    executionSummary: '导入 1280 条，0 条失败',
    errorMessage: ''
  },
  {
    logId: 4002,
    taskId: 3002,
    taskName: '订单自动治理任务',
    taskType: 'GOVERNANCE',
    targetId: 7001,
    status: 'FAILED',
    startTime: '2026-03-27 02:30',
    endTime: '2026-03-27 02:31',
    duration: 60,
    executionSummary: '治理执行失败',
    errorMessage: '字段 order_time 时间格式不符合要求',
    failureReason: {
      code: 'GOVERNANCE_OPERATOR_FAILED',
      step: 'step-2:TIME_NORMALIZE',
      reason: '字段 order_time 时间格式不符合要求',
      rawMessage: '治理流程第 2 步(TIME_NORMALIZE)执行失败: 字段 order_time 时间格式不符合要求'
    }
  }
];

export const governanceOperators = [
  { operatorKey: 'NULL_FILL', operatorType: 'CLEAN', operatorName: '空值填充' },
  { operatorKey: 'DEDUPLICATE', operatorType: 'DEDUP', operatorName: '重复数据清理' },
  { operatorKey: 'FIELD_CONVERT', operatorType: 'TRANSFORM', operatorName: '字段转换' },
  { operatorKey: 'FILTER_KEEP', operatorType: 'FILTER', operatorName: '条件过滤' },
  { operatorKey: 'ORDER_DEDUP', operatorType: 'DEDUP', operatorName: '订单去重' },
  { operatorKey: 'AMOUNT_NORMALIZE', operatorType: 'TRANSFORM', operatorName: '金额标准化' },
  { operatorKey: 'TIME_NORMALIZE', operatorType: 'TRANSFORM', operatorName: '时间标准化' },
  { operatorKey: 'CATEGORY_NORMALIZE', operatorType: 'TRANSFORM', operatorName: '商品分类标准化' },
  { operatorKey: 'STATUS_NORMALIZE', operatorType: 'TRANSFORM', operatorName: '状态标准化' }
];

export async function mockLogin(username: string, password: string): Promise<UserProfile> {
  const user = demoUsers[username];
  await new Promise((resolve) => setTimeout(resolve, 250));
  if (!user || user.password !== password) {
    throw new Error('用户名或密码错误');
  }
  return user.profile;
}
