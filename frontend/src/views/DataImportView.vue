<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { useRoute } from 'vue-router';
import { isAuthExpiredError } from '../api/client';
import {
  getImportDetail,
  importDatabase,
  importFile,
  listDatabaseSchemas,
  listDataSources,
  listDatabaseTables,
  listImportHistory,
  previewDatabaseTable,
  type BusinessDomain,
  type DataSource,
  type DatabasePreview,
  type DatabaseTableOption,
  type ImportDetail,
  type ImportHistory
} from '../api/platform';
import { useAuthStore } from '../stores/auth';

const authStore = useAuthStore();
const route = useRoute();
const dataSources = ref<DataSource[]>([]);
const history = ref<ImportHistory[]>([]);
const selectedImportDetail = ref<ImportDetail | null>(null);
const selectedFile = ref<File | null>(null);
const loading = ref(false);
const detailLoading = ref(false);
const detailDrawerVisible = ref(false);
const activeTab = ref<'file' | 'database'>('file');
const databaseSchemas = ref<string[]>([]);
const databaseTables = ref<DatabaseTableOption[]>([]);
const databasePreview = ref<DatabasePreview | null>(null);
const dbLoading = ref(false);
const schemaLoading = ref(false);
const BUSINESS_DOMAIN_OPTIONS: { label: string; value: BusinessDomain }[] = [
  { label: '用户域', value: 'USER' },
  { label: '商品域', value: 'PRODUCT' },
  { label: '交易域', value: 'TRADE' },
  { label: '支付域', value: 'PAYMENT' },
  { label: '库存域', value: 'INVENTORY' },
  { label: '评价域', value: 'REVIEW' },
  { label: '行为日志域', value: 'BEHAVIOR_LOG' }
];
const fileForm = reactive({
  sourceId: undefined as number | undefined,
  datasetName: '',
  businessDomain: 'TRADE' as BusinessDomain
});
const historyFilters = reactive({
  businessDomain: '' as '' | BusinessDomain,
  status: '' as '' | 'SUCCESS' | 'FAILED',
  timeRange: [] as string[]
});
const databaseForm = reactive({
  sourceId: undefined as number | undefined,
  schemaName: '',
  tableName: '',
  datasetName: '',
  businessDomain: 'TRADE' as BusinessDomain,
  description: ''
});

const adminDatabaseSources = computed(() => dataSources.value.filter((item) => item.sourceType === 'MYSQL'));
const fileSources = computed(() => dataSources.value.filter((item) => item.sourceType === 'FILE' || item.sourceType === 'MYSQL'));
const previewColumns = computed(() => databasePreview.value?.columns ?? []);
const canImportDatabase = computed(() => authStore.hasAction('import.database'));
const selectedImportParamsText = computed(() => JSON.stringify(selectedImportDetail.value?.importParams || {}, null, 2));
const canSelectSchema = computed(() => Boolean(canImportDatabase.value && databaseForm.sourceId));
const canSelectDatabaseTable = computed(() => Boolean(canImportDatabase.value && databaseForm.sourceId && databaseForm.schemaName));

function formatDateTime(value?: string | null) {
  if (!value) {
    return '-';
  }
  return value.replace('T', ' ').replace('Z', '');
}

async function loadBaseData() {
  const hadDatabaseSource = Boolean(databaseForm.sourceId);
  dataSources.value = await listDataSources();
  await loadHistory();
  const routeImportId = readRouteImportId();
  if (routeImportId && selectedImportDetail.value?.importId !== routeImportId) {
    await openImportDetail(routeImportId);
  }
  if (!fileForm.sourceId && fileSources.value.length > 0) {
    fileForm.sourceId = fileSources.value[0].sourceId;
  }
  if (!databaseForm.sourceId && adminDatabaseSources.value.length > 0) {
    databaseForm.sourceId = adminDatabaseSources.value[0].sourceId;
  }
  if (canImportDatabase.value && databaseForm.sourceId && hadDatabaseSource) {
    await loadDatabaseSchemasAndSelect();
  }
}

async function loadHistory() {
  history.value = await listImportHistory({
    businessDomain: historyFilters.businessDomain || undefined,
    status: historyFilters.status || undefined,
    startTime: historyFilters.timeRange[0] || undefined,
    endTime: historyFilters.timeRange[1] || undefined
  });
}

function onFileChange(event: Event) {
  const target = event.target as HTMLInputElement;
  selectedFile.value = target.files?.[0] || null;
}

async function submitFileImport() {
  if (!selectedFile.value || !fileForm.sourceId || !fileForm.datasetName) {
    ElMessage.warning('请先选择数据源、文件和数据集名称');
    return;
  }
  loading.value = true;
  try {
    const payload = new FormData();
    payload.append('file', selectedFile.value);
    payload.append('datasetName', fileForm.datasetName);
    payload.append('businessDomain', fileForm.businessDomain);
    payload.append('sourceId', String(fileForm.sourceId));
    const result = await importFile(payload);
    ElMessage.success(`导入成功，生成数据集 ${result.datasetName}`);
    fileForm.datasetName = '';
    selectedFile.value = null;
    await loadHistory();
  } catch (error) {
    ElMessage.error(`导入失败: ${(error as Error).message}`);
  } finally {
    loading.value = false;
  }
}

function readRouteImportId() {
  const raw = route.query.importId;
  const value = Array.isArray(raw) ? raw[0] : raw;
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}

function resolveSourceLabel(sourceId?: number | null) {
  if (!sourceId) {
    return '-';
  }
  const matched = dataSources.value.find((item) => item.sourceId === sourceId);
  return matched?.sourceName || `数据源 ${sourceId}`;
}

function resolveOriginLabel(detail?: ImportDetail | null) {
  if (!detail) {
    return '-';
  }
  if (detail.originalFileName && detail.originalFileName.trim()) {
    return detail.originalFileName;
  }
  if (detail.filePath && detail.filePath.trim()) {
    return detail.filePath;
  }
  return '-';
}

async function openImportDetail(importId: number) {
  detailLoading.value = true;
  detailDrawerVisible.value = true;
  try {
    selectedImportDetail.value = await getImportDetail(importId);
  } catch (error) {
    detailDrawerVisible.value = false;
    ElMessage.error(`导入详情加载失败: ${(error as Error).message}`);
  } finally {
    detailLoading.value = false;
  }
}

function handleHistoryDetail(row: ImportHistory) {
  void openImportDetail(row.importId);
}

async function applyHistoryFilters() {
  try {
    await loadHistory();
  } catch (error) {
    ElMessage.error(`导入历史筛选失败: ${(error as Error).message}`);
  }
}

async function resetHistoryFilters() {
  historyFilters.businessDomain = '';
  historyFilters.status = '';
  historyFilters.timeRange = [];
  await applyHistoryFilters();
}

async function loadDatabaseTables() {
  if (!canImportDatabase.value) {
    databaseTables.value = [];
    databasePreview.value = null;
    return;
  }
  if (!databaseForm.sourceId) {
    databaseTables.value = [];
    databasePreview.value = null;
    return;
  }
  if (!databaseForm.schemaName) {
    databaseTables.value = [];
    databasePreview.value = null;
    databaseForm.tableName = '';
    return;
  }
  try {
    databaseTables.value = await listDatabaseTables(databaseForm.sourceId, databaseForm.schemaName || undefined);
    if (!databaseTables.value.some((item) => item.tableName === databaseForm.tableName)) {
      databaseForm.tableName = '';
    }
    if (!databaseForm.tableName && databaseTables.value.length > 0) {
      databaseForm.tableName = databaseTables.value[0].tableName;
    }
    if (!databaseTables.value.length) {
      databasePreview.value = null;
    }
  } catch (error) {
    databaseTables.value = [];
    databasePreview.value = null;
    ElMessage.error(`数据库表加载失败: ${(error as Error).message}`);
  }
}

function resetDatabaseSelection(options?: { clearSchema?: boolean }) {
  if (options?.clearSchema) {
    databaseForm.schemaName = '';
    databaseSchemas.value = [];
  }
  databaseTables.value = [];
  databaseForm.tableName = '';
  databasePreview.value = null;
}

function resolvePreferredSchema() {
  const selectedSource = adminDatabaseSources.value.find((item) => item.sourceId === databaseForm.sourceId);
  const candidates = [databaseForm.schemaName, selectedSource?.dbName, databaseSchemas.value[0]]
    .map((item) => (item || '').trim())
    .filter(Boolean);
  for (const candidate of candidates) {
    if (databaseSchemas.value.includes(candidate)) {
      return candidate;
    }
  }
  return candidates[0] || '';
}

async function loadDatabaseSchemasAndSelect() {
  if (!canImportDatabase.value || !databaseForm.sourceId) {
    databaseSchemas.value = [];
    resetDatabaseSelection({ clearSchema: true });
    return;
  }
  schemaLoading.value = true;
  try {
    databaseSchemas.value = await listDatabaseSchemas(databaseForm.sourceId);
    const preferredSchema = resolvePreferredSchema();
    const schemaChanged = databaseForm.schemaName !== preferredSchema;
    databaseForm.schemaName = preferredSchema;
    if (!schemaChanged && databaseForm.schemaName) {
      await loadDatabaseTables();
    }
  } catch (error) {
    databaseSchemas.value = [];
    resetDatabaseSelection({ clearSchema: true });
    ElMessage.error(`数据库列表加载失败: ${(error as Error).message}`);
  } finally {
    schemaLoading.value = false;
  }
}

async function loadDatabasePreview() {
  if (!canImportDatabase.value) {
    databasePreview.value = null;
    return;
  }
  if (!databaseForm.sourceId || !databaseForm.tableName) {
    databasePreview.value = null;
    return;
  }
  dbLoading.value = true;
  try {
    databasePreview.value = await previewDatabaseTable(
      databaseForm.sourceId,
      databaseForm.tableName,
      databaseForm.schemaName || undefined,
      10
    );
    if (!databaseForm.datasetName) {
      databaseForm.datasetName = `${databaseForm.tableName}_dataset`;
    }
  } catch (error) {
    databasePreview.value = null;
    ElMessage.error(`表预览失败: ${(error as Error).message}`);
  } finally {
    dbLoading.value = false;
  }
}

async function submitDatabaseImport() {
  if (!canImportDatabase.value) {
    ElMessage.warning('当前角色没有数据库表导入权限');
    return;
  }
  if (!databaseForm.sourceId || !databaseForm.tableName || !databaseForm.datasetName) {
    ElMessage.warning('请先选择数据库表并填写数据集名称');
    return;
  }
  loading.value = true;
  try {
    const result = await importDatabase({
      sourceId: databaseForm.sourceId,
      schemaName: databaseForm.schemaName || undefined,
      tableName: databaseForm.tableName,
      datasetName: databaseForm.datasetName,
      businessDomain: databaseForm.businessDomain,
      description: databaseForm.description
    });
    ElMessage.success(`数据库表导入成功，生成数据集 ${result.datasetName}`);
    databaseForm.datasetName = '';
    databaseForm.description = '';
    await loadHistory();
  } catch (error) {
    ElMessage.error(`数据库表导入失败: ${(error as Error).message}`);
  } finally {
    loading.value = false;
  }
}

watch(
  () => databaseForm.sourceId,
  async (value, oldValue) => {
    if (!value) {
      resetDatabaseSelection({ clearSchema: true });
      return;
    }
    if (value !== oldValue) {
      resetDatabaseSelection({ clearSchema: true });
    }
    await loadDatabaseSchemasAndSelect();
  }
);

watch(
  () => databaseForm.schemaName,
  async (schemaName, oldSchemaName) => {
    if (!databaseForm.sourceId) {
      return;
    }
    if ((schemaName || '') === (oldSchemaName || '')) {
      return;
    }
    resetDatabaseSelection();
    if (schemaName) {
      await loadDatabaseTables();
    }
  }
);

watch(
  () => databaseForm.tableName,
  async (tableName, oldTableName) => {
    if (!databaseForm.sourceId || !databaseForm.schemaName) {
      databasePreview.value = null;
      return;
    }
    if (!tableName) {
      databasePreview.value = null;
      return;
    }
    if (tableName !== oldTableName) {
      await loadDatabasePreview();
    }
  }
);

watch(
  canImportDatabase,
  (value) => {
    if (!value && activeTab.value === 'database') {
      activeTab.value = 'file';
    }
  },
  { immediate: true }
);

watch(
  () => route.query.importId,
  () => {
    const importId = readRouteImportId();
    if (!importId || selectedImportDetail.value?.importId === importId) {
      return;
    }
    void openImportDetail(importId);
  }
);

onMounted(async () => {
  try {
    await loadBaseData();
  } catch (error) {
    if (isAuthExpiredError(error)) {
      return;
    }
    ElMessage.error(`导入页初始化失败: ${(error as Error).message}`);
  }
});
</script>

<template>
  <div class="page-grid">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>电商数据接入</span>
          <el-tag type="success">Real Import</el-tag>
        </div>
      </template>
      <el-tabs v-model="activeTab">
        <el-tab-pane label="文件导入" name="file">
          <div class="two-column-grid">
            <div class="page-grid">
              <el-form label-position="top">
                <el-form-item label="所属数据源">
                  <el-select v-model="fileForm.sourceId" class="form-select-wide" placeholder="请选择数据源">
                    <el-option
                      v-for="source in fileSources"
                      :key="source.sourceId"
                      :label="source.sourceName"
                      :value="source.sourceId"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="数据集名称">
                  <el-input v-model="fileForm.datasetName" placeholder="请输入数据集名称" />
                </el-form-item>
                <el-form-item label="业务域">
                  <el-select v-model="fileForm.businessDomain" class="form-select-wide">
                    <el-option
                      v-for="domain in BUSINESS_DOMAIN_OPTIONS"
                      :key="domain.value"
                      :label="domain.label"
                      :value="domain.value"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="上传文件">
                  <input type="file" accept=".csv,.json,.xls,.xlsx" @change="onFileChange" />
                </el-form-item>
                <el-button type="primary" :loading="loading" @click="submitFileImport">提交导入</el-button>
              </el-form>
            </div>
            <el-alert
              title="文件导入支持 CSV / JSON / Excel"
              description="导入完成后会自动生成数据集、元数据和物理数据表。"
              type="info"
              :closable="false"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane v-if="canImportDatabase" label="数据库表导入" name="database">
          <div class="two-column-grid">
            <el-form label-position="top">
              <el-form-item label="数据库数据源">
                <el-select v-model="databaseForm.sourceId" class="form-select-wide" placeholder="请选择 MYSQL 数据源">
                  <el-option
                    v-for="source in adminDatabaseSources"
                    :key="source.sourceId"
                    :label="source.sourceName"
                    :value="source.sourceId"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="Schema / Database">
                <el-select
                  v-model="databaseForm.schemaName"
                  class="form-select-wide"
                  filterable
                  allow-create
                  default-first-option
                  clearable
                  :loading="schemaLoading"
                  :disabled="!canSelectSchema"
                  placeholder="自动识别当前数据源可访问的数据库"
                >
                  <el-option
                    v-for="schema in databaseSchemas"
                    :key="schema"
                    :label="schema"
                    :value="schema"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="数据库表">
                <el-select
                  v-model="databaseForm.tableName"
                  class="form-select-wide"
                  filterable
                  :loading="dbLoading"
                  :disabled="!canSelectDatabaseTable"
                  placeholder="请先选择 Schema / Database"
                >
                  <el-option
                    v-for="table in databaseTables"
                    :key="table.displayName"
                    :label="table.displayName"
                    :value="table.tableName"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="数据集名称">
                <el-input v-model="databaseForm.datasetName" placeholder="选择数据库表后可自动带出默认名称" />
              </el-form-item>
              <el-form-item label="业务域">
                <el-select v-model="databaseForm.businessDomain" class="form-select-wide">
                  <el-option
                    v-for="domain in BUSINESS_DOMAIN_OPTIONS"
                    :key="domain.value"
                    :label="domain.label"
                    :value="domain.value"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="说明">
                <el-input v-model="databaseForm.description" type="textarea" :rows="3" placeholder="例如：外部业务库表快照导入" />
              </el-form-item>
              <div class="card-header-actions">
                <el-button :loading="schemaLoading" :disabled="!databaseForm.sourceId" @click="loadDatabaseSchemasAndSelect">刷新库列表</el-button>
                <el-button :loading="dbLoading" :disabled="!databaseForm.schemaName" @click="loadDatabaseTables">刷新表列表</el-button>
                <el-button :loading="dbLoading" :disabled="!databaseForm.tableName" @click="loadDatabasePreview">预览数据</el-button>
                <el-button type="primary" :loading="loading" :disabled="!databaseForm.tableName" @click="submitDatabaseImport">导入为数据集</el-button>
              </div>
            </el-form>

            <el-card shadow="never">
              <template #header>
                <div class="card-header">
                  <span>表结构预览</span>
                  <el-tag>{{ databasePreview?.tableName || '未选择' }}</el-tag>
                </div>
              </template>
              <el-alert
                title="请按“数据源 → Schema / Database → 数据库表”的顺序选择；切换数据库后会自动刷新表列表。"
                type="info"
                :closable="false"
              />
              <el-table :data="previewColumns" stripe>
                <el-table-column prop="fieldName" label="字段名" />
                <el-table-column prop="fieldType" label="字段类型" width="140" />
                <el-table-column prop="nullable" label="可为空" width="120" />
                <el-table-column prop="sampleValue" label="样例值" />
              </el-table>
              <el-table v-if="databasePreview?.records?.length" :data="databasePreview.records" stripe class="notice-box">
                <el-table-column
                  v-for="column in previewColumns"
                  :key="column.fieldName"
                  :prop="column.fieldName"
                  :label="column.fieldName"
                />
              </el-table>
              <el-empty v-else description="选择数据库表后可查看前 10 行样例数据" />
            </el-card>
          </div>
        </el-tab-pane>
      </el-tabs>
      <el-alert
        v-if="!canImportDatabase"
        class="notice-box"
        title="当前角色没有数据库表导入权限"
        description="你仍然可以继续使用文件导入；如果需要从 MYSQL 表导入，请在“用户与权限”中为当前角色分配 import.database 操作权限。"
        type="info"
        :closable="false"
      />
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>导入历史</span>
          <el-button link type="primary" @click="loadHistory">刷新</el-button>
        </div>
      </template>
      <el-form inline class="notice-box">
        <el-form-item label="业务域">
          <el-select v-model="historyFilters.businessDomain" class="form-select-wide" clearable placeholder="全部业务域">
            <el-option
              v-for="domain in BUSINESS_DOMAIN_OPTIONS"
              :key="domain.value"
              :label="domain.label"
              :value="domain.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="historyFilters.status" class="form-select-medium" clearable placeholder="全部状态">
            <el-option label="SUCCESS" value="SUCCESS" />
            <el-option label="FAILED" value="FAILED" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="historyFilters.timeRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="applyHistoryFilters">筛选</el-button>
          <el-button @click="resetHistoryFilters">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="history" stripe>
        <el-table-column prop="importId" label="导入编号" width="100" />
        <el-table-column prop="datasetName" label="数据集" />
        <el-table-column prop="businessDomain" label="业务域" width="130" />
        <el-table-column prop="formatType" label="格式" width="120" />
        <el-table-column prop="recordCount" label="记录数" width="100" />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column prop="operatorName" label="操作人" width="120" />
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="errorMessage" label="错误信息" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleHistoryDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="detailDrawerVisible" title="导入历史详情" size="42%">
      <div v-loading="detailLoading" class="page-grid">
        <el-descriptions v-if="selectedImportDetail" :column="1" border>
          <el-descriptions-item label="导入编号">{{ selectedImportDetail.importId }}</el-descriptions-item>
          <el-descriptions-item label="数据集">{{ selectedImportDetail.datasetName }}</el-descriptions-item>
          <el-descriptions-item label="业务域">{{ selectedImportDetail.businessDomain }}</el-descriptions-item>
          <el-descriptions-item label="格式">{{ selectedImportDetail.formatType }}</el-descriptions-item>
          <el-descriptions-item label="来源数据源">{{ resolveSourceLabel(selectedImportDetail.sourceId) }}</el-descriptions-item>
          <el-descriptions-item label="数据源类型">{{ selectedImportDetail.sourceType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="来源文件/表">{{ resolveOriginLabel(selectedImportDetail) }}</el-descriptions-item>
          <el-descriptions-item label="记录数">{{ selectedImportDetail.recordCount }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ selectedImportDetail.status }}</el-descriptions-item>
          <el-descriptions-item label="操作人">{{ selectedImportDetail.operatorName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="错误信息">{{ selectedImportDetail.errorMessage || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDateTime(selectedImportDetail.createTime) }}</el-descriptions-item>
        </el-descriptions>
        <el-card v-if="Object.keys(selectedImportDetail?.importParams || {}).length" shadow="never">
          <template #header>
            <div class="card-header">
              <span>导入参数摘要</span>
              <el-tag type="info">{{ Object.keys(selectedImportDetail?.importParams || {}).length }} 项</el-tag>
            </div>
          </template>
          <pre>{{ selectedImportParamsText }}</pre>
        </el-card>
        <el-empty v-if="!selectedImportDetail" description="暂无导入详情" />
      </div>
    </el-drawer>
  </div>
</template>
