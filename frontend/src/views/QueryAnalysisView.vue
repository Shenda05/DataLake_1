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
  getAnalysisSummary,
  listDatasets,
  listMetadata,
  queryAnalysisCharts,
  queryEcommerceMetric,
  queryIntegration,
  saveIntegrationResult,
  type BusinessDomain,
  sqlQueryPage,
  type DatasetSummary,
  type IntegrationResponse,
  type MetaField,
  type QueryCondition,
  type QueryOperator
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
const currentQueryMatchCount = computed(() => resultPage.total);
const currentQueryPageCount = computed(() => tableData.value.length);
const canExportQuery = computed(() => authStore.hasAction('query.export'));
const linkMetricWithQuery = ref(true);
const metricTimeRange = ref<[string, string] | []>([]);
const baseChartHint = ref('基础分布图（全量）');
const lastQueryMode = ref<'FILTER' | 'SQL'>('FILTER');
const resultPage = reactive({
  pageNum: 1,
  pageSize: 20,
  total: 0
});
const sqlResultColumns = ref<string[]>([]);
const filterOperators: Array<{ label: string; value: QueryOperator }> = [
  { label: 'LIKE', value: 'LIKE' },
  { label: 'EQ', value: 'EQ' },
  { label: 'GT', value: 'GT' },
  { label: 'GTE', value: 'GTE' },
  { label: 'LT', value: 'LT' },
  { label: 'LTE', value: 'LTE' },
  { label: 'BETWEEN', value: 'BETWEEN' },
  { label: 'TIME_RANGE', value: 'TIME_RANGE' }
];
const businessDomainOptions: Array<{ label: string; value: BusinessDomain }> = [
  { label: '用户域', value: 'USER' },
  { label: '商品域', value: 'PRODUCT' },
  { label: '交易域', value: 'TRADE' },
  { label: '支付域', value: 'PAYMENT' },
  { label: '库存域', value: 'INVENTORY' },
  { label: '评价域', value: 'REVIEW' },
  { label: '行为日志域', value: 'BEHAVIOR_LOG' }
];
type FilterConditionModel = {
  field: string;
  operator: QueryOperator;
  value: string;
  valueTo: string;
};
function createFilterCondition(defaultField = ''): FilterConditionModel {
  return {
    field: defaultField,
    operator: 'LIKE',
    value: '',
    valueTo: ''
  };
}
const form = reactive({
  datasetId: undefined as number | undefined,
  conditions: [createFilterCondition()] as FilterConditionModel[],
  logic: 'AND' as 'AND' | 'OR',
  chartDimensionField: '',
  sortField: '',
  sortOrder: 'ASC' as 'ASC' | 'DESC',
  sql: 'SELECT * FROM dataset LIMIT 20',
  sqlSortField: '',
  sqlSortOrder: 'ASC' as 'ASC' | 'DESC'
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

const metricTemplateHint = computed(() => {
  switch (metricForm.metricType) {
    case 'ORDER_TREND':
      return '订单量趋势：按时间聚合订单条数';
    case 'SALES_TREND':
      return '销售额趋势：按时间聚合销售金额';
    case 'TOP_PRODUCTS':
      return '热销商品排行：按销量/频次统计 Top5';
    case 'LOW_STOCK':
      return '库存预警：展示低于阈值的商品';
    case 'CATEGORY_SHARE':
      return '商品分类占比：按分类条数统计';
    default:
      return '';
  }
});

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
  const defaultField = metadata.value[0]?.fieldName || '';
  if (!form.conditions.length) {
    form.conditions.push(createFilterCondition(defaultField));
  }
  for (const condition of form.conditions) {
    if (!condition.field) {
      condition.field = defaultField;
    }
  }
  if (!form.sortField) {
    form.sortField = defaultField;
  }
  if (!form.chartDimensionField) {
    form.chartDimensionField = defaultField;
  }
  summary.value = await getAnalysisSummary(form.datasetId);
  await refreshBaseChart();
}

function isRangeOperator(operator: QueryOperator) {
  return operator === 'BETWEEN' || operator === 'TIME_RANGE';
}

function buildFilterConditions(): QueryCondition[] {
  return form.conditions
    .filter((condition) => condition.field && condition.operator)
    .map((condition) => ({
      field: condition.field,
      operator: condition.operator,
      value: condition.value,
      valueTo: condition.valueTo || undefined
    }))
    .filter((condition) => {
      if (isRangeOperator(condition.operator as QueryOperator)) {
        return Boolean(condition.value && condition.valueTo);
      }
      return Boolean(condition.value);
    });
}

function guessTimeField(datasetMeta: MetaField[]) {
  const candidates = ['order_time', 'order_date', 'created_at', 'create_time'];
  const lowered = new Map(datasetMeta.map((item) => [item.fieldName.toLowerCase(), item.fieldName]));
  for (const candidate of candidates) {
    const matched = lowered.get(candidate);
    if (matched) {
      return matched;
    }
  }
  return '';
}

function upsertTimeRangeCondition(conditions: QueryCondition[], timeField: string, range: [string, string]) {
  const [start, end] = range;
  const next = conditions.filter(
    (item) =>
      !(
        item.field.toLowerCase() === timeField.toLowerCase() &&
        ['TIME_RANGE', 'BETWEEN'].includes(String(item.operator).toUpperCase())
      )
  );
  next.push({
    field: timeField,
    operator: 'TIME_RANGE',
    value: start,
    valueTo: end
  });
  return next;
}

async function refreshBaseChart(conditions?: QueryCondition[]) {
  if (!form.datasetId) return;
  const appliedConditions = conditions ?? buildFilterConditions();
  chartData.value = await queryAnalysisCharts({
    datasetId: form.datasetId,
    dimensionField: form.chartDimensionField || undefined,
    conditions: appliedConditions,
    logic: form.logic
  });
  baseChartHint.value = appliedConditions.length ? '当前筛选条件下分布图' : '基础分布图（全量）';
  await renderChart();
}

async function buildMetricQueryContext() {
  let datasetId = metricForm.datasetId;
  if (linkMetricWithQuery.value && form.datasetId) {
    if (metricForm.datasetId !== form.datasetId) {
      metricForm.datasetId = form.datasetId;
      ElMessage.info('已联动为当前查询数据集');
    }
    datasetId = form.datasetId;
  }
  if (!datasetId) {
    throw new Error('请选择数据集');
  }
  const targetMetadata =
    datasetId === form.datasetId && metadata.value.length > 0
      ? metadata.value
      : await listMetadata(datasetId);
  let conditions = linkMetricWithQuery.value ? buildFilterConditions() : [];
  if (metricTimeRange.value.length === 2) {
    const resolvedTimeField = (metricForm.timeField || guessTimeField(targetMetadata)).trim();
    if (!resolvedTimeField) {
      throw new Error('请先填写时间字段，或选择包含标准时间字段的数据集');
    }
    conditions = upsertTimeRangeCondition(conditions, resolvedTimeField, metricTimeRange.value as [string, string]);
    if (!metricForm.timeField) {
      metricForm.timeField = resolvedTimeField;
    }
  }
  return {
    datasetId,
    conditions,
    logic: linkMetricWithQuery.value ? form.logic : 'AND' as const
  };
}

function addFilterCondition() {
  form.conditions.push(createFilterCondition(metadata.value[0]?.fieldName || ''));
}

function removeFilterCondition(index: number) {
  if (form.conditions.length <= 1) {
    form.conditions.splice(0, 1, createFilterCondition(metadata.value[0]?.fieldName || ''));
    return;
  }
  form.conditions.splice(index, 1);
}

function handleConditionOperatorChange(condition: FilterConditionModel) {
  condition.value = '';
  condition.valueTo = '';
}

async function runFilterQuery(pageNum = 1, refreshAnalysis = false) {
  if (!form.datasetId) {
    ElMessage.warning('请选择数据集');
    return;
  }
  const conditions = buildFilterConditions();
  if (!conditions.length) {
    ElMessage.warning('请至少填写一条有效筛选条件');
    return;
  }
  try {
    const result = await filterQuery({
      datasetId: form.datasetId,
      conditions,
      logic: form.logic,
      sortField: form.sortField || undefined,
      sortOrder: form.sortOrder,
      pageNum,
      pageSize: resultPage.pageSize
    });
    lastQueryMode.value = 'FILTER';
    tableData.value = result.records;
    resultPage.pageNum = result.pageNum;
    resultPage.pageSize = result.pageSize;
    resultPage.total = result.total;
    sqlResultColumns.value = [];
    if (pageNum === 1 || refreshAnalysis) {
      await refreshBaseChart(conditions);
    }
    if (refreshAnalysis) {
      summary.value = await getAnalysisSummary(form.datasetId!);
    }
  } catch (error) {
    ElMessage.error(`条件查询失败: ${(error as Error).message}`);
  }
}

async function runSqlQuery(pageNum = 1) {
  if (!form.datasetId || !form.sql) {
    ElMessage.warning('请选择数据集并输入 SQL');
    return;
  }
  try {
    lastQueryMode.value = 'SQL';
    const result = await sqlQueryPage({
      datasetId: form.datasetId,
      sql: form.sql,
      sortField: form.sqlSortField || undefined,
      sortOrder: form.sqlSortOrder,
      pageNum,
      pageSize: resultPage.pageSize
    });
    tableData.value = result.records;
    resultPage.pageNum = result.pageNum;
    resultPage.pageSize = result.pageSize;
    resultPage.total = result.total;
    if (result.records.length) {
      sqlResultColumns.value = Object.keys(result.records[0] || {});
    }
  } catch (error) {
    ElMessage.error(`SQL 查询失败: ${(error as Error).message}`);
  }
}

function handleResultPageChange(pageNum: number) {
  if (lastQueryMode.value === 'SQL') {
    void runSqlQuery(pageNum);
    return;
  }
  void runFilterQuery(pageNum, false);
}

function handleResultPageSizeChange(pageSize: number) {
  resultPage.pageSize = pageSize;
  resultPage.pageNum = 1;
  if (lastQueryMode.value === 'SQL') {
    void runSqlQuery(1);
    return;
  }
  void runFilterQuery(1, false);
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
              conditions: buildFilterConditions(),
              logic: form.logic,
              sortField: form.sortField || undefined,
              sortOrder: form.sortOrder,
              exportScope: 'ALL'
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
  try {
    const context = await buildMetricQueryContext();
    const result = await queryEcommerceMetric({
      datasetId: context.datasetId,
      metricType: metricForm.metricType,
      timeField: metricForm.timeField || undefined,
      valueField: metricForm.valueField || undefined,
      categoryField: metricForm.categoryField || undefined,
      productField: metricForm.productField || undefined,
      quantityField: metricForm.quantityField || undefined,
      stockThreshold: metricForm.stockThreshold,
      conditions: context.conditions,
      logic: context.logic
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
    resultPage.pageNum = 1;
    resultPage.total = 0;
    tableData.value = [];
    form.sortField = '';
    form.chartDimensionField = '';
    form.sqlSortField = '';
    sqlResultColumns.value = [];
    baseChartHint.value = '基础分布图（全量）';
    if (linkMetricWithQuery.value) {
      metricForm.datasetId = form.datasetId;
    }
    if (!form.conditions.length) {
      form.conditions.push(createFilterCondition());
    }
    for (const condition of form.conditions) {
      condition.field = '';
      condition.value = '';
      condition.valueTo = '';
    }
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
  () => linkMetricWithQuery.value,
  (enabled) => {
    if (enabled && form.datasetId) {
      metricForm.datasetId = form.datasetId;
    }
  }
);

watch(
  () => form.chartDimensionField,
  () => {
    if (!form.datasetId) {
      return;
    }
    void refreshBaseChart();
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
              <el-select v-model="form.datasetId" class="form-select-xl" placeholder="请选择数据集">
                <el-option
                  v-for="dataset in datasets"
                  :key="dataset.datasetId"
                  :label="`${dataset.datasetName} (${dataset.businessDomain})`"
                  :value="dataset.datasetId"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="条件逻辑">
              <el-select v-model="form.logic" class="form-select-narrow">
                <el-option label="AND（且）" value="AND" />
                <el-option label="OR（或）" value="OR" />
              </el-select>
            </el-form-item>
            <el-form-item label="图表维度字段">
              <el-select v-model="form.chartDimensionField" class="form-select-wide" clearable placeholder="默认首列字段">
                <el-option
                  v-for="column in metadata"
                  :key="`chart-${column.fieldId}`"
                  :label="column.fieldName"
                  :value="column.fieldName"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="排序字段">
              <el-select v-model="form.sortField" class="form-select-wide" clearable placeholder="默认 row_id">
                <el-option
                  v-for="column in metadata"
                  :key="column.fieldId"
                  :label="column.fieldName"
                  :value="column.fieldName"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="排序方向">
              <el-select v-model="form.sortOrder" class="form-select-narrow">
                <el-option label="ASC" value="ASC" />
                <el-option label="DESC" value="DESC" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="runFilterQuery(1, true)">执行条件查询</el-button>
            </el-form-item>
          </el-form>
          <div class="notice-box">
            <el-button link type="primary" @click="addFilterCondition">新增条件</el-button>
          </div>
          <div
            v-for="(condition, index) in form.conditions"
            :key="`condition-${index}`"
            class="action-row"
          >
            <el-form inline>
              <el-form-item :label="`条件 ${index + 1} 字段`">
                <el-select v-model="condition.field" class="form-select-wide" placeholder="请选择字段">
                  <el-option
                    v-for="column in metadata"
                    :key="column.fieldId"
                    :label="column.fieldName"
                    :value="column.fieldName"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="操作符">
                <el-select v-model="condition.operator" class="form-select-medium" @change="handleConditionOperatorChange(condition)">
                  <el-option
                    v-for="item in filterOperators"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </el-form-item>
              <el-form-item
                v-if="condition.operator === 'BETWEEN' || condition.operator === 'TIME_RANGE'"
                label="起止值"
              >
                <el-date-picker
                  v-model="condition.value"
                  type="datetime"
                  value-format="YYYY-MM-DD HH:mm:ss"
                  placeholder="开始时间"
                />
                <span style="margin: 0 8px;">到</span>
                <el-date-picker
                  v-model="condition.valueTo"
                  type="datetime"
                  value-format="YYYY-MM-DD HH:mm:ss"
                  placeholder="结束时间"
                />
              </el-form-item>
              <el-form-item v-else label="条件值">
                <el-input v-model="condition.value" class="form-input-wide" placeholder="请输入条件值" />
              </el-form-item>
              <el-form-item>
                <el-button
                  link
                  type="danger"
                  :disabled="form.conditions.length === 1"
                  @click="removeFilterCondition(index)"
                >
                  删除
                </el-button>
              </el-form-item>
            </el-form>
          </div>
          <el-input
            v-model="form.sql"
            type="textarea"
            :rows="4"
            placeholder="SELECT * FROM dataset LIMIT 20"
          />
          <div class="action-row">
            <el-input v-model="form.sqlSortField" class="form-input-medium" placeholder="SQL 排序字段（可选）" />
            <el-select v-model="form.sqlSortOrder" class="form-select-narrow">
              <el-option label="ASC" value="ASC" />
              <el-option label="DESC" value="DESC" />
            </el-select>
            <el-button type="primary" plain @click="runSqlQuery(1)">执行 SQL 查询</el-button>
          </div>
          <div v-if="sqlResultColumns.length" class="notice-box">
            <el-tag
              v-for="column in sqlResultColumns"
              :key="`sql-column-${column}`"
              style="margin-right: 8px; margin-bottom: 6px;"
            >
              {{ column }}
            </el-tag>
          </div>
          <el-alert
            v-if="!canExportQuery"
            class="notice-box"
            title="当前角色只有查询查看权限，不能导出查询结果。"
            type="info"
            :closable="false"
          />
          <el-alert
            v-else
            class="notice-box"
            title="导出按当前筛选条件导出全量结果（最多 20000 条，建议先收敛筛选条件）。"
            type="warning"
            :closable="false"
          />

          <section class="stat-grid notice-box">
            <el-card shadow="hover">
              <p class="stat-label">数据集总记录数</p>
              <p class="stat-value">{{ summary.recordCount }}</p>
            </el-card>
            <el-card shadow="hover">
              <p class="stat-label">当前命中记录数</p>
              <p class="stat-value">{{ currentQueryMatchCount }}</p>
            </el-card>
            <el-card shadow="hover">
              <p class="stat-label">数据集空值数</p>
              <p class="stat-value">{{ summary.nullCount }}</p>
            </el-card>
            <el-card shadow="hover">
              <p class="stat-label">数据集重复行数</p>
              <p class="stat-value">{{ summary.duplicateCount }}</p>
            </el-card>
          </section>

          <section class="two-column-grid notice-box">
            <el-card shadow="never">
              <template #header>
                <div class="card-header">
                  <span>查询结果</span>
                  <div>
                    <span class="inline-tip">当前页 {{ currentQueryPageCount }} 条 / 命中 {{ currentQueryMatchCount }} 条</span>
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
              <el-pagination
                class="notice-box"
                layout="total, sizes, prev, pager, next"
                :total="resultPage.total"
                :current-page="resultPage.pageNum"
                :page-size="resultPage.pageSize"
                :page-sizes="[10, 20, 50, 100, 200]"
                @current-change="handleResultPageChange"
                @size-change="handleResultPageSizeChange"
              />
            </el-card>
            <el-card shadow="never">
              <template #header>
                <div class="card-header">
                  <span>{{ baseChartHint }}</span>
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
              <el-select v-model="metricForm.datasetId" class="form-select-xl" :disabled="linkMetricWithQuery" placeholder="请选择数据集">
                <el-option
                  v-for="dataset in datasets"
                  :key="dataset.datasetId"
                  :label="`${dataset.datasetName} (${dataset.businessDomain})`"
                  :value="dataset.datasetId"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="联动当前查询条件">
              <el-switch v-model="linkMetricWithQuery" />
            </el-form-item>
            <el-form-item label="指标类型">
              <el-select v-model="metricForm.metricType" class="form-select-wide">
                <el-option label="订单量趋势" value="ORDER_TREND" />
                <el-option label="销售额趋势" value="SALES_TREND" />
                <el-option label="热销商品排行" value="TOP_PRODUCTS" />
                <el-option label="库存预警视图" value="LOW_STOCK" />
                <el-option label="商品分类占比" value="CATEGORY_SHARE" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="metricForm.metricType === 'ORDER_TREND' || metricForm.metricType === 'SALES_TREND'" label="时间字段">
              <el-input v-model="metricForm.timeField" placeholder="可留空自动识别" />
            </el-form-item>
            <el-form-item label="图表时间范围">
              <el-date-picker
                v-model="metricTimeRange"
                type="datetimerange"
                value-format="YYYY-MM-DD HH:mm:ss"
                range-separator="至"
                start-placeholder="开始时间"
                end-placeholder="结束时间"
              />
            </el-form-item>
            <el-form-item v-if="metricForm.metricType === 'SALES_TREND' || metricForm.metricType === 'LOW_STOCK'" label="数值字段">
              <el-input v-model="metricForm.valueField" placeholder="可留空自动识别" />
            </el-form-item>
            <el-form-item v-if="metricForm.metricType === 'CATEGORY_SHARE'" label="分类字段">
              <el-input v-model="metricForm.categoryField" placeholder="可留空自动识别" />
            </el-form-item>
            <el-form-item v-if="metricForm.metricType === 'TOP_PRODUCTS'" label="商品字段">
              <el-input v-model="metricForm.productField" placeholder="可留空自动识别" />
            </el-form-item>
            <el-form-item v-if="metricForm.metricType === 'TOP_PRODUCTS'" label="销量字段">
              <el-input v-model="metricForm.quantityField" placeholder="可留空自动识别" />
            </el-form-item>
            <el-form-item v-if="metricForm.metricType === 'LOW_STOCK'" label="库存阈值">
              <el-input-number v-model="metricForm.stockThreshold" :min="0" :max="9999" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="runMetricQuery">查询电商指标</el-button>
            </el-form-item>
          </el-form>

          <el-alert
            class="notice-box"
            :title="metricDescription || metricTemplateHint || '支持电商常用指标查询。'"
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
              <el-select v-model="integrationForm.leftDatasetId" class="form-select-xl" placeholder="请选择左侧数据集">
                <el-option
                  v-for="dataset in datasets"
                  :key="dataset.datasetId"
                  :label="`${dataset.datasetName} (${dataset.businessDomain})`"
                  :value="dataset.datasetId"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="右数据集">
              <el-select v-model="integrationForm.rightDatasetId" class="form-select-xl" placeholder="请选择右侧数据集">
                <el-option
                  v-for="dataset in datasets"
                  :key="dataset.datasetId"
                  :label="`${dataset.datasetName} (${dataset.businessDomain})`"
                  :value="dataset.datasetId"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="集成模式">
              <el-select v-model="integrationForm.mode" class="form-select-medium">
                <el-option label="JOIN（关联）" value="JOIN" />
                <el-option label="UNION（合并）" value="UNION" />
              </el-select>
            </el-form-item>
            <el-form-item label="左关联字段">
              <el-select v-model="integrationForm.leftField" class="form-select-wide" :disabled="integrationForm.mode === 'UNION'">
                <el-option v-for="field in leftMetadata" :key="field.fieldId" :label="field.fieldName" :value="field.fieldName" />
              </el-select>
            </el-form-item>
            <el-form-item label="右关联字段">
              <el-select v-model="integrationForm.rightField" class="form-select-wide" :disabled="integrationForm.mode === 'UNION'">
                <el-option v-for="field in rightMetadata" :key="field.fieldId" :label="field.fieldName" :value="field.fieldName" />
              </el-select>
            </el-form-item>
            <el-form-item label="结果上限">
              <el-input-number v-model="integrationForm.limit" :min="1" :max="1000" />
            </el-form-item>
            <el-form-item label="输出数据集名称">
              <el-input v-model="integrationForm.outputDatasetName" class="form-input-wide" placeholder="例如 商品订单集成结果_20260327" />
            </el-form-item>
            <el-form-item label="输出业务域">
              <el-select v-model="integrationForm.outputBusinessDomain" class="form-select-wide" placeholder="请选择业务域">
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
