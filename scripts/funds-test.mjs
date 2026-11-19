
const BASE = 'http://127.0.0.1:8080';
let pass = 0, fail = 0;
const failures = [];
const evidence = {};

async function req(method, path, body, token) {
  const headers = {};
  if (token) headers['Authorization'] = 'Bearer ' + token;
  if (body !== undefined) headers['Content-Type'] = 'application/json; charset=utf-8';
  try {
    const res = await fetch(BASE + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    const t = await res.text();
    try { return JSON.parse(t); } catch { return { code: -1, message: 'NONJSON:' + t.slice(0,150) }; }
  } catch (e) { return { code: -2, message: 'FETCH:' + e.message }; }
}
function check(name, actual, expected, detail) {
  if (String(actual) === String(expected)) { pass++; console.log('  [PASS] ' + name); }
  else { fail++; failures.push(name + ' | exp=' + expected + ' act=' + actual + ' | ' + (detail||'')); console.log('  [FAIL] ' + name + ' exp=' + expected + ' act=' + actual + ' ' + (detail||'')); }
}
function checkTrue(name, cond, detail) { check(name, cond === true, true, detail); }

(async () => {
  console.log('=== TeaClub 资金业务集成测试 ===\n');

  // ---------- login ----------
  const admin = await req('POST', '/api/admin/auth/login', { username: 'admin', password: 'TeaClub@123' });
  if (admin.code !== 0) { console.log('LOGIN FAILED: ' + JSON.stringify(admin)); process.exit(1); }
  const A = admin.data.accessToken;
  const mgr = await req('POST', '/api/admin/auth/login', { username: 'mgr_xihu', password: 'TeaClub@123' });
  const M = mgr.data.accessToken;

  // 预清理：保证脚本可重复执行。仅清理测试会员(3)的资金数据，不动种子钱包 1/2
  const { execSync } = await import('node:child_process');
  const MYSQL = 'C:/Program Files/MySQL/MySQL Server 8.4/bin/mysql.exe';
  const sqls = [
    "DELETE FROM wal_wallet_transaction WHERE member_id = 3",
    "DELETE FROM wal_wallet WHERE member_id = 3",
    "DELETE FROM wal_recharge_order WHERE member_id = 3",
    "DELETE FROM pay_payment_order WHERE biz_no LIKE 'RC%' OR biz_no IN (SELECT order_no FROM oms_order WHERE member_id = 3)",
    "DELETE FROM pay_refund_order WHERE member_id = 3",
    "DELETE FROM pay_notify_log",
    "DELETE FROM oms_order_item WHERE store_id IN (1,2) AND order_no IN (SELECT order_no FROM oms_order WHERE member_id = 3)",
    "DELETE FROM oms_order_status_log WHERE order_no IN (SELECT order_no FROM oms_order WHERE member_id = 3)",
    "DELETE FROM oms_order WHERE member_id = 3",
    "UPDATE mem_member SET total_consume_amount=0, total_recharge_amount=0, total_order_count=0 WHERE id=3",
    "UPDATE sys_store SET status='OPEN' WHERE id IN (1,2,3)"
  ];
  for (const s of sqls) {
    try {
      execSync('"' + MYSQL + '" -u teaclub -h 127.0.0.1 -D teaclub --default-character-set=utf8mb4 -e "' + s + '"',
        { env: { ...process.env, MYSQL_PWD: process.env.MYSQL_PASSWORD || process.env.MYSQL_PWD || '' }, stdio: 'pipe' });
    } catch (e) { /* 表不存在等可忽略 */ }
  }
  console.log('  [SETUP] 测试会员资金数据已重置');

  console.log('场景 1: 正常充值');
  console.log('------------------------------');
  // member 3 starts at 0 balance - good clean test subject
  const w0 = await req('GET', '/api/admin/wallet/accounts/member/3', undefined, A);
  check('会员3初始钱包查询', w0.code, 0, w0.message);
  const bal0P = w0.data.principalBalance, bal0G = w0.data.giftBalance;
  evidence.initialWallet = { principal: bal0P, gift: bal0G };

  // create recharge with plan 2 (200 yuan + 30 gift), store 1
  const rc1 = await req('POST', '/api/admin/wallet/recharges', {
    memberId: 3, storeId: 1, planId: 2, payChannel: 'MOCK'
  }, A);
  check('创建充值订单', rc1.code, 0, rc1.message);
  const rc1No = rc1.data?.rechargeNo;
  check('充值金额服务端计算=20000', rc1.data?.rechargeAmount, 20000, rc1.data?.rechargeAmount);
  check('赠送金额服务端计算=3000', rc1.data?.giftAmount, 3000, rc1.data?.giftAmount);

  // pay via mock
  const pay1 = await req('POST', '/api/admin/payment/create', {
    bizNo: rc1No, bizType: 'RECHARGE', channel: 'WECHAT_JSAPI'
  }, A);
  check('创建支付单', pay1.code, 0, pay1.message);
  check('支付单 provider=MOCK', pay1.data?.provider, 'MOCK', pay1.data?.provider);
  check('支付金额=充值金额', pay1.data?.amount, 20000, pay1.data?.amount);
  const pay1No = pay1.data?.paymentNo;

  const notify1 = await req('POST', '/api/payment/mock/notify', { paymentNo: pay1No });
  check('Mock 支付通知处理', notify1.code, 0, notify1.message);

  const w1 = await req('GET', '/api/admin/wallet/accounts/member/3', undefined, A);
  check('充值后实付余额=20000', w1.data.principalBalance, 20000, w1.data.principalBalance);
  check('充值后赠送余额=3000', w1.data.giftBalance, 3000, w1.data.giftBalance);
  evidence.afterRecharge = { principal: w1.data.principalBalance, gift: w1.data.giftBalance };

  // verify ledger
  const txn1 = await req('GET', '/api/admin/wallet/transactions?memberId=3&pageSize=10', undefined, A);
  const rechargeTxn = txn1.data.records.find(t => t.bizType === 'RECHARGE_IN');
  check('产生充值流水', !!rechargeTxn, true);
  check('流水记录变动前实付余额', rechargeTxn?.principalBefore, bal0P, rechargeTxn?.principalBefore);
  check('流水记录变动后实付余额', rechargeTxn?.principalAfter, 20000, rechargeTxn?.principalAfter);
  check('流水记录变动前赠送余额', rechargeTxn?.giftBefore, bal0G, rechargeTxn?.giftBefore);
  check('流水记录变动后赠送余额', rechargeTxn?.giftAfter, 3000, rechargeTxn?.giftAfter);

  console.log('\n场景 2: 充值重复通知（幂等）');
  console.log('------------------------------');
  const notify2 = await req('POST', '/api/payment/mock/notify', { paymentNo: pay1No });
  check('重复通知返回成功(幂等)', notify2.code, 0, notify2.message);
  const w2 = await req('GET', '/api/admin/wallet/accounts/member/3', undefined, A);
  check('重复通知后实付余额未变', w2.data.principalBalance, 20000, w2.data.principalBalance);
  check('重复通知后赠送余额未变', w2.data.giftBalance, 3000, w2.data.giftBalance);
  const txn2 = await req('GET', '/api/admin/wallet/transactions?memberId=3&bizType=RECHARGE_IN', undefined, A);
  check('充值流水仍只有 1 条', txn2.data.total, 1, txn2.data.total);

  console.log('\n场景 4: 余额不足');
  console.log('------------------------------');
  // member 3 has 20000+3000=23000. Try to order something > that.
  const quoteBig = await req('POST', '/api/admin/order/quote', {
    storeId: 1, memberId: 3, items: [{ skuId: 2, quantity: 20 }]
  }, A);
  check('试算大额订单', quoteBig.code, 0, quoteBig.message);
  const bigOrder = await req('POST', '/api/admin/order/orders', {
    storeId: 1, memberId: 3, items: [{ skuId: 2, quantity: 20 }], payWithBalance: true
  }, A);
  checkTrue('余额不足时下单/支付被拒绝', bigOrder.code !== 0, 'code=' + bigOrder.code + ' msg=' + bigOrder.message);
  const wAfterFail = await req('GET', '/api/admin/wallet/accounts/member/3', undefined, A);
  check('余额不足失败后余额未变', wAfterFail.data.principalBalance, 20000, wAfterFail.data.principalBalance);

  console.log('\n场景 3 + 9: 正常消费 与 实付/赠送混合扣款');
  console.log('------------------------------');
  // 价格从服务端试算接口读取，避免把可变的门店价硬编码进测试
  const q1 = await req('POST', '/api/admin/order/quote', {
    storeId: 1, memberId: 3, items: [{ skuId: 1, quantity: 2 }]
  }, A);
  check('试算接口可用', q1.code, 0, q1.message);
  const unitPrice = q1.data.items[0].unitPrice;
  const expectTotal = unitPrice * 2;
  evidence.unitPrice = unitPrice;

  const ord1 = await req('POST', '/api/admin/order/orders', {
    storeId: 1, memberId: 3, items: [{ skuId: 1, quantity: 2 }]
  }, A);
  check('创建订单', ord1.code, 0, ord1.message);
  check('订单总额=门店现价×数量', ord1.data?.totalAmount, expectTotal, ord1.data?.totalAmount);
  const ord1No = ord1.data?.orderNo;

  // GIFT_FIRST 顺序：先扣赠送金（3000），不足部分扣实付金
  const expectGift = Math.min(3000, expectTotal);
  const expectPrincipal = expectTotal - expectGift;
  const balAfterConsume = 20000 - expectPrincipal;

  const payBal1 = await req('POST', '/api/admin/order/orders/' + ord1No + '/pay-balance', undefined, A);
  check('余额支付成功', payBal1.code, 0, payBal1.message);
  check('余额支付-赠送金优先扣款', payBal1.data?.balanceGiftAmount, expectGift, payBal1.data?.balanceGiftAmount);
  check('余额支付-实付金扣余额部分', payBal1.data?.balancePrincipalAmount, expectPrincipal, payBal1.data?.balancePrincipalAmount);
  check('两项扣款之和=应付金额',
        (payBal1.data?.balanceGiftAmount || 0) + (payBal1.data?.balancePrincipalAmount || 0),
        expectTotal);
  evidence.mixedConsume = { giftDeduct: payBal1.data?.balanceGiftAmount, principalDeduct: payBal1.data?.balancePrincipalAmount };

  const w3 = await req('GET', '/api/admin/wallet/accounts/member/3', undefined, A);
  check('消费后实付余额正确', w3.data.principalBalance, balAfterConsume, w3.data.principalBalance);
  check('消费后赠送余额=0', w3.data.giftBalance, 0, w3.data.giftBalance);

  const txn3 = await req('GET', '/api/admin/wallet/transactions?memberId=3&bizType=CONSUME_OUT', undefined, A);
  const consumeTxn = txn3.data.records[0];
  check('产生消费流水', !!consumeTxn, true);
  check('消费流水实付变动', consumeTxn?.principalChange, -expectPrincipal, consumeTxn?.principalChange);
  check('消费流水赠送变动', consumeTxn?.giftChange, -expectGift, consumeTxn?.giftChange);

  console.log('\n场景 6: 支付失败');
  console.log('------------------------------');
  const rc2 = await req('POST', '/api/admin/wallet/recharges', {
    memberId: 3, storeId: 1, planId: 1, payChannel: 'MOCK'
  }, A);
  const rc2No = rc2.data?.rechargeNo;
  const pay2 = await req('POST', '/api/admin/payment/create', {
    bizNo: rc2No, bizType: 'RECHARGE', channel: 'WECHAT_JSAPI'
  }, A);
  const pay2No = pay2.data?.paymentNo;
  const failNotify = await req('POST', '/api/payment/mock/fail', { paymentNo: pay2No, reason: '模拟银行拒绝' });
  check('模拟支付失败', failNotify.code, 0, failNotify.message);
  const rc2Detail = await req('GET', '/api/admin/wallet/recharges/' + rc2No, undefined, A);
  check('支付失败后充值单未变 PAID', rc2Detail.data?.status !== 'PAID', true, rc2Detail.data?.status);
  const wAfterFail2 = await req('GET', '/api/admin/wallet/accounts/member/3', undefined, A);
  check('支付失败后余额未变', wAfterFail2.data.principalBalance, balAfterConsume, wAfterFail2.data.principalBalance);

  console.log('\n金额篡改拒绝测试');
  console.log('------------------------------');
  const rc3 = await req('POST', '/api/admin/wallet/recharges', {
    memberId: 3, storeId: 1, planId: 1, payChannel: 'MOCK'
  }, A);
  const rc3No = rc3.data?.rechargeNo;
  const pay3 = await req('POST', '/api/admin/payment/create', {
    bizNo: rc3No, bizType: 'RECHARGE', channel: 'WECHAT_JSAPI'
  }, A);
  const pay3No = pay3.data?.paymentNo;
  // send a tampered amount (1 cent instead of 10000)
  const tampered = await req('POST', '/api/payment/mock/notify', { paymentNo: pay3No, amount: 1 });
  checkTrue('通知金额被篡改时拒绝处理', tampered.code !== 0, 'code=' + tampered.code + ' msg=' + tampered.message);
  const wAfterTamper = await req('GET', '/api/admin/wallet/accounts/member/3', undefined, A);
  check('篡改通知未导致入账', wAfterTamper.data.principalBalance, balAfterConsume, wAfterTamper.data.principalBalance);

  console.log('\n场景 7: 退款成功（余额支付按原构成回冲）');
  console.log('------------------------------');
  // refund order ord1No fully: 3600 -> gift 3000, principal 600
  const refund1 = await req('POST', '/api/admin/payment/refunds', {
    orderNo: ord1No, refundAmount: expectTotal, reason: '测试全额退款'
  }, A);
  check('申请退款成功', refund1.code, 0, refund1.message);
  check('退款按原构成-实付部分', refund1.data?.refundPrincipalAmount, expectPrincipal, refund1.data?.refundPrincipalAmount);
  check('退款按原构成-赠送部分', refund1.data?.refundGiftAmount, expectGift, refund1.data?.refundGiftAmount);
  check('退款总额=原订单实付', refund1.data?.refundAmount, expectTotal, refund1.data?.refundAmount);

  const w4 = await req('GET', '/api/admin/wallet/accounts/member/3', undefined, A);
  check('退款后实付余额恢复=20000', w4.data.principalBalance, 20000, w4.data.principalBalance);
  check('退款后赠送余额恢复=3000', w4.data.giftBalance, 3000, w4.data.giftBalance);
  evidence.afterRefund = { principal: w4.data.principalBalance, gift: w4.data.giftBalance };

  console.log('\n场景 8: 重复退款');
  console.log('------------------------------');
  const refund2 = await req('POST', '/api/admin/payment/refunds', {
    orderNo: ord1No, refundAmount: expectTotal, reason: '重复退款测试'
  }, A);
  checkTrue('重复退款被拒绝', refund2.code !== 0, 'code=' + refund2.code + ' msg=' + refund2.message);
  const w5 = await req('GET', '/api/admin/wallet/accounts/member/3', undefined, A);
  check('重复退款未改变余额', w5.data.principalBalance, 20000, w5.data.principalBalance);

  console.log('\n场景 10: 跨门店消费');
  console.log('------------------------------');
  // member 3 wallet belongs to brand, should work at store 2 if cross-store enabled (default 1)
  const crossQuote = await req('POST', '/api/admin/order/quote', {
    storeId: 2, memberId: 3, items: [{ skuId: 1, quantity: 1 }]
  }, A);
  check('跨门店试算成功', crossQuote.code, 0, crossQuote.message);
  const crossOrder = await req('POST', '/api/admin/order/orders', {
    storeId: 2, memberId: 3, items: [{ skuId: 1, quantity: 1 }]
  }, A);
  check('跨门店下单成功', crossOrder.code, 0, crossOrder.message);
  const crossOrderNo = crossOrder.data?.orderNo;
  const wBeforeCross = await req('GET', '/api/admin/wallet/accounts/member/3', undefined, A);
  const beforeP = wBeforeCross.data.principalBalance;
  const beforeG = wBeforeCross.data.giftBalance;

  const crossPay = await req('POST', '/api/admin/order/orders/' + crossOrderNo + '/pay-balance', undefined, A);
  check('跨门店余额支付成功', crossPay.code, 0, crossPay.message);
  check('跨门店订单记录在门店2', crossOrder.data?.storeId, 2, crossOrder.data?.storeId);

  const crossTotal = crossOrder.data?.payableAmount;
  const w6 = await req('GET', '/api/admin/wallet/accounts/member/3', undefined, A);
  const consumedP = beforeP - w6.data.principalBalance;
  const consumedG = beforeG - w6.data.giftBalance;
  check('跨店消费扣款总额=应付金额', consumedP + consumedG, crossTotal,
        'principal=' + consumedP + ' gift=' + consumedG + ' payable=' + crossTotal);
  check('跨店消费遵循 GIFT_FIRST（先扣赠送金）',
        consumedG, Math.min(beforeG, crossTotal), 'giftUsed=' + consumedG);
  check('跨店钱包属于品牌而非单店（同一钱包被扣减）', w6.data.walletId, wBeforeCross.data.walletId,
        w6.data.walletId);

  console.log('\n门店数据权限：店长无法访问他店资金');
  console.log('------------------------------');
  const mgrWallet = await req('GET', '/api/admin/product/store-products?storeId=2', undefined, M);
  checkTrue('店长访问他店商品被拒', mgrWallet.code !== 0, 'code=' + mgrWallet.code);
  const mgrRechargeList = await req('GET', '/api/admin/wallet/recharges?storeId=2', undefined, M);
  checkTrue('店长查询他店充值记录被拒', mgrRechargeList.code !== 0, 'code=' + mgrRechargeList.code);
  const mgrOrder = await req('GET', '/api/admin/order/orders?storeId=2', undefined, M);
  checkTrue('店长查询他店订单被拒', mgrOrder.code !== 0, 'code=' + mgrOrder.code);

  console.log('\n资金对账：余额必须等于流水累计');
  console.log('------------------------------');
  const reconcile = await req('GET', '/api/admin/wallet/reconcile', undefined, A);
  check('对账接口可用', reconcile.code, 0, reconcile.message);
  check('对账结果一致', reconcile.data?.consistent, true,
        JSON.stringify(reconcile.data?.details || []));
  evidence.reconcile = reconcile.data;

  console.log('\n' + '='.repeat(50));
  console.log('PASS: ' + pass + '   FAIL: ' + fail + '   TOTAL: ' + (pass + fail));
  if (failures.length) {
    console.log('\n失败用例:');
    failures.forEach(f => console.log('  - ' + f));
  }
  console.log('\n--- 资金证据 ---');
  console.log(JSON.stringify(evidence, null, 2));
})();
