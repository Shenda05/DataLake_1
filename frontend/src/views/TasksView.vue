<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { useRoute, useRouter } from 'vue-router';
import {
  createTask,
  listGovernanceFlows,
  listImportHistory,
  listTaskLogs,
  listTasks,
  pauseTask,
  resumeTask,
  triggerTask,
  updateTask,
  type GovernanceFlow,
  type ImportHistory,
  type TaskLogSummary,
  type TaskSummary
} from '../api/platform';

const TASK_FILTERS_KEY = 'data-lake-task-filters';
const route = useRoute();
const router = useRouter();
const tasks = ref<TaskSummary[]>([]);
const flows = ref<GovernanceFlow[]>([]);
const importHistory = ref<ImportHistory[]>([]);
const latestLogs = ref<TaskLogSummary[]>([]);
const editingTaskId = ref<number | null>(null);
const loading = ref(false);
const autoRefreshEnabled = ref(true);
const refreshSeconds = ref(15);
const lastSyncedAt = ref('');
const recentAction = ref<{ type: 'success' | 'info' | 'warning'; message: string } | null>(null);
let refreshTimer: ReturnType<typeof window.setInterval> | null = null;
const filters = reactive({
  keyword: '',
  status: '',
  taskType: ''
});
const form = reactive({
  taskName: '',
  taskType: 'GOVERNANCE' as 'IMPORT' | 'GOVERNANCE',
  targetId: undefined as number | undefined,
  cronExpr: '0 */5 * * * *',
  retryPolicy: 1,
  description: '',
  status: 'ENABLED'
});

const targetOptions = computed(() => {
  if (form.taskType === 'IMPORT') {
    return importHistory.value.map((item) => ({
      value: item.importId,
      label: `${item.datasetName} / ${item.formatType} / ${item.createTime}`
    }));
  }
  return flows.value.map((item) => ({
    value: item.flowId,
    label: `${item.flowName} / ${item.inputDatasetName}`
  }));
});

const filteredTasks = computed(() =>
  tasks.value.filter((item) => {
    const matchesKeyword =
      !filters.keyword ||
      [item.taskName, item.targetName, item.description, item.taskType]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(filters.keyword.toLowerCase()));
    const matchesStatus = !filters.status || item.status === filters.status;
    const matchesTaskType = !filters.taskType || item.taskType === filters.taskType;
    return matchesKeyword && matchesStatus && matchesTaskType;
  })
);
const enabledCount = computed(() => tasks.value.filter((item) => item.status === 'ENABLED').length);
const pausedCount = computed(() => tasks.value.filter((item) => item.status === 'PAUSED').length);
const runningCount = computed(() => tasks.value.filter((item) => item.status === 'RUNNING').length);
const latestLogByTask = computed(() => {
  const map = new Map<number, TaskLogSummary>();
  for (const log of latestLogs.value) {
    if (log.taskId && !map.has(log.taskId)) {
      map.set(log.taskId, log);
    }
  }
  return map;
});

restoreFilters();

async function loadData(showMessage = false) {
  const [taskItems, flowItems, importItems, logItems] = await Promise.all([
    listTasks(),
    listGovernanceFlows(),
    listImportHistory(),
    listTaskLogs()
  ]);
  tasks.value = taskItems;
  flows.value = flowItems;
  importHistory.value = importItems;
  latestLogs.value = logItems;
  const routeTaskId = Number(route.query.taskId);
  if (Number.isInteger(routeTaskId) && routeTaskId > 0) {
    const matchedTask = tasks.value.find((item) => item.taskId === routeTaskId);
    if (matchedTask) {
      loadTask(matchedTask, false);
    }
  }
  if (!form.targetId || !targetOptions.value.some((item) => item.value === form.targetId)) {
    form.targetId = targetOptions.value[0].value;
  }
  lastSyncedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false });
  if (showMessage) {
    ElMessage.success('任务列表已刷新');
  }
}

function resetForm() {
  editingTaskId.value = null;
  form.taskName = '';
  form.taskType = 'GOVERNANCE';
  form.targetId = targetOptions.value[0]?.value;
  form.cronExpr = '0 */5 * * * *';
  form.retryPolicy = 1;
  form.description = '';
  form.status = 'ENABLED';
}

function loadTask(task: TaskSummary, syncRoute = true) {
  editingTaskId.value = task.taskId;
  form.taskName = task.taskName;
  form.taskType = task.taskType;
  form.targetId = task.targetId;
  form.cronExpr = task.cronExpr;
  form.retryPolicy = task.retryPolicy;
  form.description = task.description || '';
  form.status = task.status === 'PAUSED' ? 'PAUSED' : 'ENABLED';
  if (syncRoute) {
    syncRouteState({ taskId: String(task.taskId) });
  }
}

async function submit() {
  if (!form.taskName || !form.targetId || !form.cronExpr) {
    ElMessage.warning('请填写任务名称、目标和 Cron 表达式');
    return;
  }
  loading.value = true;
  try {
    const payload = {
      taskName: form.taskName,
      taskType: form.taskType,
      targetId: form.targetId,
      cronExpr: form.cronExpr,
      retryPolicy: form.retryPolicy,
      description: form.description,
      status: form.status
    };
    if (editingTaskId.value) {
      await updateTask(editingTaskId.value, payload);
      recentAction.value = { type: 'success', message: '任务更新成功，列表已同步最新配置。' };
    } else {
      await createTask(payload);
      recentAction.value = { type: 'success', message: '任务创建成功，可以立即执行或等待自动调度。' };
    }
    resetForm();
    await loadData();
  } catch (error) {
    ElMessage.error(`任务保存失败: ${(error as Error).message}`);
  } finally {
    loading.value = false;
  }
}

async function handleTrigger(taskId: number) {
  try {
    const result = await triggerTask(taskId);
    recentAction.value = { type: result.status === 'FAILED' ? 'warning' : 'success', message: result.message };
    await loadData();
  } catch (error) {
    ElMessage.error(`触发失败: ${(error as Error).message}`);
  }
}

async function handlePause(taskId: number) {
  try {
    await pauseTask(taskId);
    recentAction.value = { type: 'info', message: '任务已暂停，不会继续自动调度。' };
    await loadData();
  } catch (error) {
    ElMessage.error(`暂停失败: ${(error as Error).message}`);
  }
}

async function handleResume(taskId: number) {
  try {
    await resumeTask(taskId);
    recentAction.value = { type: 'success', message: '任务已恢复，系统会按下次执行时间自动轮询。' };
    await loadData();
  } catch (error) {
    ElMessage.error(`恢复失败: ${(error as Error).message}`);
  }
}

function latestLog(taskId: number) {
  return latestLogByTask.value.get(taskId);
}

function formatTaskType(taskType: string) {
  return taskType === 'GOVERNANCE' ? '治理任务' : taskType === 'IMPORT' ? '导入任务' : taskType;
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

function getQueryValue(key: string) {
  const value = route.query[key];
  return Array.isArray(value) ? value[0] || '' : typeof value === 'string' ? value : '';
}

function restoreFilters() {
  const queryKeyword = getQueryValue('keyword');
  const queryStatus = getQueryValue('status');
  const queryTaskType = getQueryValue('taskType');
  const stored = localStorage.getItem(TASK_FILTERS_KEY);
  let parsed: { keyword?: string; status?: string; taskType?: string } = {};
  if (stored) {
    try {
      parsed = JSON.parse(stored) as { keyword?: string; status?: string; taskType?: string };
    } catch {
      localStorage.removeItem(TASK_FILTERS_KEY);
    }
  }
  filters.keyword = queryKeyword || parsed.keyword || '';
  filters.status = queryStatus || parsed.status || '';
  filters.taskType = queryTaskType || parsed.taskType || '';
}

function persistFilters() {
  localStorage.setItem(
    TASK_FILTERS_KEY,
    JSON.stringify({
      keyword: filters.keyword,
      status: filters.status,
      taskType: filters.taskType
    })
  );
}

function syncRouteState(patch: Record<string, string>) {
  const nextQuery = {
    ...route.query,
    ...patch
  } as Record<string, string>;
  for (const key of Object.keys(nextQuery)) {
    if (!nextQuery[key]) {
      delete nextQuery[key];
    }
  }
  void router.replace({ query: nextQuery });
}

function resetFilters() {
  filters.keyword = '';
  filters.status = '';
  filters.taskType = '';
}

function startAutoRefresh() {
  stopAutoRefresh();
  if (!autoRefreshEnabled.value) {
    return;
  }
  refreshTimer = window.setInterval(() => {
    void loadData();
  }, refreshSeconds.value * 1000);
}

function stopAutoRefresh() {
  if (refreshTimer) {
    window.clearInterval(refreshTimer);
    refreshTimer = null;
  }
}

watch([autoRefreshEnabled, refreshSeconds], () => {
  startAutoRefresh();
});

watch(
  () => [filters.keyword, filters.status, filters.taskType],
  () => {
    persistFilters();
    syncRouteState({
      keyword: filters.keyword,
      status: filters.status,
      taskType: filters.taskType
    });
  }
);

watch(
  () => route.query.taskId,
  (value) => {
    const taskId = Number(value);
    if (Number.isInteger(taskId) && taskId > 0) {
      const matchedTask = tasks.value.find((item) => item.taskId === taskId);
      if (matchedTask) {
        loadTask(matchedTask, false);
      }
    }
  }
);

onMounted(async () => {
  try {
    await loadData();
    startAutoRefresh();
  } catch (error) {
    ElMessage.error(`任务页初始化失败: ${(error as Error).message}`);
  }
});

onBeforeUnmount(() => {
  stopAutoRefresh();
});
</script>

<template>
  <div class="page-grid">
    <section class="stat-grid">
      <el-card shadow="hover">
        <p class="stat-label">启用中任务</p>
        <p class="stat-value">{{ enabledCount }}</p>
      </el-card>
      <el-card shadow="hover">
        <p class="stat-label">暂停任务</p>
        <p class="stat-value">{{ pausedCount }}</p>
      </el-card>
      <el-card shadow="hover">
        <p class="stat-label">运行中任务</p>
        <p class="stat-value">{{ runningCount }}</p>
      </el-card>
      <el-card shadow="hover">
        <p class="stat-label">最近同步时间</p>
        <p class="stat-value task-sync-time">{{ lastSyncedAt || '--:--:--' }}</p>
      </el-card>
    </section>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>调度面板</span>
          <div class="task-toolbar">
            <span class="stat-label">自动刷新</span>
            <el-switch v-model="autoRefreshEnabled" />
            <el-select v-model="refreshSeconds" class="refresh-select">
              <el-option label="10 秒" :value="10" />
              <el-option label="15 秒" :value="15" />
              <el-option label="30 秒" :value="30" />
            </el-select>
            <el-button link type="primary" @click="loadData(true)">立即刷新</el-button>
          </div>
        </div>
      </template>
      <el-alert
        :title="autoRefreshEnabled ? `自动刷新已开启，每 ${refreshSeconds} 秒拉取一次任务与日志结果` : '自动刷新已关闭，可手动刷新获取最新结果'"
        type="info"
        :closable="false"
      />
      <el-alert
        v-if="recentAction"
        class="notice-box"
        :title="recentAction.message"
        :type="recentAction.type"
        :closable="false"
      />
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>任务筛选</span>
          <div class="card-header-actions">
            <span class="inline-tip">已记忆上次筛选条件，刷新或重新进入页面后会自动回填。</span>
            <el-button link type="primary" @click="resetFilters">重置筛选</el-button>
          </div>
        </div>
      </template>
      <el-form inline>
        <el-form-item label="关键字">
          <el-input v-model="filters.keyword" placeholder="任务名 / 目标 / 说明" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态">
            <el-option label="ENABLED" value="ENABLED" />
            <el-option label="PAUSED" value="PAUSED" />
            <el-option label="RUNNING" value="RUNNING" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="filters.taskType" clearable placeholder="全部类型">
            <el-option label="GOVERNANCE" value="GOVERNANCE" />
            <el-option label="IMPORT" value="IMPORT" />
          </el-select>
        </el-form-item>
      </el-form>
      <el-alert
        class="notice-box"
        :title="`当前显示 ${filteredTasks.length} / ${tasks.length} 条任务，选中任务会同步写入地址栏，便于从日志页一键回填。`"
        type="info"
        :closable="false"
      />
    </el-card>

    <section class="two-column-grid">
      <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ editingTaskId ? '编辑任务' : '新建任务' }}</span>
          <div>
            <el-button link type="primary" @click="resetForm">重置</el-button>
            <el-button type="primary" :loading="loading" @click="submit">
              {{ editingTaskId ? '保存修改' : '创建任务' }}
            </el-button>
          </div>
        </div>
      </template>
      <el-form label-position="top">
        <el-form-item label="任务名称">
          <el-input v-model="form.taskName" placeholder="例如 每日治理流程执行" />
        </el-form-item>
        <el-form-item label="任务类型">
          <el-select v-model="form.taskType" @change="form.targetId = targetOptions[0]?.value">
            <el-option label="GOVERNANCE" value="GOVERNANCE" />
            <el-option label="IMPORT" value="IMPORT" />
          </el-select>
        </el-form-item>
        <el-form-item label="执行目标">
          <el-select v-model="form.targetId" placeholder="请选择目标">
            <el-option
              v-for="item in targetOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="Cron 表达式">
          <el-input v-model="form.cronExpr" placeholder="例如 0 */5 * * * *" />
        </el-form-item>
        <el-form-item label="失败重试次数">
          <el-input-number v-model="form.retryPolicy" :min="1" :max="5" class="full-width" />
        </el-form-item>
        <el-form-item label="初始状态">
          <el-select v-model="form.status">
            <el-option label="ENABLED" value="ENABLED" />
            <el-option label="PAUSED" value="PAUSED" />
          </el-select>
        </el-form-item>
        <el-form-item label="任务说明">
          <el-input v-model="form.description" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <el-alert
        class="notice-box"
        title="当前版本支持 IMPORT 和 GOVERNANCE 两类真实任务，调度器会按 nextRunTime 轮询执行"
        type="info"
        :closable="false"
      />
      </el-card>

      <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>任务调度列表</span>
          <div class="card-header-actions">
            <el-tag type="success">实时结果</el-tag>
            <span class="inline-tip">当前筛选结果 {{ filteredTasks.length }} 条</span>
          </div>
        </div>
      </template>
      <el-table :data="filteredTasks" stripe @row-click="(row: TaskSummary) => loadTask(row)">
        <el-table-column prop="taskName" label="任务名称" />
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            {{ formatTaskType(row.taskType) }}
          </template>
        </el-table-column>
        <el-table-column prop="targetName" label="执行目标" />
        <el-table-column label="调度状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最近执行结果" width="140">
          <template #default="{ row }">
            <el-tag :type="statusTagType(latestLog(row.taskId)?.status || '')">
              {{ latestLog(row.taskId)?.status || '暂无记录' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最近结果摘要">
          <template #default="{ row }">
            {{ latestLog(row.taskId)?.executionSummary || latestLog(row.taskId)?.errorMessage || '等待首次执行' }}
          </template>
        </el-table-column>
        <el-table-column prop="nextRunTime" label="下次执行时间" width="180" />
        <el-table-column prop="lastRunTime" label="最近执行时间" width="180" />
        <el-table-column label="操作" width="240">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="handleTrigger(row.taskId)">立即执行</el-button>
            <el-button link type="warning" @click.stop="handlePause(row.taskId)">暂停</el-button>
            <el-button link type="success" @click.stop="handleResume(row.taskId)">恢复</el-button>
          </template>
        </el-table-column>
      </el-table>
      </el-card>
    </section>
  </div>
</template>
