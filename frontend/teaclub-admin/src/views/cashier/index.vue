<template>
  <div class="page cashier">
    <div class="toolbar cashier-top">
      <el-select
        :model-value="appStore.currentStoreId"
        placeholder="选择门店"
        filterable
        style="width: 220px"
        @update:model-value="onChangeStore"
      >
        <el-option v-for="store in appStore.stores" :key="store.id" :label="store.storeName" :value="store.id" />
      </el-select>
      <el-tag type="success" effect="plain">收银工作台</el-tag>
      <span class="text-muted">金额一律由 POST /api/admin/order/quote 服务端计算，前端只做展示</span>
      <div class="toolbar-spacer" />
      <el-button :icon="Refresh" @click="reloadProducts">刷新商品</el-button>
    </div>

    <el-alert
      v-if="storeLoadError"
      type="error"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
      title="门店列表加载失败，收银台需要门店上下文才能工作"
    >
      <template #default>
        <div class="store-retry">
          <span>{{ storeLoadError.message || '请检查网络后重试' }}</span>
          <el-button size="small" type="primary" :loading="storeLoading" @click="initStores">重试</el-button>
        </div>
      </template>
    </el-alert>

    <el-alert
      v-else-if="!appStore.currentStoreId && !storeLoading"
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
      title="未选择门店：请先在右上角选择门店，商品与订单将按门店过滤"
    />

    <el-row :gutter="14">
      <el-col :span="14">
        <el-card shadow="never" class="panel">
          <template #header>
            <div class="panel-head">
              <strong>商品</strong>
              <el-input v-model="keyword" placeholder="商品名称/编码（回车搜索）" clearable style="width: 240px" @keyup.enter="loadProducts" />
            </div>
          </template>

          <el-tabs v-model="activeCategory" @tab-change="onCategoryChange">
            <el-tab-pane label="全部" name="all" />
            <el-tab-pane v-for="cat in flatCategories" :key="cat.id" :label="cat.categoryName" :name="String(cat.id)" />
          </el-tabs>

          <div v-loading="productLoading" class="product-grid">
            <div v-for="spu in filteredProducts" :key="spu.id" class="product-card" @click="pickSpu(spu)">
              <div class="product-name">{{ spu.spuName }}</div>
              <div class="product-code mono">{{ spu.spuCode }}</div>
              <div class="product-price">
                <span class="money">{{ fenToYuan(spu.minPrice ?? spu.basePrice) }}</span>
                <span class="text-muted">起</span>
              </div>
              <el-tag v-if="spu.isOnSale !== 1" size="small" type="info">已下架</el-tag>
              <el-tag v-else size="small" type="success">{{ (spu.skus || []).length }} 规格</el-tag>
            </div>
            <el-empty v-if="!productLoading && !filteredProducts.length" description="当前门店无可售商品" :image-size="70" />
          </div>
        </el-card>
      </el-col>

      <el-col :span="10" class="right-col">
        <el-card shadow="never" class="panel member-panel">
          <template #header>
            <div class="panel-head">
              <strong>会员</strong>
              <el-button link type="primary" @click="clearMember">清除</el-button>
            </div>
          </template>

          <div class="member-row">
            <el-input
              v-model="memberQuery"
              placeholder="手机号 或 会员码（扫码枪回车）"
              clearable
              style="flex: 1"
              @keyup.enter="searchMember"
            />
            <el-radio-group v-model="memberMode" size="small" style="margin-left: 8px">
              <el-radio-button label="phone">手机号</el-radio-button>
              <el-radio-button label="code">会员码</el-radio-button>
            </el-radio-group>
            <el-button type="primary" :loading="memberLoading" @click="searchMember">查询</el-button>
          </div>

          <ApiPending
            v-if="memberError"
            :title="memberMode === 'code' ? '会员码查询接口未就绪' : '会员查询失败'"
            :url="memberMode === 'code' ? 'GET /api/admin/member/members/by-code?code=' : 'GET /api/admin/member/members/by-phone?phone='"
            :code="memberError.code"
            :message="memberError.message"
            :trace-id="memberError.traceId"
          />

          <div v-if="member" class="member-card">
            <div class="member-line">
              <strong>{{ member.nickname || member.phone }}</strong>
              <el-tag size="small">{{ member.levelName || '无等级' }}</el-tag>
              <span class="text-muted">{{ member.memberNo }}</span>
            </div>
            <div class="member-line text-muted">
              累计消费 {{ fenToYuan(member.totalConsumeAmount) }} · 积分 {{ member.points ?? 0 }}
            </div>
            <div class="member-line">
              <span>钱包余额：</span>
              <template v-if="walletError">
                <el-tag size="small" type="warning" effect="plain">
                  钱包接口未就绪 code={{ walletError.code }}
                </el-tag>
              </template>
              <span v-else-if="wallet" class="money">{{ fenToYuan(wallet.totalBalance) }} 元</span>
              <span v-else class="text-muted">未开通</span>
            </div>
          </div>
          <el-empty v-else-if="!memberError" description="散客收银（可选会员）" :image-size="50" />
        </el-card>

        <el-card shadow="never" class="panel cart-panel">
          <template #header>
            <div class="panel-head">
              <strong>购物车（{{ cart.length }}）</strong>
              <el-button link type="danger" @click="clearCart">清空</el-button>
            </div>
          </template>

          <el-table :data="cart" size="small" border>
            <el-table-column prop="spuName" label="商品" min-width="120" show-overflow-tooltip />
            <el-table-column prop="skuName" label="规格" min-width="110" show-overflow-tooltip />
            <el-table-column label="单价" width="90" align="right">
              <template #default="{ row }">{{ fenToYuan(row.unitPrice) }}</template>
            </el-table-column>
            <el-table-column label="数量" width="120" align="center">
              <template #default="{ row, $index }">
                <el-button link @click="changeQty($index, -1)">-</el-button>
                <span style="margin: 0 6px">{{ row.quantity }}</span>
                <el-button link @click="changeQty($index, 1)">+</el-button>
              </template>
            </el-table-column>
            <el-table-column label="" width="50">
              <template #default="{ $index }">
                <el-button link type="danger" @click="removeItem($index)">删</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="quote-box">
            <ApiPending
              v-if="quoteError"
              title="试算接口不可用"
              url="POST /api/admin/order/quote"
              :code="quoteError.code"
              :message="quoteError.message"
              :trace-id="quoteError.traceId"
            />
            <el-descriptions v-else-if="quote" :column="1" border size="small">
              <el-descriptions-item label="商品总额">{{ fenToYuan(quote.totalAmount) }} 元</el-descriptions-item>
              <el-descriptions-item label="等级折扣">
                {{ quote.memberLevelName || '-' }} -{{ fenToYuan(quote.levelDiscountAmount) }} 元
              </el-descriptions-item>
              <el-descriptions-item label="活动优惠">-{{ fenToYuan(quote.activityDiscountAmount) }} 元</el-descriptions-item>
              <el-descriptions-item label="券优惠">-{{ fenToYuan(quote.couponAmount) }} 元</el-descriptions-item>
              <el-descriptions-item label="优惠合计">-{{ fenToYuan(quote.discountAmount) }} 元</el-descriptions-item>
              <el-descriptions-item label="应付金额">
                <span class="money payable">{{ fenToYuan(quote.payableAmount) }} 元</span>
              </el-descriptions-item>
            </el-descriptions>
            <el-empty v-else description="加入商品后自动向服务端试算" :image-size="60" />
          </div>

          <div class="pay-actions">
            <el-tooltip content="营销服务未上线，优惠券功能预留" placement="top">
              <span>
                <el-button disabled>使用优惠券</el-button>
              </span>
            </el-tooltip>
            <el-tooltip content="营销服务未上线，积分抵扣预留" placement="top">
              <span>
                <el-button disabled>积分抵扣</el-button>
              </span>
            </el-tooltip>
            <div class="toolbar-spacer" />
            <el-button type="success" :loading="payingWallet" :disabled="!canPay" @click="payWithWallet">
              钱包支付
            </el-button>
            <el-button type="primary" :loading="creatingNative" :disabled="!canPay" @click="payWithNative">
              微信扫码
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="panel order-panel" style="margin-top: 14px">
      <template #header>
        <div class="panel-head">
          <strong>本店订单{{ appStore.currentStoreId ? '' : '（未选择门店）' }}</strong>
          <el-button link type="primary" :icon="Refresh" :disabled="!appStore.currentStoreId" @click="loadOrders">刷新</el-button>
        </div>
      </template>
      <el-table :data="recentOrders" v-loading="orderLoading" size="small" border>
        <el-table-column prop="orderNo" label="订单号" min-width="170" show-overflow-tooltip />
        <el-table-column label="会员" min-width="100">
          <template #default="{ row }">{{ row.memberName || row.memberPhone || '散客' }}</template>
        </el-table-column>
        <el-table-column label="应付" width="84" align="right">
          <template #default="{ row }">{{ fenToYuan(row.payableAmount) }}</template>
        </el-table-column>
        <el-table-column label="实付" width="84" align="right">
          <template #default="{ row }"><span class="money">{{ fenToYuan(row.paidAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="ORDER_STATUS_TAG[row.status] || 'info'">
              {{ row.statusLabel || ORDER_STATUS_LABEL[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="支付渠道" width="100">
          <template #default="{ row }">{{ PAY_CHANNEL_LABEL[row.payChannel] || row.payChannel || '-' }}</template>
        </el-table-column>
        <el-table-column label="时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130">
          <template #default="{ row }">
            <el-button
              v-if="['PAID', 'COMPLETED', 'REFUNDING'].includes(row.status) && row.refundedAmount < row.paidAmount"
              link
              type="danger"
              @click="openRefund(row)"
            >
              退款
            </el-button>
            <el-button v-if="row.status === 'PAID'" link type="success" @click="completeOrder(row)">完成</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="skuVisible" title="选择规格" width="460px">
      <el-table :data="skuOptions" size="small" border @row-click="chooseSku">
        <el-table-column prop="skuName" label="规格" min-width="160" />
        <el-table-column label="门店价" width="110" align="right">
          <template #default="{ row }">{{ fenToYuan(row.storePrice ?? row.basePrice) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default>
            <el-button link type="primary">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="qrVisible" title="扫码支付" width="430px" :close-on-click-modal="false" @closed="stopPolling">
      <div class="qr-box">
        <img v-if="qrDataUrl" :src="qrDataUrl" alt="支付二维码" width="240" height="240" />
        <el-skeleton v-else :rows="4" animated />

        <p class="qr-tip">手机扫码打开支付页 → 点「确认支付」完成付款</p>

        <el-button
          type="success"
          size="large"
          class="qr-pay-btn"
          :loading="mockPaying"
          @click="mockConfirmPay"
        >
          确认支付 {{ fenToYuan(payAmount) }} 元
        </el-button>

        <div class="qr-meta">
          <div>支付单号：<span class="mono">{{ paymentNo }}</span></div>
          <div>金额：<span class="money">{{ fenToYuan(payAmount) }} 元</span></div>
          <div>状态：
            <el-tag size="small" :type="tagTypeByStatus(payStatus)">{{ PAY_STATUS_LABEL[payStatus] || payStatus }}</el-tag>
          </div>
        </div>

        <div class="text-muted">每 3 秒轮询支付结果，支付成功自动关窗</div>
      </div>
      <template #footer>
        <el-button @click="qrVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="refundVisible" title="退款申请" width="440px">
      <el-form label-width="110px">
        <el-form-item label="订单号">{{ refundForm.orderNo }}</el-form-item>
        <el-form-item label="可退金额">{{ fenToYuan(refundForm.maxRefund) }} 元</el-form-item>
        <el-form-item label="退款金额（元）"><el-input v-model="refundForm.amountYuan" /></el-form-item>
        <el-form-item label="退款原因"><el-input v-model="refundForm.reason" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="refundVisible = false">取消</el-button>
        <el-button type="danger" :loading="refundSubmitting" @click="submitRefund">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import QRCode from 'qrcode'
import { useAppStore } from '@/stores/app'
import { getCategoryTree, getSpuPage, getStoreProducts } from '@/api/product'
import { getMemberByCode, getMemberByPhone } from '@/api/member'
import { getWalletByMember } from '@/api/wallet'
import { createOrder, getOrderPage, payByBalance, quoteOrder } from '@/api/order'
import { createPayment, queryPayment } from '@/api/payment'
import { applyRefund } from '@/api/payment'
import { ApiError, isApiError } from '@/api/request'
import ApiPending from '@/components/ApiPending.vue'
import {
  ORDER_STATUS_LABEL,
  ORDER_STATUS_TAG,
  PAY_CHANNEL_LABEL,
  PAY_STATUS_LABEL,
  fenToYuan,
  formatDateTime,
  tagTypeByStatus,
  yuanToFen,
} from '@/utils/format'
import type {
  CategoryVO,
  MemberVO,
  OrderQuoteVO,
  OrderVO,
  StoreSkuVO,
  StoreSpuVO,
  WalletVO,
} from '@/api/types'

const appStore = useAppStore()

// ---------- 商品 ----------
const keyword = ref('')
const activeCategory = ref('all')
const productLoading = ref(false)
const storeProducts = ref<StoreSpuVO[]>([])
const categories = ref<CategoryVO[]>([])
const spuCategoryMap = ref<Record<number, number>>({})

const flatCategories = computed(() => {
  const list: CategoryVO[] = []
  const walk = (nodes: CategoryVO[]) => {
    nodes.forEach((node) => {
      list.push(node)
      if (node.children?.length) walk(node.children)
    })
  }
  walk(categories.value)
  return list
})

const filteredProducts = computed(() => {
  if (activeCategory.value === 'all') return storeProducts.value
  const categoryId = Number(activeCategory.value)
  return storeProducts.value.filter((item) => spuCategoryMap.value[item.spuId] === categoryId)
})

async function loadProducts(): Promise<void> {
  if (!appStore.currentStoreId) return
  productLoading.value = true
  try {
    const page = await getStoreProducts({
      storeId: appStore.currentStoreId,
      keyword: keyword.value || undefined,
      pageNo: 1,
      pageSize: 200,
    })
    storeProducts.value = page.records || []
  } finally {
    productLoading.value = false
  }
}

async function loadCategories(): Promise<void> {
  try {
    categories.value = await getCategoryTree()
  } catch {
    categories.value = []
  }
}

async function loadSpuCategoryMap(): Promise<void> {
  try {
    const page = await getSpuPage({ pageNo: 1, pageSize: 200 })
    const map: Record<number, number> = {}
    ;(page.records || []).forEach((spu) => {
      map[spu.id] = spu.categoryId
    })
    spuCategoryMap.value = map
  } catch {
    spuCategoryMap.value = {}
  }
}

function onCategoryChange(): void {
  // 分类过滤在前端按 spuId -> categoryId 映射完成（store-products 返回不含 categoryId）
}

const storeLoading = ref(false)
const storeLoadError = ref<{ code?: number; message?: string } | null>(null)

async function initStores(): Promise<void> {
  storeLoading.value = true
  storeLoadError.value = null
  try {
    await appStore.loadStores(true)
  } catch (e: unknown) {
    const err = e as { code?: number; message?: string }
    storeLoadError.value = { code: err?.code, message: err?.message }
    appStore.setStore(0)
  } finally {
    storeLoading.value = false
  }
}

async function reloadProducts(): Promise<void> {
  await Promise.all([loadProducts(), loadCategories(), loadSpuCategoryMap()])
}

function onChangeStore(id: number): void {
  appStore.setStore(id)
  clearCart()
  clearMember()
  reloadProducts()
  loadOrders()
}

// ---------- 规格选择 ----------
const skuVisible = ref(false)
const skuOptions = ref<StoreSkuVO[]>([])
const currentSpu = ref<StoreSpuVO | null>(null)

function pickSpu(spu: StoreSpuVO): void {
  if (spu.isOnSale !== 1) {
    ElMessage.warning('该商品在当前门店已下架')
    return
  }
  const skus = spu.skus || []
  if (!skus.length) {
    ElMessage.warning('该门店商品未配置 SKU')
    return
  }
  if (skus.length === 1) {
    addToCart(spu, skus[0])
    return
  }
  currentSpu.value = spu
  skuOptions.value = skus
  skuVisible.value = true
}

function chooseSku(row: StoreSkuVO): void {
  if (currentSpu.value) addToCart(currentSpu.value, row)
  skuVisible.value = false
}

// ---------- 购物车 ----------
interface CartItem {
  spuId: number
  skuId: number
  spuName: string
  skuName: string
  unitPrice: number
  quantity: number
}

const cart = ref<CartItem[]>([])

function addToCart(spu: StoreSpuVO, sku: StoreSkuVO): void {
  if (sku.isOnSale !== 1) {
    ElMessage.warning('该规格在当前门店已下架')
    return
  }
  const exist = cart.value.find((item) => item.skuId === sku.skuId)
  if (exist) {
    exist.quantity += 1
  } else {
    cart.value.push({
      spuId: spu.spuId,
      skuId: sku.skuId,
      spuName: spu.spuName,
      skuName: sku.skuName,
      unitPrice: sku.storePrice ?? sku.basePrice,
      quantity: 1,
    })
  }
  scheduleQuote()
}

function changeQty(index: number, delta: number): void {
  const item = cart.value[index]
  if (!item) return
  item.quantity += delta
  if (item.quantity <= 0) cart.value.splice(index, 1)
  scheduleQuote()
}

function removeItem(index: number): void {
  cart.value.splice(index, 1)
  scheduleQuote()
}

function clearCart(): void {
  cart.value = []
  quote.value = null
  quoteError.value = null
}

// ---------- 会员 ----------
const memberMode = ref<'phone' | 'code'>('phone')
const memberQuery = ref('')
const memberLoading = ref(false)
const member = ref<MemberVO | null>(null)
const memberError = ref<ApiError | null>(null)
const wallet = ref<WalletVO | null>(null)
const walletError = ref<ApiError | null>(null)

function toApiError(error: unknown): ApiError {
  return isApiError(error) ? error : new ApiError(-1, (error as Error)?.message || '未知错误')
}

async function searchMember(): Promise<void> {
  const value = memberQuery.value.trim()
  if (!value) {
    ElMessage.warning('请输入手机号或会员码')
    return
  }
  memberLoading.value = true
  memberError.value = null
  try {
    if (memberMode.value === 'phone') {
      member.value = await getMemberByPhone(value)
    } else {
      // by-code 为契约「新增」接口，后端未落地时返回 code=50000
      member.value = await getMemberByCode(value, true)
    }
    if (member.value) {
      await loadWallet(member.value.id)
      scheduleQuote()
    }
  } catch (error) {
    member.value = null
    wallet.value = null
    memberError.value = toApiError(error)
  } finally {
    memberLoading.value = false
  }
}

async function loadWallet(memberId: number): Promise<void> {
  try {
    wallet.value = await getWalletByMember(memberId)
    walletError.value = null
  } catch (error) {
    wallet.value = null
    walletError.value = toApiError(error)
  }
}

function clearMember(): void {
  member.value = null
  memberQuery.value = ''
  memberError.value = null
  wallet.value = null
  walletError.value = null
  scheduleQuote()
}

// ---------- 试算（服务端计算） ----------
const quote = ref<OrderQuoteVO | null>(null)
const quoteError = ref<ApiError | null>(null)
let quoteTimer: ReturnType<typeof setTimeout> | null = null

const uploading = ref(false)

function scheduleQuote(): void {
  if (quoteTimer) clearTimeout(quoteTimer)
  quoteTimer = setTimeout(() => {
    void runQuote()
  }, 260)
}

async function runQuote(): Promise<void> {
  if (!appStore.currentStoreId || !cart.value.length) {
    quote.value = null
    quoteError.value = null
    return
  }
  try {
    quote.value = await quoteOrder({
      storeId: appStore.currentStoreId,
      memberId: member.value?.id,
      items: cart.value.map((item) => ({ skuId: item.skuId, quantity: item.quantity })),
    })
    quoteError.value = null
  } catch (error) {
    quote.value = null
    quoteError.value = toApiError(error)
  }
}

const canPay = computed(() => Boolean(quote.value) && cart.value.length > 0 && !uploading.value)

// ---------- 下单 / 支付 ----------
const payingWallet = ref(false)
const creatingNative = ref(false)

async function buildOrder(): Promise<OrderVO | null> {
  if (!appStore.currentStoreId) {
    ElMessage.warning('请先选择门店')
    return null
  }
  if (!cart.value.length) {
    ElMessage.warning('购物车为空')
    return null
  }
  const created = await createOrder({
    storeId: appStore.currentStoreId,
    memberId: member.value?.id ?? null,
    orderSource: 'POS',
    items: cart.value.map((item) => ({ skuId: item.skuId, quantity: item.quantity })),
  })
  return created
}

function afterPaid(): void {
  clearCart()
  clearMember()
  loadOrders()
}

async function payWithWallet(): Promise<void> {
  if (!member.value) {
    ElMessage.warning('钱包支付需要先选择会员')
    return
  }
  payingWallet.value = true
  try {
    const order = await buildOrder()
    if (!order) return
    const paid = await payByBalance(order.orderNo)
    ElMessage.success('钱包支付成功：' + paid.orderNo + '，实付 ' + fenToYuan(paid.paidAmount) + ' 元')
    afterPaid()
  } finally {
    payingWallet.value = false
  }
}

async function payWithNative(): Promise<void> {
  creatingNative.value = true
  try {
    const order = await buildOrder()
    if (!order) return
    const pay = await createPayment({ bizNo: order.orderNo, bizType: 'ORDER', channel: 'WECHAT_NATIVE' })
    paymentNo.value = pay.paymentNo
    payAmount.value = pay.amount
    payStatus.value = pay.status
    // 二维码内容指向本机局域网的支付确认页（手机与收银台须在同一局域网）
    const confirmUrl = buildConfirmUrl(pay.paymentNo)
    qrDataUrl.value = confirmUrl ? await QRCode.toDataURL(confirmUrl, { width: 240, margin: 1 }) : ''
    qrVisible.value = true
    startPolling()
  } finally {
    creatingNative.value = false
  }
}

// ---------- 扫码确认页 URL ----------
/**
 * 构造手机可访问的支付确认页地址。
 *
 * <p>关键：必须用**局域网 IP**，不能用 127.0.0.1 ——
 * 后者在手机上指向手机自己，扫出来必然打不开。
 * 优先取当前浏览器访问后台时用的 host（顾客能打开收银台，说明这个 host 在网络内可达）。
 */
function buildConfirmUrl(paymentNo: string): string {
  if (!paymentNo) return ''
  const { protocol, hostname, port } = window.location
  // 若后台是通过 127.0.0.1 打开（本机调试），手机无法访问，给出明确提示
  const isLoopback = ['127.0.0.1', 'localhost', '::1'].includes(hostname)
  if (isLoopback) {
    ElMessage.warning({
      message: '当前用 127.0.0.1 打开后台，二维码手机上打不开。请改用局域网 IP 访问后台后再扫码。',
      duration: 8000,
    })
  }
  const origin = protocol + '//' + hostname + (port ? ':' + port : '')
  return origin + '/pay/confirm?paymentNo=' + encodeURIComponent(paymentNo)
}

// ---------- Native 轮询 ----------
const qrVisible = ref(false)
const qrDataUrl = ref('')
const paymentNo = ref('')
const payAmount = ref(0)
const payStatus = ref('')
let pollTimer: ReturnType<typeof setInterval> | null = null
let pollCount = 0

const mockPaying = ref(false)

/** 收银台本机直接确认支付：等价于顾客在手机确认页点心确认，走同一个 Mock 通知接口 */
async function mockConfirmPay(): Promise<void> {
  if (!paymentNo.value || mockPaying.value) return
  mockPaying.value = true
  try {
    const res = await fetch('/api/payment/mock/notify', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        paymentNo: paymentNo.value,
        amount: payAmount.value,
        appId: 'mock-appid',
        mchId: 'mock-mchid',
      }),
    })
    const json = await res.json()
    if (json.code === 0) {
      payStatus.value = 'SUCCESS'
      ElMessage.success('支付成功')
      stopPolling()
      qrVisible.value = false
      afterPaid()
    } else {
      ElMessage.error(json.message || '支付失败')
    }
  } catch {
    ElMessage.error('网络异常，支付未完成')
  } finally {
    mockPaying.value = false
  }
}

function stopPolling(): void {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
  pollCount = 0
}

function startPolling(): void {
  stopPolling()
  pollTimer = setInterval(async () => {
    if (!paymentNo.value) return
    pollCount += 1
    if (pollCount > 100) {
      stopPolling()
      payStatus.value = 'TIMEOUT'
      return
    }
    try {
      const result = await queryPayment(paymentNo.value)
      payStatus.value = result.status
      if (['SUCCESS', 'FAILED', 'CLOSED'].includes(result.status)) {
        stopPolling()
        if (result.status === 'SUCCESS') {
          ElMessage.success('支付成功：' + result.paymentNo)
          qrVisible.value = false
          afterPaid()
        } else {
          ElMessage.warning('支付未成功，状态：' + result.status)
        }
      }
    } catch {
      // 轮询失败静默重试，避免刷屏
    }
  }, 3000)
}

onBeforeUnmount(stopPolling)

// ---------- 订单 ----------
const orderLoading = ref(false)
const recentOrders = ref<OrderVO[]>([])

async function loadOrders(): Promise<void> {
  if (!appStore.currentStoreId) {
    recentOrders.value = []
    return
  }
  orderLoading.value = true
  try {
    const page = await getOrderPage({
      pageNo: 1,
      pageSize: 10,
      storeId: appStore.currentStoreId,
    })
    recentOrders.value = page.records || []
  } finally {
    orderLoading.value = false
  }
}

function completeOrder(row: OrderVO): void {
  ElMessage.info('订单完成请通过订单管理页的状态流转操作（PATCH /orders/{orderNo}/status），本页只做收银')
}

const refundVisible = ref(false)
const refundSubmitting = ref(false)
const refundForm = reactive({ orderNo: '', maxRefund: 0, amountYuan: '', reason: '' })

function openRefund(row: OrderVO): void {
  refundForm.orderNo = row.orderNo
  refundForm.maxRefund = Math.max(0, Number(row.paidAmount || 0) - Number(row.refundedAmount || 0))
  refundForm.amountYuan = fenToYuan(refundForm.maxRefund)
  refundForm.reason = ''
  refundVisible.value = true
}

async function submitRefund(): Promise<void> {
  const fen = yuanToFen(refundForm.amountYuan)
  if (Number.isNaN(fen) || fen <= 0) {
    ElMessage.error('请输入合法退款金额')
    return
  }
  if (fen > refundForm.maxRefund) {
    ElMessage.error('退款金额不能超过可退金额')
    return
  }
  refundSubmitting.value = true
  try {
    await applyRefund({ orderNo: refundForm.orderNo, refundAmount: fen, reason: refundForm.reason || undefined })
    ElMessage.success('退款申请已提交')
    refundVisible.value = false
    await loadOrders()
  } finally {
    refundSubmitting.value = false
  }
}

watch(
  () => appStore.currentStoreId,
  () => {
    void loadOrders()
  },
)

onMounted(async () => {
  await initStores()
  if (storeLoadError.value) {
    // 门店加载失败：重试一次；仍失败则停留在引导态，不加载业务数据
    await initStores()
    if (storeLoadError.value) return
  }
  await reloadProducts()
  await loadOrders()
})
</script>

<style scoped>
.cashier-top {
  margin-bottom: 12px;
}

.store-retry {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* 面板基础样式：不设 height:100%，避免同列多卡互相挤压、溢出压住下方卡片 */
.panel {
  display: flex;
  flex-direction: column;
}

.panel :deep(.el-card__body) {
  flex: 1 1 auto;
  min-height: 0;
}

/* 右列：会员卡 + 购物车卡纵向排列，各自按内容高度展开 */
.right-col {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.right-col .cart-panel {
  flex: 1 1 auto;
}

/* 订单卡片：表格横向溢出时在卡片内滚动，不越过卡片边界 */
.order-panel {
  width: 100%;
}

.order-panel :deep(.el-card__body) {
  overflow-x: auto;
}

.order-panel :deep(.el-table) {
  width: 100%;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 10px;
  min-height: 200px;
}

.product-card {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 10px;
  cursor: pointer;
  transition: all 0.15s;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.product-card:hover {
  border-color: #67c23a;
  box-shadow: 0 2px 10px rgba(103, 194, 58, 0.18);
}

.product-name {
  font-weight: 600;
  font-size: 13px;
}

.product-code {
  color: #909399;
}

.product-price {
  display: flex;
  align-items: baseline;
  gap: 3px;
}

.member-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.member-card {
  border: 1px dashed #67c23a;
  border-radius: 8px;
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.member-line {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.quote-box {
  margin-top: 12px;
}

.payable {
  font-size: 18px;
}

.pay-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
}

.qr-tip {
  margin: 10px 0 0;
  font-size: 12px;
  color: #909399;
  text-align: center;
}

.qr-pay-btn {
  width: 100%;
  margin: 10px 0 4px;
}

.qr-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.qr-meta {
  width: 100%;
  font-size: 13px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
</style>
