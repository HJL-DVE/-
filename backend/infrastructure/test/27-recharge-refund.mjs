/**
 * 阶段九：充值退卡（一键）端到端测试
 *
 * 验证口径：带赠送的充值单退卡 → 原路退实付本金，扣回全部赠送（不折现、不把赠送折成现金）。
 * 前置：网关 9000 与 wallet/payment 服务已启动，provider=mock。
 */
const GW = 'http://127.0.0.1:9000';
let pass = 0, fail = 0;
const chk = (name, ok, detail) => {
  if (ok) pass++; else fail++;
  console.log((ok ? '  [PASS] ' : '  [FAIL] ') + name + '  ' + detail);
};
const call = async (method, path, { token, body } = {}) => {
  const h = { 'Content-Type': 'application/json' };
  if (token) h.Authorization = 'Bearer ' + token;
  const res = await fetch(GW + path, { method, headers: h, body: body === undefined ? undefined : JSON.stringify(body) });
  const text = await res.text();
  let json = null; try { json = JSON.parse(text); } catch {}
  return { status: res.status, json, text: text.slice(0, 260) };
};
const yuan = fen => (fen / 100).toFixed(2);
const sleep = ms => new Promise(r => setTimeout(r, ms));

let ready = false;
for (let i = 0; i < 30; i++) {
  try { const r = await fetch(GW + '/actuator/health'); if (r.ok) { ready = true; break; } } catch {}
  await sleep(2000);
}
chk('网关就绪', ready, 'health=' + ready);
if (!ready) { console.log('中止'); process.exit(1); }

console.log('');
console.log('=== 0. 登录 ===');
let r = await call('POST', '/api/admin/auth/login', { body: { username: 'admin', password: 'TeaClub@123' } });
const adminTok = r.json?.data?.accessToken;
chk('管理端登录', !!adminTok, 'code=' + r.json?.code);
r = await call('POST', '/api/auth/wx-login', { body: { code: 'refund-e2e-' + Date.now(), nickname: '退卡测试' } });
const memberTok = r.json?.data?.token;
chk('会员登录', !!memberTok, 'code=' + r.json?.code);
if (!adminTok || !memberTok) { console.log('中止'); process.exit(1); }

console.log('');
console.log('=== 1. 准备一笔已支付的「充 100 送 20」 ===');
r = await call('GET', '/api/wallet/plans?storeId=1', { token: memberTok });
const plans = r.json?.data || [];
const plan = plans.find(p => p.rechargeAmount === 10000 && p.giftAmount > 0) || plans.find(p => p.giftAmount > 0) || plans[0];
chk('取到带赠送的充值方案', !!plan, plan ? plan.planName + ' 充=' + yuan(plan.rechargeAmount) + ' 送=' + yuan(plan.giftAmount) : '(无)');

r = await call('GET', '/api/wallet/account', { token: memberTok });
const bal0 = r.json?.data;
chk('读取充值前余额', r.json?.code === 0, '实付=' + yuan(bal0?.principalBalance || 0) + ' 赠送=' + yuan(bal0?.giftBalance || 0));

r = await call('POST', '/api/wallet/recharges', { token: memberTok, body: { planId: plan.id, storeId: 1 } });
const rec = r.json?.data;
chk('充值单创建', r.json?.code === 0 && !!rec?.rechargeNo, 'rechargeNo=' + rec?.rechargeNo);
if (!rec?.rechargeNo) { console.log('中止'); process.exit(1); }

r = await call('POST', '/api/payment/create', { token: memberTok, body: { bizType: 'RECHARGE', bizNo: rec.rechargeNo } });
const pay = r.json?.data;
chk('支付单创建', r.json?.code === 0 && !!pay?.paymentNo, 'paymentNo=' + pay?.paymentNo + ' 金额=' + yuan(pay?.amount || 0));
r = await call('POST', '/api/payment/mock/notify', { body: { paymentNo: pay?.paymentNo, amount: pay?.amount } });
chk('模拟支付成功', r.json?.code === 0, 'code=' + r.json?.code + ' ' + (r.json?.data || ''));

let balPaid = null;
for (let i = 0; i < 10; i++) {
  await sleep(700);
  const rr = await call('GET', '/api/wallet/account', { token: memberTok });
  balPaid = rr.json?.data;
  if ((balPaid?.principalBalance || 0) === (bal0?.principalBalance || 0) + rec.rechargeAmount) break;
}
chk('充值到账（实付+赠送）',
  (balPaid?.principalBalance || 0) === (bal0?.principalBalance || 0) + rec.rechargeAmount &&
  (balPaid?.giftBalance || 0) === (bal0?.giftBalance || 0) + rec.giftAmount,
  '实付=' + yuan(balPaid?.principalBalance || 0) + ' 赠送=' + yuan(balPaid?.giftBalance || 0));

console.log('');
console.log('=== 2. 一键退卡 ===');
r = await call('POST', '/api/admin/payment/recharge-refunds', {
  token: adminTok,
  body: { rechargeNo: rec.rechargeNo, reason: 'E2E 退卡验证' },
});
const refund = r.json?.data;
chk('退卡接口受理', r.json?.code === 0 && !!refund?.refundNo,
  'code=' + r.json?.code + ' ' + (r.json?.message || '') + ' refundNo=' + refund?.refundNo);
chk('只退实付本金（不是 120）', refund?.refundAmount === rec.rechargeAmount && refund?.refundCashAmount === rec.rechargeAmount,
  'refundAmount=' + yuan(refund?.refundAmount || 0) + ' cash=' + yuan(refund?.refundCashAmount || 0));
chk('赠送金不进退款金额', (refund?.refundGiftAmount || 0) === 0, 'refundGiftAmount=' + (refund?.refundGiftAmount || 0));

let balAfter = null;
for (let i = 0; i < 10; i++) {
  await sleep(600);
  const rr = await call('GET', '/api/wallet/account', { token: memberTok });
  balAfter = rr.json?.data;
  if ((balAfter?.principalBalance || 0) === (balPaid?.principalBalance || 0) - rec.rechargeAmount) break;
}
chk('钱包扣回实付', (balAfter?.principalBalance || 0) === (balPaid?.principalBalance || 0) - rec.rechargeAmount,
  yuan(balPaid?.principalBalance || 0) + ' -> ' + yuan(balAfter?.principalBalance || 0));
chk('钱包扣回赠送', (balAfter?.giftBalance || 0) === (balPaid?.giftBalance || 0) - rec.giftAmount,
  yuan(balPaid?.giftBalance || 0) + ' -> ' + yuan(balAfter?.giftBalance || 0));

console.log('');
console.log('=== 3. 幂等：重复退卡 ===');
r = await call('POST', '/api/admin/payment/recharge-refunds', {
  token: adminTok,
  body: { rechargeNo: rec.rechargeNo, reason: '重复点击' },
});
chk('重复退卡返回同一退款单', r.json?.code === 0 && r.json?.data?.refundNo === refund?.refundNo,
  'refundNo=' + r.json?.data?.refundNo);
const balDup = (await call('GET', '/api/wallet/account', { token: memberTok })).json?.data;
chk('重复退卡没有二次扣款',
  (balDup?.principalBalance || 0) === (balAfter?.principalBalance || 0) &&
  (balDup?.giftBalance || 0) === (balAfter?.giftBalance || 0),
  '实付=' + yuan(balDup?.principalBalance || 0) + ' 赠送=' + yuan(balDup?.giftBalance || 0));

console.log('');
console.log('=== 4. 退款通知 → 充值单终态 ===');
r = await call('POST', '/api/payment/mock/refund-notify', { body: { refundNo: refund?.refundNo } });
chk('模拟退款成功通知', r.json?.code === 0, 'code=' + r.json?.code + ' ' + (r.json?.data || ''));
r = await call('GET', '/api/wallet/recharges/' + rec.rechargeNo, { token: memberTok });
chk('充值单状态 = REFUNDED', r.json?.data?.status === 'REFUNDED', 'status=' + r.json?.data?.status);

console.log('');
console.log('=== 汇总: PASS=' + pass + ' FAIL=' + fail + ' ===');
if (fail > 0) process.exitCode = 1;
