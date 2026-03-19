import { apiDelete, apiDownload, apiGet, apiPost, apiPut, apiUpload } from './client';

export type LoginResult = {
  token: string;
  username: string;
  role: 'ADMIN' | 'OPERATOR';
  displayName: string;
  menus: string[];
};

export type DataSource = {
  sourceId: number;
  sourceName: string;
  sourceType: string;
  host?: string | null;
  port?: number | null;
  dbName?: string | null;
  username?: string | null;
  status: string;
  description?: string | null;
};

export type ImportHistory = {
  importId: number;
  sourceId?: number | null;
  datasetName: string;
  formatType: string;
  status: string;
  recordCount: number;
  errorMessage?: string | null;
  createTime: string;
};

export type DatasetSummary = {
  datasetId: number;
  datasetName: string;
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
  operatorUser?: number | null;
  createTime?: string | null;
};

export function login(payload: { username: string; password: string }) {
  return apiPost<LoginResult>('/auth/login', payload);
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
  }>('/dashboard/overview');
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

export function listImportHistory() {
  return apiGet<ImportHistory[]>('/imports/history');
}

export function listDatasets() {
  return apiGet<DatasetSummary[]>('/datasets');
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

export function exportDataset(datasetId: number, format: 'csv' | 'json', field?: string, keyword?: string) {
  return apiDownload(`/datasets/${datasetId}/export`, { format, field, keyword });
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
