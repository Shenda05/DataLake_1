import { apiDelete, apiGet, apiPost, apiPut, apiUpload } from './client';

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

export type OperatorDefinition = {
  operatorId: number;
  operatorName: string;
  operatorKey: string;
  operatorType: string;
  configSchema: string;
  description: string;
  status: string;
};

export type GovernanceFlow = {
  flowId: number;
  flowName: string;
  inputDatasetId: number;
  outputDatasetId?: number | null;
  createTime: string;
};

export type TaskRecord = {
  taskId: number;
  taskName: string;
  taskType: string;
  targetId: number;
  cronExpr: string;
  status: string;
  retryPolicy: number;
  description?: string | null;
  nextRunTime?: string | null;
  lastRunTime?: string | null;
};

export type TaskLogRecord = {
  logId: number;
  taskId?: number | null;
  taskType: string;
  targetId?: number | null;
  startTime: string;
  endTime?: string | null;
  status: string;
  executionSummary?: string | null;
  errorMessage?: string | null;
  duration: number;
};

export function login(payload: { username: string; password: string }) {
  return apiPost<LoginResult>('/auth/login', payload);
}

export function profile() {
  return apiGet<{ userId: number; username: string; role: 'ADMIN' | 'OPERATOR' }>('/auth/profile');
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

export function createDataSource(payload: Partial<DataSource>) {
  return apiPost<DataSource>('/data-sources', payload);
}

export function updateDataSource(sourceId: number, payload: Partial<DataSource>) {
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

export function replayImport(importId: number) {
  return apiPost<{
    importId: number;
    datasetId: number;
    datasetName: string;
    formatType: string;
    recordCount: number;
    status: string;
    errorMessage: string;
  }>('/imports/database', undefined, { params: { importId } });
}

export function listImportHistory() {
  return apiGet<any[]>('/imports/history');
}

export function getImportDetail(importId: number) {
  return apiGet(`/imports/${importId}`);
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

export function filterQuery(payload: { datasetId: number; filters: { field: string; operator: string; value: string }[]; pageNum: number; pageSize: number }) {
  return apiPost<PageResponse<Record<string, unknown>>>('/queries/filter', payload);
}

export function sqlQuery(payload: { datasetId: number; sql: string }) {
  return apiPost<Record<string, unknown>[]>('/queries/sql', payload);
}

export function getAnalysisSummary(datasetId: number) {
  return apiGet(`/analysis/${datasetId}/summary`);
}

export function getAnalysisCharts(datasetId: number) {
  return apiGet<{ name: string; value: number }[]>(`/analysis/${datasetId}/charts`);
}

export function listOperators() {
  return apiGet<OperatorDefinition[]>('/governance/operators');
}

export function listFlows() {
  return apiGet<GovernanceFlow[]>('/governance/flows');
}

export function saveFlow(payload: { flowName: string; datasetId: number; operatorChain: { operatorKey: string; params: Record<string, unknown> }[] }) {
  return apiPost('/governance/flows', payload);
}

export function executeGovernance(payload: { datasetId: number; operatorChain: { operatorKey: string; params: Record<string, unknown> }[] }) {
  return apiPost('/governance/execute', payload);
}

export function listTasks() {
  return apiGet<TaskRecord[]>('/tasks');
}

export function createTask(payload: { taskName: string; taskType: string; targetId: number; cronExpr: string; description?: string; status?: string }) {
  return apiPost<TaskRecord>('/tasks', payload);
}

export function updateTask(taskId: number, payload: { taskName: string; taskType: string; targetId: number; cronExpr: string; description?: string; status?: string }) {
  return apiPut<TaskRecord>(`/tasks/${taskId}`, payload);
}

export function triggerTask(taskId: number) {
  return apiPost<TaskRecord>(`/tasks/${taskId}/trigger`);
}

export function pauseTask(taskId: number) {
  return apiPost<TaskRecord>(`/tasks/${taskId}/pause`);
}

export function resumeTask(taskId: number) {
  return apiPost<TaskRecord>(`/tasks/${taskId}/resume`);
}

export function listTaskLogs() {
  return apiGet<TaskLogRecord[]>('/task-logs');
}

export function getTaskLogDetail(logId: number) {
  return apiGet<TaskLogRecord>(`/task-logs/${logId}`);
}

export function listUsers() {
  return apiGet<any[]>('/users');
}

export function listRoles() {
  return apiGet<any[]>('/roles');
}
