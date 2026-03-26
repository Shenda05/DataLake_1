<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { isAuthExpiredError } from '../api/client';
import {
  importDatabase,
  importFile,
  listDataSources,
  listDatabaseTables,
  listImportHistory,
  previewDatabaseTable,
  type DataSource,
  type DatabasePreview,
  type DatabaseTableOption,
  type ImportHistory
} from '../api/platform';
import { useAuthStore } from '../stores/auth';

const authStore = useAuthStore();
const dataSources = ref<DataSource[]>([]);
const history = ref<ImportHistory[]>([]);
const selectedFile = ref<File | null>(null);
const loading = ref(false);
const activeTab = ref<'file' | 'database'>('file');
const databaseTables = ref<DatabaseTableOption[]>([]);
const databasePreview = ref<DatabasePreview | null>(null);
const dbLoading = ref(false);
const fileForm = reactive({
  sourceId: undefined as number | undefined,
  datasetName: ''
});
const databaseForm = reactive({
  sourceId: undefined as number | undefined,
  schemaName: '',
  tableName: '',
  datasetName: '',
  description: ''
});

const adminDatabaseSources = computed(() => dataSources.value.filter((item) => item.sourceType === 'MYSQL'));
const fileSources = computed(() => dataSources.value.filter((item) => item.sourceType === 'FILE' || item.sourceType === 'MYSQL'));
const previewColumns = computed(() => databasePreview.value?.columns ?? []);
const canImportDatabase = computed(() => authStore.hasAction('import.database'));

async function loadBaseData() {
  dataSources.value = await listDataSources();
  history.value = await listImportHistory();
  if (!fileForm.sourceId && fileSources.value.length > 0) {
    fileForm.sourceId = fileSources.value[0].sourceId;
  }
  if (!databaseForm.sourceId && adminDatabaseSources.value.length > 0) {
    databaseForm.sourceId = adminDatabaseSources.value[0].sourceId;
    const matched = adminDatabaseSources.value[0];
    databaseForm.schemaName = matched.dbName || '';
  }
  if (canImportDatabase.value && databaseForm.sourceId) {
    await loadDatabaseTables();
  }
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
    payload.append('sourceId', String(fileForm.sourceId));
    const result = await importFile(payload);
    ElMessage.success(`导入成功，生成数据集 ${result.datasetName}`);
    fileForm.datasetName = '';
    selectedFile.value = null;
    history.value = await listImportHistory();
  } catch (error) {
    ElMessage.error(`导入失败: ${(error as Error).message}`);
  } finally {
    loading.value = false;
  }
}

async function loadDatabaseTables() {
  if (!canImportDatabase.value) {
    databaseTables.value = [];
    databasePreview.value = null;
    return;
  }
  if (!databaseForm.sourceId) {
    databaseTables.value = [];
    return;
  }
  try {
    databaseTables.value = await listDatabaseTables(databaseForm.sourceId, databaseForm.schemaName || undefined);
    if (!databaseForm.tableName && databaseTables.value.length > 0) {
      databaseForm.tableName = databaseTables.value[0].tableName;
    }
    if (databaseForm.tableName) {
      await loadDatabasePreview();
    }
  } catch (error) {
    databaseTables.value = [];
    databasePreview.value = null;
    ElMessage.error(`数据库表加载失败: ${(error as Error).message}`);
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
      description: databaseForm.description
    });
    ElMessage.success(`数据库表导入成功，生成数据集 ${result.datasetName}`);
    databaseForm.datasetName = '';
    databaseForm.description = '';
    history.value = await listImportHistory();
  } catch (error) {
    ElMessage.error(`数据库表导入失败: ${(error as Error).message}`);
  } finally {
    loading.value = false;
  }
}

watch(
  () => databaseForm.sourceId,
  async (value) => {
    if (!value) {
      return;
    }
    const source = adminDatabaseSources.value.find((item) => item.sourceId === value);
    if (source && !databaseForm.schemaName) {
      databaseForm.schemaName = source.dbName || '';
    }
    await loadDatabaseTables();
  }
);

watch(
  () => [databaseForm.schemaName, databaseForm.tableName],
  async ([, tableName], [, oldTableName]) => {
    if (!databaseForm.sourceId) {
      return;
    }
    if (tableName && tableName !== oldTableName) {
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
          <span>数据接入</span>
          <el-tag type="success">Real Import</el-tag>
        </div>
      </template>
      <el-tabs v-model="activeTab">
        <el-tab-pane label="文件导入" name="file">
          <div class="two-column-grid">
            <div class="page-grid">
              <el-form label-position="top">
                <el-form-item label="所属数据源">
                  <el-select v-model="fileForm.sourceId" placeholder="请选择数据源">
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
                <el-select v-model="databaseForm.sourceId" placeholder="请选择 MYSQL 数据源">
                  <el-option
                    v-for="source in adminDatabaseSources"
                    :key="source.sourceId"
                    :label="source.sourceName"
                    :value="source.sourceId"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="Schema / Database">
                <el-input v-model="databaseForm.schemaName" placeholder="默认使用数据源配置中的 dbName" />
              </el-form-item>
              <el-form-item label="数据库表">
                <el-select v-model="databaseForm.tableName" filterable placeholder="请选择数据表">
                  <el-option
                    v-for="table in databaseTables"
                    :key="table.displayName"
                    :label="table.displayName"
                    :value="table.tableName"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="数据集名称">
                <el-input v-model="databaseForm.datasetName" placeholder="导入后的数据集名称" />
              </el-form-item>
              <el-form-item label="说明">
                <el-input v-model="databaseForm.description" type="textarea" :rows="3" placeholder="例如：外部业务库表快照导入" />
              </el-form-item>
              <div class="card-header-actions">
                <el-button :loading="dbLoading" @click="loadDatabaseTables">刷新表列表</el-button>
                <el-button :loading="dbLoading" @click="loadDatabasePreview">预览数据</el-button>
                <el-button type="primary" :loading="loading" @click="submitDatabaseImport">导入为数据集</el-button>
              </div>
            </el-form>

            <el-card shadow="never">
              <template #header>
                <div class="card-header">
                  <span>表结构预览</span>
                  <el-tag>{{ databasePreview?.tableName || '未选择' }}</el-tag>
                </div>
              </template>
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
          <el-button link type="primary" @click="loadBaseData">刷新</el-button>
        </div>
      </template>
      <el-table :data="history" stripe>
        <el-table-column prop="importId" label="导入编号" width="100" />
        <el-table-column prop="datasetName" label="数据集" />
        <el-table-column prop="formatType" label="格式" width="120" />
        <el-table-column prop="recordCount" label="记录数" width="100" />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column prop="errorMessage" label="错误信息" />
      </el-table>
    </el-card>
  </div>
</template>
