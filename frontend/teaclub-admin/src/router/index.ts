import { createRouter, createWebHistory, type Router } from 'vue-router'
import type { RouteRecordRaw, RouteRecordSingleView } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import Layout from '@/layout/index.vue'
import { fixedRoutes, flattenMenus, placeholderComponents, resolveView } from './routes'
import type { MenuNode } from '@/api/types'

const baseRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/login/index.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/',
    name: 'root',
    component: Layout,
    redirect: '/dashboard',
    children: [],
  },
]

const errorRoutes: RouteRecordRaw[] = [
  {
    path: '/403',
    name: 'forbidden',
    component: () => import('@/views/error/403.vue'),
    meta: { public: true, title: '无权限' },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/error/404.vue'),
    meta: { public: true, title: '页面不存在' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes: [...baseRoutes, ...errorRoutes],
  scrollBehavior: () => ({ top: 0 }),
})

const registered = new Set<string>()

function removeDynamicRoutes(target: Router): void {
  registered.forEach((name) => {
    if (target.hasRoute(name)) target.removeRoute(name)
  })
  registered.clear()
}

/** 由后端菜单树注册路由：父级 Layout 只做分组，叶子节点注册为 Layout 的子路由 */
export function registerDynamicRoutes(target: Router, menus: MenuNode[]): void {
  removeDynamicRoutes(target)
  flattenMenus(menus).forEach((item) => {
    if (target.hasRoute(item.name)) return
    const record: RouteRecordSingleView = {
      path: item.path,
      name: item.name,
      component: resolveView(item.component) as RouteRecordSingleView['component'],
      meta: {
        title: item.title,
        icon: item.icon,
        permCode: item.permCode,
        placeholder: placeholderComponents.has(item.component),
        fromMenu: true,
      },
    }
    target.addRoute('root', record)
    registered.add(item.name)
  })

  fixedRoutes.forEach((route) => {
    const name = String(route.name)
    if (target.hasRoute(name)) return
    target.addRoute('root', route)
    registered.add(name)
  })
}

export function resetDynamicRoutes(): void {
  removeDynamicRoutes(router)
}

router.beforeEach(async (to) => {
  const userStore = useUserStore()

  // 仅 /login 免登录；其余（含 403/404）都要求已登录，
  // 否则刷新或深链会先命中 catch-all 路由的 public 标记而绕过动态路由注册
  if (to.path === '/login') {
    if (userStore.token) {
      const redirect = typeof to.query.redirect === 'string' ? to.query.redirect : '/'
      return { path: redirect }
    }
    return true
  }

  if (!userStore.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if (!userStore.loaded) {
    try {
      await userStore.bootstrap()
      registerDynamicRoutes(router, userStore.menus)
      const appStore = useAppStore()
      await appStore.loadStores()
      return { path: to.path, query: to.query, hash: to.hash, replace: true }
    } catch (error) {
      userStore.reset()
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }

  return true
})

router.afterEach((to) => {
  const title = (to.meta.title as string) || ''
  document.title = title ? title + ' · TeaClub' : 'TeaClub 管理后台'
})

export default router
