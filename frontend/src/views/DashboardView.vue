<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import * as echarts from 'echarts';
import { useRouter } from 'vue-router';
import { isAuthExpiredError } from '../api/client';
import {
  getEcommerceOverview,
  getOverview,
  getRecentTasks,
  listTaskLogs,
  type TaskLogSummary
} from '../api/platform';
import { useAuthStore } from '../stores/auth';

type MenuRoute = 'dashboard' | 'data-sources' | 'imports' | 'datasets' | 'governance' | 'tasks' | 'logs' | 'queries';

const router = useRouter();
const authStore = useAuthStore();
const trendChartRef = ref<HTMLDivElement | null>(null);
const overview = ref({
  dataSources: 0,
  datasets: 0,
  newDatasetsToday: 0,
  totalTasks: 0,
  runningTasks: 0,
  successTasks: 0,
  failedTasks: 0,
  recentFailedTasks: 0
});
const ecommerce = ref({
  orderTrend: [] as { day: string; value: number }[],
  salesTrend: [] as { day: string; value: number }[],
  topProducts: [] as { name: string; value: number }[],
  lowStockCount: 0
});
const recentTasks = ref<{ taskId: number; taskName: string; taskType: string; status: string; nextRunTime?: string | null }[]>([]);
const recentLogs = ref<TaskLogSummary[]>([]);
const lastSyncedAt = ref('');
// [待完善增强] 后续可增加指标字段映射向导与异常数据质量提示卡片。
let refreshTimer: ReturnType<typeof window.setInterval> | null = null;
let trendChart: echarts.ECharts | null = null;

const successRate = computed(() => {
  const total = overview.value.successTasks + overview.value.failedTasks;
  if (total === 0) {
    return '100%';
  }
  return `${Math.round((overview.value.successTasks / total) * 100)}%`;
});

const orderTotal7d = computed(() => Math.round(ecommerce.value.orderTrend.reduce((sum, item) => sum + item.value, 0)));
const salesTotal7d = computed(() => Math.round(ecommerce.value.salesTrend.reduce((sum, item) => sum + item.value, 0) * 100) / 100);
const dashboardStats = computed(() => [
  { label: '数据集总数', value: overview.value.datasets, target: 'datasets' as MenuRoute, hint: '点击查看数据集列表' },
  { label: '数据源总数', value: overview.value.dataSources, target: 'data-sources' as MenuRoute, hint: '点击查看数据源管理' },
  { label: '调度任务数', value: overview.value.totalTasks, target: 'tasks' as MenuRoute, hint: '点击查看任务调度' },
  { label: '最近失败任务数', value: overview.value.recentFailedTasks, target: 'logs' as MenuRoute, hint: '点击查看失败日志' },
  { label: '近 7 天订单量', value: orderTotal7d.value, target: 'queries' as MenuRoute, hint: '点击进入查询分析' },
  { label: '近 7 天销售额', value: salesTotal7d.value, target: 'queries' as MenuRoute, hint: '点击进入查询分析' },
  { label: '低库存商品数', value: ecommerce.value.lowStockCount, target: 'queries' as MenuRoute, hint: '点击查看库存分析' },
  { label: '任务成功率', value: successRate.value, target: 'tasks' as MenuRoute, hint: '点击查看任务状态' }
]);

const latestStatus = computed(() => {
  if (!recentLogs.value.length) {
    return { text: '暂无执行记录', type: 'info' as const };
  }
  const latest = recentLogs.value[0];
  if (latest.status === 'FAILED') {
    return { text: '最近一次执行失败', type: 'danger' as const };
  }
  if (latest.status === 'RUNNING') {
    return { text: '当前有任务在执行', type: 'warning' as const };
  }
  return { text: '最近一次执行成功', type: 'success' as const };
});

function statusTagType(status: string) {
  switch (status) {
    case 'SUCCESS':
      return 'success';
    case 'FAILED':
      return 'danger';
    case 'RUNNING':
      return 'warning';
    case 'PAUSED':
      return 'info';
    default:
      return '';
  }
}

function formatTaskType(taskType: string) {
  if (taskType === 'GOVERNANCE') {
    return '治理任务';
  }
  if (taskType === 'IMPORT') {
    return '导入任务';
  }
  return taskType;
}

function menuLabel(name: MenuRoute) {
  const labels: Record<MenuRoute, string> = {
    imports: '电商数据接入',
    'data-sources': '数据源管理',
    datasets: '电商数据集',
    governance: '电商数据治理',
    tasks: '电商任务调度',
    logs: '任务与日志',
    queries: '电商查询分析',
    dashboard: '电商仪表盘'
  };
  return labels[name];
}

function hasMenu(name: MenuRoute) {
  return authStore.allowedMenus.includes(name as never);
}

function ensureMenu(name: MenuRoute) {
  if (hasMenu(name)) {
    return true;
  }
  ElMessage.warning(`当前角色没有“${menuLabel(name)}”菜单权限`);
  return false;
}

function goTo(name: MenuRoute) {
  if (!ensureMenu(name)) {
    return;
  }
  void router.push({ name });
}

function handleTrendJump() {
  goTo('queries');
}

async function renderTrendChart() {
  await nextTick();
  if (!trendChartRef.value) return;
  if (!trendChart) {
    trendChart = echarts.init(trendChartRef.value);
    trendChart.on('click', handleTrendJump);
  }
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['订单量', '销售额'] },
    grid: { left: 24, right: 24, top: 32, bottom: 24 },
    xAxis: { type: 'category', data: ecommerce.value.orderTrend.map((item) => item.day) },
    yAxis: { type: 'value' },
    series: [
      {
        name: '订单量',
        type: 'line',
        smooth: true,
        data: ecommerce.value.orderTrend.map((item) => item.value),
        lineStyle: { color: '#1f8f6b', width: 3 },
        itemStyle: { color: '#1f8f6b' }
      },
      {
        name: '销售额',
        type: 'line',
        smooth: true,
        data: ecommerce.value.salesTrend.map((item) => item.value),
        lineStyle: { color: '#db6f44', width: 3 },
        itemStyle: { color: '#db6f44' }
      }
    ]
  });
}

async function loadData() {
  const [overviewData, ecommerceData, recentTaskData, logData] = await Promise.all([
    getOverview(),
    getEcommerceOverview(),
    getRecentTasks(),
    listTaskLogs()
  ]);
  overview.value = overviewData;
  ecommerce.value = ecommerceData;
  recentTasks.value = recentTaskData;
  recentLogs.value = logData;
  lastSyncedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false });
  await renderTrendChart();
}

function startAutoRefresh() {
  stopAutoRefresh();
  refreshTimer = window.setInterval(() => {
    void loadData();
  }, 15000);
}

function stopAutoRefresh() {
  if (refreshTimer) {
    window.clearInterval(refreshTimer);
    refreshTimer = null;
  }
}

onMounted(async () => {
  try {
    await loadData();
    startAutoRefresh();
  } catch (error) {
    if (isAuthExpiredError(error)) {
      return;
    }
    ElMessage.error(`首页数据加载失败: ${(error as Error).message}`);
  }
});

onBeforeUnmount(() => {
  stopAutoRefresh();
  trendChart?.dispose();
});
</script>

<template>
  <div class="page-grid">
    <section class="hero-panel">
      <div>
        <p class="hero-kicker">E-commerce Dashboard</p>
        <h2>订单、商品、库存电商数据湖总览</h2>
        <p class="hero-text">
          首页聚合展示核心指标与执行状态，帮助演示“接入 → 分析 → 治理 → 调度 → 日志”主流程。
        </p>
      </div>
      <div class="hero-side">
        <div class="hero-badges">
          <el-tag size="large">Order</el-tag>
          <el-tag size="large">Product</el-tag>
          <el-tag size="large">Inventory</el-tag>
          <el-tag :type="latestStatus.type" size="large">{{ latestStatus.text }}</el-tag>
        </div>
        <div class="hero-actions">
          <el-button v-if="hasMenu('imports')" type="primary" @click="goTo('imports')">开始电商数据接入</el-button>
          <el-button v-if="hasMenu('queries')" @click="goTo('queries')">打开查询分析</el-button>
          <el-button v-if="hasMenu('governance')" @click="goTo('governance')">执行治理流程</el-button>
          <el-button v-if="hasMenu('tasks')" @click="goTo('tasks')">查看任务调度</el-button>
          <el-button v-if="hasMenu('logs')" link type="primary" @click="goTo('logs')">查看日志详情</el-button>
        </div>
        <p class="hero-meta">最近同步 {{ lastSyncedAt || '--:--:--' }}，首页每 15 秒自动刷新。</p>
      </div>
    </section>

    <section class="stat-grid">
      <el-card
        v-for="item in dashboardStats"
        :key="item.label"
        shadow="hover"
        class="dashboard-jump-card"
        @click="goTo(item.target)"
      >
        <p class="stat-label">{{ item.label }}</p>
        <p class="stat-value">{{ item.value }}</p>
        <p class="stat-card-hint">{{ item.hint }}</p>
      </el-card>
    </section>

    <section class="two-column-grid">
      <el-card shadow="never" class="dashboard-jump-card" @click="handleTrendJump">
        <template #header>
          <div class="card-header">
            <span>近 7 天订单量/销售额趋势</span>
            <el-tag type="success">E-commerce KPI</el-tag>
          </div>
        </template>
        <div ref="trendChartRef" class="chart-box"></div>
      </el-card>

      <el-card shadow="never" class="dashboard-jump-card" @click="goTo('queries')">
        <template #header>
          <div class="card-header">
            <span>热销商品 Top5</span>
            <el-tag>{{ ecommerce.topProducts.length }} 条</el-tag>
          </div>
        </template>
        <el-table :data="ecommerce.topProducts" stripe @row-click="goTo('queries')">
          <el-table-column prop="name" label="商品" />
          <el-table-column prop="value" label="销量" width="160" />
        </el-table>
      </el-card>
    </section>

    <section class="two-column-grid">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>最近任务</span>
            <el-button v-if="hasMenu('tasks')" link type="primary" @click="goTo('tasks')">进入任务页</el-button>
          </div>
        </template>
        <el-table :data="recentTasks" size="small" stripe>
          <el-table-column prop="taskName" label="任务名称" />
          <el-table-column label="类型" width="120">
            <template #default="{ row }">
              {{ formatTaskType(row.taskType) }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="nextRunTime" label="下次执行" width="180" />
        </el-table>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>最近执行日志</span>
            <el-button v-if="hasMenu('logs')" link type="primary" @click="goTo('logs')">打开日志页</el-button>
          </div>
        </template>
        <el-table :data="recentLogs.slice(0, 5)" size="small" stripe>
          <el-table-column prop="taskName" label="任务名称" />
          <el-table-column prop="taskType" label="类型" width="120" />
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="duration" label="耗时(秒)" width="100" />
          <el-table-column prop="executionSummary" label="执行摘要" />
        </el-table>
      </el-card>
    </section>
  </div>
</template>
