<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { getDatasetDetail, listDatasets, listMetadata, previewDataset, type DatasetDetail, type DatasetSummary, type MetaField } from '../api/platform';

const datasets = ref<DatasetSummary[]>([]);
const metadata = ref<MetaField[]>([]);
const preview = ref<Record<string, unknown>[]>([]);
const selectedDataset = ref<DatasetDetail | null>(null);

async function loadDatasets() {
  datasets.value = await listDatasets();
  if (datasets.value.length > 0) {
    await selectDataset(datasets.value[0].datasetId);
  }
}

async function selectDataset(datasetId: number) {
  selectedDataset.value = await getDatasetDetail(datasetId);
  metadata.value = await listMetadata(datasetId);
  preview.value = (await previewDataset(datasetId, 1, 10)).records;
}

function handleRowClick(row: DatasetSummary) {
  void selectDataset(row.datasetId);
}

onMounted(async () => {
  try {
    await loadDatasets();
  } catch (error) {
    ElMessage.error(`数据集加载失败: ${(error as Error).message}`);
  }
});
</script>

<template>
  <div class="page-grid">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>数据集列表</span>
          <el-tag type="success">Real Data</el-tag>
        </div>
      </template>
      <el-table :data="datasets" stripe @row-click="handleRowClick">
        <el-table-column prop="datasetName" label="数据集名称" />
        <el-table-column prop="formatType" label="格式" width="120" />
        <el-table-column prop="recordCount" label="记录数" width="120" />
        <el-table-column prop="fieldCount" label="字段数" width="120" />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column prop="creator" label="创建人" width="120" />
      </el-table>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>元数据预览</span>
          <el-tag>{{ selectedDataset?.datasetName || 'MetaField' }}</el-tag>
        </div>
      </template>
      <el-table :data="metadata" stripe>
        <el-table-column prop="fieldName" label="字段名" />
        <el-table-column prop="fieldType" label="字段类型" width="120" />
        <el-table-column prop="nullable" label="可为空" width="120" />
        <el-table-column prop="sampleValue" label="样例值" />
      </el-table>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>数据预览</span>
          <el-tag>{{ selectedDataset?.formatType || 'Preview' }}</el-tag>
        </div>
      </template>
      <el-table :data="preview" stripe>
        <el-table-column
          v-for="column in metadata"
          :key="column.fieldId"
          :prop="column.fieldName"
          :label="column.fieldName"
        />
      </el-table>
    </el-card>
  </div>
</template>
