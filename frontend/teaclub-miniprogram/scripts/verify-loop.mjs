// 用前端实际使用的请求体，跑通「充值 -> Mock 支付通知 -> 钱包入账 -> 下单 -> 钱包支付 -> 流水」，
// 并打印每一步的真实响应形状（对齐 src/api/types.ts）。
import { writeFileSync } from 'node:fs'

const BASE = 'http://127.0.0.1:9000'
const log = []
const shapes = {}
let token = ''

async function call(method, path, body, useAuth = true) {
  const headers = { 'Content-Type': 'application/json' }
  if (useAuth && token) headers.Authorization = 'Bearer ' + token
  const res = await fetch(BASE + path, { method, headers, body: body ? JSON.stringify(body) : undefined })
  const text = await res.text()
  let json = null
  try { json = JSON.parse(text) } catch { json = { raw: text.slice(0, 200) } }
  const ok = res.status === 200 && json && json.code === 0
  log.push({ step: path, method, http: res.status, code: json && json.code, msg: json && json.message, ok })
  console.log((ok ? '[PASS] ' : '[FAIL] ') + method + ' ' + path + '  http=' + res.status + ' code=' + (json && json.code) + ' ' + (json && json.message || ''))
  return json
}

const short = (v) => JSON.stringify(v, null, 1).slice(0, 1200)

// 1) 登录
const login = await call('POST', '/api/auth/wx-login', { code: 'loop-' + Date.now() }, false)
token = login.data.token
const memberId = login.data.memberId
console.log('memberId=' + memberId + '\n')

// 2) 钱包初始
let acc = await call('GET', '/api/wallet/account')
shapes.walletBefore = acc.data
console.log('钱包(充值前): ' + short(acc.data))

// 3) 创建充值单（契约已改为 planId + storeId 必传）
const rc = await call('POST', '/api/wallet/recharges', { planId: 1, storeId: 1 })
shapes.rechargeCreate = rc.data
console.log('\n充值单响应: ' + short(rc.data))
const rechargeNo = rc.data.rechargeNo

// 4) 创建支付单
const pay = await call('POST', '/api/payment/create', { bizType: 'RECHARGE', bizNo: rechargeNo })
shapes.paymentCreate = pay.data
console.log('\n支付单响应: ' + short(pay.data))
const paymentNo = pay.data.paymentNo

// 5) Mock 支付成功通知（小程序端不调用，仅用于验证后端入账）
const notify = await call('POST', '/api/payment/mock/notify', { paymentNo, amount: pay.data.amount })
console.log('\nmock notify: ' + short(notify.data))

// 6) 轮询充值单 + 钱包（这正是 pages/recharge/result 的逻辑）
let paidStatus = ''
let accAfter = null
for (let i = 1; i <= 10; i++) {
  await new Promise((r) => setTimeout(r, 800))
  const r = await call('GET', '/api/wallet/recharges/' + rechargeNo)
  paidStatus = r.data.status
  const a = await call('GET', '/api/wallet/account')
  accAfter = a.data
  console.log('  轮询#' + i + ' recharge.status=' + paidStatus + ' balance=' + JSON.stringify(a.data))
  if (paidStatus === 'PAID') break
}
shapes.rechargeDetail = (await call('GET', '/api/wallet/recharges/' + rechargeNo)).data
shapes.walletAfterRecharge = accAfter
console.log('\n充值后充值单: ' + short(shapes.rechargeDetail))

// 7) 试算 + 下单（前端 order/confirm 的真实请求体）
const sku = 1
const q = await call('POST', '/api/order/quote', { storeId: 1, items: [{ skuId: sku, quantity: 1 }] })
shapes.quote = q.data
console.log('\n试算: ' + short(q.data))

const created = await call('POST', '/api/order/orders', {
  storeId: 1,
  items: [{ skuId: sku, quantity: 1 }],
  remark: '验证脚本：糖度:三分糖 / 温度:少冰',
  orderSource: 'MINIAPP',
})
shapes.orderCreate = created.data
console.log('\n下单响应: ' + short(created.data))
const orderNo = created.data.orderNo

// 8) 订单详情 + 列表
const od = await call('GET', '/api/order/orders/' + orderNo)
shapes.orderDetail = od.data
console.log('\n订单详情: ' + short(od.data))

const ol = await call('GET', '/api/order/orders?pageNo=1&pageSize=3')
shapes.orderList = ol.data
console.log('\n订单列表 records[0]: ' + short(ol.data.records && ol.data.records[0]))

// 9) 钱包支付
const payW = await call('POST', '/api/order/orders/' + orderNo + '/pay-wallet')
shapes.payWallet = payW.data
console.log('\n钱包支付: ' + short(payW.data))

// 10) 支付后钱包 + 流水（确认 bizType 真实取值）
const accFinal = await call('GET', '/api/wallet/account')
shapes.walletFinal = accFinal.data
console.log('\n支付后钱包: ' + short(accFinal.data))

const txn = await call('GET', '/api/wallet/transactions?pageNo=1&pageSize=5')
shapes.transactions = txn.data
console.log('\n流水: ' + short(txn.data.records))
const bizTypes = [...new Set((txn.data.records || []).map((t) => t.bizType))]
console.log('\n真实 bizType 取值: ' + JSON.stringify(bizTypes))

const pass = log.filter((l) => l.ok).length
console.log('\n=== 闭环 PASS ' + pass + ' / ' + log.length + ' ===')
writeFileSync('probe-loop.json', JSON.stringify({ shapes, log, bizTypes }, null, 2))
console.log('明细 -> probe-loop.json')
