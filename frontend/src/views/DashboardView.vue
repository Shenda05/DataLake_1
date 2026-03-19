<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import * as echarts from 'echarts';
import { getOverview, getRecentTasks, getTaskTrend } from '../api/platform';

const overview = ref({
  dataSources: 0,
  datasets: 0,
  newDatasetsToday: 0,
  totalTasks: 0,
  runningTasks: 0,
  successTasks: 0,
  failedTasks: 0
});
const recentTasks = ref<any[]>([]);
const taskTrend = ref<{ day: string; total: number }[]>([]);
const chartRef = ref<HTMLDivElement | null>(null);
let chart: echarts.ECharts | null = null;

async function loadData() {
  try {
    overview.value = await getOverview();
    recentTasks.value = await getRecentTasks();
    taskTrend.value = await getTaskTrend();
    await renderChart();
  } catch (error) {
    ElMessage.error(`首页数据加载失败: ${(error as Error).message}`);
  }
}

async function renderChart() {
  await nextTick();
  if (!chartRef.value) return;
  chart ??= echarts.init(chartRef.value);
  chart.setOption({
    grid: { left: 24, right: 24, top: 28, bottom: 24 },
    xAxis: { type: 'category', data: taskTrend.value.map((item) => item.day) },
    yAxis: { type: 'value' },
    tooltip: { trigger: 'axis' },
    series: [
      {
        type: 'line',
        smooth: true,
        data: taskTrend.value.map((item) => item.total),
        areaStyle: {},
        lineStyle: { width: 3, color: '#1f8f6b' },
        itemStyle: { color: '#1f8f6b' }
      }
    ]
  });
}

onMounted(loadData);
watch(taskTrend, renderChart);
onBeforeUnmount(() => chart?.dispose());
</script>

<template>
  <div class="page-grid">
    <section class="hero-panel">
      <div>
        <p class="hero-kicker">Platform Overview</p>
        <h2>首页已经改成真实后端聚合数据</h2>
        <p class="hero-text">当前仪表盘直接读取任务、日志、数据源和数据集统计，可作为后续答辩演示的总览入口。</p>
      </div>
      <div class="hero-badges">
        <el-tag size="large">Real API</el-tag>
        <el-tag size="large">JWT</el-tag>
        <el-tag size="large">MySQL/H2</el-tag>
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
        <p class="stat-label">成功 / 失败任务</p>
        <p class="stat-value">{{ overview.successTasks }} / {{ overview.failedTasks }}</p>
      </el-card>
    </section>

    <section class="two-column-grid">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>近 7 天任务趋势</span>
            <el-button link type="primary" @click="loadData">刷新</el-button>
          </div>
        </template>
        <div ref="chartRef" class="chart-box"></div>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>最近任务</span>
            <el-tag>{{ recentTasks.length }} 项</el-tag>
          </div>
        </template>
        <el-timeline>
          <el-timeline-item
            v-for="task in recentTasks"
            :key="task.taskId"
            :timestamp="task.nextRunTime || '待计算'"
            placement="top"
          >
            <strong>{{ task.taskName }}</strong>
            <p>{{ task.taskType }} / {{ task.status }}</p>
          </el-timeline-item>
        </el-timeline>
      </el-card>
    </section>
  </div>
</template>

