<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import * as echarts from 'echarts';
import { filterQuery, getAnalysisCharts, getAnalysisSummary, listDatasets, sqlQuery, type DatasetSummary } from '../api/platform';

const datasets = ref<DatasetSummary[]>([]);
const resultRows = ref<Record<string, unknown>[]>([]);
const summary = ref<any>(null);
const chartData = ref<{ name: string; value: number }[]>([]);
const chartRef = ref<HTMLDivElement | null>(null);
let chart: echarts.ECharts | null = null;

const filterForm = reactive({
  datasetId: undefined as number | undefined,
  field: '',
  operator: 'LIKE',
  value: '',
  sql: 'SELECT * FROM dataset LIMIT 20'
});

async function loadBaseData() {
  datasets.value = await listDatasets();
  if (!filterForm.datasetId && datasets.value.length > 0) {
    filterForm.datasetId = datasets.value[0].datasetId;
  }
  if (filterForm.datasetId) {
    await refreshAnalytics();
  }
}

async function executeFilter() {
  if (!filterForm.datasetId || !filterForm.field || !filterForm.value) {
    ElMessage.warning('请选择数据集并填写过滤条件');
    return;
  }
  try {
    const page = await filterQuery({
      datasetId: filterForm.datasetId,
      filters: [{ field: filterForm.field, operator: filterForm.operator, value: filterForm.value }],
      pageNum: 1,
      pageSize: 20
    });
    resultRows.value = page.records;
  } catch (error) {
    ElMessage.error(`条件查询失败: ${(error as Error).message}`);
  }
}

async function executeSqlQuery() {
  if (!filterForm.datasetId || !filterForm.sql) {
    ElMessage.warning('请选择数据集并填写 SQL');
    return;
  }
  try {
    resultRows.value = await sqlQuery({ datasetId: filterForm.datasetId, sql: filterForm.sql });
  } catch (error) {
    ElMessage.error(`SQL 查询失败: ${(error as Error).message}`);
  }
}

async function refreshAnalytics() {
  if (!filterForm.datasetId) return;
  try {
    summary.value = await getAnalysisSummary(filterForm.datasetId);
    chartData.value = await getAnalysisCharts(filterForm.datasetId);
  } catch (error) {
    ElMessage.error(`分析数据加载失败: ${(error as Error).message}`);
  }
}

async function renderChart() {
  await nextTick();
  if (!chartRef.value) return;
  chart ??= echarts.init(chartRef.value);
  chart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: chartData.value.map((item) => item.name) },
    yAxis: { type: 'value' },
    series: [{ type: 'bar', data: chartData.value.map((item) => item.value), itemStyle: { color: '#db6f44' } }]
  });
}

watch(chartData, renderChart);
onMounted(loadBaseData);
onBeforeUnmount(() => chart?.dispose());
</script>

<template>
  <div class="page-grid">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>查询分析</span>
          <el-button link type="primary" @click="refreshAnalytics">刷新统计</el-button>
        </div>
      </template>
      <el-form inline>
        <el-form-item label="数据集">
          <el-select v-model="filterForm.datasetId" style="width: 220px" @change="refreshAnalytics">
            <el-option
              v-for="dataset in datasets"
              :key="dataset.datasetId"
              :label="dataset.datasetName"
              :value="dataset.datasetId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="字段">
          <el-input v-model="filterForm.field" placeholder="例如 company_name" />
        </el-form-item>
        <el-form-item label="操作符">
          <el-select v-model="filterForm.operator" style="width: 120px">
            <el-option label="LIKE" value="LIKE" />
            <el-option label="EQ" value="EQ" />
            <el-option label="GT" value="GT" />
            <el-option label="LT" value="LT" />
          </el-select>
        </el-form-item>
        <el-form-item label="值">
          <el-input v-model="filterForm.value" placeholder="例如 示例科技" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="executeFilter">条件查询</el-button>
        </el-form-item>
      </el-form>
      <el-input v-model="filterForm.sql" type="textarea" :rows="4" />
      <el-button class="notice-box" type="success" @click="executeSqlQuery">执行 SQL</el-button>
    </el-card>

    <section class="two-column-grid">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>基础统计</span>
            <el-tag v-if="summary">{{ summary.recordCount }} 条</el-tag>
          </div>
        </template>
        <div v-if="summary" class="plain-list">
          <div>记录数：{{ summary.recordCount }}</div>
          <div>空值数：{{ summary.nullCount }}</div>
          <div>重复数：{{ summary.duplicateCount }}</div>
        </div>
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

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>查询结果</span>
          <el-tag>{{ resultRows.length }} 行</el-tag>
        </div>
      </template>
      <el-table :data="resultRows" stripe>
        <el-table-column
          v-for="column in Object.keys(resultRows[0] || {})"
          :key="column"
          :prop="column"
          :label="column"
        />
      </el-table>
    </el-card>
  </div>
</template>

