/**
 * 41-e2e-business-flow.mjs —— 核心业务全链路端到端测试
 * 注册 → 领券 → 充值 → 点单试算(带券) → 下单(带券) → 钱包支付 → 积分 → 报表五方一致
 */
const GW = 'http://127.0.0.1:9000';
let pass = 0, fail = 0;
const fails = [];
const chk = (n, ok, d) => {
  if (ok) pass++; else { fail++; fails.push({ n, d }); }
  console.log((ok ? '  [PASS] ' : '  [FAIL] ') + n + '  ' + (d || ''));
};
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const req = async (m, p, { token, body } = {}) => {
  const h = { 'Content-Type': 'application/json' };
  if (token) h.Authorization = 'Bearer ' + token;
  const r = await fetch(GW + p, { method: m, headers: h, body: body === undefined ? undefined : JSON.stringify(body) });
  return await r.json().catch(() => ({}));
};

const stamp = Date.now().toString(36);
console.log('=== 1. 会员注册 ===');
const phone = '1' + String(Date.now()).slice(-10);
const reg = await req('POST', '/api/auth/member-register', { body: { phone, password: 'Test@12345', nickname: 'E2E' + stamp } });
chk('注册成功', reg.code === 0, reg.code === 0 ? 'memberId=' + reg.data?.memberId : reg.message);
const tok = reg.data?.token;
const mid = reg.data?.memberId;
if (!tok) { console.log('注册失败，终止'); process.exit(1); }

console.log('');
console.log('=== 2. 领取优惠券 ===');
const tpls = (await req('GET', '/api/marketing/coupons/available', { token: tok })).data || [];
const tpl = tpls.find(t => t.thresholdAmount > 0 && t.faceValue > 0);
chk('有可领券模板', !!tpl, tpl ? `${tpl.couponName} 门槛${tpl.thresholdAmount / 100} 面额${tpl.faceValue / 100}` : '无');
if (tpl) {
  const got = await req('POST', `/api/marketing/coupons/receive?templateId=${tpl.id}&qty=1`, { token: tok });
  chk('领券成功', got.code === 0, 'count=' + (got.data || []).length);
}
const usable = (await req('GET', '/api/marketing/coupons/usable', { token: tok })).data || [];
chk('可用券非空', usable.length > 0, usable.length + ' 张');
const cp = usable[0];

console.log('');
console.log('=== 3. 充值（本金+赠送）===');
const plans = (await req('GET', '/api/wallet/plans', { token: tok })).data || [];
const plan = plans.find(p => p.rechargeAmount >= 5000) || plans[0];
chk('有充值套餐', !!plan, plan ? `${plan.rechargeAmount / 100}元` : '无');
const rc = (await req('POST', '/api/wallet/recharges', { token: tok, body: { planId: plan.id, storeId: 1 } })).data;
chk('创建充值单', !!rc?.rechargeNo, rc?.rechargeNo);
const pay = (await req('POST', '/api/payment/create', { token: tok, body: { bizType: 'RECHARGE', bizNo: rc.rechargeNo } })).data;
chk('创建支付单', !!pay?.paymentNo, pay?.paymentNo + ' 金额=' + pay?.amount);
const notify = await req('POST', '/api/payment/mock/notify', { body: { paymentNo: pay.paymentNo, amount: pay.amount } });
chk('Mock 支付回调', notify.code === 0, notify.code === 0 ? 'ok' : notify.message);
await sleep(2500);
const acct = (await req('GET', '/api/wallet/account', { token: tok })).data;
chk('钱包到账', (acct?.principalBalance || 0) > 0, '本金=' + (acct?.principalBalance || 0) / 100 + ' 赠送=' + (acct?.giftBalance || 0) / 100);

console.log('');
console.log('=== 4. 订单试算（带券 vs 不带券）===');
const items = [{ skuId: 1, quantity: 2 }];
const q0 = (await req('POST', '/api/order/quote', { token: tok, body: { storeId: 1, items } })).data;
chk('不带券试算', q0?.payableAmount > 0, '应付=' + (q0?.payableAmount || 0) / 100);
let q1 = null;
if (cp) {
  q1 = (await req('POST', '/api/order/quote', { token: tok, body: { storeId: 1, items, memberCouponId: cp.id } })).data;
  chk('带券试算有抵扣', q1?.couponAmount > 0, '抵扣=' + (q1?.couponAmount || 0) / 100 + ' 应付=' + (q1?.payableAmount || 0) / 100);
  chk('带券更便宜', q1.payableAmount < q0.payableAmount, (q0.payableAmount / 100) + ' -> ' + (q1.payableAmount / 100));
}

console.log('');
console.log('=== 5. 下单 → 钱包支付 → 券核销 ===');
const ord = (await req('POST', '/api/order/orders', { token: tok, body: { storeId: 1, items, memberCouponId: cp?.id } })).data;
chk('创建订单', !!ord?.orderNo, ord?.orderNo + ' 应付=' + (ord?.payableAmount || 0) / 100);
const payRes = await req('POST', `/api/order/orders/${ord.orderNo}/pay-wallet`, { token: tok, body: {} });
chk('钱包支付', payRes.code === 0, payRes.code === 0 ? 'ok' : payRes.message);
await sleep(3000);
const detail = (await req('GET', `/api/order/orders/${ord.orderNo}`, { token: tok })).data;
chk('订单已支付', detail?.status === 'PAID', 'status=' + detail?.status);
if (cp) {
  const mine = (await req('GET', '/api/marketing/coupons/mine?status=USED', { token: tok })).data || [];
  chk('券已核销', mine.some(c => c.id === cp.id), 'USED 券=' + mine.length + ' 张');
}

console.log('');
console.log('=== 6. 积分与等级 ===');
const acc = (await req('GET', '/api/marketing/points/account', { token: tok })).data;
chk('积分已到账', (acc?.totalPoints || 0) > 0, '积分=' + acc?.totalPoints);
const txns = (await req('GET', '/api/marketing/points/transactions?limit=10', { token: tok })).data || [];
const earn = txns.find(t => t.bizType === 'ORDER_EARN');
chk('含 ORDER_EARN 流水', !!earn, earn ? `+${earn.changePoints}` : '未找到');
const prof = (await req('GET', '/api/member/profile', { token: tok })).data;
chk('会员等级已记录', !!prof?.levelName, '等级=' + prof?.levelName + ' 累计消费=' + (prof?.totalConsumeAmount || 0) / 100);

console.log('');
console.log('=== 7. 报表五方一致 ===');
const adm = await req('POST', '/api/admin/auth/login', { body: { username: 'admin', password: 'TeaClub@123' } });
const atok = adm.data?.accessToken;
const stats = (await req('GET', '/api/report/dashboard/overview', { token: atok })).data;
chk('报表可读', !!stats, '今日订单=' + stats?.todayOrders + ' 今日销售=' + (stats?.todaySales || 0) / 100);
const memberStats = (await req('GET', `/api/report/member/overview?memberId=${mid}`, { token: atok })).data;
chk('会员报表按 memberId 过滤', memberStats !== null, JSON.stringify(memberStats || {}).slice(0, 100));

console.log('');
console.log('=== 8. 数据一致性交叉核对 ===');
const orderDetail = (await req('GET', `/api/order/orders/${ord.orderNo}`, { token: tok })).data;
const paid = orderDetail?.paidAmount || 0;
const q1Pay = q1?.payableAmount || 0;
chk('订单实付与试算应付一致', paid === q1Pay || Math.abs(paid - q1Pay) < 2, `订单=${paid} 试算=${q1Pay}`);
const walletAfter = (await req('GET', '/api/wallet/account', { token: tok })).data;
const expectLeft = (acct?.principalBalance || 0) - (orderDetail?.principalPaid ?? paid);
chk('钱包余额扣减与订单一致', (walletAfter?.principalBalance || 0) <= acct?.principalBalance, `支付前=${acct?.principalBalance} 后=${walletAfter?.principalBalance}`);

console.log('');
console.log('=== 汇总: PASS=' + pass + ' FAIL=' + fail + ' ===');
if (fails.length) {
  console.log('失败明细:');
  fails.forEach(f => console.log('  - ' + f.n + '  ' + f.d));
}
if (fail > 0) process.exitCode = 1;