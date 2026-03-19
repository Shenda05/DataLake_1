<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { useRoute, useRouter } from 'vue-router';
import { getTaskLogDetail, listTaskLogs, type TaskLogDetail, type TaskLogSummary } from '../api/platform';

const route = useRoute();
const router = useRouter();
const logs = ref<TaskLogSummary[]>([]);
const selectedLog = ref<TaskLogDetail | null>(null);
const lastSyncedAt = ref('');
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
  lastSyncedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false });
  const queryLogId = Number(route.query.logId);
  if (Number.isInteger(queryLogId) && queryLogId > 0 && logs.value.some((item) => item.logId === queryLogId)) {
    await selectLog(queryLogId);
  } else if (logs.value.length > 0) {
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

function formatTaskType(taskType: string) {
  return taskType === 'GOVERNANCE' ? '治理任务' : taskType === 'IMPORT' ? '导入任务' : taskType;
}

function goToDashboard() {
  void router.push({ name: 'dashboard' });
}

function goToTasks(log?: TaskLogSummary | TaskLogDetail | null) {
  if (!log?.taskId) {
    return;
  }
  void router.push({ name: 'tasks', query: { taskId: String(log.taskId), logId: String(log.logId) } });
}

function goToTarget(log?: TaskLogSummary | TaskLogDetail | null) {
  if (!log) {
    return;
  }
  if (log.taskType === 'GOVERNANCE') {
    void router.push({ name: 'governance', query: log.targetId ? { flowId: String(log.targetId) } : undefined });
    return;
  }
  if (log.taskType === 'IMPORT') {
    void router.push({ name: 'imports', query: log.targetId ? { importId: String(log.targetId) } : undefined });
  }
}

watch(
  () => route.query.logId,
  async (value) => {
    const logId = Number(value);
    if (Number.isInteger(logId) && logId > 0 && logs.value.some((item) => item.logId === logId)) {
      await selectLog(logId);
    }
  }
);

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
        <el-button link type="primary" @click="goToDashboard">回首页概览</el-button>
      </el-card>
      <el-card shadow="hover">
        <p class="stat-label">失败日志</p>
        <p class="stat-value">{{ failedCount }}</p>
        <el-button link type="primary" @click="goToDashboard">查看全局状态</el-button>
      </el-card>
      <el-card shadow="hover">
        <p class="stat-label">运行中</p>
        <p class="stat-value">{{ runningCount }}</p>
        <el-button link type="primary" @click="goToTasks(selectedLog)">前往任务页</el-button>
      </el-card>
      <el-card shadow="hover">
        <p class="stat-label">平均耗时</p>
        <p class="stat-value">{{ avgDuration }}s</p>
        <el-button link type="primary" @click="goToTarget(selectedLog)">查看来源模块</el-button>
      </el-card>
    </section>

    <section class="two-column-grid">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>日志筛选</span>
            <div class="card-header-actions">
              <span class="inline-tip">最近同步 {{ lastSyncedAt || '--:--:--' }}</span>
              <el-button link type="primary" @click="resetFilters">重置</el-button>
              <el-button link type="primary" @click="loadData">刷新</el-button>
              <el-button link type="primary" @click="goToDashboard">回首页</el-button>
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
          <div class="card-header-actions">
            <el-tag type="success">实时列表</el-tag>
            <el-button link type="primary" @click="goToTasks(selectedLog)">查看对应任务</el-button>
          </div>
        </div>
      </template>
      <el-table :data="filteredLogs" stripe @row-click="(row: TaskLogSummary) => selectLog(row.logId)">
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
        <el-table-column prop="startTime" label="开始时间" />
        <el-table-column prop="endTime" label="结束时间" />
        <el-table-column prop="duration" label="耗时(秒)" width="100" />
        <el-table-column prop="executionSummary" label="执行摘要" />
        <el-table-column label="跳转" width="170">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="goToTasks(row)">任务页</el-button>
            <el-button link @click.stop="goToTarget(row)">来源页</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>日志详情</span>
          <div class="card-header-actions">
            <el-tag :type="statusTagType(selectedLog?.status || '')">{{ selectedLog?.status || '未选择' }}</el-tag>
            <el-button link type="primary" @click="goToTasks(selectedLog)">对应任务</el-button>
            <el-button link @click="goToTarget(selectedLog)">来源页面</el-button>
          </div>
        </div>
      </template>
      <div v-if="selectedLog" class="page-grid">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="任务名称">{{ selectedLog.taskName }}</el-descriptions-item>
          <el-descriptions-item label="任务类型">{{ formatTaskType(selectedLog.taskType) }}</el-descriptions-item>
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
        <div class="detail-actions">
          <el-button type="primary" @click="goToTasks(selectedLog)">去任务调度继续观察</el-button>
          <el-button @click="goToTarget(selectedLog)">回到来源模块</el-button>
          <el-button link type="primary" @click="goToDashboard">返回首页总览</el-button>
        </div>
      </div>
      <el-empty v-else description="暂无日志详情" />
    </el-card>
    </section>
  </div>
</template>
