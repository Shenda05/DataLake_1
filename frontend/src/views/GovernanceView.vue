<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { isAuthExpiredError } from '../api/client';
import {
  createGovernanceFlow,
  executeGovernanceFlow,
  listDatasets,
  listGovernanceFlows,
  listGovernanceOperators,
  type DatasetSummary,
  type GovernanceFlow,
  type GovernanceOperator
} from '../api/platform';
import { useAuthStore } from '../stores/auth';

type EditableStep = {
  operatorKey: string;
  paramsText: string;
};

const datasets = ref<DatasetSummary[]>([]);
const operators = ref<GovernanceOperator[]>([]);
const flows = ref<GovernanceFlow[]>([]);
const executionResult = ref<{ outputDatasetName: string; summary: string; logRef: number } | null>(null);
const loading = ref(false);
const editingFlowId = ref<number | null>(null);
const form = reactive({
  datasetId: undefined as number | undefined,
  flowName: '',
  executionName: ''
});
const steps = ref<EditableStep[]>([
  { operatorKey: 'ORDER_DEDUP', paramsText: '{\n  "field": "order_id"\n}' },
  { operatorKey: 'AMOUNT_NORMALIZE', paramsText: '{\n  "field": "amount"\n}' },
  { operatorKey: 'TIME_NORMALIZE', paramsText: '{\n  "field": "order_time"\n}' }
]);
// [Ecom-MVP Completed] 电商治理流程模板，复用通用治理引擎
const ecommerceFlowTemplates = [
  {
    key: 'ORDER',
    label: '订单自动治理',
    flowName: '订单自动治理流程',
    steps: [
      { operatorKey: 'ORDER_DEDUP', paramsText: '{\n  "field": "order_id"\n}' },
      { operatorKey: 'AMOUNT_NORMALIZE', paramsText: '{\n  "field": "amount"\n}' },
      { operatorKey: 'TIME_NORMALIZE', paramsText: '{\n  "field": "order_time"\n}' },
      { operatorKey: 'STATUS_NORMALIZE', paramsText: '{\n  "field": "order_status"\n}' }
    ]
  },
  {
    key: 'PRODUCT',
    label: '商品分类治理',
    flowName: '商品分类标准化流程',
    steps: [
      { operatorKey: 'CATEGORY_NORMALIZE', paramsText: '{\n  "field": "category"\n}' },
      { operatorKey: 'STATUS_NORMALIZE', paramsText: '{\n  "field": "status"\n}' }
    ]
  }
];
const authStore = useAuthStore();
const canManageGovernance = computed(() => authStore.hasAction('governance.manage'));
const canExecuteGovernance = computed(() => authStore.hasAction('governance.execute'));
const canEditWorkflow = computed(() => canManageGovernance.value || canExecuteGovernance.value);
const governancePermissionHint = computed(() => {
  if (canManageGovernance.value && canExecuteGovernance.value) {
    return '';
  }
  if (canExecuteGovernance.value) {
    return '当前角色可临时编排算子并执行治理，但不能保存治理流程。';
  }
  if (canManageGovernance.value) {
    return '当前角色可保存治理流程，但不能直接执行治理。';
  }
  return '当前角色只有治理查看权限，不能编排、保存或执行治理流程。';
});

const selectedDatasetName = computed(() => datasets.value.find((item) => item.datasetId === form.datasetId)?.datasetName || '未选择');

async function loadData() {
  datasets.value = await listDatasets();
  operators.value = await listGovernanceOperators();
  flows.value = await listGovernanceFlows();
  if (!form.datasetId && datasets.value.length > 0) {
    form.datasetId = datasets.value[0].datasetId;
  }
}

function addStep() {
  if (!canEditWorkflow.value) {
    ElMessage.warning('当前角色没有治理操作权限');
    return;
  }
  steps.value.push({
    operatorKey: operators.value[0]?.operatorKey || 'NULL_FILL',
    paramsText: '{\n  "field": ""\n}'
  });
}

function removeStep(index: number) {
  if (!canEditWorkflow.value) {
    ElMessage.warning('当前角色没有治理操作权限');
    return;
  }
  if (steps.value.length === 1) {
    ElMessage.warning('至少保留一个治理步骤');
    return;
  }
  steps.value.splice(index, 1);
}

function resetForm() {
  editingFlowId.value = null;
  form.flowName = '';
  form.executionName = '';
  steps.value = [
    { operatorKey: 'ORDER_DEDUP', paramsText: '{\n  "field": "order_id"\n}' },
    { operatorKey: 'AMOUNT_NORMALIZE', paramsText: '{\n  "field": "amount"\n}' },
    { operatorKey: 'TIME_NORMALIZE', paramsText: '{\n  "field": "order_time"\n}' }
  ];
}

function loadFlow(flow: GovernanceFlow) {
  editingFlowId.value = flow.flowId;
  form.datasetId = flow.inputDatasetId;
  form.flowName = flow.flowName;
  form.executionName = flow.flowName;
  steps.value = flow.operatorChain.map((step) => ({
    operatorKey: step.operatorKey,
    paramsText: JSON.stringify(step.params || {}, null, 2)
  }));
}

function applyEcommerceTemplate(templateKey: string) {
  const template = ecommerceFlowTemplates.find((item) => item.key === templateKey);
  if (!template) {
    return;
  }
  form.flowName = template.flowName;
  form.executionName = template.flowName;
  steps.value = template.steps.map((step) => ({ ...step }));
  ElMessage.success(`已套用模板：${template.label}`);
}

function buildOperatorChain() {
  return steps.value.map((step, index) => {
    try {
      return {
        operatorKey: step.operatorKey,
        params: step.paramsText.trim() ? (JSON.parse(step.paramsText) as Record<string, unknown>) : {}
      };
    } catch (error) {
      throw new Error(`第 ${index + 1} 步参数 JSON 无法解析`);
    }
  });
}

async function saveFlow() {
  if (!canManageGovernance.value) {
    ElMessage.warning('当前角色没有治理流程保存权限');
    return;
  }
  if (!form.datasetId || !form.flowName) {
    ElMessage.warning('请先选择输入数据集并填写流程名称');
    return;
  }
  loading.value = true;
  try {
    const operatorChain = buildOperatorChain();
    const result = await createGovernanceFlow({
      flowName: form.flowName,
      datasetId: form.datasetId,
      operatorChain
    });
    ElMessage.success(`治理流程已保存：${result.flowName}`);
    await loadData();
  } catch (error) {
    ElMessage.error(`保存流程失败: ${(error as Error).message}`);
  } finally {
    loading.value = false;
  }
}

async function executeFlow() {
  if (!canExecuteGovernance.value) {
    ElMessage.warning('当前角色没有治理执行权限');
    return;
  }
  if (!form.datasetId) {
    ElMessage.warning('请先选择输入数据集');
    return;
  }
  loading.value = true;
  try {
    const result = await executeGovernanceFlow({
      datasetId: form.datasetId,
      operatorChain: buildOperatorChain(),
      executionName: form.executionName || form.flowName || undefined
    });
    executionResult.value = {
      outputDatasetName: result.outputDatasetName,
      summary: result.summary,
      logRef: result.logRef
    };
    ElMessage.success(`治理完成，输出数据集 ${result.outputDatasetName}`);
    await loadData();
  } catch (error) {
    ElMessage.error(`执行治理失败: ${(error as Error).message}`);
  } finally {
    loading.value = false;
  }
}

onMounted(async () => {
  try {
    await loadData();
  } catch (error) {
    if (isAuthExpiredError(error)) {
      return;
    }
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
            <span>电商治理算子（兼容通用）</span>
            <el-tag type="success">Real API</el-tag>
          </div>
        </template>
        <el-table :data="operators" stripe>
          <el-table-column prop="operatorName" label="算子名称" />
          <el-table-column prop="operatorType" label="类型" width="120" />
          <el-table-column prop="operatorKey" label="标识" width="180" />
          <el-table-column prop="description" label="说明" />
        </el-table>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>{{ editingFlowId ? '编辑电商治理流程' : '电商治理流程编排' }}</span>
            <div>
              <el-button link type="primary" @click="resetForm">重置</el-button>
              <el-button type="primary" :loading="loading" :disabled="!canExecuteGovernance" @click="executeFlow">执行流程</el-button>
            </div>
          </div>
        </template>
        <div class="card-header-actions">
          <span class="inline-tip">快捷模板：</span>
          <el-button
            v-for="template in ecommerceFlowTemplates"
            :key="template.key"
            size="small"
            :disabled="!canEditWorkflow"
            @click="applyEcommerceTemplate(template.key)"
          >
            {{ template.label }}
          </el-button>
        </div>
        <el-alert
          v-if="governancePermissionHint"
          class="notice-box"
          :title="governancePermissionHint"
          type="warning"
          :closable="false"
        />
        <el-form label-position="top">
          <el-form-item label="输入数据集">
            <el-select v-model="form.datasetId" placeholder="请选择数据集" :disabled="!canEditWorkflow">
              <el-option
                v-for="dataset in datasets"
                :key="dataset.datasetId"
                :label="`${dataset.datasetName} (${dataset.businessDomain})`"
                :value="dataset.datasetId"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="流程名称">
            <el-input v-model="form.flowName" placeholder="例如 订单自动治理流程" :disabled="!canManageGovernance" />
          </el-form-item>
          <el-form-item label="执行名称">
            <el-input v-model="form.executionName" placeholder="留空则自动生成输出数据集名称" :disabled="!canExecuteGovernance" />
          </el-form-item>
        </el-form>

        <div class="page-grid">
          <el-card
            v-for="(step, index) in steps"
            :key="index"
            shadow="hover"
            class="embedded-card"
          >
            <template #header>
              <div class="card-header">
                <span>步骤 {{ index + 1 }}</span>
                <el-button link type="danger" :disabled="!canEditWorkflow" @click="removeStep(index)">删除</el-button>
              </div>
            </template>
            <el-form label-position="top">
              <el-form-item label="算子">
                <el-select v-model="step.operatorKey" :disabled="!canEditWorkflow">
                  <el-option
                    v-for="operator in operators"
                    :key="operator.operatorKey"
                    :label="operator.operatorName"
                    :value="operator.operatorKey"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="参数 JSON">
                <el-input v-model="step.paramsText" type="textarea" :rows="5" :disabled="!canEditWorkflow" />
              </el-form-item>
            </el-form>
          </el-card>
        </div>

        <div class="action-row">
          <el-button plain :disabled="!canEditWorkflow" @click="addStep">新增步骤</el-button>
          <el-button type="success" plain :loading="loading" :disabled="!canManageGovernance" @click="saveFlow">保存流程</el-button>
        </div>

        <el-alert
          class="notice-box"
          :title="`当前输入数据集：${selectedDatasetName}`"
          type="info"
          :closable="false"
        />
        <el-alert
          class="notice-box"
          title="已支持订单去重、金额标准化、时间标准化、商品分类标准化、状态标准化算子。"
          type="success"
          :closable="false"
        />
        <el-alert
          class="notice-box"
          title="待增强：字段映射向导和算子参数可视化配置将在下一轮补齐。"
          type="warning"
          :closable="false"
        />
        <el-alert
          v-if="executionResult"
          class="notice-box"
          :title="executionResult.summary"
          :description="`输出数据集：${executionResult.outputDatasetName}，日志编号：${executionResult.logRef}`"
          type="success"
          :closable="false"
        />
      </el-card>
    </section>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>已保存电商治理流程</span>
          <el-button link type="primary" @click="loadData">刷新</el-button>
        </div>
      </template>
      <el-table :data="flows" stripe @row-click="loadFlow">
        <el-table-column prop="flowName" label="流程名称" />
        <el-table-column prop="inputDatasetName" label="输入数据集" />
        <el-table-column prop="outputDatasetName" label="最近输出数据集" />
        <el-table-column label="步骤数" width="100">
          <template #default="{ row }">
            {{ row.operatorChain.length }}
          </template>
        </el-table-column>
        <el-table-column prop="creatorName" label="创建人" width="120" />
        <el-table-column prop="updateTime" label="更新时间" width="180" />
      </el-table>
    </el-card>
  </div>
</template>
