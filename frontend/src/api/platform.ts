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
