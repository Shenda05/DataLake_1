<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import * as echarts from 'echarts';
import { getOverview, getRecentTasks, getTaskTrend, listTaskLogs, type TaskLogSummary } from '../api/platform';

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
  overview.value = await getOverview();
  taskTrend.value = await getTaskTrend();
  recentTasks.value = await getRecentTasks();
  recentLogs.value = (await listTaskLogs()).slice(0, 5);
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

onMounted(async () => {
  try {
    await loadData();
  } catch (error) {
    ElMessage.error(`首页数据加载失败: ${(error as Error).message}`);
  }
});

onBeforeUnmount(() => {
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
      <div class="hero-badges">
        <el-tag size="large">Vue 3</el-tag>
        <el-tag size="large">Spring Boot</el-tag>
        <el-tag size="large">MySQL</el-tag>
        <el-tag size="large">ECharts</el-tag>
        <el-tag :type="latestStatus.type" size="large">{{ latestStatus.text }}</el-tag>
      </div>
    </section>

    <section class="stat-grid">
      <el-card shadow="hover">
        <p class="stat-label">数据源总数</p>
        <p class="stat-value">{{ overview.dataSources }}</p>
      </el-card>
      <el-card shadow="hover">
        <p class="stat-label">数据集总数</p>
        <p class="stat-value">{{ overview.datasets }}</p>
      </el-card>
      <el-card shadow="hover">
        <p class="stat-label">今日新增数据集</p>
        <p class="stat-value">{{ overview.newDatasetsToday }}</p>
      </el-card>
      <el-card shadow="hover">
        <p class="stat-label">任务总数</p>
        <p class="stat-value">{{ overview.totalTasks }}</p>
      </el-card>
      <el-card shadow="hover">
        <p class="stat-label">运行中任务</p>
        <p class="stat-value">{{ overview.runningTasks }}</p>
      </el-card>
      <el-card shadow="hover">
        <p class="stat-label">成功率</p>
        <p class="stat-value">{{ successRate }}</p>
      </el-card>
      <el-card shadow="hover">
        <p class="stat-label">成功 / 失败日志</p>
        <p class="stat-value">{{ overview.successTasks }} / {{ overview.failedTasks }}</p>
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
            <el-tag type="success">Live</el-tag>
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
        </el-table>
      </el-card>
    </section>

    <section class="two-column-grid">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>最近执行日志</span>
            <el-tag type="warning">Traceable</el-tag>
          </div>
        </template>
        <el-table :data="recentLogs" size="small" stripe>
          <el-table-column prop="taskName" label="任务名称" />
          <el-table-column prop="taskType" label="类型" width="110" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="duration" label="耗时(秒)" width="100" />
          <el-table-column prop="executionSummary" label="结果摘要" />
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
  </div>
</template>
