<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import * as echarts from 'echarts';
import { overview, recentTasks, taskTrend } from '../mock/api';

const chartRef = ref<HTMLDivElement | null>(null);
let chart: echarts.ECharts | null = null;

onMounted(async () => {
  await nextTick();
  if (!chartRef.value) return;
  chart = echarts.init(chartRef.value);
  chart.setOption({
    grid: { left: 24, right: 24, top: 28, bottom: 24 },
    xAxis: { type: 'category', data: taskTrend.map((item) => item.day) },
    yAxis: { type: 'value' },
    tooltip: { trigger: 'axis' },
    series: [
      {
        type: 'line',
        smooth: true,
        data: taskTrend.map((item) => item.total),
        areaStyle: {},
        lineStyle: { width: 3, color: '#1f8f6b' },
        itemStyle: { color: '#1f8f6b' }
      }
    ]
  });
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
        <h2>课程项目先把“接入到调度”主链路做透</h2>
        <p class="hero-text">
          当前骨架已经把登录、菜单、仪表盘和功能入口统一起来，后续只需要按模块替换成真实接口。
        </p>
      </div>
      <div class="hero-badges">
        <el-tag size="large">Vue 3</el-tag>
        <el-tag size="large">Spring Boot</el-tag>
        <el-tag size="large">MySQL</el-tag>
        <el-tag size="large">ECharts</el-tag>
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
        <p class="stat-label">任务成功 / 失败</p>
        <p class="stat-value">{{ overview.successTasks }} / {{ overview.failedTasks }}</p>
      </el-card>
    </section>

    <section class="two-column-grid">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>近 7 天任务趋势</span>
            <el-tag type="success">可替换为真实接口</el-tag>
          </div>
        </template>
        <div ref="chartRef" class="chart-box"></div>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>最近任务</span>
            <el-tag>Week 7</el-tag>
          </div>
        </template>
        <el-timeline>
          <el-timeline-item
            v-for="task in recentTasks"
            :key="task.taskId"
            :timestamp="task.nextRunTime"
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

