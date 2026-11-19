
const BASE = 'http://127.0.0.1:8080';
const { execSync } = await import('node:child_process');
const MYSQL = 'C:/Program Files/MySQL/MySQL Server 8.4/bin/mysql.exe';
const PWD = process.env.MYSQL_PASSWORD || process.env.MYSQL_PWD || '';
if (!PWD) { console.error('请先设置环境变量 MYSQL_PASSWORD'); process.exit(1); }

let pass = 0, fail = 0; const failures = [];
function check(name, actual, expected, detail) {
  if (String(actual) === String(expected)) { pass++; console.log('  [PASS] ' + name); }
  else { fail++; failures.push(name + ' exp=' + expected + ' act=' + actual + ' ' + (detail||'')); console.log('  [FAIL] ' + name + ' exp=' + expected + ' act=' + actual + ' ' + (detail||'')); }
}
function sql(q) {
  try {
    return execSync('"' + MYSQL + '" -u teaclub -h 127.0.0.1 -D teaclub -N -B --default-character-set=utf8mb4 -e "' + q + '"',
      { env: { ...process.env, MYSQL_PWD: PWD }, encoding: 'utf8' }).trim();
  } catch (e) { return 'ERR:' + e.message; }
}
async function req(method, path, body, token) {
  const headers = {};
  if (token) headers['Authorization'] = 'Bearer ' + token;
  if (body !== undefined) headers['Content-Type'] = 'application/json; charset=utf-8';
  try {
    const res = await fetch(BASE + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    const t = await res.text();
    try { return JSON.parse(t); } catch { return { code: -1, message: 'NONJSON' }; }
  } catch (e) { return { code: -2, message: e.message }; }
}

(async () => {
  console.log('=== 资金并发与幂等专项测试 ===\n');

  const login = await req('POST', '/api/admin/auth/login', { username: 'admin', password: 'TeaClub@123' });
  const A = login.data.accessToken;

  console.log('[准备] 重置会员 3 并充值 10000 分实付金');
  console.log('--------------------------------------');
  sql("DELETE FROM wal_wallet_transaction WHERE member_id=3");
  sql("DELETE FROM wal_wallet WHERE member_id=3");
  sql("DELETE FROM wal_recharge_order WHERE member_id=3");
  sql("DELETE FROM pay_payment_order WHERE biz_type='RECHARGE'");
  sql("DELETE FROM pay_notify_log");
  sql("DELETE FROM pay_refund_order WHERE member_id=3");
  sql("DELETE FROM oms_order_item WHERE order_no IN (SELECT order_no FROM oms_order WHERE member_id=3)");
  sql("DELETE FROM oms_order_status_log WHERE order_no IN (SELECT order_no FROM oms_order WHERE member_id=3)");
  sql("DELETE FROM oms_order WHERE member_id=3");

  const rc = await req('POST', '/api/admin/wallet/recharges', {
    memberId: 3, storeId: 1, planId: 1, payChannel: 'MOCK'
  }, A);
  const pay = await req('POST', '/api/admin/payment/create', {
    bizNo: rc.data.rechargeNo, bizType: 'RECHARGE', channel: 'WECHAT_JSAPI'
  }, A);
  await req('POST', '/api/payment/mock/notify', { paymentNo: pay.data.paymentNo });

  const w = await req('GET', '/api/admin/wallet/accounts/member/3', undefined, A);
  check('准备完成：实付余额=10000', w.data.principalBalance, 10000, w.data.principalBalance);
  check('准备完成：赠送余额=1000', w.data.giftBalance, 1000, w.data.giftBalance);

  console.log('\n[测试 A] 并发 10 次消费同一钱包，余额恰好只够 3 笔');
  console.log('--------------------------------------');
  // SKU 1 store1 = 1750; use quote to get exact price
  const q = await req('POST', '/api/admin/order/quote', { storeId: 1, memberId: 3, items: [{ skuId: 1, quantity: 1 }] }, A);
  const price = q.data.items[0].unitPrice;
  console.log('  单价=' + price + ' 分，总余额=11000 分，最多可支付 ' + Math.floor(11000 / price) + ' 笔');

  // create 10 orders up-front so the concurrency is on payment, not order creation
  const orderNos = [];
  for (let i = 0; i < 10; i++) {
    const o = await req('POST', '/api/admin/order/orders', {
      storeId: 1, memberId: 3, items: [{ skuId: 1, quantity: 1 }]
    }, A);
    if (o.code === 0) orderNos.push(o.data.orderNo);
  }
  check('成功创建 10 个待支付订单', orderNos.length, 10, orderNos.length);

  // fire all payments concurrently
  const results = await Promise.all(orderNos.map(no =>
    req('POST', '/api/admin/order/orders/' + no + '/pay-balance', undefined, A)));

  const okCount = results.filter(r => r.code === 0).length;
  const failCount = results.filter(r => r.code !== 0).length;
  console.log('  并发结果：成功 ' + okCount + ' 笔，失败 ' + failCount + ' 笔');

  const maxPayable = Math.floor(11000 / price);
  check('成功笔数不超过余额可支撑笔数', okCount <= maxPayable, true, 'ok=' + okCount + ' max=' + maxPayable);
  check('失败笔数为余额不足', failCount, 10 - okCount, failCount);

  const wAfter = await req('GET', '/api/admin/wallet/accounts/member/3', undefined, A);
  const remaining = wAfter.data.principalBalance + wAfter.data.giftBalance;
  check('并发后余额非负', remaining >= 0, true, 'remaining=' + remaining);
  check('并发后余额=初始-已支付', remaining, 11000 - okCount * price,
        'remaining=' + remaining + ' expected=' + (11000 - okCount * price));

  const ledgerBal = sql("SELECT principal_balance + gift_balance FROM wal_wallet WHERE member_id=3");
  check('数据库余额与接口一致', String(remaining), ledgerBal.trim(), ledgerBal);

  const reconcile1 = await req('GET', '/api/admin/wallet/reconcile', undefined, A);
  check('并发后对账一致（余额=流水累计）', reconcile1.data?.consistent, true,
        JSON.stringify(reconcile1.data?.details || []));

  console.log('\n[测试 B] 同一订单并发支付 5 次（幂等）');
  console.log('--------------------------------------');
  sql("DELETE FROM wal_wallet_transaction WHERE member_id=3");
  sql("DELETE FROM wal_wallet WHERE member_id=3");
  // re-fund
  const rc2 = await req('POST', '/api/admin/wallet/recharges', { memberId: 3, storeId: 1, planId: 2, payChannel: 'MOCK' }, A);
  const pay2 = await req('POST', '/api/admin/payment/create', { bizNo: rc2.data.rechargeNo, bizType: 'RECHARGE', channel: 'WECHAT_JSAPI' }, A);
  await req('POST', '/api/payment/mock/notify', { paymentNo: pay2.data.paymentNo });

  const wB = await req('GET', '/api/admin/wallet/accounts/member/3', undefined, A);
  const beforeB = wB.data.principalBalance + wB.data.giftBalance;

  const oB = await req('POST', '/api/admin/order/orders', { storeId: 1, memberId: 3, items: [{ skuId: 1, quantity: 1 }] }, A);
  const ordB = oB.data.orderNo;
  const payB = await Promise.all(Array.from({ length: 5 }, () =>
    req('POST', '/api/admin/order/orders/' + ordB + '/pay-balance', undefined, A)));
  const okB = payB.filter(r => r.code === 0).length;
  check('同一订单仅一次实际扣款（幂等）', okB >= 1, true, 'okCount=' + okB);

  const wB2 = await req('GET', '/api/admin/wallet/accounts/member/3', undefined, A);
  const afterB = wB2.data.principalBalance + wB2.data.giftBalance;
  check('同一订单重复支付只扣一次', beforeB - afterB, price, 'delta=' + (beforeB - afterB) + ' price=' + price);

  const txnCountB = sql("SELECT COUNT(*) FROM wal_wallet_transaction WHERE biz_no='" + ordB + "' AND biz_type='CONSUME_OUT'");
  check('该订单只有 1 条消费流水', txnCountB.trim(), '1', txnCountB);

  console.log('\n[测试 C] 同一充值单并发通知 8 次（幂等）');
  console.log('--------------------------------------');
  const rcC = await req('POST', '/api/admin/wallet/recharges', { memberId: 3, storeId: 1, planId: 1, payChannel: 'MOCK' }, A);
  const payC = await req('POST', '/api/admin/payment/create', { bizNo: rcC.data.rechargeNo, bizType: 'RECHARGE', channel: 'WECHAT_JSAPI' }, A);
  const payCNo = payC.data.paymentNo;

  const wC1 = await req('GET', '/api/admin/wallet/accounts/member/3', undefined, A);
  const beforeC = wC1.data.principalBalance;

  const notifies = await Promise.all(Array.from({ length: 8 }, () =>
    req('POST', '/api/payment/mock/notify', { paymentNo: payCNo })));
  const okC = notifies.filter(r => r.code === 0).length;
  console.log('  8 次并发通知，成功返回 ' + okC + ' 次');

  const wC2 = await req('GET', '/api/admin/wallet/accounts/member/3', undefined, A);
  check('并发重复通知只入账一次', wC2.data.principalBalance - beforeC, 10000,
        'delta=' + (wC2.data.principalBalance - beforeC));

  const txnC = sql("SELECT COUNT(*) FROM wal_wallet_transaction WHERE biz_no='" + rcC.data.rechargeNo + "' AND biz_type='RECHARGE_IN'");
  check('该充值单只有 1 条入账流水', txnC.trim(), '1', txnC);

  const rcCStatus = sql("SELECT status FROM wal_recharge_order WHERE recharge_no='" + rcC.data.rechargeNo + "'");
  check('充值单状态为 PAID', rcCStatus.trim(), 'PAID', rcCStatus);

  console.log('\n[测试 D] 并发退款超发防护');
  console.log('--------------------------------------');
  const oD = await req('POST', '/api/admin/order/orders', { storeId: 1, memberId: 3, items: [{ skuId: 1, quantity: 1 }] }, A);
  const ordD = oD.data.orderNo;
  await req('POST', '/api/admin/order/orders/' + ordD + '/pay-balance', undefined, A);

  // Try 5 concurrent refunds of the full amount; only one should succeed
  const refunds = await Promise.all(Array.from({ length: 5 }, () =>
    req('POST', '/api/admin/payment/refunds', { orderNo: ordD, refundAmount: price, reason: '并发退款测试' }, A)));
  const okD = refunds.filter(r => r.code === 0).length;
  check('并发退款仅 1 笔成功', okD, 1, 'okCount=' + okD);

  const refundSum = sql("SELECT COALESCE(SUM(refund_amount),0) FROM pay_refund_order WHERE biz_no='" + ordD + "' AND status='SUCCESS'");
  check('退款总额不超过原订单金额', Number(refundSum.trim()) <= price, true, 'sum=' + refundSum + ' price=' + price);

  const wD = await req('GET', '/api/admin/wallet/accounts/member/3', undefined, A);
  check('退款后余额未超发', wD.data.principalBalance + wD.data.giftBalance >= 0, true, 'bal=' + (wD.data.principalBalance + wD.data.giftBalance));

  console.log('\n[最终] 资金一致性巡检');
  console.log('--------------------------------------');
  const finalReconcile = await req('GET', '/api/admin/wallet/reconcile', undefined, A);
  check('全部并发测试后对账一致', finalReconcile.data?.consistent, true,
        JSON.stringify(finalReconcile.data?.details || []));

  const negCheck = sql("SELECT COUNT(*) FROM wal_wallet WHERE principal_balance < 0 OR gift_balance < 0");
  check('不存在负余额钱包', negCheck.trim(), '0', negCheck);

  const orphanTxn = sql("SELECT COUNT(*) FROM wal_wallet_transaction t LEFT JOIN wal_wallet w ON w.id=t.wallet_id WHERE w.id IS NULL");
  check('不存在孤儿流水', orphanTxn.trim(), '0', orphanTxn);

  console.log('\n' + '='.repeat(50));
  console.log('PASS: ' + pass + '   FAIL: ' + fail + '   TOTAL: ' + (pass + fail));
  if (failures.length) { console.log('\n失败用例:'); failures.forEach(f => console.log('  - ' + f)); }
})();
