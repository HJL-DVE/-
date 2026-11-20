import type { RouteRecordRaw } from 'vue-router'
import type { MenuNode } from '@/api/types'

type LazyView = () => Promise<unknown>

/**
 * 后端菜单只提供 component 标识（如 "member/list"），
 * 页面组件必须在前端本地注册 —— 不假设后端菜单能自动生成组件。
 */
export const viewRegistry: Record<string, LazyView> = {
  'dashboard/index': () => import('@/views/dashboard/index.vue'),
  'store/list': () => import('@/views/store/list.vue'),
  'brand/detail': () => import('@/views/store/brand.vue'),
  'system/user': () => import('@/views/system/user.vue'),
  'system/role': () => import('@/views/system/role.vue'),
  'system/permission': () => import('@/views/system/permission.vue'),
  'system/log': () => import('@/views/system/log.vue'),
  'member/list': () => import('@/views/member/list.vue'),
  'member/level': () => import('@/views/member/level.vue'),
  'member/detail': () => import('@/views/member/detail.vue'),
  'product/spu': () => import('@/views/product/spu.vue'),
  'product/category': () => import('@/views/product/category.vue'),
  'product/store': () => import('@/views/product/store.vue'),
  'order/list': () => import('@/views/order/list.vue'),
  'order/statistics': () => import('@/views/order/statistics.vue'),
  'wallet/recharge': () => import('@/views/wallet/recharge.vue'),
  'wallet/plan': () => import('@/views/wallet/plan.vue'),
  'wallet/transaction': () => import('@/views/wallet/transaction.vue'),
  'wallet/account': () => import('@/views/wallet/account.vue'),
  'payment/list': () => import('@/views/payment/list.vue'),
  'payment/order': () => import('@/views/payment/list.vue'),
  'payment/refund': () => import('@/views/payment/refund.vue'),
  'payment/notify': () => import('@/views/payment/notify.vue'),
  'cashier/index': () => import('@/views/cashier/index.vue'),
  // ===== 阶段八：经营报表 =====
  'report/dashboard': () => import('@/views/report/dashboard.vue'),
  'report/store': () => import('@/views/report/store.vue'),
  'report/sales': () => import('@/views/report/sales.vue'),
  'report/member': () => import('@/views/report/member.vue'),
  'report/recharge': () => import('@/views/report/recharge.vue'),
  'report/payment': () => import('@/views/report/payment.vue'),
  'report/refund': () => import('@/views/report/refund.vue'),
  'report/product': () => import('@/views/report/product.vue'),
  'report/marketing': () => import('@/views/report/marketing.vue'),
  // 营销模块（component 标识与 iam_db.sys_permission 完全一致）
  'marketing/coupon': () => import('@/views/marketing/coupon.vue'),
  'marketing/level': () => import('@/views/marketing/level.vue'),
  // 注意：iam_db.sys_permission 实际值是驼峰 'marketing/pointsRule'，
  // 契约文档写作 'marketing/points-rule'，两个 key 同时注册以兼容，
  // 避免菜单命中 placeholder。
  'marketing/pointsRule': () => import('@/views/marketing/pointsRule.vue'),
  'marketing/points-rule': () => import('@/views/marketing/pointsRule.vue'),
  'marketing/points': () => import('@/views/marketing/points.vue'),
  'marketing/activity': () => import('@/views/marketing/activity.vue'),
  'marketing/stat': () => import('@/views/marketing/stat.vue'),
}

/** 菜单里存在、但不在 docs/33 第四节 13 页清单内的条目：显式占位，不伪造页面 */
export const placeholderComponents = new Set<string>([
])

export function resolveView(component?: string | null): LazyView {
  if (!component) return () => import('@/views/common/placeholder.vue')
  const found = viewRegistry[component]
  if (found) return found
  return () => import('@/views/common/placeholder.vue')
}

/** 不依赖菜单的固定路由（详情页 / 工作台 / 契约别名路径） */
export const fixedRoutes: RouteRecordRaw[] = [
  {
    path: '/member/detail/:id',
    name: 'member-detail',
    component: () => import('@/views/member/detail.vue'),
    meta: { title: '会员详情', hidden: true },
  },
  {
    path: '/cashier',
    name: 'cashier',
    component: () => import('@/views/cashier/index.vue'),
    meta: { title: '收银工作台', icon: 'ShoppingCartFull' },
  },
  {
    // docs/33 第四节 支付记录路由为 /payment/list，数据库菜单用 /payment/order，二者同页
    path: '/payment/list',
    name: 'payment-list-alias',
    component: () => import('@/views/payment/list.vue'),
    meta: { title: '支付记录', hidden: true },
  },
]

export interface FlatMenu {
  path: string
  name: string
  component: string
  title: string
  permCode: string
  icon?: string | null
}

export function flattenMenus(nodes: MenuNode[], result: FlatMenu[] = []): FlatMenu[] {
  nodes.forEach((node) => {
    const isGroup = node.component === 'Layout' || (node.children && node.children.length > 0)
    if (!isGroup && node.routePath) {
      result.push({
        path: node.routePath,
        name: 'menu:' + node.routePath,
        component: node.component,
        title: node.permName,
        permCode: node.permCode,
        icon: node.icon,
      })
    }
    if (node.children && node.children.length > 0) flattenMenus(node.children, result)
  })
  return result
}
