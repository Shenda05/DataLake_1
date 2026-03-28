<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useRoute } from 'vue-router';
import { isAuthExpiredError } from '../api/client';
import {
  deleteDataset,
  exportDataset,
  getDatasetDetail,
  listDatasets,
  listMetadata,
  previewDatasetWithFilter,
  type DatasetDetail,
  type DatasetSummary,
  type MetaField,
  type PageResponse
} from '../api/platform';
import { useAuthStore } from '../stores/auth';

const authStore = useAuthStore();
const route = useRoute();
const datasets = ref<DatasetSummary[]>([]);
const domainFilter = ref('');
const metadata = ref<MetaField[]>([]);
const previewPage = ref<PageResponse<Record<string, unknown>>>({
  pageNum: 1,
  pageSize: 10,
  total: 0,
  records: []
});
const selectedDataset = ref<DatasetDetail | null>(null);
const previewFilters = reactive({
  field: '',
  keyword: ''
});
const canDeleteDataset = computed(() => authStore.hasAction('dataset.delete'));
const canExportDataset = computed(() => authStore.hasAction('dataset.export'));
const filteredDatasets = computed(() =>
  !domainFilter.value ? datasets.value : datasets.value.filter((item) => item.businessDomain === domainFilter.value)
);

async function loadDatasets() {
  datasets.value = await listDatasets();
  const preferredDatasetId = readRouteDatasetId();
  const preferred = preferredDatasetId ? datasets.value.find((item) => item.datasetId === preferredDatasetId) : null;
  const first = preferred || filteredDatasets.value[0] || datasets.value[0];
  if (first && selectedDataset.value?.datasetId !== first.datasetId) {
    await selectDataset(first.datasetId);
  }
}

async function selectDataset(datasetId: number) {
  selectedDataset.value = await getDatasetDetail(datasetId);
  metadata.value = await listMetadata(datasetId);
  previewFilters.field = '';
  previewFilters.keyword = '';
  await loadPreview(1);
}

async function loadPreview(pageNum = 1) {
  if (!selectedDataset.value) return;
  previewPage.value = await previewDatasetWithFilter(
    selectedDataset.value.datasetId,
    pageNum,
    10,
    previewFilters.field || undefined,
    previewFilters.keyword || undefined
  );
}

function handleRowClick(row: DatasetSummary) {
  void selectDataset(row.datasetId);
}

function readRouteDatasetId() {
  const raw = route.query.datasetId;
  const value = Array.isArray(raw) ? raw[0] : raw;
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}

function handlePageChange(page: number) {
  void loadPreview(page);
}

async function handleSearch() {
  try {
    await loadPreview(1);
  } catch (error) {
    ElMessage.error(`预览筛选失败: ${(error as Error).message}`);
  }
}

async function handleExport(format: 'csv' | 'json' | 'xlsx') {
  if (!canExportDataset.value) {
    ElMessage.warning('当前角色没有数据集导出权限');
    return;
  }
  if (!selectedDataset.value) return;
  try {
    const result = await exportDataset(
      selectedDataset.value.datasetId,
      format,
      previewFilters.field || undefined,
      previewFilters.keyword || undefined
    );
    const url = window.URL.createObjectURL(result.blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = result.filename || `${selectedDataset.value.datasetName}.${format}`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
  } catch (error) {
    ElMessage.error(`导出失败: ${(error as Error).message}`);
  }
}

async function handleDelete(dataset: DatasetSummary) {
  if (!canDeleteDataset.value) {
    ElMessage.warning('当前角色没有数据集删除权限');
    return;
  }
  try {
    await ElMessageBox.confirm(`确定删除数据集 ${dataset.datasetName} 吗？`, '删除确认', { type: 'warning' });
    await deleteDataset(dataset.datasetId);
    ElMessage.success('数据集已删除');
    await loadDatasets();
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(`删除失败: ${(error as Error).message}`);
    }
  }
}

onMounted(async () => {
  try {
    await loadDatasets();
  } catch (error) {
    if (isAuthExpiredError(error)) {
      return;
    }
    ElMessage.error(`数据集加载失败: ${(error as Error).message}`);
  }
});

watch(
  () => domainFilter.value,
  (value) => {
    if (!value || !selectedDataset.value) {
      return;
    }
    if (selectedDataset.value.businessDomain !== value) {
      const next = filteredDatasets.value[0];
      if (next) {
        void selectDataset(next.datasetId);
      }
    }
  }
);

watch(
  () => route.query.datasetId,
  () => {
    const datasetId = readRouteDatasetId();
    if (!datasetId || selectedDataset.value?.datasetId === datasetId) {
      return;
    }
    if (datasets.value.some((item) => item.datasetId === datasetId)) {
      void selectDataset(datasetId);
    }
  }
);
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
      <el-form inline class="notice-box">
        <el-form-item label="业务域">
          <el-select v-model="domainFilter" clearable placeholder="全部业务域">
            <el-option label="用户域" value="USER" />
            <el-option label="商品域" value="PRODUCT" />
            <el-option label="交易域" value="TRADE" />
            <el-option label="支付域" value="PAYMENT" />
            <el-option label="库存域" value="INVENTORY" />
            <el-option label="评价域" value="REVIEW" />
            <el-option label="行为日志域" value="BEHAVIOR_LOG" />
          </el-select>
        </el-form-item>
      </el-form>
      <el-table :data="filteredDatasets" stripe @row-click="handleRowClick">
        <el-table-column prop="datasetName" label="数据集名称" />
        <el-table-column prop="businessDomain" label="业务域" width="130" />
        <el-table-column prop="formatType" label="格式" width="120" />
        <el-table-column prop="recordCount" label="记录数" width="120" />
        <el-table-column prop="fieldCount" label="字段数" width="120" />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column prop="creator" label="创建人" width="120" />
        <el-table-column v-if="canDeleteDataset" label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="danger" @click.stop="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="filteredDatasets.length === 0" description="当前业务域下暂无数据集" />
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
      <el-form inline>
        <el-form-item label="字段">
          <el-select v-model="previewFilters.field" clearable placeholder="全部字段">
            <el-option
              v-for="column in metadata"
              :key="column.fieldId"
              :label="column.fieldName"
              :value="column.fieldName"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="previewFilters.keyword" placeholder="输入关键字搜索" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">筛选</el-button>
        </el-form-item>
        <el-form-item>
          <el-button :disabled="!canExportDataset" @click="handleExport('csv')">导出 CSV</el-button>
        </el-form-item>
        <el-form-item>
          <el-button :disabled="!canExportDataset" @click="handleExport('json')">导出 JSON</el-button>
        </el-form-item>
        <el-form-item>
          <el-button :disabled="!canExportDataset" @click="handleExport('xlsx')">导出 Excel</el-button>
        </el-form-item>
      </el-form>
      <el-alert
        v-if="!canExportDataset"
        class="notice-box"
        title="当前角色只有数据集查看权限，不能导出数据集内容。"
        type="info"
        :closable="false"
      />
      <el-table :data="previewPage.records" stripe>
        <el-table-column
          v-for="column in metadata"
          :key="column.fieldId"
          :prop="column.fieldName"
          :label="column.fieldName"
        />
      </el-table>
      <el-pagination
        class="table-pagination"
        layout="prev, pager, next, total"
        :total="previewPage.total"
        :page-size="previewPage.pageSize"
        :current-page="previewPage.pageNum"
        @current-change="handlePageChange"
      />
    </el-card>
  </div>
</template>
