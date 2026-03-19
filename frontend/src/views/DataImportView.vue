<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { importFile, listDataSources, listImportHistory, type DataSource, type ImportHistory } from '../api/platform';

const dataSources = ref<DataSource[]>([]);
const history = ref<ImportHistory[]>([]);
const selectedFile = ref<File | null>(null);
const loading = ref(false);
const form = reactive({
  sourceId: undefined as number | undefined,
  datasetName: ''
});

async function loadBaseData() {
  dataSources.value = await listDataSources();
  history.value = await listImportHistory();
  if (!form.sourceId && dataSources.value.length > 0) {
    form.sourceId = dataSources.value[0].sourceId;
  }
}

function onFileChange(event: Event) {
  const target = event.target as HTMLInputElement;
  selectedFile.value = target.files?.[0] || null;
}

async function submit() {
  if (!selectedFile.value || !form.sourceId || !form.datasetName) {
    ElMessage.warning('请先选择数据源、文件和数据集名称');
    return;
  }
  loading.value = true;
  try {
    const payload = new FormData();
    payload.append('file', selectedFile.value);
    payload.append('datasetName', form.datasetName);
    payload.append('sourceId', String(form.sourceId));
    const result = await importFile(payload);
    ElMessage.success(`导入成功，生成数据集 ${result.datasetName}`);
    form.datasetName = '';
    selectedFile.value = null;
    history.value = await listImportHistory();
  } catch (error) {
    ElMessage.error(`导入失败: ${(error as Error).message}`);
  } finally {
    loading.value = false;
  }
}

onMounted(async () => {
  try {
    await loadBaseData();
  } catch (error) {
    ElMessage.error(`导入页初始化失败: ${(error as Error).message}`);
  }
});
</script>

<template>
  <div class="page-grid two-column-grid">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>文件上传导入</span>
          <el-tag type="success">Real Import</el-tag>
        </div>
      </template>
      <el-form label-position="top">
        <el-form-item label="所属数据源">
          <el-select v-model="form.sourceId" placeholder="请选择数据源">
            <el-option
              v-for="source in dataSources"
              :key="source.sourceId"
              :label="source.sourceName"
              :value="source.sourceId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="数据集名称">
          <el-input v-model="form.datasetName" placeholder="请输入数据集名称" />
        </el-form-item>
        <el-form-item label="上传文件">
          <input type="file" accept=".csv,.json,.xls,.xlsx" @change="onFileChange" />
        </el-form-item>
        <el-button type="primary" :loading="loading" @click="submit">提交导入</el-button>
      </el-form>
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
        <el-table-column prop="formatType" label="格式" width="100" />
        <el-table-column prop="recordCount" label="记录数" width="100" />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column prop="errorMessage" label="错误信息" />
      </el-table>
    </el-card>
  </div>
</template>
