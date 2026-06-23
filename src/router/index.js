import { createRouter, createWebHistory } from 'vue-router';
import DashboardView from '../views/DashboardView.vue';
import IndexingView from '../views/IndexingView.vue';
import AuditView from '../views/AuditView.vue';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: DashboardView },
    { path: '/indexing', component: IndexingView },
    { path: '/audit', component: AuditView }
  ]
});

export default router;