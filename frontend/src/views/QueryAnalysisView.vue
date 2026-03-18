<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import * as echarts from 'echarts';
import { datasets } from '../mock/api';

const chartRef = ref<HTMLDivElement | null>(null);
let chart: echarts.ECharts | null = null;

const tableData = [
  { company_name: '示例科技', industry: '智能制造', patent_count: 22 },
  { company_name: '未来工业', industry: '新能源', patent_count: 14 },
  { company_name: '启明数据', industry: '人工智能', patent_count: 18 }
];

onMounted(async () => {
  await nextTick();
  if (!chartRef.value) return;
  chart = echarts.init(chartRef.value);
  chart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: tableData.map((item) => item.company_name) },
    yAxis: { type: 'value' },
    series: [
      {
        type: 'bar',
        data: tableData.map((item) => item.patent_count),
        itemStyle: { color: '#db6f44' }
      }
    ]
  });
});

onBeforeUnmount(() => chart?.dispose());
</script>

<template>
  <div class="page-grid">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>条件查询与 SQL 查询</span>
          <el-tag type="warning">Week 5</el-tag>
        </div>
      </template>
      <el-form inline>
        <el-form-item label="数据集">
          <el-select placeholder="请选择数据集">
            <el-option
              v-for="dataset in datasets"
              :key="dataset.datasetId"
              :label="dataset.datasetName"
              :value="dataset.datasetId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="字段">
          <el-input placeholder="例如 industry" />
        </el-form-item>
        <el-form-item label="条件值">
          <el-input placeholder="例如 智能制造" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary">执行查询</el-button>
        </el-form-item>
      </el-form>
      <el-input
        type="textarea"
        :rows="4"
        placeholder="SELECT company_name, patent_count FROM patent_dataset WHERE patent_count > 10 ORDER BY patent_count DESC"
      />
    </el-card>

    <section class="two-column-grid">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>查询结果</span>
            <el-button link type="primary">导出 CSV/JSON</el-button>
          </div>
        </template>
        <el-table :data="tableData" stripe>
          <el-table-column prop="company_name" label="企业名称" />
          <el-table-column prop="industry" label="行业" />
          <el-table-column prop="patent_count" label="专利数量" />
        </el-table>
      </el-card>
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>图表可视化</span>
            <el-tag>ECharts</el-tag>
          </div>
        </template>
        <div ref="chartRef" class="chart-box"></div>
      </el-card>
    </section>
  </div>
</template>

