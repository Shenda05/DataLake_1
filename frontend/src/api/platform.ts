import { apiDelete, apiDownload, apiDownloadPost, apiGet, apiPost, apiPut, apiUpload } from './client';

export type BusinessDomain =
  | 'USER'
  | 'PRODUCT'
  | 'TRADE'
  | 'PAYMENT'
  | 'INVENTORY'
  | 'REVIEW'
  | 'BEHAVIOR_LOG';

export type LoginResult = {
  token: string;
  username: string;
  role: 'ADMIN' | 'OPERATOR';
  displayName: string;
  menus: string[];
  actions: string[];
};

export type DataSource = {
  sourceId: number;
  sourceName: string;
  sourceType: string;
  host?: string | null;
  port?: number | null;
  dbName?: string | null;
  username?: string | null;
  status: 'ENABLED' | 'DISABLED' | string;
  description?: string | null;
};

export type ImportHistory = {
  importId: number;
  sourceId?: number | null;
  datasetName: string;
  businessDomain: BusinessDomain;
  formatType: string;
  status: string;
  recordCount: number;
  errorMessage?: string | null;
  createUser?: number | null;
  operatorName?: string | null;
  createTime: string;
};

export type ImportDetail = {
  importId: number;
  sourceId?: number | null;
  datasetName: string;
  businessDomain: BusinessDomain;
  formatType: string;
  originalFileName?: string | null;
  filePath?: string | null;
  status: string;
  recordCount: number;
  errorMessage?: string | null;
  createUser?: number | null;
  operatorName?: string | null;
  sourceName?: string | null;
  sourceType?: string | null;
  createTime: string;
  importParams?: Record<string, unknown>;
};

export type DatabaseTableOption = {
  schemaName: string;
  tableName: string;
  displayName: string;
};

export type DatabasePreviewColumn = {
  fieldName: string;
  fieldType: string;
  nullable: boolean;
  sampleValue?: string | null;
};

export type DatabasePreview = {
  schemaName: string;
  tableName: string;
  columns: DatabasePreviewColumn[];
  records: Record<string, unknown>[];
};

export type DatasetSummary = {
  datasetId: number;
  datasetName: string;
  businessDomain: BusinessDomain;
  sourceId?: number | null;
  formatType: string;
  recordCount: number;
  fieldCount: number;
  status: string;
  creator?: number | null;
  physicalTableName: string;
};

export type DatasetDetail = DatasetSummary & {
  storagePath: string;
  description?: string | null;
  createTime: string;
  updateTime: string;
};

export type MetaField = {
  fieldId: number;
  datasetId: number;
  fieldName: string;
  physicalColumnName: string;
  fieldType: string;
  nullable: boolean;
  sampleValue: string;
  fieldOrder: number;
};

export type PageResponse<T> = {
  pageNum: number;
  pageSize: number;
  total: number;
  records: T[];
};

export type MetricPoint = {
  name: string;
  value: number;
};

export type EcommerceMetricResponse = {
  metricType: string;
  chart: MetricPoint[];
  table: Record<string, unknown>[];
  description: string;
};

export type IntegrationResponse = {
  mode: 'JOIN' | 'UNION' | string;
  columns: string[];
  total: number;
  records: Record<string, unknown>[];
};

export type IntegrationSaveResponse = {
  datasetId: number;
  datasetName: string;
  businessDomain: BusinessDomain;
  recordCount: number;
  fieldCount: number;
  mode: 'JOIN' | 'UNION' | string;
};

export type EcommerceOverviewResponse = {
  orderTrend: { day: string; value: number }[];
  salesTrend: { day: string; value: number }[];
  topProducts: { name: string; value: number }[];
  lowStockCount: number;
};

export type GovernanceStep = {
  operatorKey: string;
  params: Record<string, unknown>;
};

export type GovernanceOperator = {
  operatorId: number;
  operatorName: string;
  operatorKey: string;
  operatorType: string;
  description: string;
  status: string;
};

export type GovernanceFlow = {
  flowId: number;
  flowName: string;
  inputDatasetId: number;
  inputDatasetName: string;
  outputDatasetId?: number | null;
  outputDatasetName?: string | null;
  operatorChain: GovernanceStep[];
  creator?: number | null;
  creatorName?: string | null;
  createTime?: string | null;
  updateTime?: string | null;
};

export type GovernanceExecutionResult = {
  inputDatasetId: number;
  outputDatasetId: number;
  outputDatasetName: string;
  operatorCount: number;
  inputRecordCount: number;
  outputRecordCount: number;
  abnormalHandledCount: number;
  failedStep?: string;
  failedReason?: string;
  logRef: number;
  summary: string;
};

export type TaskSummary = {
  taskId: number;
  taskName: string;
  taskType: 'IMPORT' | 'GOVERNANCE';
  targetId: number;
  targetName?: string | null;
  cronExpr: string;
  status: string;
  retryPolicy: number;
  description?: string | null;
  nextRunTime?: string | null;
  lastRunTime?: string | null;
  createUser?: number | null;
};

export type TaskActionResponse = {
  taskId: number;
  status: string;
  logId?: number | null;
  message: string;
  nextRunTime?: string | null;
};

export type TaskLogSummary = {
  logId: number;
  taskId?: number | null;
  taskName: string;
  taskType: string;
  targetId?: number | null;
  status: string;
  startTime?: string | null;
  endTime?: string | null;
  duration: number;
  executionSummary?: string | null;
  errorMessage?: string | null;
};

export type TaskLogDetail = TaskLogSummary & {
  inputParams?: Record<string, unknown>;
  executionSteps?: TaskLogExecutionStep[];
  failureReason?: TaskLogFailureReason | null;
  operatorUser?: number | null;
  createTime?: string | null;
};

export type TaskLogExecutionStep = {
  stepIndex: number;
  stepName: string;
  status: string;
  detail?: string | null;
};

export type TaskLogFailureReason = {
  code?: string;
  step?: string;
  reason?: string;
  rawMessage?: string;
};

export type RoleSummary = {
  roleId: number;
  roleName: string;
  roleDesc?: string | null;
  menuPermissions: string[];
  actionPermissions: string[];
};

export type UserSummary = {
  userId: number;
  username: string;
  roleId: number;
  role: string;
  roleDesc?: string | null;
  status: string;
  createTime?: string | null;
  updateTime?: string | null;
};

export function login(payload: { username: string; password: string }) {
  return apiPost<LoginResult>('/auth/login', payload);
}

export function getAuthProfile() {
  return apiGet<Omit<LoginResult, 'token'>>('/auth/profile');
}

export function getOverview() {
  return apiGet<{
    dataSources: number;
    datasets: number;
    newDatasetsToday: number;
    totalTasks: number;
    runningTasks: number;
    successTasks: number;
    failedTasks: number;
    recentFailedTasks: number;
  }>('/dashboard/overview');
}

export function getEcommerceOverview() {
  return apiGet<EcommerceOverviewResponse>('/dashboard/ecommerce');
}

export function getTaskTrend() {
  return apiGet<{ day: string; total: number }[]>('/dashboard/task-trend');
}

export function getRecentTasks() {
  return apiGet<{ taskId: number; taskName: string; taskType: string; status: string; nextRunTime?: string | null }[]>('/dashboard/recent-tasks');
}

export function listDataSources() {
  return apiGet<DataSource[]>('/data-sources');
}

export function createDataSource(payload: Partial<DataSource> & { sourceName: string; sourceType: string }) {
  return apiPost<DataSource>('/data-sources', payload);
}

export function updateDataSource(sourceId: number, payload: Partial<DataSource> & { sourceName: string; sourceType: string }) {
  return apiPut<DataSource>(`/data-sources/${sourceId}`, payload);
}

export function updateDataSourceStatus(sourceId: number, status: 'ENABLED' | 'DISABLED') {
  return apiPost<DataSource>(`/data-sources/${sourceId}/status`, { status });
}

export function deleteDataSource(sourceId: number) {
  return apiDelete<void>(`/data-sources/${sourceId}`);
}

export function testDataSource(sourceId: number) {
  return apiPost<{ sourceId: number; connected: boolean; message: string }>(`/data-sources/${sourceId}/test`);
}

export function importFile(formData: FormData) {
  return apiUpload<{
    importId: number;
    datasetId: number;
    datasetName: string;
    formatType: string;
    recordCount: number;
    status: string;
    errorMessage: string;
  }>('/imports/file', formData);
}

export function importDatabase(payload: {
  sourceId: number;
  schemaName?: string;
  tableName: string;
  datasetName: string;
  businessDomain?: BusinessDomain;
  description?: string;
}) {
  return apiPost<{
    importId: number;
    datasetId: number;
    datasetName: string;
    formatType: string;
    recordCount: number;
    status: string;
    errorMessage: string;
  }>('/imports/database', payload);
}

export function listDatabaseTables(sourceId: number, schemaName?: string) {
  return apiGet<DatabaseTableOption[]>('/imports/database/tables', { sourceId, schemaName });
}

export function previewDatabaseTable(sourceId: number, tableName: string, schemaName?: string, limit = 10) {
  return apiGet<DatabasePreview>('/imports/database/preview', { sourceId, schemaName, tableName, limit });
}

export function listImportHistory(params?: {
  businessDomain?: BusinessDomain;
  status?: string;
  startTime?: string;
  endTime?: string;
}) {
  return apiGet<ImportHistory[]>('/imports/history', params);
}

export function getImportDetail(importId: number) {
  return apiGet<ImportDetail>(`/imports/${importId}`);
}

export function listDatasets(params?: {
  keyword?: string;
  sourceId?: number;
  status?: string;
  businessDomain?: BusinessDomain;
}) {
  return apiGet<DatasetSummary[]>('/datasets', params);
}

export function getDatasetDetail(datasetId: number) {
  return apiGet<DatasetDetail>(`/datasets/${datasetId}`);
}

export function listMetadata(datasetId: number) {
  return apiGet<MetaField[]>(`/metadata/${datasetId}`);
}

export function previewDataset(datasetId: number, pageNum = 1, pageSize = 10) {
  return apiGet<PageResponse<Record<string, unknown>>>(`/preview/${datasetId}`, { pageNum, pageSize });
}

export function previewDatasetWithFilter(datasetId: number, pageNum = 1, pageSize = 10, field?: string, keyword?: string) {
  return apiGet<PageResponse<Record<string, unknown>>>(`/preview/${datasetId}`, { pageNum, pageSize, field, keyword });
}

export function exportDataset(datasetId: number, format: 'csv' | 'json' | 'xlsx', field?: string, keyword?: string) {
  return apiDownload(`/datasets/${datasetId}/export`, { format, field, keyword });
}

export function deleteDataset(datasetId: number) {
  return apiDelete<void>(`/datasets/${datasetId}`);
}

export function filterQuery(payload: {
  datasetId: number;
  field: string;
  operator: string;
  value: string;
  pageNum?: number;
  pageSize?: number;
}) {
  return apiPost<PageResponse<Record<string, unknown>>>('/queries/filter', payload);
}

export function sqlQuery(payload: { datasetId: number; sql: string }) {
  return apiPost<Record<string, unknown>[]>('/queries/sql', payload);
}

export function exportFilterQuery(
  payload: {
    datasetId: number;
    field: string;
    operator: string;
    value: string;
    pageNum?: number;
    pageSize?: number;
  },
  format: 'csv' | 'json' | 'xlsx'
) {
  return apiDownloadPost('/queries/filter/export', payload, { format });
}

export function exportSqlQuery(payload: { datasetId: number; sql: string }, format: 'csv' | 'json' | 'xlsx') {
  return apiDownloadPost('/queries/sql/export', payload, { format });
}

export function getAnalysisSummary(datasetId: number) {
  return apiGet<{
    datasetId: number;
    recordCount: number;
    nullCount: number;
    duplicateCount: number;
  }>(`/analysis/${datasetId}/summary`);
}

export function getAnalysisCharts(datasetId: number) {
  return apiGet<{ name: string; value: number }[]>(`/analysis/${datasetId}/charts`);
}

export function queryEcommerceMetric(payload: {
  datasetId: number;
  metricType: 'ORDER_TREND' | 'SALES_TREND' | 'TOP_PRODUCTS' | 'CATEGORY_SHARE' | 'LOW_STOCK';
  timeField?: string;
  valueField?: string;
  categoryField?: string;
  productField?: string;
  quantityField?: string;
  stockThreshold?: number;
}) {
  return apiPost<EcommerceMetricResponse>('/queries/ecommerce-metric', payload);
}

export function queryIntegration(payload: {
  leftDatasetId: number;
  rightDatasetId: number;
  mode: 'JOIN' | 'UNION';
  leftField?: string;
  rightField?: string;
  limit?: number;
}) {
  return apiPost<IntegrationResponse>('/queries/integration', payload);
}

export function saveIntegrationResult(payload: {
  leftDatasetId: number;
  rightDatasetId: number;
  mode: 'JOIN' | 'UNION';
  leftField?: string;
  rightField?: string;
  limit?: number;
  outputDatasetName: string;
  outputBusinessDomain?: BusinessDomain;
}) {
  return apiPost<IntegrationSaveResponse>('/queries/integration/save', payload);
}

export function listGovernanceOperators() {
  return apiGet<GovernanceOperator[]>('/governance/operators');
}

export function listGovernanceFlows() {
  return apiGet<GovernanceFlow[]>('/governance/flows');
}

export function createGovernanceFlow(payload: {
  flowName: string;
  datasetId: number;
  operatorChain: GovernanceStep[];
}) {
  return apiPost<{ flowId: number; flowName: string; operatorCount: number }>('/governance/flows', payload);
}

export function executeGovernanceFlow(payload: {
  datasetId: number;
  operatorChain: GovernanceStep[];
  executionName?: string;
}) {
  return apiPost<GovernanceExecutionResult>('/governance/execute', payload);
}

export function listTasks() {
  return apiGet<TaskSummary[]>('/tasks');
}

export function createTask(payload: {
  taskName: string;
  taskType: 'IMPORT' | 'GOVERNANCE';
  targetId: number;
  cronExpr: string;
  retryPolicy?: number;
  description?: string;
  status?: string;
}) {
  return apiPost<TaskSummary>('/tasks', payload);
}

export function updateTask(
  taskId: number,
  payload: {
    taskName: string;
    taskType: 'IMPORT' | 'GOVERNANCE';
    targetId: number;
    cronExpr: string;
    retryPolicy?: number;
    description?: string;
    status?: string;
  }
) {
  return apiPut<TaskSummary>(`/tasks/${taskId}`, payload);
}

export function deleteTask(taskId: number) {
  return apiDelete<void>(`/tasks/${taskId}`);
}

export function triggerTask(taskId: number) {
  return apiPost<TaskActionResponse>(`/tasks/${taskId}/trigger`);
}

export function pauseTask(taskId: number) {
  return apiPost<TaskActionResponse>(`/tasks/${taskId}/pause`);
}

export function resumeTask(taskId: number) {
  return apiPost<TaskActionResponse>(`/tasks/${taskId}/resume`);
}

export function listTaskLogs() {
  return apiGet<TaskLogSummary[]>('/task-logs');
}

export function getTaskLogDetail(logId: number) {
  return apiGet<TaskLogDetail>(`/task-logs/${logId}`);
}

export function replayTaskLog(logId: number) {
  return apiPost<TaskActionResponse>(`/task-logs/${logId}/replay`);
}

export function listRoles() {
  return apiGet<RoleSummary[]>('/roles');
}

export function updateRole(roleId: number, payload: { roleDesc?: string; menus: string[]; actions?: string[] }) {
  return apiPut<RoleSummary>(`/roles/${roleId}`, payload);
}

export function listUsers() {
  return apiGet<UserSummary[]>('/users');
}

export function createUser(payload: { username: string; password: string; roleId: number; status?: string }) {
  return apiPost<UserSummary>('/users', payload);
}

export function updateUser(userId: number, payload: { username: string; password?: string; roleId: number; status?: string }) {
  return apiPut<UserSummary>(`/users/${userId}`, payload);
}

export function deleteUser(userId: number) {
  return apiDelete<void>(`/users/${userId}`);
}
