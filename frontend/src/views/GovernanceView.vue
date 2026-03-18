<script setup lang="ts">
import { governanceOperators } from '../mock/api';

const flowChain = [
  { step: 1, name: '空值填充', params: 'industry -> UNKNOWN' },
  { step: 2, name: '重复数据清理', params: 'company_name + patent_code' }
];
</script>

<template>
  <div class="page-grid two-column-grid">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>可用算子</span>
          <el-tag type="success">Week 6</el-tag>
        </div>
      </template>
      <el-table :data="governanceOperators" stripe>
        <el-table-column prop="operatorName" label="算子名称" />
        <el-table-column prop="operatorType" label="类型" />
        <el-table-column prop="operatorKey" label="标识" />
      </el-table>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>治理流程</span>
          <el-button type="primary">执行流程</el-button>
        </div>
      </template>
      <el-steps direction="vertical" :active="flowChain.length">
        <el-step
          v-for="step in flowChain"
          :key="step.step"
          :title="`${step.step}. ${step.name}`"
          :description="step.params"
        />
      </el-steps>
      <el-alert
        class="notice-box"
        title="流程输出固定保存为新数据集，避免覆盖原始数据"
        type="info"
        :closable="false"
      />
    </el-card>
  </div>
</template>

