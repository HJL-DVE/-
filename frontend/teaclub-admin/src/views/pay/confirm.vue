<!--
  移动端支付确认页（Mock 演示通道）
  ---------------------------------------------------------------
  用途：收银台生成二维码 → 手机扫码打开本页 → 点「确认支付」
        后端走与真实微信支付完全相同的通知处理链路（MockPaymentController#notify）

  设计约束：
  1. 本页必须免登录（路由 meta.public = true），因为扫码的是顾客手机，没有后台账号
  2. 页面只做「确认」动作，不承载任何金额计算 —— 金额由服务端支付单给出
  3. 重复点击由后端幂等（通知处理幂等表）兜底，前端只做按钮防抖
-->
<template>
  <div class="pay-confirm">
    <div class="card" v-loading="loading">
      <template v-if="state === 'loading'">
        <p class="tip">正在读取支付单…</p>
      </template>

      <template v-else-if="state === 'error'">
        <el-result icon="error" title="支付单无效" :sub-title="errorMsg" />
      </template>

      <template v-else-if="state === 'paid'">
        <el-result icon="success" title="支付已完成" :sub-title="'支付单号：' + detail.paymentNo" />
        <p class="tip">可以关闭本页面，收银台会自动关闭二维码窗口</p>
      </template>

      <template v-else>
        <h2 class="title">TeaClub 收银台</h2>
        <p class="tip">请确认本次消费金额</p>

        <div class="amount">
          <span class="symbol">¥</span>
          <span class="num">{{ yuan(detail.amount) }}</span>
        </div>

        <el-descriptions :column="1" border size="small" class="meta">
          <el-descriptions-item label="支付单号">{{ detail.paymentNo }}</el-descriptions-item>
          <el-descriptions-item label="业务单号">{{ detail.bizNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="支付状态">{{ detail.status }}</el-descriptions-item>
        </el-descriptions>

        <el-button
          type="success"
          size="large"
          class="pay-btn"
          :loading="submitting"
          @click="confirmPay"
        >
          确认支付 {{ yuan(detail.amount) }} 元
        </el-button>

        <p class="hint">
          演示环境说明：本页为课程设计的模拟收银通道，点击后通过后端 Mock 通知接口完成支付，
          走的验签、幂等、状态机链路与真实支付一致，但不会产生真实扣款。
        </p>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'

interface PayDetail {
  paymentNo: string
  bizNo?: string
  amount: number
  status: string
}

const route = useRoute()
const loading = ref(true)
const submitting = ref(false)
const state = ref<'loading' | 'ready' | 'error' | 'paid'>('loading')
const errorMsg = ref('')
const detail = reactive<PayDetail>({ paymentNo: '', bizNo: '', amount: 0, status: '' })

function yuan(fen: number): string {
  return ((fen || 0) / 100).toFixed(2)
}

/** 查询支付单详情（页面自检，确认支付单存在且未支付） */
async function loadDetail(): Promise<void> {
  const paymentNo = String(route.query.paymentNo || '')
  if (!paymentNo) {
    state.value = 'error'
    errorMsg.value = '缺少支付单号，请重新扫码'
    loading.value = false
    return
  }
  try {
    // 复用收银台同一个查询接口，免登录由网关放行
    const res = await fetch('/api/payment/mock/detail?paymentNo=' + encodeURIComponent(paymentNo))
    const json = await res.json()
    if (json.code !== 0 || !json.data) {
      state.value = 'error'
      errorMsg.value = json.message || '支付单不存在或已失效'
      return
    }
    Object.assign(detail, json.data)
    state.value = ['SUCCESS', 'PAID'].includes(json.data.status) ? 'paid' : 'ready'
  } catch (e) {
    state.value = 'error'
    errorMsg.value = '网络异常，请检查是否与收银台在同一网络'
  } finally {
    loading.value = false
  }
}

/** 确认支付：调用后端 Mock 通知接口 */
async function confirmPay(): Promise<void> {
  if (submitting.value) return
  submitting.value = true
  try {
    const res = await fetch('/api/payment/mock/notify', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        paymentNo: detail.paymentNo,
        amount: detail.amount,
        appId: 'mock-appid',
        mchId: 'mock-mchid',
      }),
    })
    const json = await res.json()
    if (json.code === 0) {
      state.value = 'paid'
      detail.status = 'SUCCESS'
      ElMessage.success('支付成功')
    } else {
      ElMessage.error(json.message || '支付失败')
    }
  } catch (e) {
    ElMessage.error('网络异常，支付未完成')
  } finally {
    submitting.value = false
  }
}

onMounted(loadDetail)
</script>

<style scoped>
.pay-confirm {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(160deg, #f0f9f4 0%, #e8f5e9 100%);
  padding: 20px 16px;
  box-sizing: border-box;
}

.card {
  width: 100%;
  max-width: 420px;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.08);
  padding: 24px 20px 20px;
  text-align: center;
}

.title {
  margin: 0 0 6px;
  font-size: 18px;
  color: #1f2d3d;
}

.tip {
  margin: 4px 0 0;
  color: #909399;
  font-size: 13px;
}

.amount {
  margin: 18px 0;
  color: #e6a23c;
}

.amount .symbol {
  font-size: 20px;
  margin-right: 2px;
}

.amount .num {
  font-size: 42px;
  font-weight: 700;
  line-height: 1;
}

.meta {
  margin-bottom: 20px;
  text-align: left;
}

.pay-btn {
  width: 100%;
  height: 46px;
  font-size: 16px;
}

.hint {
  margin: 16px 0 0;
  font-size: 12px;
  color: #b0b3b8;
  line-height: 1.6;
  text-align: left;
}
</style>
