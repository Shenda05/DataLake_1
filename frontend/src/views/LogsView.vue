<script setup lang="ts">
import { taskLogs } from '../mock/api';
</script>

<template>
  <div class="page-grid">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>任务日志</span>
          <el-tag type="danger">Week 7</el-tag>
        </div>
      </template>
      <el-table :data="taskLogs" stripe>
        <el-table-column prop="taskName" label="任务名称" />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column prop="startTime" label="开始时间" />
        <el-table-column prop="endTime" label="结束时间" />
        <el-table-column prop="duration" label="耗时(秒)" width="120" />
        <el-table-column prop="message" label="结果摘要" />
      </el-table>
    </el-card>
  </div>
</template>

