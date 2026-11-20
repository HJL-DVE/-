<template>
  <div class="page">
    <div class="page-card">
      <el-alert type="warning" :closable="false" show-icon class="pending-box">
        <template #title><strong>规则编辑接口后端未提供，本期只读</strong></template>
        <div>
          marketing-service 的管理端仅暴露积分账户 / 流水 / 人工调整三个接口
          （<code>GET /api/admin/marketing/points/account</code>、
          <code>GET /api/admin/marketing/points/transactions</code>、
          <code>POST /api/admin/marketing/points/adjust</code>），
          <b>没有</b> <code>/api/admin/marketing/points/rules</code> 之类的规则读写端点。
          本页因此只做规则说明，不提供保存按钮，也不会用前端常量冒充接口返回。
        </div>
      </el-alert>

      <el-descriptions :column="2" border size="small" style="margin: 14px 0">
        <el-descriptions-item label="后端表">mkt_points_rule（按 brand_id 配置，uk_points_rule 唯一）</el-descriptions-item>
        <el-descriptions-item label="默认数据来源">
          Flyway 迁移 V2__points_level_activity.sql（ORDER_EARN）+ V5__sign_in_rule.sql（SIGN_IN）
        </el-descriptions-item>
        <el-descriptions-item label="管理端可用接口">account / transactions / adjust（无规则 CRUD）</el-descriptions-item>
        <el-descriptions-item label="权限点">
          <span class="mono">marketing:points:rule</span>、<span class="mono">marketing:points:rule:update</span>
          已存在于 iam_db，但后端尚无对应 Controller 方法
        </el-descriptions-item>
      </el-descriptions>

      <h3 style="margin: 18px 0 10px">当前生效规则（迁移脚本默认值）</h3>
      <div class="stat-grid">
        <div class="stat-card">
          <div class="stat-label">消费得积分</div>
          <div class="stat-value">1 积分 / 1 元</div>
          <div class="stat-extra">amount_per_point = 100（分），即每消费 100 分钱得 1 积分</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">签到基础积分</div>
          <div class="stat-value">5 分</div>
          <div class="stat-extra">sign_in_points = 5（每日签到）</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">连续签到加成</div>
          <div class="stat-value">2 分 / 天</div>
          <div class="stat-extra">sign_in_bonus = 2，连续签到上限 sign_in_continuous_max = 7 天</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">积分有效期</div>
          <div class="stat-value">365 天</div>
          <div class="stat-extra">expire_days = 365，0 表示永不过期；按批次先进先出过期</div>
        </div>
      </div>

      <h3 style="margin: 22px 0 10px">规则明细</h3>
      <el-table :data="rules" border stripe size="small">
        <el-table-column prop="ruleCode" label="rule_code" width="140">
          <template #default="{ row }"><span class="mono">{{ row.ruleCode }}</span></template>
        </el-table-column>
        <el-table-column prop="ruleName" label="规则名称" width="140" />
        <el-table-column prop="amountPerPoint" label="amount_per_point" width="150" align="right" />
        <el-table-column prop="signInPoints" label="sign_in_points" width="130" align="right" />
        <el-table-column prop="continuousMax" label="连续上限（天）" width="130" align="right" />
        <el-table-column prop="signInBonus" label="连续加成（分/天）" width="140" align="right" />
        <el-table-column prop="expireDays" label="expire_days" width="120" align="right" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ENABLED' ? 'success' : 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="usedBy" label="生效场景" min-width="180" show-overflow-tooltip />
      </el-table>

      <h3 style="margin: 22px 0 10px">积分流水业务类型（PointsBizType，出流水时展示）</h3>
      <el-table :data="bizTypes" border stripe size="small">
        <el-table-column prop="code" label="bizType" width="180">
          <template #default="{ row }"><span class="mono">{{ row.code }}</span></template>
        </el-table-column>
        <el-table-column prop="label" label="中文名" width="140" />
        <el-table-column prop="bizNo" label="bizNo 语义" min-width="220" show-overflow-tooltip />
        <el-table-column prop="direction" label="方向" width="120" />
      </el-table>

      <div class="text-muted" style="margin-top: 12px">
        说明：上表的数值来自仓库内 Flyway 迁移脚本的默认值，属于「数据库设计约定的规则」，不是任何接口的返回值。
        若运营已在库中改过值，请以
        <code>SELECT rule_code, amount_per_point, sign_in_points, sign_in_continuous_max, sign_in_bonus, expire_days FROM mkt_points_rule</code>
        的实际结果为准；后台暂无可视化入口读取该表。
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
interface RuleRow {
  ruleCode: string
  ruleName: string
  amountPerPoint: number
  signInPoints: number
  continuousMax: number
  signInBonus: number
  expireDays: number
  status: string
  usedBy: string
}

interface BizTypeRow {
  code: string
  label: string
  bizNo: string
  direction: string
}

/** 数值取自 marketing-service Flyway 迁移 V2/V5 的 mkt_points_rule 默认值 */
const rules: RuleRow[] = [
  {
    ruleCode: 'ORDER_EARN',
    ruleName: '消费得积分',
    amountPerPoint: 100,
    signInPoints: 5,
    continuousMax: 7,
    signInBonus: 2,
    expireDays: 365,
    status: 'ENABLED',
    usedBy: '订单支付成功后按实付金额换算积分',
  },
  {
    ruleCode: 'SIGN_IN',
    ruleName: '签到得积分',
    amountPerPoint: 100,
    signInPoints: 5,
    continuousMax: 7,
    signInBonus: 2,
    expireDays: 365,
    status: 'ENABLED',
    usedBy: '每日签到，连续签到按天数加成（上限 7 天）',
  },
]

const bizTypes: BizTypeRow[] = [
  { code: 'ORDER_EARN', label: '消费得积分', bizNo: '订单号', direction: '增加' },
  { code: 'ORDER_REFUND_REVERSE', label: '退款冲正', bizNo: '原订单号', direction: '扣回' },
  { code: 'SIGN_IN', label: '签到', bizNo: 'yyyyMMdd', direction: '增加' },
  { code: 'ACTIVITY', label: '活动奖励', bizNo: '活动编号+会员+业务单', direction: '增加' },
  { code: 'EXCHANGE', label: '积分兑换', bizNo: '兑换单号', direction: '扣减' },
  { code: 'DEDUCT', label: '人工扣减', bizNo: '操作单号', direction: '扣减' },
  { code: 'EXPIRE', label: '积分过期', bizNo: '批次号', direction: '扣减' },
  { code: 'ADJUST', label: '人工调整', bizNo: '操作单号', direction: '增加 / 扣减' },
]
</script>
