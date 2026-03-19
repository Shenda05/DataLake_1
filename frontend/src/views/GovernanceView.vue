<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { executeGovernance, listDatasets, listFlows, listOperators, saveFlow, type DatasetSummary, type GovernanceFlow, type OperatorDefinition } from '../api/platform';

type FlowStep = {
  operatorKey: string;
  field?: string;
  value?: string;
  targetField?: string;
  operator?: string;
  fieldsText?: string;
};

const datasets = ref<DatasetSummary[]>([]);
const operators = ref<OperatorDefinition[]>([]);
const flows = ref<GovernanceFlow[]>([]);
const executionResult = ref<any>(null);
const form = reactive({
  flowName: '',
  datasetId: undefined as number | undefined
});
const steps = ref<FlowStep[]>([
  { operatorKey: 'NULL_FILL', field: 'industry', value: 'UNKNOWN' },
  { operatorKey: 'DEDUPLICATE', fieldsText: 'company_name,patent_code' }
]);

function buildParams(step: FlowStep) {
  if (step.operatorKey === 'NULL_FILL') {
    return { field: step.field, value: step.value };
  }
  if (step.operatorKey === 'DEDUPLICATE') {
    return { fields: (step.fieldsText || '').split(',').map((item) => item.trim()).filter(Boolean) };
  }
  if (step.operatorKey === 'FIELD_CONVERT') {
    return { sourceField: step.field, targetField: step.targetField };
  }
  return { field: step.field, operator: step.operator || 'EQ', value: step.value };
}

async function loadBaseData() {
  datasets.value = await listDatasets();
  operators.value = await listOperators();
  flows.value = await listFlows();
  if (!form.datasetId && datasets.value.length > 0) {
    form.datasetId = datasets.value[0].datasetId;
  }
}

function addStep() {
  steps.value.push({ operatorKey: 'FILTER_KEEP', field: '', value: '', operator: 'EQ' });
}

async function handleSaveFlow() {
  if (!form.flowName || !form.datasetId) {
    ElMessage.warning('请填写流程名并选择输入数据集');
    return;
  }
  try {
    await saveFlow({
      flowName: form.flowName,
      datasetId: form.datasetId,
      operatorChain: steps.value.map((step) => ({ operatorKey: step.operatorKey, params: buildParams(step) }))
    });
    ElMessage.success('治理流程已保存');
    flows.value = await listFlows();
  } catch (error) {
    ElMessage.error(`保存流程失败: ${(error as Error).message}`);
  }
}

async function handleExecute() {
  if (!form.datasetId) {
    ElMessage.warning('请先选择输入数据集');
    return;
  }
  try {
    executionResult.value = await executeGovernance({
      datasetId: form.datasetId,
      operatorChain: steps.value.map((step) => ({ operatorKey: step.operatorKey, params: buildParams(step) }))
    });
    ElMessage.success('治理执行成功');
    flows.value = await listFlows();
  } catch (error) {
    ElMessage.error(`治理执行失败: ${(error as Error).message}`);
  }
}

onMounted(async () => {
  try {
    await loadBaseData();
  } catch (error) {
    ElMessage.error(`治理页初始化失败: ${(error as Error).message}`);
  }
});
</script>

<template>
  <div class="page-grid">
    <section class="two-column-grid">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>治理流程配置</span>
            <el-button type="primary" @click="addStep">新增步骤</el-button>
          </div>
        </template>
        <el-form label-position="top">
          <el-form-item label="流程名称">
            <el-input v-model="form.flowName" />
          </el-form-item>
          <el-form-item label="输入数据集">
            <el-select v-model="form.datasetId">
              <el-option
                v-for="dataset in datasets"
                :key="dataset.datasetId"
                :label="dataset.datasetName"
                :value="dataset.datasetId"
              />
            </el-select>
          </el-form-item>
          <div v-for="(step, index) in steps" :key="index" class="notice-box">
            <el-divider content-position="left">步骤 {{ index + 1 }}</el-divider>
            <el-form-item label="算子">
              <el-select v-model="step.operatorKey">
                <el-option
                  v-for="operator in operators"
                  :key="operator.operatorKey"
                  :label="operator.operatorName"
                  :value="operator.operatorKey"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="字段">
              <el-input v-model="step.field" />
            </el-form-item>
            <el-form-item v-if="step.operatorKey === 'DEDUPLICATE'" label="去重字段">
              <el-input v-model="step.fieldsText" placeholder="多个字段用逗号分隔" />
            </el-form-item>
            <el-form-item v-else-if="step.operatorKey === 'FIELD_CONVERT'" label="目标字段">
              <el-input v-model="step.targetField" />
            </el-form-item>
            <el-form-item v-else label="值">
              <el-input v-model="step.value" />
            </el-form-item>
            <el-form-item v-if="step.operatorKey === 'FILTER_KEEP'" label="过滤操作">
              <el-select v-model="step.operator">
                <el-option label="EQ" value="EQ" />
                <el-option label="LIKE" value="LIKE" />
                <el-option label="GT" value="GT" />
                <el-option label="LT" value="LT" />
              </el-select>
            </el-form-item>
          </div>
          <el-button type="primary" @click="handleSaveFlow">保存流程</el-button>
          <el-button type="success" @click="handleExecute">执行流程</el-button>
        </el-form>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>治理结果</span>
            <el-tag v-if="executionResult">{{ executionResult.outputDatasetId }}</el-tag>
          </div>
        </template>
        <div v-if="executionResult" class="plain-list">
          <div>输入数据集：{{ executionResult.inputDatasetId }}</div>
          <div>输出数据集：{{ executionResult.outputDatasetId }}</div>
          <div>输出数据集名：{{ executionResult.outputDatasetName }}</div>
          <div>执行算子数：{{ executionResult.operatorCount }}</div>
          <div>执行摘要：{{ executionResult.summary }}</div>
        </div>
        <div v-else class="plain-list">还没有执行结果。</div>
      </el-card>
    </section>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>已保存流程</span>
          <el-button link type="primary" @click="loadBaseData">刷新</el-button>
        </div>
      </template>
      <el-table :data="flows" stripe>
        <el-table-column prop="flowId" label="流程ID" width="100" />
        <el-table-column prop="flowName" label="流程名称" />
        <el-table-column prop="inputDatasetId" label="输入数据集" width="120" />
        <el-table-column prop="outputDatasetId" label="最近输出数据集" width="140" />
        <el-table-column prop="createTime" label="创建时间" />
      </el-table>
    </el-card>
  </div>
</template>

