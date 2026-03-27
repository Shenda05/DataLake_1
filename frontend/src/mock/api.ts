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
  { datasetId: 2001, datasetName: '订单明细数据', businessDomain: 'TRADE', formatType: 'CSV', recordCount: 1280, fieldCount: 12, status: 'READY', creator: 'admin' },
  { datasetId: 2002, datasetName: '库存快照数据', businessDomain: 'INVENTORY', formatType: 'JSON', recordCount: 640, fieldCount: 8, status: 'READY', creator: 'operator' }
];

export const metadata = [
  { fieldName: 'order_id', fieldType: 'STRING', nullable: false, fieldDesc: '订单编号', sampleValue: 'ORD-20260327001' },
  { fieldName: 'product_id', fieldType: 'STRING', nullable: false, fieldDesc: '商品编号', sampleValue: 'SKU-1001' },
  { fieldName: 'amount', fieldType: 'DOUBLE', nullable: true, fieldDesc: '订单金额', sampleValue: '129.90' }
];

export const recentTasks = [
  { taskId: 3001, taskName: '每日订单导入任务', taskType: 'IMPORT', status: 'SUCCESS', nextRunTime: '2026-03-28 02:00' },
  { taskId: 3002, taskName: '订单自动治理任务', taskType: 'GOVERNANCE', status: 'RUNNING', nextRunTime: '2026-03-28 02:30' }
];

export const taskLogs = [
  { logId: 4001, taskName: '每日订单导入任务', status: 'SUCCESS', startTime: '2026-03-27 02:00', endTime: '2026-03-27 02:03', duration: 180, message: '导入 1280 条，0 条失败' },
  { logId: 4002, taskName: '订单自动治理任务', status: 'FAILED', startTime: '2026-03-27 02:30', endTime: '2026-03-27 02:31', duration: 60, message: '字段 order_time 时间格式不符合要求' }
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
