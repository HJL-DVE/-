/**
 * 金额一律以「分」为整数参与运算，展示时才转元。禁止浮点运算金额。
 */

export function fenToYuan(fen?: number | null): string {
  if (fen === null || fen === undefined || Number.isNaN(Number(fen))) return '-'
  const n = Math.trunc(Number(fen))
  const negative = n < 0
  const abs = Math.abs(n)
  const yuan = Math.trunc(abs / 100)
  const cents = abs % 100
  return (negative ? '-' : '') + String(yuan) + '.' + String(cents).padStart(2, '0')
}

export function fenToYuanNumber(fen?: number | null): number {
  return Math.trunc(Number(fen ?? 0)) / 100
}

export function yuanToFen(input: string | number | null | undefined): number {
  if (input === null || input === undefined) return NaN
  const s = String(input).trim()
  if (!/^-?\d+(\.\d{1,2})?$/.test(s)) return NaN
  const negative = s.startsWith('-')
  const body = negative ? s.slice(1) : s
  const [intPart, decPart = ''] = body.split('.')
  const cents = decPart.padEnd(2, '0').slice(0, 2)
  const value = Number(intPart) * 100 + Number(cents)
  return negative ? -value : value
}

export function formatDateTime(value?: string | null): string {
  if (!value) return '-'
  const s = String(value).replace('T', ' ')
  return s.length > 19 ? s.slice(0, 19) : s
}

export function formatDate(value?: string | null): string {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 10)
}

export const ORDER_STATUS_LABEL: Record<string, string> = {
  UNPAID: '待支付',
  PAID: '已支付',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  REFUNDING: '退款中',
  REFUNDED: '已退款',
  CLOSED: '已关闭',
}

export const ORDER_STATUS_TAG: Record<string, string> = {
  UNPAID: 'warning',
  PAID: 'success',
  COMPLETED: 'success',
  CANCELLED: 'info',
  CLOSED: 'info',
  REFUNDING: 'warning',
  REFUNDED: 'danger',
}

export const PAY_STATUS_LABEL: Record<string, string> = {
  CREATED: '待支付',
  PAYING: '支付中',
  SUCCESS: '支付成功',
  FAILED: '支付失败',
  CLOSED: '已关闭',
  REFUNDED: '已退款',
  PARTIAL_REFUNDED: '部分退款',
}

export const REFUND_STATUS_LABEL: Record<string, string> = {
  CREATED: '待处理',
  PROCESSING: '处理中',
  SUCCESS: '退款成功',
  FAILED: '退款失败',
  CLOSED: '已关闭',
}

export const MEMBER_STATUS_LABEL: Record<string, string> = {
  NORMAL: '正常',
  FROZEN: '已冻结',
  DISABLED: '已停用',
}

export const STORE_STATUS_LABEL: Record<string, string> = {
  OPEN: '营业中',
  CLOSED: '已停业',
  SUSPENDED: '暂停营业',
  PREPARING: '筹备中',
}

/** 钱包流水业务类型（后端实际枚举，另优先使用 bizTypeName） */
export const BIZ_TYPE_LABEL: Record<string, string> = {
  CONSUME_OUT: '消费扣款',
  RECHARGE_IN: '充值入账',
  REFUND_IN: '退款入账',
  ADMIN_ADJUST: '管理调整',
  RECHARGE_REFUND_OUT: '充值退款扣回',
  FREEZE: '冻结',
  FREEZE_CONFIRM: '冻结确认',
  FREEZE_RELEASE: '冻结释放',
}

export const RECHARGE_STATUS_LABEL: Record<string, string> = {
  PENDING: '待支付',
  PAID: '已支付',
  FAILED: '支付失败',
  CANCELLED: '已取消',
  CLOSED: '已关闭',
  REFUNDED: '已退卡',
}

export const PAY_CHANNEL_LABEL: Record<string, string> = {
  WECHAT_JSAPI: '微信 JSAPI',
  WECHAT_NATIVE: '微信 Native',
  MOCK: '模拟支付',
  BALANCE: '钱包余额',
}

/** 通用启用/停用状态（ENABLED/DISABLED）及商品上下架（ON_SALE/OFF_SALE） */
export const ENABLED_LABEL: Record<string, string> = {
  ENABLED: '已启用',
  DISABLED: '已停用',
  ON_SALE: '在售',
  OFF_SALE: '已下架',
}

/** 权限类型 / 注册渠道等零散枚举的兜底映射 */
export const PERM_TYPE_LABEL: Record<string, string> = {
  MENU: '菜单',
  BUTTON: '按钮',
  API: '接口',
}

/** 支付通知记录枚举（notify-logs） */
export const NOTIFY_TYPE_LABEL: Record<string, string> = {
  PAY: '支付通知',
  REFUND: '退款通知',
}

export const EVENT_TYPE_LABEL: Record<string, string> = {
  'TRANSACTION.SUCCESS': '交易成功',
  'REFUND.SUCCESS': '退款成功',
  'TRANSACTION.SUCCESS ': '交易成功',
}

export const PROCESS_STATUS_LABEL: Record<string, string> = {
  PROCESSED: '已处理',
  PROCESSING: '处理中',
  FAILED: '处理失败',
  PENDING: '待处理',
  IGNORED: '已忽略',
}

export const VERIFY_RESULT_LABEL: Record<string, string> = {
  '1': '验签通过',
  '0': '验签失败',
}

/** 金额正负号展示：钱包流水无 direction 字段，按变动值正负判断 */
export function signedFen(fen?: number | null): string {
  const n = Math.trunc(Number(fen ?? 0))
  if (n === 0) return '0.00'
  return (n > 0 ? '+' : '-') + fenToYuan(Math.abs(n))
}

export function tagTypeByStatus(status?: string | null): string {
  switch (status) {
    case 'SUCCESS':
    case 'PAID':
    case 'COMPLETED':
    case 'NORMAL':
    case 'OPEN':
    case 'ENABLED':
    case 'ON_SALE':
      return 'success'
    case 'FAILED':
    case 'FROZEN':
    case 'REFUNDED':
    case 'DISABLED':
    case 'OFF_SALE':
      return 'danger'
    case 'CREATED':
    case 'PAYING':
    case 'UNPAID':
    case 'PROCESSING':
    case 'REFUNDING':
    case 'SUSPENDED':
      return 'warning'
    default:
      return 'info'
  }
}
