<script setup lang="ts">
import { recentTasks } from '../mock/api';
</script>

<template>
  <div class="page-grid">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>任务调度</span>
          <el-button type="primary">新建任务</el-button>
        </div>
      </template>
      <el-table :data="recentTasks" stripe>
        <el-table-column prop="taskName" label="任务名称" />
        <el-table-column prop="taskType" label="类型" width="140" />
        <el-table-column prop="status" label="状态" width="140" />
        <el-table-column prop="nextRunTime" label="下次执行时间" />
        <el-table-column label="操作" width="220">
          <template #default>
            <el-button link type="primary">立即执行</el-button>
            <el-button link type="warning">暂停</el-button>
            <el-button link type="success">恢复</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

