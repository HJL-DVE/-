// 抓取每个会员端接口的真实响应形状（用于对齐 src/api/types.ts）
import { writeFileSync } from 'node:fs'

const BASE = 'http://127.0.0.1:9000'
const out = {}

async function raw(method, path, body, token) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = 'Bearer ' + token
  const res = await fetch(BASE + path, { method, headers, body: body ? JSON.stringify(body) : undefined })
  const text = await res.text()
  let json = null
  try { json = JSON.parse(text) } catch { json = { nonJson: text.slice(0, 200) } }
  return { http: res.status, json }
}

const clone = (v, depth = 0) => {
  if (v === null || typeof v !== 'object') return v
  if (Array.isArray(v)) return [v.length > 0 ? clone(v[0], depth + 1) : null]
  if (depth > 3) return '{...}'
  const o = {}
  for (const k of Object.keys(v)) o[k] = clone(v[k], depth + 1)
  return o
}

async function dump(name, method, path, body, token) {
  const { http, json } = await raw(method, path, body, token)
  const code = json && json.code
  out[name] = { http, code, message: json && json.message, data: clone(json && json.data) }
  console.log('--- ' + name + '  ' + method + ' ' + path + '  http=' + http + ' code=' + code)
  console.log(JSON.stringify(clone(json && json.data), null, 1).slice(0, 1400))
  return json
}

const login1 = await raw('POST', '/api/auth/wx-login', { code: 'align-' + Date.now() })
console.log('=== wx-login raw ===')
console.log(JSON.stringify(login1.json, null, 1).slice(0, 900))
const token = login1.json && login1.json.data ? login1.json.data.token : ''
console.log('token length = ' + (token || '').length + '\n')
if (!token) { console.log('!! 未取得 token，终止'); process.exit(1) }

const t = token
await dump('member-me', 'GET', '/api/auth/member/me', null, t)
await dump('member-profile', 'GET', '/api/member/profile', null, t)
await dump('member-levels', 'GET', '/api/member/levels', null, t)
await dump('wallet-account', 'GET', '/api/wallet/account', null, t)
await dump('wallet-plans', 'GET', '/api/wallet/plans?storeId=1', null, t)
await dump('wallet-transactions', 'GET', '/api/wallet/transactions?pageNo=1&pageSize=3', null, t)
await dump('recharges-list', 'GET', '/api/wallet/recharges?pageNo=1&pageSize=3', null, t)
await dump('categories', 'GET', '/api/product/stores/1/categories', null, t)
await dump('spus', 'GET', '/api/product/stores/1/spus?pageNo=1&pageSize=3', null, t)
await dump('spu-detail', 'GET', '/api/product/stores/1/spus/1', null, t)
await dump('hot', 'GET', '/api/product/hot?storeId=1&limit=2', null, t)
await dump('stores', 'GET', '/api/auth/stores', null, t)
await dump('order-list', 'GET', '/api/order/orders?pageNo=1&pageSize=3', null, t)

// 试算：用真实 skuId
const detail = out['spu-detail'] && out['spu-detail'].data
const sku = detail && detail.skus && detail.skus.length ? detail.skus[0] : null
const skuIdGuess = sku ? (sku.skuId ?? sku.id) : 1
const q = await dump('order-quote', 'POST', '/api/order/quote', { storeId: 1, items: [{ skuId: skuIdGuess, quantity: 2 }] }, t)

// 充值单 + 支付单（不真实支付，只验证字段形状）
const rch = await dump('recharge-create', 'POST', '/api/wallet/recharges', { planId: 1, storeId: 1 }, t)
const rechargeNo = rch && rch.data ? rch.data.rechargeNo : ''
if (rechargeNo) {
  await dump('recharge-detail', 'GET', '/api/wallet/recharges/' + rechargeNo, null, t)
  const pay = await dump('payment-create', 'POST', '/api/payment/create', { bizType: 'RECHARGE', bizNo: rechargeNo }, t)
  const paymentNo = pay && pay.data ? pay.data.paymentNo : ''
  if (paymentNo) await dump('payment-query', 'GET', '/api/payment/orders/' + paymentNo, null, t)
}

writeFileSync('probe-shapes.json', JSON.stringify(out, null, 2))
console.log('\n形状明细 -> probe-shapes.json')
