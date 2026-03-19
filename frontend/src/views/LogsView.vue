<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { getTaskLogDetail, listTaskLogs, type TaskLogDetail, type TaskLogSummary } from '../api/platform';

const logs = ref<TaskLogSummary[]>([]);
const selectedLog = ref<TaskLogDetail | null>(null);
const filters = reactive({
  keyword: '',
  status: '',
  taskType: ''
});

const filteredLogs = computed(() =>
  logs.value.filter((log) => {
    const matchesKeyword =
      !filters.keyword ||
      [log.taskName, log.executionSummary, log.errorMessage, log.taskType]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(filters.keyword.toLowerCase()));
    const matchesStatus = !filters.status || log.status === filters.status;
    const matchesTaskType = !filters.taskType || log.taskType === filters.taskType;
    return matchesKeyword && matchesStatus && matchesTaskType;
  })
);

const successCount = computed(() => logs.value.filter((item) => item.status === 'SUCCESS').length);
const failedCount = computed(() => logs.value.filter((item) => item.status === 'FAILED').length);
const runningCount = computed(() => logs.value.filter((item) => item.status === 'RUNNING').length);
const avgDuration = computed(() => {
  if (!logs.value.length) {
    return 0;
  }
  return Math.round(logs.value.reduce((sum, item) => sum + (item.duration || 0), 0) / logs.value.length);
});

async function loadData() {
  logs.value = await listTaskLogs();
  if (logs.value.length > 0) {
    await selectLog(logs.value[0].logId);
  } else {
    selectedLog.value = null;
  }
}

async function selectLog(logId: number) {
  selectedLog.value = await getTaskLogDetail(logId);
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

function resetFilters() {
  filters.keyword = '';
  filters.status = '';
  filters.taskType = '';
}

onMounted(async () => {
  try {
    await loadData();
  } catch (error) {
    ElMessage.error(`日志页初始化失败: ${(error as Error).message}`);
  }
});
</script>

<template>
  <div class="page-grid">
    <section class="stat-grid">
      <el-card shadow="hover">
        <p class="stat-label">成功日志</p>
        <p class="stat-value">{{ successCount }}</p>
      </el-card>
      <el-card shadow="hover">
        <p class="stat-label">失败日志</p>
        <p class="stat-value">{{ failedCount }}</p>
      </el-card>
      <el-card shadow="hover">
        <p class="stat-label">运行中</p>
        <p class="stat-value">{{ runningCount }}</p>
      </el-card>
      <el-card shadow="hover">
        <p class="stat-label">平均耗时</p>
        <p class="stat-value">{{ avgDuration }}s</p>
      </el-card>
    </section>

    <section class="two-column-grid">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>日志筛选</span>
            <div>
              <el-button link type="primary" @click="resetFilters">重置</el-button>
              <el-button link type="primary" @click="loadData">刷新</el-button>
            </div>
          </div>
        </template>
        <el-form inline>
          <el-form-item label="关键字">
            <el-input v-model="filters.keyword" placeholder="任务名 / 摘要 / 错误信息" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="filters.status" clearable placeholder="全部状态">
              <el-option label="SUCCESS" value="SUCCESS" />
              <el-option label="FAILED" value="FAILED" />
              <el-option label="RUNNING" value="RUNNING" />
            </el-select>
          </el-form-item>
          <el-form-item label="类型">
            <el-select v-model="filters.taskType" clearable placeholder="全部类型">
              <el-option label="IMPORT" value="IMPORT" />
              <el-option label="GOVERNANCE" value="GOVERNANCE" />
            </el-select>
          </el-form-item>
        </el-form>
      </el-card>
    </section>

    <section class="two-column-grid">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>任务日志</span>
          <el-tag type="success">实时列表</el-tag>
        </div>
      </template>
      <el-table :data="filteredLogs" stripe @row-click="(row: TaskLogSummary) => selectLog(row.logId)">
        <el-table-column prop="taskName" label="任务名称" />
        <el-table-column prop="taskType" label="类型" width="120" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="startTime" label="开始时间" />
        <el-table-column prop="endTime" label="结束时间" />
        <el-table-column prop="duration" label="耗时(秒)" width="100" />
        <el-table-column prop="executionSummary" label="执行摘要" />
      </el-table>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>日志详情</span>
          <el-tag :type="statusTagType(selectedLog?.status || '')">{{ selectedLog?.status || '未选择' }}</el-tag>
        </div>
      </template>
      <div v-if="selectedLog" class="page-grid">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="任务名称">{{ selectedLog.taskName }}</el-descriptions-item>
          <el-descriptions-item label="任务类型">{{ selectedLog.taskType }}</el-descriptions-item>
          <el-descriptions-item label="目标 ID">{{ selectedLog.targetId ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ selectedLog.startTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ selectedLog.endTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="耗时">{{ selectedLog.duration }} 秒</el-descriptions-item>
          <el-descriptions-item label="操作用户">{{ selectedLog.operatorUser ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="执行摘要">{{ selectedLog.executionSummary || '-' }}</el-descriptions-item>
          <el-descriptions-item label="错误信息">{{ selectedLog.errorMessage || '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-alert
          v-if="selectedLog.errorMessage"
          class="notice-box"
          title="本次执行出现异常"
          :description="selectedLog.errorMessage"
          type="error"
          :closable="false"
        />
        <el-alert
          v-else
          class="notice-box"
          title="本次执行已完成"
          :description="selectedLog.executionSummary || '暂无摘要'"
          type="success"
          :closable="false"
        />
      </div>
      <el-empty v-else description="暂无日志详情" />
    </el-card>
    </section>
  </div>
</template>
