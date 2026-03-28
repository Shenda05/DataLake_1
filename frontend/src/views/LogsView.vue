<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { useRoute, useRouter } from 'vue-router';
import { isAuthExpiredError } from '../api/client';
import { getTaskLogDetail, listTaskLogs, replayTaskLog, type TaskLogDetail, type TaskLogSummary } from '../api/platform';
import { useAuthStore } from '../stores/auth';

const LOG_FILTERS_KEY = 'data-lake-log-filters';
const LOG_FILTER_VIEWS_KEY = 'data-lake-log-filter-views';
type LogFilterSnapshot = {
  keyword: string;
  status: string;
  taskType: string;
  targetId: string;
  timeRange: string[];
  failedOnly: boolean;
};
type SavedLogFilterView = {
  id: string;
  name: string;
  filters: LogFilterSnapshot;
};
const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const logs = ref<TaskLogSummary[]>([]);
const selectedLog = ref<TaskLogDetail | null>(null);
const lastSyncedAt = ref('');
const savedViewName = ref('');
const selectedViewId = ref('');
const savedViews = ref<SavedLogFilterView[]>([]);
const filters = reactive({
  keyword: '',
  status: '',
  taskType: '',
  targetId: '',
  timeRange: [] as string[],
  failedOnly: false
});

const logTargetOptions = computed(() => {
  const map = new Map<string, { value: string; label: string }>();
  for (const item of logs.value) {
    if (item.targetId == null) {
      continue;
    }
    const key = String(item.targetId);
    if (!map.has(key)) {
      map.set(key, {
        value: key,
        label: `${item.taskName} / ${formatTaskType(item.taskType)} / 目标 ${item.targetId}`
      });
    }
  }
  return Array.from(map.values());
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
    const matchesTarget = !filters.targetId || String(log.targetId ?? '') === filters.targetId;
    const matchesTimeRange = matchesDateRange(log.startTime || log.endTime, filters.timeRange);
    const matchesRecentFailed = !filters.failedOnly || isRecentFailed(log);
    return matchesKeyword && matchesStatus && matchesTaskType && matchesTarget && matchesTimeRange && matchesRecentFailed;
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
const recentFailedCount = computed(() => logs.value.filter((item) => isRecentFailed(item)).length);
// [已改造完成] 电商日志重点分类：导入、治理、调度、失败原因聚合。
const importLogCount = computed(() => logs.value.filter((item) => item.taskType === 'IMPORT').length);
const governanceLogCount = computed(() => logs.value.filter((item) => item.taskType === 'GOVERNANCE').length);
const scheduledLogCount = computed(() => logs.value.filter((item) => item.taskId != null).length);
const failureReasons = computed(() => {
  const counter = new Map<string, number>();
  for (const item of logs.value) {
    if (item.status !== 'FAILED') {
      continue;
    }
    const key = item.errorMessage || item.executionSummary || '未知失败原因';
    counter.set(key, (counter.get(key) || 0) + 1);
  }
  return Array.from(counter.entries())
    .sort((left, right) => right[1] - left[1])
    .slice(0, 5)
    .map(([reason, count]) => ({ reason, count }));
});
const canExportLogs = computed(() => authStore.hasAction('log.export'));
const canReplayLogs = computed(() => authStore.hasAction('log.replay'));
const selectedInputParamsText = computed(() => JSON.stringify(selectedLog.value?.inputParams || {}, null, 2));
const selectedExecutionSteps = computed(() => selectedLog.value?.executionSteps || []);
const selectedFailureReason = computed(() => selectedLog.value?.failureReason || null);

restoreFilters();
loadSavedViews();

async function loadData() {
  logs.value = await listTaskLogs();
  lastSyncedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false });
  const queryLogId = Number(route.query.logId);
  if (Number.isInteger(queryLogId) && queryLogId > 0 && logs.value.some((item) => item.logId === queryLogId)) {
    await selectLog(queryLogId, false);
  } else if (selectedLog.value && logs.value.some((item) => item.logId === selectedLog.value?.logId)) {
    await selectLog(selectedLog.value.logId, false);
  } else if (logs.value.length > 0) {
    await selectLog(logs.value[0].logId, false);
  } else {
    selectedLog.value = null;
  }
}

async function selectLog(logId: number, syncRoute = true) {
  selectedLog.value = await getTaskLogDetail(logId);
  if (syncRoute) {
    syncRouteState({ logId: String(logId) });
  }
}

async function handleReplay(log?: TaskLogSummary | TaskLogDetail | null) {
  if (!canReplayLogs.value) {
    ElMessage.warning('当前角色没有日志回放权限');
    return;
  }
  if (!log?.taskId) {
    ElMessage.warning('当前日志不是调度任务生成，暂不支持回放');
    return;
  }
  try {
    const result = await replayTaskLog(log.logId);
    ElMessage.success(result.message);
    await loadData();
  } catch (error) {
    ElMessage.error(`回放失败: ${(error as Error).message}`);
  }
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

function executionStepTagType(status: string) {
  switch (status) {
    case 'SUCCESS':
      return 'success';
    case 'FAILED':
      return 'danger';
    case 'RETRYING':
      return 'warning';
    case 'SKIPPED':
      return 'info';
    default:
      return '';
  }
}

function resetFilters() {
  filters.keyword = '';
  filters.status = '';
  filters.taskType = '';
  filters.targetId = '';
  filters.timeRange = [];
  filters.failedOnly = false;
}

function formatTaskType(taskType: string) {
  return taskType === 'GOVERNANCE' ? '治理任务' : taskType === 'IMPORT' ? '导入任务' : taskType;
}

function goToDashboard() {
  void router.push({ name: 'dashboard' });
}

function hasMenu(name: 'tasks' | 'governance' | 'imports') {
  return authStore.allowedMenus.includes(name as never);
}

function goToTasks(log?: TaskLogSummary | TaskLogDetail | null) {
  if (!log?.taskId) {
    return;
  }
  if (!hasMenu('tasks')) {
    ElMessage.warning('当前角色没有“电商任务调度”菜单权限');
    return;
  }
  void router.push({ name: 'tasks', query: { taskId: String(log.taskId), logId: String(log.logId) } });
}

function goToTaskCenter() {
  if (!hasMenu('tasks')) {
    ElMessage.warning('当前角色没有“电商任务调度”菜单权限');
    return;
  }
  void router.push({ name: 'tasks' });
}

function goToTarget(log?: TaskLogSummary | TaskLogDetail | null) {
  if (!log) {
    return;
  }
  if (log.taskType === 'GOVERNANCE') {
    if (!hasMenu('governance')) {
      ElMessage.warning('当前角色没有“电商数据治理”菜单权限');
      return;
    }
    void router.push({ name: 'governance', query: log.targetId ? { flowId: String(log.targetId) } : undefined });
    return;
  }
  if (log.taskType === 'IMPORT') {
    if (!hasMenu('imports')) {
      ElMessage.warning('当前角色没有“电商数据接入”菜单权限');
      return;
    }
    void router.push({ name: 'imports', query: log.targetId ? { importId: String(log.targetId) } : undefined });
  }
}

function getQueryValue(key: string) {
  const value = route.query[key];
  return Array.isArray(value) ? value[0] || '' : typeof value === 'string' ? value : '';
}

function toTimestamp(value?: string | null, endOfDay = false) {
  if (!value) {
    return null;
  }
  const normalized = value.length === 10 ? `${value}${endOfDay ? 'T23:59:59' : 'T00:00:00'}` : value;
  const timestamp = new Date(normalized).getTime();
  return Number.isNaN(timestamp) ? null : timestamp;
}

function matchesDateRange(value?: string | null, range: string[] = []) {
  if (!range.length || (!range[0] && !range[1])) {
    return true;
  }
  const valueTime = toTimestamp(value);
  if (valueTime === null) {
    return false;
  }
  const start = toTimestamp(range[0]);
  const end = toTimestamp(range[1], true);
  return (start === null || valueTime >= start) && (end === null || valueTime <= end);
}

function isRecentFailed(log?: TaskLogSummary | null) {
  if (!log || log.status !== 'FAILED') {
    return false;
  }
  const timestamp = toTimestamp(log.startTime || log.endTime);
  return timestamp !== null && Date.now() - timestamp <= 7 * 24 * 60 * 60 * 1000;
}

function snapshotFilters(): LogFilterSnapshot {
  return {
    keyword: filters.keyword,
    status: filters.status,
    taskType: filters.taskType,
    targetId: filters.targetId,
    timeRange: [...filters.timeRange],
    failedOnly: filters.failedOnly
  };
}

function restoreFilters() {
  const queryKeyword = getQueryValue('keyword');
  const queryStatus = getQueryValue('status');
  const queryTaskType = getQueryValue('taskType');
  const queryTargetId = getQueryValue('targetId');
  const queryStartDate = getQueryValue('startDate');
  const queryEndDate = getQueryValue('endDate');
  const queryFailedOnly = getQueryValue('failedOnly');
  const stored = localStorage.getItem(LOG_FILTERS_KEY);
  let parsed: { keyword?: string; status?: string; taskType?: string; targetId?: string; timeRange?: string[]; failedOnly?: boolean } = {};
  if (stored) {
    try {
      parsed = JSON.parse(stored) as { keyword?: string; status?: string; taskType?: string; targetId?: string; timeRange?: string[]; failedOnly?: boolean };
    } catch {
      localStorage.removeItem(LOG_FILTERS_KEY);
    }
  }
  filters.keyword = queryKeyword || parsed.keyword || '';
  filters.status = queryStatus || parsed.status || '';
  filters.taskType = queryTaskType || parsed.taskType || '';
  filters.targetId = queryTargetId || parsed.targetId || '';
  filters.timeRange =
    queryStartDate || queryEndDate
      ? [queryStartDate, queryEndDate].filter(Boolean)
      : Array.isArray(parsed.timeRange)
        ? parsed.timeRange
        : [];
  filters.failedOnly = queryFailedOnly === '1' || parsed.failedOnly || false;
}

function persistFilters() {
  localStorage.setItem(
    LOG_FILTERS_KEY,
    JSON.stringify(snapshotFilters())
  );
}

function loadSavedViews() {
  const stored = localStorage.getItem(LOG_FILTER_VIEWS_KEY);
  if (!stored) {
    savedViews.value = [];
    return;
  }
  try {
    const parsed = JSON.parse(stored) as SavedLogFilterView[];
    savedViews.value = Array.isArray(parsed) ? parsed : [];
  } catch {
    localStorage.removeItem(LOG_FILTER_VIEWS_KEY);
    savedViews.value = [];
  }
}

function persistSavedViews() {
  localStorage.setItem(LOG_FILTER_VIEWS_KEY, JSON.stringify(savedViews.value));
}

function applyFilterSnapshot(snapshot: LogFilterSnapshot) {
  filters.keyword = snapshot.keyword || '';
  filters.status = snapshot.status || '';
  filters.taskType = snapshot.taskType || '';
  filters.targetId = snapshot.targetId || '';
  filters.timeRange = Array.isArray(snapshot.timeRange) ? snapshot.timeRange : [];
  filters.failedOnly = Boolean(snapshot.failedOnly);
}

function saveCurrentView() {
  if (!savedViewName.value.trim()) {
    ElMessage.warning('请先输入视图名称');
    return;
  }
  const existingIndex = savedViews.value.findIndex((item) => item.name === savedViewName.value.trim());
  const nextView: SavedLogFilterView = {
    id: existingIndex >= 0 ? savedViews.value[existingIndex].id : `log-view-${Date.now()}`,
    name: savedViewName.value.trim(),
    filters: snapshotFilters()
  };
  if (existingIndex >= 0) {
    savedViews.value.splice(existingIndex, 1, nextView);
  } else {
    savedViews.value.unshift(nextView);
  }
  selectedViewId.value = nextView.id;
  persistSavedViews();
  ElMessage.success('已保存当前日志筛选视图');
}

function applySelectedView() {
  if (!selectedViewId.value) {
    return;
  }
  const matched = savedViews.value.find((item) => item.id === selectedViewId.value);
  if (!matched) {
    return;
  }
  savedViewName.value = matched.name;
  applyFilterSnapshot(matched.filters);
}

function deleteSelectedView() {
  if (!selectedViewId.value) {
    ElMessage.warning('请先选择要删除的视图');
    return;
  }
  savedViews.value = savedViews.value.filter((item) => item.id !== selectedViewId.value);
  selectedViewId.value = '';
  persistSavedViews();
  ElMessage.success('日志筛选视图已删除');
}

function focusLatestFailed() {
  const latestFailed = logs.value.find((item) => item.status === 'FAILED');
  if (!latestFailed) {
    ElMessage.info('当前没有失败日志');
    return;
  }
  filters.failedOnly = true;
  void selectLog(latestFailed.logId);
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

function buildCsv(rows: TaskLogSummary[]) {
  const headers = ['logId', 'taskId', 'taskName', 'taskType', 'targetId', 'status', 'startTime', 'endTime', 'duration', 'executionSummary', 'errorMessage'];
  return [headers.join(','), ...rows.map((row) =>
    headers
      .map((header) => {
        const value = row[header as keyof TaskLogSummary] == null ? '' : String(row[header as keyof TaskLogSummary]);
        const escaped = value.replace(/"/g, '""');
        return escaped.includes(',') || escaped.includes('"') || escaped.includes('\n') ? `"${escaped}"` : escaped;
      })
      .join(',')
  )].join('\n');
}

function exportLogs(format: 'csv' | 'json') {
  if (!canExportLogs.value) {
    ElMessage.warning('当前角色没有日志导出权限');
    return;
  }
  if (!filteredLogs.value.length) {
    ElMessage.warning('当前筛选结果为空，暂无可导出的日志');
    return;
  }
  const filename = `task-logs-${new Date().toISOString().slice(0, 19).replace(/[:T]/g, '-')}.${format}`;
  const blob =
    format === 'json'
      ? new Blob([JSON.stringify(filteredLogs.value, null, 2)], { type: 'application/json' })
      : new Blob([buildCsv(filteredLogs.value)], { type: 'text/csv;charset=utf-8' });
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
  ElMessage.success(`已导出 ${filteredLogs.value.length} 条日志为 ${format.toUpperCase()}`);
}

watch(
  () => [filters.keyword, filters.status, filters.taskType, filters.targetId, filters.timeRange[0], filters.timeRange[1], String(filters.failedOnly)],
  () => {
    persistFilters();
    syncRouteState({
      keyword: filters.keyword,
      status: filters.status,
      taskType: filters.taskType,
      targetId: filters.targetId,
      startDate: filters.timeRange[0] || '',
      endDate: filters.timeRange[1] || '',
      failedOnly: filters.failedOnly ? '1' : ''
    });
  }
);

watch(
  () => route.query.logId,
  async (value) => {
    const logId = Number(value);
    if (Number.isInteger(logId) && logId > 0 && logs.value.some((item) => item.logId === logId)) {
      await selectLog(logId, false);
    }
  }
);

onMounted(async () => {
  try {
    await loadData();
  } catch (error) {
    if (isAuthExpiredError(error)) {
      return;
    }
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
        <el-button v-if="hasMenu('tasks')" link type="primary" @click="goToTasks(selectedLog)">前往任务页</el-button>
      </el-card>
      <el-card shadow="hover">
        <p class="stat-label">平均耗时</p>
        <p class="stat-value">{{ avgDuration }}s</p>
        <el-button
          v-if="(selectedLog?.taskType === 'GOVERNANCE' && hasMenu('governance')) || (selectedLog?.taskType === 'IMPORT' && hasMenu('imports'))"
          link
          type="primary"
          @click="goToTarget(selectedLog)"
        >
          查看来源模块
        </el-button>
      </el-card>
      <el-card shadow="hover">
        <p class="stat-label">近 7 天失败日志</p>
        <p class="stat-value">{{ recentFailedCount }}</p>
        <el-button link type="primary" @click="focusLatestFailed">定位最近失败</el-button>
      </el-card>
      <el-card shadow="hover">
        <p class="stat-label">导入任务日志</p>
        <p class="stat-value">{{ importLogCount }}</p>
        <el-button link type="primary" @click="filters.taskType = 'IMPORT'">只看导入日志</el-button>
      </el-card>
      <el-card shadow="hover">
        <p class="stat-label">治理任务日志</p>
        <p class="stat-value">{{ governanceLogCount }}</p>
        <el-button link type="primary" @click="filters.taskType = 'GOVERNANCE'">只看治理日志</el-button>
      </el-card>
      <el-card shadow="hover">
        <p class="stat-label">调度执行日志</p>
        <p class="stat-value">{{ scheduledLogCount }}</p>
        <el-button v-if="hasMenu('tasks')" link type="primary" @click="goToTaskCenter">定位任务调度</el-button>
      </el-card>
    </section>

    <section class="two-column-grid">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>日志筛选</span>
            <div class="card-header-actions">
              <span class="inline-tip">最近同步 {{ lastSyncedAt || '--:--:--' }}</span>
              <span class="inline-tip">已记忆筛选条件并同步到地址栏</span>
              <el-button link type="primary" :disabled="!canExportLogs" @click="exportLogs('csv')">导出 CSV</el-button>
              <el-button link type="primary" :disabled="!canExportLogs" @click="exportLogs('json')">导出 JSON</el-button>
              <el-button link type="primary" @click="resetFilters">重置</el-button>
              <el-button link type="primary" @click="loadData">刷新</el-button>
              <el-button link type="primary" @click="goToDashboard">回首页</el-button>
            </div>
          </div>
        </template>
        <div class="saved-view-toolbar">
          <el-input v-model="savedViewName" class="saved-view-name" placeholder="保存为常用视图，例如：近 7 天失败日志" />
          <el-select v-model="selectedViewId" class="saved-view-select" clearable placeholder="选择已保存视图">
            <el-option
              v-for="item in savedViews"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
          <el-button @click="applySelectedView">应用视图</el-button>
          <el-button @click="saveCurrentView">保存当前视图</el-button>
          <el-button link type="danger" @click="deleteSelectedView">删除视图</el-button>
        </div>
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
          <el-form-item label="执行目标">
            <el-select v-model="filters.targetId" clearable placeholder="全部目标">
              <el-option
                v-for="item in logTargetOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="执行时间">
            <el-date-picker
              v-model="filters.timeRange"
              class="filter-range"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
            />
          </el-form-item>
          <el-form-item label="快速筛选">
            <el-checkbox v-model="filters.failedOnly">仅看近 7 天失败</el-checkbox>
          </el-form-item>
        </el-form>
        <el-alert
          class="notice-box"
          :title="`当前显示 ${filteredLogs.length} / ${logs.length} 条日志，目标、时间范围和近 7 天失败筛选都会被记忆。`"
          type="info"
          :closable="false"
        />
        <el-alert
          v-if="!canExportLogs"
          class="notice-box"
          title="当前角色只有日志查看权限，不能导出日志列表。"
          type="warning"
          :closable="false"
        />
      </el-card>
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>失败原因 Top5</span>
            <el-tag type="danger">{{ failureReasons.length }} 项</el-tag>
          </div>
        </template>
        <el-alert
          class="notice-box"
          title="按失败原因聚合，便于优先排查高频问题。"
          type="error"
          :closable="false"
        />
        <el-table :data="failureReasons" stripe>
          <el-table-column label="排名" width="80">
            <template #default="{ $index }">
              {{ $index + 1 }}
            </template>
          </el-table-column>
          <el-table-column prop="reason" label="失败原因" />
          <el-table-column prop="count" label="次数" width="100" />
        </el-table>
        <el-empty v-if="failureReasons.length === 0" description="当前暂无失败日志" />
      </el-card>
    </section>

    <section class="two-column-grid">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>任务日志</span>
          <div class="card-header-actions">
            <el-tag type="success">实时列表</el-tag>
            <span class="inline-tip">当前筛选结果 {{ filteredLogs.length }} 条</span>
            <el-button v-if="hasMenu('tasks')" link type="primary" @click="goToTasks(selectedLog)">查看对应任务</el-button>
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
            <el-button v-if="hasMenu('tasks')" link type="primary" @click.stop="goToTasks(row)">任务页</el-button>
            <el-button
              v-if="(row.taskType === 'GOVERNANCE' && hasMenu('governance')) || (row.taskType === 'IMPORT' && hasMenu('imports'))"
              link
              @click.stop="goToTarget(row)"
            >
              来源页
            </el-button>
            <el-button v-if="row.taskId && canReplayLogs" link type="danger" @click.stop="handleReplay(row)">回放</el-button>
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
            <el-button v-if="hasMenu('tasks')" link type="primary" @click="goToTasks(selectedLog)">对应任务</el-button>
            <el-button
              v-if="(selectedLog?.taskType === 'GOVERNANCE' && hasMenu('governance')) || (selectedLog?.taskType === 'IMPORT' && hasMenu('imports'))"
              link
              @click="goToTarget(selectedLog)"
            >
              来源页面
            </el-button>
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
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>输入参数</span>
              <el-tag type="info">{{ Object.keys(selectedLog.inputParams || {}).length }} 项</el-tag>
            </div>
          </template>
          <pre v-if="Object.keys(selectedLog.inputParams || {}).length">{{ selectedInputParamsText }}</pre>
          <el-alert
            v-else
            class="notice-box"
            title="暂无结构化输入参数（历史日志）"
            type="info"
            :closable="false"
          />
        </el-card>
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>执行步骤</span>
              <el-tag>{{ selectedExecutionSteps.length }} 步</el-tag>
            </div>
          </template>
          <el-table v-if="selectedExecutionSteps.length" :data="selectedExecutionSteps" stripe>
            <el-table-column prop="stepIndex" label="步骤" width="90" />
            <el-table-column prop="stepName" label="阶段/算子" width="220" />
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <el-tag :type="executionStepTagType(row.status)">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="detail" label="说明" />
          </el-table>
          <el-alert
            v-else
            class="notice-box"
            title="暂无结构化执行步骤（历史日志）"
            type="info"
            :closable="false"
          />
        </el-card>
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>结构化失败原因</span>
              <el-tag :type="selectedFailureReason ? 'danger' : 'info'">{{ selectedFailureReason ? '已记录' : '暂无' }}</el-tag>
            </div>
          </template>
          <el-descriptions v-if="selectedFailureReason" :column="1" border>
            <el-descriptions-item label="错误码">{{ selectedFailureReason.code || '-' }}</el-descriptions-item>
            <el-descriptions-item label="失败步骤">{{ selectedFailureReason.step || '-' }}</el-descriptions-item>
            <el-descriptions-item label="失败原因">{{ selectedFailureReason.reason || '-' }}</el-descriptions-item>
            <el-descriptions-item label="原始信息">{{ selectedFailureReason.rawMessage || '-' }}</el-descriptions-item>
          </el-descriptions>
          <el-alert
            v-else
            class="notice-box"
            title="当前日志未记录结构化失败原因（成功日志或历史日志）"
            type="info"
            :closable="false"
          />
        </el-card>
        <div class="detail-actions">
          <el-button v-if="hasMenu('tasks')" type="primary" @click="goToTasks(selectedLog)">去任务调度继续观察</el-button>
          <el-button
            v-if="(selectedLog?.taskType === 'GOVERNANCE' && hasMenu('governance')) || (selectedLog?.taskType === 'IMPORT' && hasMenu('imports'))"
            @click="goToTarget(selectedLog)"
          >
            回到来源模块
          </el-button>
          <el-button v-if="selectedLog.taskId && canReplayLogs" type="danger" plain @click="handleReplay(selectedLog)">一键回放</el-button>
          <el-button link type="primary" @click="goToDashboard">返回首页总览</el-button>
        </div>
      </div>
      <el-empty v-else description="暂无日志详情" />
    </el-card>
    </section>
  </div>
</template>
