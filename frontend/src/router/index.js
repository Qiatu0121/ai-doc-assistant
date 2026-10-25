import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '@/layout/AppLayout.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      component: AppLayout,
      redirect: '/documents',
      children: [
        {
          path: 'documents',
          name: 'documents',
          component: () => import('@/views/DocumentListView.vue'),
          meta: { title: '文档管理' },
        },
        {
          path: 'agent',
          name: 'agent',
          component: () => import('@/views/AgentChatView.vue'),
          meta: { title: 'Agent 问答' },
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/documents',
    },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

export default router
