<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import * as echarts from 'echarts';
import { useRouter } from 'vue-router';
import { getOverview, getRecentTasks, getTaskTrend, listTaskLogs, type TaskLogSummary } from '../api/platform';

const router = useRouter();
const chartRef = ref<HTMLDivElement | null>(null);
const overview = ref({
  dataSources: 0,
  datasets: 0,
  newDatasetsToday: 0,
  totalTasks: 0,
  runningTasks: 0,
  successTasks: 0,
  failedTasks: 0
});
const taskTrend = ref<{ day: string; total: number }[]>([]);
const recentTasks = ref<{ taskId: number; taskName: string; taskType: string; status: string; nextRunTime?: string | null }[]>([]);
const recentLogs = ref<TaskLogSummary[]>([]);
const lastSyncedAt = ref('');
let refreshTimer: ReturnType<typeof window.setInterval> | null = null;
const successRate = computed(() => {
  const total = overview.value.successTasks + overview.value.failedTasks;
  if (total === 0) {
    return '100%';
  }
  return `${Math.round((overview.value.successTasks / total) * 100)}%`;
});
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
const recentEventStream = computed(() => {
  const now = Date.now();
  return recentLogs.value
    .filter((log) => {
      const timestamp = parseDateTime(log.startTime || log.endTime);
      return timestamp !== null && now - timestamp <= 60 * 1000;
    })
    .slice(0, 8);
});
let chart: echarts.ECharts | null = null;

async function renderChart() {
  await nextTick();
  if (!chartRef.value) return;
  if (!chart) {
    chart = echarts.init(chartRef.value);
  }
  chart.setOption({
    grid: { left: 24, right: 24, top: 36, bottom: 24 },
    xAxis: { type: 'category', data: taskTrend.value.map((item) => item.day) },
    yAxis: { type: 'value' },
    tooltip: { trigger: 'axis' },
    series: [
      {
        type: 'line',
        smooth: true,
        data: taskTrend.value.map((item) => item.total),
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(31, 143, 107, 0.28)' },
            { offset: 1, color: 'rgba(31, 143, 107, 0.03)' }
          ])
        },
        lineStyle: { width: 3, color: '#1f8f6b' },
        itemStyle: { color: '#1f8f6b' },
        showSymbol: true
      }
    ]
  });
}

async function loadData() {
  const [overviewData, trendData, recentTaskData, logData] = await Promise.all([
    getOverview(),
    getTaskTrend(),
    getRecentTasks(),
    listTaskLogs()
  ]);
  overview.value = overviewData;
  taskTrend.value = trendData;
  recentTasks.value = recentTaskData;
  recentLogs.value = logData;
  lastSyncedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false });
  await renderChart();
}

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
  return taskType === 'GOVERNANCE' ? '治理任务' : taskType === 'IMPORT' ? '导入任务' : taskType;
}

function parseDateTime(value?: string | null) {
  if (!value) {
    return null;
  }
  const timestamp = new Date(value).getTime();
  return Number.isNaN(timestamp) ? null : timestamp;
}

function formatDateTime(value?: string | null) {
  if (!value) {
    return '--';
  }
  return value.replace('T', ' ');
}

function formatEventAge(value?: string | null) {
  const timestamp = parseDateTime(value);
  if (timestamp === null) {
    return '刚刚';
  }
  const diff = Math.max(0, Date.now() - timestamp);
  const seconds = Math.floor(diff / 1000);
  if (seconds < 60) {
    return `${seconds} 秒前`;
  }
  const minutes = Math.floor(seconds / 60);
  return `${minutes} 分钟前`;
}

function goTo(name: 'imports' | 'datasets' | 'governance' | 'tasks' | 'logs' | 'queries') {
  void router.push({ name });
}

function goToLog(logId: number) {
  void router.push({ name: 'logs', query: { logId: String(logId) } });
}

function goToTask(taskId: number) {
  void router.push({ name: 'tasks', query: { taskId: String(taskId) } });
}

function goToTaskTarget(taskType: string, targetId?: number | null) {
  if (taskType === 'GOVERNANCE') {
    void router.push({ name: 'governance', query: targetId ? { flowId: String(targetId) } : undefined });
    return;
  }
  if (taskType === 'IMPORT') {
    void router.push({ name: 'imports', query: targetId ? { importId: String(targetId) } : undefined });
  }
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
    ElMessage.error(`首页数据加载失败: ${(error as Error).message}`);
  }
});

onBeforeUnmount(() => {
  stopAutoRefresh();
  chart?.dispose();
});
</script>

<template>
  <div class="page-grid">
    <section class="hero-panel">
      <div>
        <p class="hero-kicker">Platform Overview</p>
        <h2>P0 主链路已经跑通，现在重点展示“结果可信 + 过程可追踪”</h2>
        <p class="hero-text">
          首页现在直接汇总真实数据源、数据集、任务和日志结果，适合在答辩时用来总览“导入、治理、调度、追踪”这一整条业务链。
        </p>
      </div>
      <div class="hero-side">
        <div class="hero-badges">
          <el-tag size="large">Vue 3</el-tag>
          <el-tag size="large">Spring Boot</el-tag>
          <el-tag size="large">MySQL</el-tag>
          <el-tag size="large">ECharts</el-tag>
          <el-tag :type="latestStatus.type" size="large">{{ latestStatus.text }}</el-tag>
        </div>
        <div class="hero-actions">
          <el-button type="primary" @click="goTo('imports')">开始数据接入</el-button>
          <el-button @click="goTo('governance')">打开治理流程</el-button>
          <el-button @click="goTo('tasks')">查看任务调度</el-button>
          <el-button @click="goTo('logs')">查看执行日志</el-button>
          <el-button link type="primary" @click="goTo('datasets')">查看全部数据集</el-button>
        </div>
        <p class="hero-meta">最近同步 {{ lastSyncedAt || '--:--:--' }}，首页每 15 秒自动刷新一次。</p>
      </div>
    </section>

    <section class="stat-grid">
      <el-card shadow="hover">
        <p class="stat-label">数据源总数</p>
        <p class="stat-value">{{ overview.dataSources }}</p>
        <el-button link type="primary" @click="goTo('imports')">继续导入数据</el-button>
      </el-card>
      <el-card shadow="hover">
        <p class="stat-label">数据集总数</p>
        <p class="stat-value">{{ overview.datasets }}</p>
        <el-button link type="primary" @click="goTo('datasets')">查看数据集</el-button>
      </el-card>
      <el-card shadow="hover">
        <p class="stat-label">今日新增数据集</p>
        <p class="stat-value">{{ overview.newDatasetsToday }}</p>
        <el-button link type="primary" @click="goTo('governance')">去生成新数据集</el-button>
      </el-card>
      <el-card shadow="hover">
        <p class="stat-label">任务总数</p>
        <p class="stat-value">{{ overview.totalTasks }}</p>
        <el-button link type="primary" @click="goTo('tasks')">进入任务页</el-button>
      </el-card>
      <el-card shadow="hover">
        <p class="stat-label">运行中任务</p>
        <p class="stat-value">{{ overview.runningTasks }}</p>
        <el-button link type="primary" @click="goTo('tasks')">观察实时状态</el-button>
      </el-card>
      <el-card shadow="hover">
        <p class="stat-label">成功率</p>
        <p class="stat-value">{{ successRate }}</p>
        <el-button link type="primary" @click="goTo('logs')">查看成功明细</el-button>
      </el-card>
      <el-card shadow="hover">
        <p class="stat-label">成功 / 失败日志</p>
        <p class="stat-value">{{ overview.successTasks }} / {{ overview.failedTasks }}</p>
        <el-button link type="primary" @click="goTo('logs')">打开日志中心</el-button>
      </el-card>
    </section>

    <section class="two-column-grid">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>近 7 天任务趋势</span>
            <el-tag type="success">Real Data</el-tag>
          </div>
        </template>
        <div ref="chartRef" class="chart-box"></div>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>最近任务</span>
            <div class="card-header-actions">
              <el-tag type="success">Live</el-tag>
              <el-button link type="primary" @click="goTo('tasks')">进入任务页</el-button>
            </div>
          </div>
        </template>
        <el-table :data="recentTasks" size="small" stripe>
          <el-table-column prop="taskName" label="任务名称" />
          <el-table-column label="类型" width="110">
            <template #default="{ row }">
              {{ formatTaskType(row.taskType) }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="nextRunTime" label="下次执行" width="180" />
          <el-table-column label="跳转" width="160">
            <template #default="{ row }">
              <el-button link type="primary" @click="goToTask(row.taskId)">任务页</el-button>
              <el-button link @click="goToTaskTarget(row.taskType)">目标页</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </section>

    <section class="two-column-grid">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>最近执行日志</span>
            <div class="card-header-actions">
              <el-tag type="warning">Traceable</el-tag>
              <el-button link type="primary" @click="goTo('logs')">打开日志页</el-button>
            </div>
          </div>
        </template>
        <el-table :data="recentLogs.slice(0, 5)" size="small" stripe>
          <el-table-column prop="taskName" label="任务名称" />
          <el-table-column prop="taskType" label="类型" width="110" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="duration" label="耗时(秒)" width="100" />
          <el-table-column prop="executionSummary" label="结果摘要" />
          <el-table-column label="跳转" width="160">
            <template #default="{ row }">
              <el-button link type="primary" @click="goToLog(row.logId)">详情</el-button>
              <el-button v-if="row.taskId" link @click="goToTask(row.taskId)">任务</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>演示脚本</span>
            <el-tag type="info">答辩顺序</el-tag>
          </div>
        </template>
        <ol class="demo-script">
          <li>登录管理员账号，展示首页真实统计和最近任务。</li>
          <li>进入数据接入页上传文件，生成输入数据集。</li>
          <li>进入治理页保存流程并执行，得到新数据集。</li>
          <li>进入任务调度页创建治理任务或导入任务并手动触发。</li>
          <li>进入日志监控页查看执行摘要、耗时和异常信息。</li>
        </ol>
        <el-alert
          class="notice-box"
          title="建议演示时优先用治理任务，能够同时展示新数据集产出与日志可追踪性。"
          type="info"
          :closable="false"
        />
      </el-card>
    </section>

    <section class="two-column-grid">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>最近 1 分钟任务事件流</span>
            <div class="card-header-actions">
              <el-tag type="danger">{{ recentEventStream.length }} 条</el-tag>
              <el-button link type="primary" @click="goTo('logs')">跳转日志中心</el-button>
            </div>
          </div>
        </template>
        <div v-if="recentEventStream.length" class="event-stream">
          <article v-for="log in recentEventStream" :key="log.logId" class="event-item">
            <div class="event-dot"></div>
            <div class="event-content">
              <div class="event-header">
                <strong>{{ log.taskName }}</strong>
                <el-tag :type="statusTagType(log.status)">{{ log.status }}</el-tag>
              </div>
              <p class="event-meta">
                {{ formatTaskType(log.taskType) }} · {{ formatDateTime(log.startTime || log.endTime) }} · {{ formatEventAge(log.startTime || log.endTime) }}
              </p>
              <p class="event-summary">{{ log.executionSummary || log.errorMessage || '任务已开始执行' }}</p>
              <div class="card-header-actions">
                <el-button link type="primary" @click="goToLog(log.logId)">日志详情</el-button>
                <el-button v-if="log.taskId" link @click="goToTask(log.taskId)">查看任务</el-button>
              </div>
            </div>
          </article>
        </div>
        <el-empty v-else description="最近 1 分钟暂无新事件，保持首页打开即可持续观察调度变化。" />
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>演示捷径</span>
            <el-tag type="success">少点几步</el-tag>
          </div>
        </template>
        <p class="hero-text">
          如果现场只想展示主价值，可以从这里直接跳到导入、治理、任务和日志，避免在侧边栏来回切页。
        </p>
        <div class="quick-links">
          <el-button type="primary" @click="goTo('imports')">上传一份样例文件</el-button>
          <el-button @click="goTo('queries')">查看查询分析</el-button>
          <el-button @click="goTo('governance')">执行治理流程</el-button>
          <el-button @click="goTo('tasks')">观察任务轮询</el-button>
          <el-button @click="goTo('logs')">查看日志详情</el-button>
        </div>
        <el-alert
          class="notice-box"
          title="首页会持续自动刷新，适合一边触发任务，一边回到首页观察最近 1 分钟事件流。"
          type="success"
          :closable="false"
        />
      </el-card>
    </section>
  </div>
</template>
