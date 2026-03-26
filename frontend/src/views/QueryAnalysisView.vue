<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import * as echarts from 'echarts';
import { isAuthExpiredError } from '../api/client';
import {
  exportFilterQuery,
  exportSqlQuery,
  filterQuery,
  getAnalysisCharts,
  getAnalysisSummary,
  listDatasets,
  listMetadata,
  sqlQuery,
  type DatasetSummary,
  type MetaField
} from '../api/platform';

const chartRef = ref<HTMLDivElement | null>(null);
const datasets = ref<DatasetSummary[]>([]);
const metadata = ref<MetaField[]>([]);
const summary = ref({
  datasetId: 0,
  recordCount: 0,
  nullCount: 0,
  duplicateCount: 0
});
const tableData = ref<Record<string, unknown>[]>([]);
const chartData = ref<{ name: string; value: number }[]>([]);
const resultColumns = computed(() => Object.keys(tableData.value[0] || {}));
const lastQueryMode = ref<'FILTER' | 'SQL'>('FILTER');
const form = reactive({
  datasetId: undefined as number | undefined,
  field: '',
  operator: 'LIKE',
  value: '',
  sql: 'SELECT * FROM dataset LIMIT 20'
});
let chart: echarts.ECharts | null = null;

async function renderChart() {
  await nextTick();
  if (!chartRef.value) return;
  if (!chart) {
    chart = echarts.init(chartRef.value);
  }
  chart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: chartData.value.map((item) => item.name) },
    yAxis: { type: 'value' },
    series: [
      {
        type: 'bar',
        data: chartData.value.map((item) => item.value),
        itemStyle: { color: '#db6f44' }
      }
    ]
  });
}

async function loadBaseData() {
  datasets.value = await listDatasets();
  if (datasets.value.length > 0 && !form.datasetId) {
    form.datasetId = datasets.value[0].datasetId;
  }
  await loadDatasetAnalysis();
}

async function loadDatasetAnalysis() {
  if (!form.datasetId) return;
  metadata.value = await listMetadata(form.datasetId);
  form.field = metadata.value[0]?.fieldName || '';
  summary.value = await getAnalysisSummary(form.datasetId);
  chartData.value = await getAnalysisCharts(form.datasetId);
  await renderChart();
}

async function runFilterQuery() {
  if (!form.datasetId || !form.field || !form.value) {
    ElMessage.warning('请选择数据集并填写筛选条件');
    return;
  }
  try {
    const result = await filterQuery({
      datasetId: form.datasetId,
      field: form.field,
      operator: form.operator,
      value: form.value,
      pageNum: 1,
      pageSize: 20
    });
    lastQueryMode.value = 'FILTER';
    tableData.value = result.records;
    await loadDatasetAnalysis();
  } catch (error) {
    ElMessage.error(`条件查询失败: ${(error as Error).message}`);
  }
}

async function runSqlQuery() {
  if (!form.datasetId || !form.sql) {
    ElMessage.warning('请选择数据集并输入 SQL');
    return;
  }
  try {
    lastQueryMode.value = 'SQL';
    tableData.value = await sqlQuery({
      datasetId: form.datasetId,
      sql: form.sql
    });
  } catch (error) {
    ElMessage.error(`SQL 查询失败: ${(error as Error).message}`);
  }
}

async function exportRows(format: 'csv' | 'json' | 'xlsx') {
  if (!tableData.value.length) {
    ElMessage.warning('当前没有可导出的查询结果');
    return;
  }
  try {
    const result =
      lastQueryMode.value === 'SQL'
        ? await exportSqlQuery({ datasetId: form.datasetId!, sql: form.sql }, format)
        : await exportFilterQuery(
            {
              datasetId: form.datasetId!,
              field: form.field,
              operator: form.operator,
              value: form.value,
              pageNum: 1,
              pageSize: 20
            },
            format
          );
    const url = window.URL.createObjectURL(result.blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = result.filename || `query-result.${format}`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
  } catch (error) {
    ElMessage.error(`导出失败: ${(error as Error).message}`);
  }
}

onMounted(async () => {
  try {
    await loadBaseData();
  } catch (error) {
    if (isAuthExpiredError(error)) {
      return;
    }
    ElMessage.error(`查询页初始化失败: ${(error as Error).message}`);
  }
});

onBeforeUnmount(() => chart?.dispose());
</script>

<template>
  <div class="page-grid">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>条件查询与 SQL 查询</span>
          <el-tag type="success">Real Query</el-tag>
        </div>
      </template>
      <el-form inline>
        <el-form-item label="数据集">
          <el-select v-model="form.datasetId" placeholder="请选择数据集" @change="loadDatasetAnalysis">
            <el-option
              v-for="dataset in datasets"
              :key="dataset.datasetId"
              :label="dataset.datasetName"
              :value="dataset.datasetId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="字段">
          <el-select v-model="form.field" placeholder="请选择字段">
            <el-option
              v-for="column in metadata"
              :key="column.fieldId"
              :label="column.fieldName"
              :value="column.fieldName"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="操作符">
          <el-select v-model="form.operator">
            <el-option label="LIKE" value="LIKE" />
            <el-option label="EQ" value="EQ" />
            <el-option label="GT" value="GT" />
            <el-option label="LT" value="LT" />
          </el-select>
        </el-form-item>
        <el-form-item label="条件值">
          <el-input v-model="form.value" placeholder="例如 智能制造" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="runFilterQuery">执行条件查询</el-button>
        </el-form-item>
      </el-form>
      <el-input
        v-model="form.sql"
        type="textarea"
        :rows="4"
        placeholder="SELECT * FROM dataset LIMIT 20"
      />
      <div class="action-row">
        <el-button type="primary" plain @click="runSqlQuery">执行 SQL 查询</el-button>
      </div>
    </el-card>

    <section class="stat-grid">
      <el-card shadow="hover">
        <p class="stat-label">记录数</p>
        <p class="stat-value">{{ summary.recordCount }}</p>
      </el-card>
      <el-card shadow="hover">
        <p class="stat-label">空值数</p>
        <p class="stat-value">{{ summary.nullCount }}</p>
      </el-card>
      <el-card shadow="hover">
        <p class="stat-label">重复行数</p>
        <p class="stat-value">{{ summary.duplicateCount }}</p>
      </el-card>
    </section>

    <section class="two-column-grid">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>查询结果</span>
            <div>
              <el-button link type="primary" @click="exportRows('csv')">导出 CSV</el-button>
              <el-button link type="primary" @click="exportRows('json')">导出 JSON</el-button>
              <el-button link type="primary" @click="exportRows('xlsx')">导出 Excel</el-button>
            </div>
          </div>
        </template>
        <el-table :data="tableData" stripe>
          <el-table-column
            v-for="column in resultColumns"
            :key="column"
            :prop="column"
            :label="column"
          />
        </el-table>
      </el-card>
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>图表可视化</span>
            <el-tag>ECharts</el-tag>
          </div>
        </template>
        <div ref="chartRef" class="chart-box"></div>
      </el-card>
    </section>
  </div>
</template>
