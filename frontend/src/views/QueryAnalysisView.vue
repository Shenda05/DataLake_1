<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import * as echarts from 'echarts';
import { useRouter } from 'vue-router';
import { isAuthExpiredError } from '../api/client';
import {
  exportFilterQuery,
  exportSqlQuery,
  filterQuery,
  getAnalysisCharts,
  getAnalysisSummary,
  listDatasets,
  listMetadata,
  queryEcommerceMetric,
  queryIntegration,
  saveIntegrationResult,
  type BusinessDomain,
  sqlQuery,
  type DatasetSummary,
  type IntegrationResponse,
  type MetaField
} from '../api/platform';
import { useAuthStore } from '../stores/auth';

const chartRef = ref<HTMLDivElement | null>(null);
const metricChartRef = ref<HTMLDivElement | null>(null);
const router = useRouter();
const authStore = useAuthStore();
const activeTab = ref<'base' | 'metric' | 'integration'>('base');
const datasets = ref<DatasetSummary[]>([]);
const metadata = ref<MetaField[]>([]);
const leftMetadata = ref<MetaField[]>([]);
const rightMetadata = ref<MetaField[]>([]);
const summary = ref({
  datasetId: 0,
  recordCount: 0,
  nullCount: 0,
  duplicateCount: 0
});
const tableData = ref<Record<string, unknown>[]>([]);
const chartData = ref<{ name: string; value: number }[]>([]);
const metricData = ref<{ name: string; value: number }[]>([]);
const metricTable = ref<Record<string, unknown>[]>([]);
const metricDescription = ref('');
const integrationResult = ref<IntegrationResponse | null>(null);
const integrationSaving = ref(false);
const resultColumns = computed(() => Object.keys(tableData.value[0] || {}));
const metricColumns = computed(() => Object.keys(metricTable.value[0] || {}));
const integrationColumns = computed(() => integrationResult.value?.columns || []);
const canExportQuery = computed(() => authStore.hasAction('query.export'));
const lastQueryMode = ref<'FILTER' | 'SQL'>('FILTER');
const businessDomainOptions: Array<{ label: string; value: BusinessDomain }> = [
  { label: '用户域', value: 'USER' },
  { label: '商品域', value: 'PRODUCT' },
  { label: '交易域', value: 'TRADE' },
  { label: '支付域', value: 'PAYMENT' },
  { label: '库存域', value: 'INVENTORY' },
  { label: '评价域', value: 'REVIEW' },
  { label: '行为日志域', value: 'BEHAVIOR_LOG' }
];
const form = reactive({
  datasetId: undefined as number | undefined,
  field: '',
  operator: 'LIKE',
  value: '',
  sql: 'SELECT * FROM dataset LIMIT 20'
});
const metricForm = reactive({
  datasetId: undefined as number | undefined,
  metricType: 'ORDER_TREND' as 'ORDER_TREND' | 'SALES_TREND' | 'TOP_PRODUCTS' | 'CATEGORY_SHARE' | 'LOW_STOCK',
  timeField: '',
  valueField: '',
  categoryField: '',
  productField: '',
  quantityField: '',
  stockThreshold: 10
});
const integrationForm = reactive({
  leftDatasetId: undefined as number | undefined,
  rightDatasetId: undefined as number | undefined,
  mode: 'JOIN' as 'JOIN' | 'UNION',
  leftField: '',
  rightField: '',
  limit: 200,
  outputDatasetName: '',
  outputBusinessDomain: 'TRADE' as BusinessDomain
});
// [旧通用版共用] 条件查询与 SQL 查询能力保留，仅在同页增加电商指标与集成子标签。
let chart: echarts.ECharts | null = null;
let metricChart: echarts.ECharts | null = null;

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

async function renderMetricChart() {
  await nextTick();
  if (!metricChartRef.value) return;
  if (!metricChart) {
    metricChart = echarts.init(metricChartRef.value);
  }
  if (metricForm.metricType === 'CATEGORY_SHARE') {
    metricChart.setOption({
      tooltip: { trigger: 'item' },
      legend: { bottom: 0 },
      series: [
        {
          type: 'pie',
          radius: ['42%', '72%'],
          data: metricData.value.map((item) => ({ name: item.name, value: item.value })),
          itemStyle: { borderRadius: 6 }
        }
      ]
    }, { notMerge: true });
    return;
  }
  metricChart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: metricData.value.map((item) => item.name) },
    yAxis: { type: 'value' },
    series: [
      {
        type: metricForm.metricType === 'TOP_PRODUCTS' || metricForm.metricType === 'LOW_STOCK' ? 'bar' : 'line',
        smooth: metricForm.metricType === 'ORDER_TREND' || metricForm.metricType === 'SALES_TREND',
        data: metricData.value.map((item) => item.value),
        itemStyle: { color: '#1f8f6b' }
      }
    ]
  }, { notMerge: true });
}

async function loadBaseData() {
  datasets.value = await listDatasets();
  if (datasets.value.length > 0 && !form.datasetId) {
    form.datasetId = datasets.value[0].datasetId;
  }
  if (datasets.value.length > 0 && !metricForm.datasetId) {
    metricForm.datasetId = datasets.value[0].datasetId;
  }
  if (datasets.value.length > 1) {
    integrationForm.leftDatasetId = integrationForm.leftDatasetId || datasets.value[0].datasetId;
    integrationForm.rightDatasetId = integrationForm.rightDatasetId || datasets.value[1].datasetId;
  } else if (datasets.value.length === 1) {
    integrationForm.leftDatasetId = datasets.value[0].datasetId;
    integrationForm.rightDatasetId = datasets.value[0].datasetId;
  }
  if (!integrationForm.outputDatasetName) {
    integrationForm.outputDatasetName = buildIntegrationOutputName();
  }
  if (!integrationForm.outputBusinessDomain) {
    integrationForm.outputBusinessDomain = resolveDatasetBusinessDomain(integrationForm.leftDatasetId);
  }
  await Promise.all([loadDatasetAnalysis(), loadIntegrationMetadata()]);
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
  if (!canExportQuery.value) {
    ElMessage.warning('当前角色没有查询结果导出权限');
    return;
  }
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

async function runMetricQuery() {
  if (!metricForm.datasetId) {
    ElMessage.warning('请选择数据集');
    return;
  }
  try {
    const result = await queryEcommerceMetric({
      datasetId: metricForm.datasetId,
      metricType: metricForm.metricType,
      timeField: metricForm.timeField || undefined,
      valueField: metricForm.valueField || undefined,
      categoryField: metricForm.categoryField || undefined,
      productField: metricForm.productField || undefined,
      quantityField: metricForm.quantityField || undefined,
      stockThreshold: metricForm.stockThreshold
    });
    metricData.value = result.chart;
    metricTable.value = result.table;
    metricDescription.value = result.description;
    await renderMetricChart();
  } catch (error) {
    ElMessage.error(`电商指标查询失败: ${(error as Error).message}`);
  }
}

async function loadIntegrationMetadata() {
  if (integrationForm.leftDatasetId) {
    leftMetadata.value = await listMetadata(integrationForm.leftDatasetId);
    if (!integrationForm.leftField && leftMetadata.value.length > 0) {
      integrationForm.leftField = leftMetadata.value[0].fieldName;
    }
  }
  if (integrationForm.rightDatasetId) {
    rightMetadata.value = await listMetadata(integrationForm.rightDatasetId);
    if (!integrationForm.rightField && rightMetadata.value.length > 0) {
      integrationForm.rightField = rightMetadata.value[0].fieldName;
    }
  }
}

async function runIntegration() {
  if (!integrationForm.leftDatasetId || !integrationForm.rightDatasetId) {
    ElMessage.warning('请先选择左右数据集');
    return;
  }
  if (integrationForm.mode === 'JOIN' && (!integrationForm.leftField || !integrationForm.rightField)) {
    ElMessage.warning('JOIN 模式需要选择左右关联字段');
    return;
  }
  try {
    integrationResult.value = await queryIntegration({
      leftDatasetId: integrationForm.leftDatasetId,
      rightDatasetId: integrationForm.rightDatasetId,
      mode: integrationForm.mode,
      leftField: integrationForm.leftField || undefined,
      rightField: integrationForm.rightField || undefined,
      limit: integrationForm.limit
    });
    if (!integrationForm.outputDatasetName.trim()) {
      integrationForm.outputDatasetName = buildIntegrationOutputName();
    }
    integrationForm.outputBusinessDomain = resolveDatasetBusinessDomain(integrationForm.leftDatasetId);
  } catch (error) {
    ElMessage.error(`数据集成失败: ${(error as Error).message}`);
  }
}

async function saveIntegrationAsDataset() {
  if (!integrationResult.value) {
    ElMessage.warning('请先执行数据集成');
    return;
  }
  if (!integrationForm.outputDatasetName.trim()) {
    ElMessage.warning('请填写输出数据集名称');
    return;
  }
  integrationSaving.value = true;
  try {
    const created = await saveIntegrationResult({
      leftDatasetId: integrationForm.leftDatasetId!,
      rightDatasetId: integrationForm.rightDatasetId!,
      mode: integrationForm.mode,
      leftField: integrationForm.leftField || undefined,
      rightField: integrationForm.rightField || undefined,
      limit: integrationForm.limit,
      outputDatasetName: integrationForm.outputDatasetName.trim(),
      outputBusinessDomain: integrationForm.outputBusinessDomain
    });
    ElMessage.success(`集成结果已保存为数据集：${created.datasetName}`);
    await router.push({ name: 'datasets', query: { datasetId: String(created.datasetId) } });
  } catch (error) {
    ElMessage.error(`保存集成结果失败: ${(error as Error).message}`);
  } finally {
    integrationSaving.value = false;
  }
}

function resolveDatasetBusinessDomain(datasetId?: number) {
  return datasets.value.find((item) => item.datasetId === datasetId)?.businessDomain || 'TRADE';
}

function buildIntegrationOutputName() {
  const left = datasets.value.find((item) => item.datasetId === integrationForm.leftDatasetId)?.datasetName || 'left';
  const right = datasets.value.find((item) => item.datasetId === integrationForm.rightDatasetId)?.datasetName || 'right';
  const suffix = new Date().toISOString().slice(0, 19).replace(/[-:T]/g, '');
  return `${left}_${integrationForm.mode}_${right}_${suffix}`;
}

watch(
  () => form.datasetId,
  () => {
    void loadDatasetAnalysis();
  }
);

watch(
  () => [integrationForm.leftDatasetId, integrationForm.rightDatasetId],
  () => {
    integrationForm.outputBusinessDomain = resolveDatasetBusinessDomain(integrationForm.leftDatasetId);
    if (!integrationForm.outputDatasetName.trim()) {
      integrationForm.outputDatasetName = buildIntegrationOutputName();
    }
    void loadIntegrationMetadata();
  }
);

watch(
  () => activeTab.value,
  (tab) => {
    if (tab === 'metric') {
      void renderMetricChart();
    }
    if (tab === 'base') {
      void renderChart();
    }
  }
);

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

onBeforeUnmount(() => {
  chart?.dispose();
  metricChart?.dispose();
});
</script>

<template>
  <div class="page-grid">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>电商查询分析</span>
          <el-tag type="success">Real Query</el-tag>
        </div>
      </template>
      <el-tabs v-model="activeTab">
        <el-tab-pane label="条件/SQL 查询" name="base">
          <el-form inline>
            <el-form-item label="数据集">
              <el-select v-model="form.datasetId" placeholder="请选择数据集">
                <el-option
                  v-for="dataset in datasets"
                  :key="dataset.datasetId"
                  :label="`${dataset.datasetName} (${dataset.businessDomain})`"
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
              <el-input v-model="form.value" placeholder="例如 已支付" />
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
          <el-alert
            v-if="!canExportQuery"
            class="notice-box"
            title="当前角色只有查询查看权限，不能导出查询结果。"
            type="info"
            :closable="false"
          />

          <section class="stat-grid notice-box">
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

          <section class="two-column-grid notice-box">
            <el-card shadow="never">
              <template #header>
                <div class="card-header">
                  <span>查询结果</span>
                  <div>
                    <el-button link type="primary" :disabled="!canExportQuery" @click="exportRows('csv')">导出 CSV</el-button>
                    <el-button link type="primary" :disabled="!canExportQuery" @click="exportRows('json')">导出 JSON</el-button>
                    <el-button link type="primary" :disabled="!canExportQuery" @click="exportRows('xlsx')">导出 Excel</el-button>
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
                  <span>基础分布图</span>
                  <el-tag>ECharts</el-tag>
                </div>
              </template>
              <div ref="chartRef" class="chart-box"></div>
            </el-card>
          </section>
        </el-tab-pane>

        <el-tab-pane label="电商指标" name="metric">
          <el-form inline>
            <el-form-item label="数据集">
              <el-select v-model="metricForm.datasetId" placeholder="请选择数据集">
                <el-option
                  v-for="dataset in datasets"
                  :key="dataset.datasetId"
                  :label="`${dataset.datasetName} (${dataset.businessDomain})`"
                  :value="dataset.datasetId"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="指标类型">
              <el-select v-model="metricForm.metricType">
                <el-option label="订单量趋势" value="ORDER_TREND" />
                <el-option label="销售额趋势" value="SALES_TREND" />
                <el-option label="热销商品排行" value="TOP_PRODUCTS" />
                <el-option label="商品分类占比" value="CATEGORY_SHARE" />
                <el-option label="库存预警视图" value="LOW_STOCK" />
              </el-select>
            </el-form-item>
            <el-form-item label="时间字段">
              <el-input v-model="metricForm.timeField" placeholder="可留空自动识别" />
            </el-form-item>
            <el-form-item label="数值字段">
              <el-input v-model="metricForm.valueField" placeholder="可留空自动识别" />
            </el-form-item>
            <el-form-item label="分类字段">
              <el-input v-model="metricForm.categoryField" placeholder="可留空自动识别" />
            </el-form-item>
            <el-form-item label="商品字段">
              <el-input v-model="metricForm.productField" placeholder="可留空自动识别" />
            </el-form-item>
            <el-form-item label="销量字段">
              <el-input v-model="metricForm.quantityField" placeholder="可留空自动识别" />
            </el-form-item>
            <el-form-item label="库存阈值">
              <el-input-number v-model="metricForm.stockThreshold" :min="0" :max="9999" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="runMetricQuery">查询电商指标</el-button>
            </el-form-item>
          </el-form>

          <el-alert
            class="notice-box"
            :title="metricDescription || '支持订单量趋势、销售额趋势、热销商品、分类占比和库存预警查询。'"
            type="success"
            :closable="false"
          />

          <section class="two-column-grid notice-box">
            <el-card shadow="never">
              <template #header>
                <div class="card-header">
                  <span>指标图表</span>
                  <el-tag>Metric</el-tag>
                </div>
              </template>
              <div ref="metricChartRef" class="chart-box"></div>
            </el-card>
            <el-card shadow="never">
              <template #header>
                <div class="card-header">
                  <span>指标明细</span>
                  <el-tag>{{ metricData.length }} 条</el-tag>
                </div>
              </template>
              <el-table :data="metricTable" stripe>
                <el-table-column
                  v-for="column in metricColumns"
                  :key="column"
                  :prop="column"
                  :label="column"
                />
              </el-table>
            </el-card>
          </section>
        </el-tab-pane>

        <el-tab-pane label="数据集成" name="integration">
          <el-form inline>
            <el-form-item label="左数据集">
              <el-select v-model="integrationForm.leftDatasetId" placeholder="请选择左侧数据集">
                <el-option
                  v-for="dataset in datasets"
                  :key="dataset.datasetId"
                  :label="`${dataset.datasetName} (${dataset.businessDomain})`"
                  :value="dataset.datasetId"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="右数据集">
              <el-select v-model="integrationForm.rightDatasetId" placeholder="请选择右侧数据集">
                <el-option
                  v-for="dataset in datasets"
                  :key="dataset.datasetId"
                  :label="`${dataset.datasetName} (${dataset.businessDomain})`"
                  :value="dataset.datasetId"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="集成模式">
              <el-select v-model="integrationForm.mode">
                <el-option label="JOIN（关联）" value="JOIN" />
                <el-option label="UNION（合并）" value="UNION" />
              </el-select>
            </el-form-item>
            <el-form-item label="左关联字段">
              <el-select v-model="integrationForm.leftField" :disabled="integrationForm.mode === 'UNION'">
                <el-option v-for="field in leftMetadata" :key="field.fieldId" :label="field.fieldName" :value="field.fieldName" />
              </el-select>
            </el-form-item>
            <el-form-item label="右关联字段">
              <el-select v-model="integrationForm.rightField" :disabled="integrationForm.mode === 'UNION'">
                <el-option v-for="field in rightMetadata" :key="field.fieldId" :label="field.fieldName" :value="field.fieldName" />
              </el-select>
            </el-form-item>
            <el-form-item label="结果上限">
              <el-input-number v-model="integrationForm.limit" :min="1" :max="1000" />
            </el-form-item>
            <el-form-item label="输出数据集名称">
              <el-input v-model="integrationForm.outputDatasetName" placeholder="例如 商品订单集成结果_20260327" />
            </el-form-item>
            <el-form-item label="输出业务域">
              <el-select v-model="integrationForm.outputBusinessDomain" placeholder="请选择业务域">
                <el-option
                  v-for="domain in businessDomainOptions"
                  :key="domain.value"
                  :label="domain.label"
                  :value="domain.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="runIntegration">执行数据集成</el-button>
            </el-form-item>
            <el-form-item>
              <el-button
                type="success"
                plain
                :disabled="!integrationResult"
                :loading="integrationSaving"
                @click="saveIntegrationAsDataset"
              >
                保存为新数据集
              </el-button>
            </el-form-item>
          </el-form>

          <el-alert
            class="notice-box"
            title="MVP 支持 JOIN/UNION，可用于用户+订单、商品+订单、商品+库存三类核心集成。"
            type="info"
            :closable="false"
          />
          <el-alert
            class="notice-box"
            title="保存动作仅落库当前集成结果快照（受“结果上限”限制）。"
            type="warning"
            :closable="false"
          />

          <el-card shadow="never" class="notice-box">
            <template #header>
              <div class="card-header">
                <span>集成结果</span>
                <el-tag>{{ integrationResult?.total || 0 }} 条</el-tag>
              </div>
            </template>
            <el-table :data="integrationResult?.records || []" stripe>
              <el-table-column
                v-for="column in integrationColumns"
                :key="column"
                :prop="column"
                :label="column"
              />
            </el-table>
          </el-card>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>
