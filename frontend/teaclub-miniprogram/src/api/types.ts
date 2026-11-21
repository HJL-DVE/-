/**
 * 会员端出参类型。字段以 **网关实测响应** 为准（2026-11-20 直连 127.0.0.1:9000 抓取），
 * 与 docs/33 第三节契约字段一致；契约未列出的附加字段一并声明，避免再次错位。
 */

export interface PageResult<T> {
  records: T[]
  total: number
  pageNo: number
  pageSize: number
  totalPages: number
}

/** POST /api/auth/wx-login 实测：token, expiresIn, memberId, memberNo, isNew, nickname, avatarUrl, phone */
export interface WxLoginResult {
  token: string
  expiresIn: number
  memberId: number
  memberNo: string
  isNew: boolean
  nickname: string | null
  avatarUrl: string | null
  phone: string | null
}

/** GET /api/auth/stores 实测：id, storeName, address, phone, businessStatus, openTime, closeTime, latitude, longitude */
export interface StoreItem {
  id: number
  storeName: string
  address: string | null
  phone: string | null
  businessStatus: string | null
  openTime: string | null
  closeTime: string | null
  latitude: number | null
  longitude: number | null
}

/** GET /api/auth/member/me、/api/member/profile 实测（MemberVO） */
export interface MemberProfile {
  id?: number
  memberNo?: string
  phone?: string | null
  nickname?: string | null
  avatarUrl?: string | null
  gender?: string
  birthday?: string | null
  levelId?: number
  levelName?: string
  levelCode?: string
  registerStoreId?: number | null
  registerStoreName?: string | null
  registerChannel?: string
  totalConsumeAmount?: number
  totalRechargeAmount?: number
  totalOrderCount?: number
  points?: number
  lastConsumeTime?: string | null
  status?: string
  createTime?: string
  updateTime?: string
}

/** GET /api/member/levels 实测：upgradeThreshold / pointRate / discountRate(万分数) */
export interface MemberLevel {
  id: number
  levelCode: string
  levelName: string
  levelNo?: number
  upgradeType?: string
  upgradeThreshold?: number
  discountRate?: number
  pointRate?: number
  iconUrl?: string | null
  benefits?: string | null
  status?: string
  memberCount?: number
}

/** GET /api/product/stores/{storeId}/categories 实测：categoryId（不是 id） */
export interface CategoryItem {
  categoryId: number
  categoryName: string
  iconUrl?: string | null
  sort?: number
}

/** SKU 实测：skuId / skuName / price(分) / specJson(JSON 字符串) */
export interface SkuItem {
  skuId: number
  skuName: string
  price: number
  specJson?: string | null
  imageUrl?: string | null
  status?: string
}

/** 列表与详情 spu 结构略有差异：详情没有 minPrice，列表没有 description */
export interface SpuItem {
  spuId: number
  spuName: string
  imageUrl: string | null
  minPrice?: number
  categoryId?: number
  categoryName?: string
  description?: string | null
  subtitle?: string | null
  unit?: string | null
  status?: string
}

/** GET /api/product/stores/{storeId}/spus/{spuId} 实测：{ spu, skus } */
export interface SpuDetail {
  spu: SpuItem
  skus: SkuItem[]
}

/** 试算 / 订单明细行实测字段 */
export interface QuoteItem {
  id?: number | null
  spuId: number
  skuId: number
  spuName: string
  skuName: string
  specJson?: string | null
  imageUrl?: string | null
  unitPrice: number
  quantity: number
  totalAmount: number
  discountAmount?: number
  actualAmount?: number
  refundedQuantity?: number | null
}

/** POST /api/order/quote 实测 */
export interface OrderQuote {
  totalAmount: number
  levelDiscountAmount: number
  activityDiscountAmount: number
  couponAmount: number
  discountAmount: number
  payableAmount: number
  memberId?: number
  memberLevelName?: string
  discountRate?: number
  items: QuoteItem[]
}

export type OrderItem = QuoteItem

/** OrderVO 实测（列表页 items 为 null，详情页有值） */
export interface OrderInfo {
  id?: number
  orderNo: string
  storeId?: number
  storeName?: string
  memberId?: number
  memberName?: string
  memberPhone?: string | null
  orderSource?: string
  totalAmount?: number
  discountAmount?: number
  couponAmount?: number
  levelDiscountAmount?: number
  activityDiscountAmount?: number
  payableAmount?: number
  paidAmount?: number
  refundedAmount?: number
  balancePrincipalAmount?: number
  balanceGiftAmount?: number
  /** UNPAID / PAID / COMPLETED / CANCELLED / REFUNDING / REFUNDED */
  status?: string
  statusLabel?: string
  payChannel?: string | null
  paymentNo?: string | null
  memberCouponId?: number | null
  pointsEarned?: number
  pickupNo?: string | null
  payTime?: string | null
  completeTime?: string | null
  cancelTime?: string | null
  cancelReason?: string | null
  remark?: string | null
  createTime?: string
  items?: OrderItem[] | null
}

/** POST /api/payment/create 实测：PAYING 起始状态，invokeParams 可能为 null（Native 渠道） */
export interface PaymentInfo {
  paymentNo: string
  provider?: string
  channel?: string
  amount: number
  /** CREATED / PAYING / SUCCESS / FAILED / CLOSED */
  status: string
  invokeParams?: string | null
  codeUrl?: string | null
  tip?: string
  /** 支付单创建时间，用于计算支付超时倒计时 */
  createTime?: string
}

/** GET /api/wallet/account 实测 */
export interface WalletAccount {
  principalBalance: number
  giftBalance: number
  totalBalance: number
  status: string
}

/** GET /api/wallet/transactions 实测：principalChange / giftChange 等（不是 changePrincipal） */
export interface WalletTransaction {
  id?: number
  txnNo?: string
  walletId?: number
  memberId?: number
  memberName?: string
  storeId?: number
  storeName?: string
  /** RECHARGE_IN / CONSUME_OUT / REFUND_IN / RECHARGE_REFUND_OUT / ADMIN_ADJUST */
  bizType?: string
  bizTypeName?: string
  bizNo?: string
  principalChange?: number
  principalBefore?: number
  principalAfter?: number
  giftChange?: number
  giftBefore?: number
  giftAfter?: number
  amountPaid?: number
  giftAmount?: number
  giftExpireTime?: string | null
  remark?: string | null
  operatorType?: string
  createTime?: string
}

/** GET /api/wallet/plans 实测 */
export interface RechargePlan {
  id: number
  planCode?: string
  planName: string
  rechargeAmount: number
  giftAmount: number
  giftExpireDays?: number
  giftPoints?: number
  minLevelId?: number | null
  minLevelName?: string | null
  customAmountEnabled?: number
  minCustomAmount?: number
  maxCustomAmount?: number
  perMemberLimit?: number
  sort?: number
  status?: string
  startTime?: string | null
  endTime?: string | null
  remark?: string | null
  description?: string | null
  effective?: boolean
}

/** 充值单实测：status = PENDING / PAID / CANCELLED；创建时 planName/storeName 可能为 null */
export interface RechargeOrder {
  id?: number
  rechargeNo: string
  memberId?: number
  memberName?: string | null
  memberPhone?: string | null
  storeId?: number
  storeName?: string | null
  planId?: number
  planName?: string | null
  rechargeAmount: number
  giftAmount: number
  actualAmount: number
  payChannel?: string
  status: string
  paymentNo?: string | null
  payTime?: string | null
  createTime?: string
  remark?: string | null
}
