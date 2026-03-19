<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { getDatasetDetail, listDatasets, listMetadata, previewDataset, type DatasetDetail, type DatasetSummary, type MetaField } from '../api/platform';

const datasets = ref<DatasetSummary[]>([]);
const currentDetail = ref<DatasetDetail | null>(null);
const metadata = ref<MetaField[]>([]);
const preview = ref<{ pageNum: number; pageSize: number; total: number; records: Record<string, unknown>[] }>({
  pageNum: 1,
  pageSize: 10,
  total: 0,
  records: []
});

async function loadDataset(datasetId?: number) {
  try {
    datasets.value = await listDatasets();
    const targetId = datasetId || datasets.value[0]?.datasetId;
    if (!targetId) return;
    currentDetail.value = await getDatasetDetail(targetId);
    metadata.value = await listMetadata(targetId);
    preview.value = await previewDataset(targetId, 1, 10);
  } catch (error) {
    ElMessage.error(`数据集加载失败: ${(error as Error).message}`);
  }
}

onMounted(() => loadDataset());
</script>

<template>
  <div class="page-grid">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>数据集列表</span>
          <el-button link type="primary" @click="loadDataset(currentDetail?.datasetId)">刷新</el-button>
        </div>
      </template>
      <el-table :data="datasets" stripe @row-click="(row: DatasetSummary) => loadDataset(row.datasetId)">
        <el-table-column prop="datasetName" label="数据集名称" />
        <el-table-column prop="formatType" label="格式" width="120" />
        <el-table-column prop="recordCount" label="记录数" width="120" />
        <el-table-column prop="fieldCount" label="字段数" width="120" />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column prop="physicalTableName" label="物理表名" />
      </el-table>
    </el-card>

    <section class="two-column-grid">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>数据集详情</span>
            <el-tag v-if="currentDetail">{{ currentDetail.datasetName }}</el-tag>
          </div>
        </template>
        <div v-if="currentDetail" class="plain-list">
          <div>格式：{{ currentDetail.formatType }}</div>
          <div>记录数：{{ currentDetail.recordCount }}</div>
          <div>字段数：{{ currentDetail.fieldCount }}</div>
          <div>存储路径：{{ currentDetail.storagePath }}</div>
          <div>物理表名：{{ currentDetail.physicalTableName }}</div>
          <div>创建时间：{{ currentDetail.createTime }}</div>
        </div>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>元数据</span>
            <el-tag>{{ metadata.length }} 个字段</el-tag>
          </div>
        </template>
        <el-table :data="metadata" stripe>
          <el-table-column prop="fieldName" label="字段名" />
          <el-table-column prop="physicalColumnName" label="物理列名" />
          <el-table-column prop="fieldType" label="类型" width="120" />
          <el-table-column prop="sampleValue" label="样例值" />
        </el-table>
      </el-card>
    </section>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>数据预览</span>
          <el-tag>{{ preview.total }} 条</el-tag>
        </div>
      </template>
      <el-table :data="preview.records" stripe>
        <el-table-column
          v-for="column in Object.keys(preview.records[0] || {})"
          :key="column"
          :prop="column"
          :label="column"
        />
      </el-table>
    </el-card>
  </div>
</template>
