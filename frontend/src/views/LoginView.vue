<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '../stores/auth';

const authStore = useAuthStore();
const router = useRouter();
const loading = ref(false);
const form = reactive({
  username: 'admin',
  password: 'admin123'
});

async function submit() {
  loading.value = true;
  try {
    await authStore.login(form.username, form.password);
    ElMessage.success('登录成功');
    router.push('/dashboard');
  } catch (error) {
    ElMessage.error((error as Error).message);
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-panel">
      <div>
        <p class="hero-kicker">Week 1-2 MVP</p>
        <h1>数据湖管理平台</h1>
        <p class="hero-text">
          从数据接入、元数据管理到治理、调度与日志监控，先把课程项目最关键的闭环跑通。
        </p>
      </div>
      <el-card shadow="never" class="login-card">
        <template #header>
          <div class="card-header">
            <span>账号登录</span>
            <el-tag>Mock Ready</el-tag>
          </div>
        </template>
        <el-form label-position="top">
          <el-form-item label="用户名">
            <el-input v-model="form.username" placeholder="请输入用户名" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="form.password" placeholder="请输入密码" show-password />
          </el-form-item>
          <el-button type="primary" class="full-width" :loading="loading" @click="submit">
            登录系统
          </el-button>
        </el-form>
        <div class="demo-accounts">
          <p>演示账号</p>
          <p>`admin / admin123`</p>
          <p>`operator / operator123`</p>
        </div>
      </el-card>
    </div>
  </div>
</template>

