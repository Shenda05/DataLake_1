import { createRouter, createWebHistory } from 'vue-router';
import AppLayout from '../layouts/AppLayout.vue';
import LoginView from '../views/LoginView.vue';
import DashboardView from '../views/DashboardView.vue';
import DataSourcesView from '../views/DataSourcesView.vue';
import DataImportView from '../views/DataImportView.vue';
import DatasetsView from '../views/DatasetsView.vue';
import QueryAnalysisView from '../views/QueryAnalysisView.vue';
import GovernanceView from '../views/GovernanceView.vue';
import TasksView from '../views/TasksView.vue';
import LogsView from '../views/LogsView.vue';
import UsersView from '../views/UsersView.vue';
import { useAuthStore } from '../stores/auth';

const menuRouteMap: Record<string, string> = {
  dashboard: 'dashboard',
  'data-sources': 'data-sources',
  imports: 'imports',
  datasets: 'datasets',
  queries: 'queries',
  governance: 'governance',
  tasks: 'tasks',
  logs: 'logs',
  users: 'users'
};

const routes = [
  { path: '/login', name: 'login', component: LoginView, meta: { title: '登录' } },
  {
    path: '/',
    component: AppLayout,
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: '/dashboard' },
      { path: 'dashboard', name: 'dashboard', component: DashboardView, meta: { title: '首页', menuKey: 'dashboard' } },
      { path: 'data-sources', name: 'data-sources', component: DataSourcesView, meta: { title: '数据源管理', menuKey: 'data-sources' } },
      { path: 'imports', name: 'imports', component: DataImportView, meta: { title: '数据接入', menuKey: 'imports' } },
      { path: 'datasets', name: 'datasets', component: DatasetsView, meta: { title: '数据集管理', menuKey: 'datasets' } },
      { path: 'queries', name: 'queries', component: QueryAnalysisView, meta: { title: '查询分析', menuKey: 'queries' } },
      { path: 'governance', name: 'governance', component: GovernanceView, meta: { title: '数据治理', menuKey: 'governance' } },
      { path: 'tasks', name: 'tasks', component: TasksView, meta: { title: '任务调度', menuKey: 'tasks' } },
      { path: 'logs', name: 'logs', component: LogsView, meta: { title: '日志监控', menuKey: 'logs' } },
      { path: 'users', name: 'users', component: UsersView, meta: { title: '用户与权限', menuKey: 'users' } }
    ]
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

router.beforeEach(async (to) => {
  const authStore = useAuthStore();
  if (to.meta.title) {
    document.title = `${to.meta.title} - 数据湖管理平台`;
  }
  if (to.meta.requiresAuth && !authStore.isLoggedIn) {
    return { name: 'login' };
  }
  if (authStore.isLoggedIn) {
    try {
      await authStore.syncProfile();
    } catch {
      // A 401 will already trigger the client-side auth-expired redirect.
    }
  }
  if (authStore.isLoggedIn && authStore.allowedMenus.length === 0) {
    authStore.logout();
    return { name: 'login' };
  }
  if (to.meta.menuKey && !authStore.allowedMenus.includes(to.meta.menuKey as never)) {
    const fallbackMenu = authStore.allowedMenus[0];
    const fallbackRoute = fallbackMenu ? menuRouteMap[fallbackMenu] : null;
    return fallbackRoute ? { name: fallbackRoute } : { name: 'login' };
  }
  if (to.name === 'login' && authStore.isLoggedIn) {
    const fallbackMenu = authStore.allowedMenus[0] || 'dashboard';
    return { name: menuRouteMap[fallbackMenu] || 'dashboard' };
  }
  return true;
});

export default router;
