<template>
  <div class="page">
    <div class="page-card">
      <div class="rule-header">
        <div>
          <h3 class="rule-title">退款规则配置</h3>
          <p class="rule-desc">
            本页配置储值卡退款策略，影响会员在小程序的自助退卡行为。改动立即生效并记入审计日志。
          </p>
        </div>
        <el-tag type="danger" effect="plain">仅老板可修改</el-tag>
      </div>

      <el-alert
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 20px"
        title="资金安全提示：退款规则直接决定资金流出，请确认后再保存"
      />

      <el-form :model="form" label-width="200px" style="max-width: 720px">
        <el-divider content-position="left">扣款顺序</el-divider>

        <el-form-item label="消费扣款顺序">
          <el-radio-group v-model="form.walletDeductOrder">
            <el-radio label="GIFT_FIRST">先扣赠送金（对会员更有利）</el-radio>
            <el-radio label="PRINCIPAL_FIRST">先扣实付本金</el-radio>
          </el-radio-group>
          <div class="hint">
            影响退卡时实付余额的剩余量。选「先扣实付本金」时，赠送金会保留到最后。
          </div>
        </el-form-item>

        <el-divider content-position="left">退卡审核</el-divider>

        <el-form-item label="退卡是否需要人工审核">
          <el-switch
            v-model="needAudit"
            active-text="需要审核（更安全）"
            inactive-text="自助秒退"
            active-value="1"
            inactive-value="0"
          />
          <div class="hint">
            需要审核时：会员提交申请后由店长在「退卡记录」处理；
            自助秒退时：会员提交即退款，适合体验优先的场景。
          </div>
        </el-form-item>

        <el-form-item label="每日退卡次数上限">
          <el-input-number v-model="form.refundDailyLimit" :min="0" :max="10" />
          <span class="hint-inline">0 表示不限制</span>
          <div class="hint">
            防止短期内反复退卡刷流水，建议保持 1 次/天。
          </div>
        </el-form-item>

        <el-form-item label="赠送金已被消费时">
          <el-radio-group v-model="form.refundGiftConsumedAction">
            <el-radio label="REJECT">拒绝自助退卡，转门店人工处理</el-radio>
            <el-radio label="ALLOW">允许退卡，按剩余赠送金扣回</el-radio>
          </el-radio-group>
          <div class="hint">
            选择「拒绝」可防止会员把赠送金花掉后再退实付本金套利，
            这是更稳妥的资金策略。
          </div>
        </el-form-item>

        <el-divider content-position="left">当前生效规则</el-divider>

        <div class="summary">
          <div class="summary-row">
            <span class="muted">扣款顺序</span>
            <span>{{ form.walletDeductOrder === 'GIFT_FIRST' ? '先扣赠送金' : '先扣实付本金' }}</span>
          </div>
          <div class="summary-row">
            <span class="muted">退卡方式</span>
            <span>{{ needAudit === '1' ? '需人工审核' : '自助秒退' }}</span>
          </div>
          <div class="summary-row">
            <span class="muted">每日上限</span>
            <span>{{ form.refundDailyLimit === 0 ? '不限' : form.refundDailyLimit + ' 次' }}</span>
          </div>
          <div class="summary-row">
            <span class="muted">赠送金已用</span>
            <span>{{ form.refundGiftConsumedAction === 'REJECT' ? '拒绝转人工' : '按剩余扣回' }}</span>
          </div>
        </div>

        <el-form-item style="margin-top: 24px">
          <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
          <el-button @click="load">重置</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getBrand, updateRefundRule } from '@/api/brand'

const saving = ref(false)
const loading = ref(false)

const form = reactive({
  walletDeductOrder: 'GIFT_FIRST',
  refundNeedAudit: 1 as number,
  refundDailyLimit: 1,
  refundGiftConsumedAction: 'REJECT',
})

// el-switch 用字符串值，转换一下
const needAudit = computed({
  get: () => String(form.refundNeedAudit),
  set: (v: string) => { form.refundNeedAudit = Number(v) },
})

async function load(): Promise<void> {
  loading.value = true
  try {
    const brand = await getBrand()
    form.walletDeductOrder = brand.walletDeductOrder || 'GIFT_FIRST'
    form.refundNeedAudit = brand.refundNeedAudit ?? 1
    form.refundDailyLimit = brand.refundDailyLimit ?? 1
    form.refundGiftConsumedAction = brand.refundGiftConsumedAction || 'REJECT'
  } catch (e) {
    ElMessage.error('读取退款规则失败')
  } finally {
    loading.value = false
  }
}

async function save(): Promise<void> {
  try {
    await ElMessageBox.confirm(
      '退款规则将立即生效，确认保存？',
      '确认修改',
      { type: 'warning', confirmButtonText: '确认保存', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  saving.value = true
  try {
    await updateRefundRule({
      walletDeductOrder: form.walletDeductOrder,
      refundNeedAudit: form.refundNeedAudit,
      refundDailyLimit: form.refundDailyLimit,
      refundGiftConsumedAction: form.refundGiftConsumedAction,
    })
    ElMessage.success('退款规则已保存')
    await load()
  } catch (e) {
    ElMessage.error((e as Error)?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.rule-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 20px;
}
.rule-title {
  margin: 0 0 6px;
  font-size: 18px;
  font-weight: 600;
}
.rule-desc {
  margin: 0;
  font-size: 13px;
  color: #909399;
  line-height: 1.6;
}
.hint {
  font-size: 12px;
  color: #909399;
  line-height: 1.7;
  margin-top: 4px;
}
.hint-inline {
  font-size: 12px;
  color: #909399;
  margin-left: 10px;
}
.summary {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 16px 20px;
}
.summary-row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  line-height: 2.2;
}
.muted {
  color: #909399;
}
</style>