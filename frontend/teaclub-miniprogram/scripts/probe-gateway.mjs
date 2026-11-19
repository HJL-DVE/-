// 直连网关验证小程序调用的每一个接口的真实返回（不使用任何 mock 数据源）。
// 用法: node scripts/probe-gateway.mjs
import { writeFileSync } from 'node:fs'

const BASE = 'http://127.0.0.1:9000'
const rows = []
let memberToken = ''

async function call(method, path, body, useAuth = true) {
  const headers = { 'Content-Type': 'application/json' }
  if (useAuth && memberToken) headers.Authorization = 'Bearer ' + memberToken
  try {
    const res = await fetch(BASE + path, { method, headers, body: body ? JSON.stringify(body) : undefined })
    const text = await res.text()
    let json = null
    try { json = JSON.parse(text) } catch { /* 非 JSON */ }
    if (json && json.data && json.data.token) memberToken = json.data.token
    return {
      http: res.status,
      code: json && json.code !== undefined ? json.code : null,
      msg: (json && json.message) || '',
      data: json ? json.data : null,
      snippet: text.slice(0, 160).replace(/\s+/g, ' '),
    }
  } catch (e) {
    return { http: 'ERR', code: 'ERR', msg: e.message, data: null, snippet: '' }
  }
}

async function probe(name, method, path, body, useAuth = true) {
  const r = await call(method, path, body, useAuth)
  const ok = r.http === 200 && r.code === 0
  const shape = r.data && typeof r.data === 'object'
    ? (Array.isArray(r.data) ? 'array(' + r.data.length + ')' : Object.keys(r.data).slice(0, 10).join(','))
    : String(r.data)
  rows.push({ name, method, path, http: r.http, code: r.code, msg: r.msg, ok })
  console.log((ok ? '[PASS] ' : '[FAIL] ') + name.padEnd(20) + ' ' + method.padEnd(4) + ' ' + path)
  console.log('       http=' + r.http + ' code=' + r.code + ' msg=' + (r.msg || '-') + ' data=' + shape)
  return r.data
}

console.log('网关: ' + BASE + '\n')

await probe('wx-login', 'POST', '/api/auth/wx-login', { code: 'probe-' + Date.now() }, false)
console.log('会员 token: ' + (memberToken ? '已获取(' + memberToken.length + 'B)' : '未获取') + '\n')

await probe('stores', 'GET', '/api/auth/stores', null, false)
await probe('store-detail', 'GET', '/api/auth/stores/1', null, false)
await probe('member-me', 'GET', '/api/auth/member/me')
await probe('member-profile', 'GET', '/api/member/profile')
await probe('member-levels', 'GET', '/api/member/levels')
await probe('wallet-account', 'GET', '/api/wallet/account')
await probe('wallet-transactions', 'GET', '/api/wallet/transactions?pageNo=1&pageSize=10')
await probe('wallet-plans', 'GET', '/api/wallet/plans?storeId=1')
await probe('recharges-list', 'GET', '/api/wallet/recharges?pageNo=1&pageSize=10')
await probe('categories', 'GET', '/api/product/stores/1/categories')
await probe('spus', 'GET', '/api/product/stores/1/spus?pageNo=1&pageSize=10')
await probe('spu-detail', 'GET', '/api/product/stores/1/spus/1')
await probe('hot', 'GET', '/api/product/hot?storeId=1&limit=6')
await probe('order-quote', 'POST', '/api/order/quote', { storeId: 1, items: [{ skuId: 1, quantity: 1 }] })

const orderPage = await probe('order-list', 'GET', '/api/order/orders?pageNo=1&pageSize=10')
const orderNo = orderPage && orderPage.records && orderPage.records[0] ? orderPage.records[0].orderNo : ''
if (orderNo) await probe('order-detail', 'GET', '/api/order/orders/' + orderNo)
else console.log('[SKIP] order-detail：列表为空，无单号可查')

const recharge = await probe('recharge-create', 'POST', '/api/wallet/recharges', { planId: 1, storeId: 1 })
const rechargeNo = recharge && recharge.rechargeNo ? recharge.rechargeNo : ''
if (rechargeNo) await probe('recharge-detail', 'GET', '/api/wallet/recharges/' + rechargeNo)

if (rechargeNo) {
  const payment = await probe('payment-create', 'POST', '/api/payment/create', { bizType: 'RECHARGE', bizNo: rechargeNo })
  const paymentNo = payment && payment.paymentNo ? payment.paymentNo : ''
  if (paymentNo) await probe('payment-query', 'GET', '/api/payment/orders/' + paymentNo)
}

const pass = rows.filter((r) => r.ok).length
console.log('\n=== 汇总: PASS ' + pass + ' / ' + rows.length + ' ===')
rows.filter((r) => !r.ok).forEach((r) => console.log('  未通过: ' + r.name + ' -> http=' + r.http + ' code=' + r.code + ' ' + r.msg))
writeFileSync('probe-result.json', JSON.stringify({ base: BASE, at: new Date().toISOString(), pass, total: rows.length, rows }, null, 2))
console.log('明细 -> probe-result.json')
