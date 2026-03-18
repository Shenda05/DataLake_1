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
  failedTasks: 3
};

export const taskTrend = [
  { day: '03-12', total: 3 },
  { day: '03-13', total: 4 },
  { day: '03-14', total: 2 },
  { day: '03-15', total: 6 },
  { day: '03-16', total: 5 },
  { day: '03-17', total: 4 },
  { day: '03-18', total: 7 }
];

export const dataSources = [
  { sourceId: 1001, sourceName: '本地上传目录', sourceType: 'FILE', status: 'ENABLED', description: '课程演示文件源' },
  { sourceId: 1002, sourceName: '业务 MySQL', sourceType: 'MYSQL', status: 'ENABLED', description: '后续 P1 数据库接入' }
];

export const datasets = [
  { datasetId: 2001, datasetName: '专利基础数据', formatType: 'CSV', recordCount: 1280, fieldCount: 12, status: 'READY', creator: 'admin' },
  { datasetId: 2002, datasetName: '企业画像数据', formatType: 'JSON', recordCount: 640, fieldCount: 8, status: 'READY', creator: 'operator' }
];

export const metadata = [
  { fieldName: 'patent_code', fieldType: 'STRING', nullable: false, fieldDesc: '专利编号', sampleValue: 'CN20250001' },
  { fieldName: 'company_name', fieldType: 'STRING', nullable: false, fieldDesc: '企业名称', sampleValue: '示例科技' },
  { fieldName: 'industry', fieldType: 'STRING', nullable: true, fieldDesc: '行业分类', sampleValue: '智能制造' }
];

export const recentTasks = [
  { taskId: 3001, taskName: '每日专利数据导入', taskType: 'IMPORT', status: 'SUCCESS', nextRunTime: '2026-03-19 09:00' },
  { taskId: 3002, taskName: '治理流程-企业画像清洗', taskType: 'GOVERNANCE', status: 'RUNNING', nextRunTime: '2026-03-19 10:00' }
];

export const taskLogs = [
  { logId: 4001, taskName: '每日专利数据导入', status: 'SUCCESS', startTime: '2026-03-18 09:00', endTime: '2026-03-18 09:03', duration: 180, message: '导入 1280 条，0 条失败' },
  { logId: 4002, taskName: '治理流程-企业画像清洗', status: 'FAILED', startTime: '2026-03-18 14:00', endTime: '2026-03-18 14:01', duration: 60, message: '字段 industry 为空值比例过高' }
];

export const governanceOperators = [
  { operatorKey: 'NULL_FILL', operatorType: 'CLEAN', operatorName: '空值填充' },
  { operatorKey: 'DEDUPLICATE', operatorType: 'DEDUP', operatorName: '重复数据清理' },
  { operatorKey: 'FIELD_CONVERT', operatorType: 'TRANSFORM', operatorName: '字段转换' },
  { operatorKey: 'FILTER_KEEP', operatorType: 'FILTER', operatorName: '条件过滤' }
];

export async function mockLogin(username: string, password: string): Promise<UserProfile> {
  const user = demoUsers[username];
  await new Promise((resolve) => setTimeout(resolve, 250));
  if (!user || user.password !== password) {
    throw new Error('用户名或密码错误');
  }
  return user.profile;
}

