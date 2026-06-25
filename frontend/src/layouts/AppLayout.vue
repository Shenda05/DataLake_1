<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
let profileSyncTimer: number | undefined;

const menuItems = computed(() => [
  { label: '电商仪表盘', key: 'dashboard', path: '/dashboard' },
  { label: '数据源管理', key: 'data-sources', path: '/data-sources' },
  { label: '电商数据接入', key: 'imports', path: '/imports' },
  { label: '电商数据集', key: 'datasets', path: '/datasets' },
  { label: '电商查询分析', key: 'queries', path: '/queries' },
  { label: '电商数据治理', key: 'governance', path: '/governance' },
  { label: '电商任务调度', key: 'tasks', path: '/tasks' },
  { label: '任务与日志', key: 'logs', path: '/logs' },
  { label: '用户与权限', key: 'users', path: '/users' }
].filter((item) => authStore.allowedMenus.includes(item.key as never)));

const fallbackPath = computed(() => menuItems.value[0]?.path || '/dashboard');

function navigate(path: string) {
  router.push(path);
}

function logout() {
  authStore.logout();
  router.push('/login');
}

async function syncProfile() {
  try {
    await authStore.syncProfile(true);
  } catch {
    return;
  }
  ensureCurrentRouteAccessible();
}

function ensureCurrentRouteAccessible() {
  const menuKey = route.meta.menuKey as string | undefined;
  if (menuKey && !authStore.allowedMenus.includes(menuKey as never)) {
    void router.replace(fallbackPath.value);
  }
}

watch(() => authStore.allowedMenus.slice(), ensureCurrentRouteAccessible);

onMounted(() => {
  void syncProfile();
  profileSyncTimer = window.setInterval(() => {
    void syncProfile();
  }, 30000);
});

onBeforeUnmount(() => {
  if (profileSyncTimer) {
    window.clearInterval(profileSyncTimer);
  }
});
</script>

<template>
  <el-container class="app-shell">
    <el-aside class="sidebar" width="240px">
      <div class="brand">
        <p class="brand-eyebrow">E-commerce</p>
        <h1>数据湖平台</h1>
      </div>
      <el-menu
        :default-active="route.path"
        class="menu"
        @select="navigate"
      >
        <el-menu-item
          v-for="item in menuItems"
          :key="item.path"
          :index="item.path"
        >
          {{ item.label }}
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="topbar">
        <div>
          <p class="topbar-title">{{ route.meta.title }}</p>
          <span class="topbar-subtitle">订单、商品、库存优先的电商数据湖控制台</span>
        </div>
        <div class="topbar-user">
          <span>{{ authStore.profile?.displayName }}</span>
          <el-tag effect="dark" round>{{ authStore.profile?.role }}</el-tag>
          <el-button link type="primary" @click="logout">退出</el-button>
        </div>
      </el-header>
      <el-main class="content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>
