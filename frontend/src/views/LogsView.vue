<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { getTaskLogDetail, listTaskLogs, type TaskLogRecord } from '../api/platform';

const logs = ref<TaskLogRecord[]>([]);
const detail = ref<TaskLogRecord | null>(null);

async function loadLogs() {
  logs.value = await listTaskLogs();
}

async function showDetail(logId: number) {
  detail.value = await getTaskLogDetail(logId);
}

onMounted(async () => {
  try {
    await loadLogs();
  } catch (error) {
    ElMessage.error(`日志加载失败: ${(error as Error).message}`);
  }
});
</script>

<template>
  <div class="page-grid two-column-grid">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>任务日志</span>
          <el-button link type="primary" @click="loadLogs">刷新</el-button>
        </div>
      </template>
      <el-table :data="logs" stripe @row-click="(row: TaskLogRecord) => showDetail(row.logId)">
        <el-table-column prop="logId" label="日志ID" width="100" />
        <el-table-column prop="taskType" label="任务类型" width="120" />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column prop="startTime" label="开始时间" />
        <el-table-column prop="duration" label="耗时(ms)" width="120" />
        <el-table-column prop="executionSummary" label="摘要" />
      </el-table>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>日志详情</span>
          <el-tag v-if="detail">{{ detail.status }}</el-tag>
        </div>
      </template>
      <div v-if="detail" class="plain-list">
        <div>日志 ID：{{ detail.logId }}</div>
        <div>任务类型：{{ detail.taskType }}</div>
        <div>任务 ID：{{ detail.taskId }}</div>
        <div>开始时间：{{ detail.startTime }}</div>
        <div>结束时间：{{ detail.endTime }}</div>
        <div>执行摘要：{{ detail.executionSummary }}</div>
        <div>错误信息：{{ detail.errorMessage || '无' }}</div>
      </div>
      <div v-else class="plain-list">点击左侧日志查看详情。</div>
    </el-card>
  </div>
</template>
