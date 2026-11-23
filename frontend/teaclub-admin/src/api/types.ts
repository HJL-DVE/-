export interface PageResult<T> {
  records: T[]
  total: number
  pageNo: number
  pageSize: number
  totalPages: number
}

export interface PageQuery {
  pageNo: number
  pageSize: number
}

// ---------- 认证 ----------
export interface StoreBrief {
  storeId: number
  storeCode: string
  storeName: string
  isDefault: boolean
}

export interface UserInfo {
  userId: number
  username: string
  realName: string
  userType: string
  avatarUrl?: string | null
  brandId: number
  roles: string[]
  permissions: string[]
  dataScope: string
  stores: StoreBrief[]
}

export interface LoginResult {
  accessToken: string
  tokenType: string
  expiresIn: number
  userInfo: UserInfo
}

export interface MenuNode {
  id: number
  parentId: number
  permCode: string
  permName: string
  permType: string
  routePath: string
  component: string
  icon?: string | null
  sort: number
  children: MenuNode[]
}

// ---------- 门店 ----------
export interface StoreVO {
  id: number
  brandId: number
  storeCode: string
  storeName: string
  province?: string | null
  city?: string | null
  district?: string | null
  address?: string | null
  longitude?: number | null
  latitude?: number | null
  phone?: string | null
  managerName?: string | null
  businessHours?: string | null
  openDate?: string | null
  status: string
  /** 是否旗舰店：0 否 1 是（品牌内唯一） */
  isFlagship?: number | null
  /** 门店排序号，越小越靠前 */
  storeSort?: number | null
  remark?: string | null
  createTime?: string | null
  updateTime?: string | null
}

export interface StoreQuery extends PageQuery {
  keyword?: string
  city?: string
  status?: string
}

// ---------- 员工 / 系统 ----------
export interface UserVO {
  id: number
  username: string
  realName?: string | null
  phone?: string | null
  email?: string | null
  avatarUrl?: string | null
  userType: string
  status: string
  lastLoginTime?: string | null
  lastLoginIp?: string | null
  createTime?: string | null
  roleCodes?: string[]
  roleNames?: string[]
  stores?: StoreBrief[]
}

export interface UserQuery extends PageQuery {
  keyword?: string
  phone?: string
  userType?: string
  status?: string
  storeId?: number
}

export interface RoleVO {
  id: number
  roleCode: string
  roleName: string
  dataScope: string
  isBuiltin?: number
  sort?: number
  status?: string
  remark?: string | null
  createTime?: string | null
  permissionIds?: number[]
  permissionCount?: number
}

// ---------- 会员 ----------
export interface MemberVO {
  id: number
  memberNo: string
  phone: string
  nickname?: string | null
  avatarUrl?: string | null
  gender?: string | null
  birthday?: string | null
  levelId?: number | null
  levelName?: string | null
  levelCode?: string | null
  registerStoreId?: number | null
  registerStoreName?: string | null
  registerChannel?: string | null
  totalConsumeAmount?: number | null
  totalRechargeAmount?: number | null
  totalOrderCount?: number | null
  points?: number | null
  lastConsumeTime?: string | null
  status: string
  freezeReason?: string | null
  remark?: string | null
  createTime?: string | null
  updateTime?: string | null
}

export interface MemberQuery extends PageQuery {
  phone?: string
  keyword?: string
  levelId?: number
  status?: string
  registerStoreId?: number
  createTimeStart?: string
  createTimeEnd?: string
}

export interface MemberLevelVO {
  id: number
  levelCode: string
  levelName: string
  levelNo: number
  upgradeType: string
  upgradeThreshold: number
  discountRate: number
  pointRate: number
  iconUrl?: string | null
  benefits?: string | null
  status: string
  createTime?: string | null
  memberCount?: number | null
}

// ---------- 商品 ----------
export interface CategoryVO {
  id: number
  parentId: number
  categoryName: string
  iconUrl?: string | null
  sort: number
  status: string
  productCount?: number | null
  children?: CategoryVO[]
}

export interface SkuVO {
  id: number
  skuCode: string
  skuName: string
  specJson?: string | null
  price: number
  costPrice?: number | null
  imageUrl?: string | null
  sort?: number
  status: string
}

export interface SpuVO {
  id: number
  categoryId: number
  categoryName?: string | null
  spuCode: string
  spuName: string
  subtitle?: string | null
  description?: string | null
  mainImage?: string | null
  imagesJson?: string | null
  basePrice: number
  minPrice?: number | null
  unit?: string | null
  hasSku?: number | null
  sort?: number
  status: string
  createTime?: string | null
  skus?: SkuVO[]
}

export interface SpuQuery extends PageQuery {
  keyword?: string
  categoryId?: number
  status?: string
}

export interface StoreSkuVO {
  id: number
  skuId: number
  skuCode: string
  skuName: string
  basePrice: number
  storePrice?: number | null
  minPrice?: number | null
  isOnSale: number
}

export interface StoreSpuVO {
  id: number
  storeId: number
  spuId: number
  spuCode: string
  spuName: string
  mainImage?: string | null
  basePrice: number
  minPrice?: number | null
  spuStatus?: string | null
  isOnSale: number
  sort?: number
  skus?: StoreSkuVO[]
}

// ---------- 订单 ----------
export interface OrderItemRequest {
  skuId: number
  quantity: number
}

export interface OrderItemVO {
  id: number
  spuId: number
  skuId: number
  spuName: string
  skuName: string
  specJson?: string | null
  imageUrl?: string | null
  unitPrice: number
  quantity: number
  totalAmount: number
  discountAmount?: number | null
  actualAmount?: number | null
  refundedQuantity?: number | null
}

export interface OrderQuoteVO {
  totalAmount: number
  levelDiscountAmount: number
  activityDiscountAmount: number
  couponAmount: number
  pointsDiscountAmount?: number
  discountAmount: number
  payableAmount: number
  memberId?: number | null
  memberLevelName?: string | null
  discountRate?: number | null
  items: OrderItemVO[]
}

export interface OrderVO {
  id: number
  orderNo: string
  storeId: number
  storeName?: string | null
  memberId?: number | null
  memberName?: string | null
  memberPhone?: string | null
  orderSource?: string | null
  totalAmount: number
  discountAmount: number
  couponAmount?: number | null
  levelDiscountAmount?: number | null
  activityDiscountAmount?: number | null
  payableAmount: number
  paidAmount: number
  refundedAmount: number
  balancePrincipalAmount?: number | null
  balanceGiftAmount?: number | null
  status: string
  statusLabel?: string | null
  payChannel?: string | null
  paymentNo?: string | null
  memberCouponId?: number | null
  pointsEarned?: number | null
  pickupNo?: string | null
  payTime?: string | null
  completeTime?: string | null
  cancelTime?: string | null
  cancelReason?: string | null
  remark?: string | null
  createTime?: string | null
  items?: OrderItemVO[]
}

export interface OrderQuery extends PageQuery {
  orderNo?: string
  storeId?: number
  memberId?: number
  status?: string
  payChannel?: string
  orderSource?: string
  startDate?: string
  endDate?: string
}

export interface OrderCreateRequest {
  storeId: number
  memberId?: number | null
  orderSource?: string
  memberCouponId?: number | null
  usePoints?: number | null
  remark?: string
  payWithBalance?: boolean
  items: OrderItemRequest[]
}

// ---------- 支付 / 退款 ----------
export interface PayCreateRequest {
  bizNo: string
  bizType: 'ORDER' | 'RECHARGE'
  channel?: string
  openid?: string
}

export interface PayCreateVO {
  paymentNo: string
  provider: string
  channel: string
  amount: number
  status: string
  invokeParams?: Record<string, string> | null
  codeUrl?: string | null
  tip?: string | null
}

export interface PaymentVO {
  id: number
  paymentNo: string
  bizType: string
  bizNo: string
  storeId?: number | null
  memberId?: number | null
  channel: string
  provider: string
  amount: number
  status: string
  transactionId?: string | null
  codeUrl?: string | null
  successTime?: string | null
  failReason?: string | null
  createTime?: string | null
}

export interface RefundVO {
  id: number
  refundNo: string
  paymentNo: string
  bizType: string
  bizNo: string
  storeId?: number | null
  memberId?: number | null
  channel?: string | null
  refundAmount: number
  refundPrincipalAmount?: number | null
  refundGiftAmount?: number | null
  refundCashAmount?: number | null
  reason?: string | null
  status: string
  wxRefundId?: string | null
  successTime?: string | null
  failReason?: string | null
  createTime?: string | null
}

export interface RefundApplyRequest {
  orderNo: string
  refundAmount: number
  reason?: string
}

/** 充值退卡：金额全部由服务端按充值单计算，调用方只传单号与原因 */
export interface RechargeRefundRequest {
  rechargeNo: string
  reason?: string
}

export interface NotifyLogVO {
  id: number
  paymentNo?: string | null
  status?: string | null
  [key: string]: unknown
}

// ---------- 钱包 / 充值（wallet-service，字段以实际接口为准） ----------
export interface WalletVO {
  walletId: number
  id: number
  memberId: number
  memberNo?: string | null
  memberName?: string | null
  memberPhone?: string | null
  memberNickname?: string | null
  phone?: string | null
  principalBalance: number
  giftBalance: number
  totalBalance: number
  totalPrincipalIn?: number | null
  totalGiftIn?: number | null
  totalPrincipalOut?: number | null
  totalGiftOut?: number | null
  totalRefundIn?: number | null
  status: string
  createTime?: string | null
  updateTime?: string | null
}

export interface WalletTransactionVO {
  id: number
  txnNo: string
  walletId: number
  memberId: number
  memberName?: string | null
  storeId?: number | null
  storeName?: string | null
  bizType: string
  bizTypeName?: string | null
  bizNo?: string | null
  principalChange?: number | null
  principalBefore?: number | null
  principalAfter?: number | null
  giftChange?: number | null
  giftBefore?: number | null
  giftAfter?: number | null
  amountPaid?: number | null
  giftAmount?: number | null
  giftExpireTime?: string | null
  remark?: string | null
  operatorType?: string | null
  createTime?: string | null
}

export interface RechargeOrderVO {
  id?: number
  rechargeNo: string
  memberId: number
  memberName?: string | null
  memberPhone?: string | null
  storeId?: number | null
  storeName?: string | null
  planId?: number | null
  planName?: string | null
  rechargeAmount: number
  giftAmount: number
  actualAmount: number
  payChannel?: string | null
  status: string
  paymentNo?: string | null
  payTime?: string | null
  createTime?: string | null
  remark?: string | null
}

export interface RechargePlanVO {
  id: number
  planCode: string
  planName: string
  rechargeAmount: number
  giftAmount: number
  giftExpireDays?: number | null
  giftPoints?: number | null
  minLevelId?: number | null
  minLevelName?: string | null
  customAmountEnabled?: number | null
  minCustomAmount?: number | null
  maxCustomAmount?: number | null
  perMemberLimit?: number | null
  sort?: number | null
  status: string
  startTime?: string | null
  endTime?: string | null
  remark?: string | null
  description?: string | null
  createTime?: string | null
  storeIds?: number[]
  effective?: boolean | null
}

export interface WalletQuery extends PageQuery {
  phone?: string
  keyword?: string
  status?: string
}

export interface TransactionQuery extends PageQuery {
  memberId?: number
  storeId?: number
  bizType?: string
  bizNo?: string
  startDate?: string
  endDate?: string
}

export interface RechargeQuery extends PageQuery {
  memberId?: number
  storeId?: number
  planId?: number
  status?: string
  startDate?: string
  endDate?: string
}

// ---------- 仪表盘统计 ----------
export interface OrderStatsVO {
  days: number
  storeId?: number | null
  orderCount: number
  paidOrderCount: number
  gmv: number
  avgOrderAmount: number
  balancePayAmount: number
  wechatPayAmount: number
  /** 钱包支付中的实付本金抵扣（分） */
  balancePrincipalAmount?: number
  /** 钱包支付中的赠送金抵扣（分），营销成本不计真实收入 */
  balanceGiftAmount?: number
  /** 实付营业额 = GMV - 赠送金抵扣（现金 + 钱包本金，分） */
  actualPayAmount?: number
  dailyTrend?: { statDate: string; gmv: number; orderCount: number }[]
}

export interface MemberStatsVO {
  totalMembers: number
  newMembers7d: number
  activeMembers30d: number
  totalPoints: number
}

export interface WalletStatsVO {
  walletCount?: number
  principalTotal?: number
  giftTotal?: number
  totalBalance?: number
  rechargeTotal?: number
  giftGrantTotal?: number
}
