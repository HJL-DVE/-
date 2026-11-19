
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
    return execSync('"' + MYSQL + '" -u teaclub -h 127.0.0.1 -D teaclub -N -B -e "' + q + '"',
      { env: { ...process.env, MYSQL_PWD: PWD }, encoding: 'utf8' }).trim();
  } catch (e) { return 'ERR'; }
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
  console.log('=== 金额篡改与客户端金额约束测试 ===\n');

  const login = await req('POST', '/api/admin/auth/login', { username: 'admin', password: 'TeaClub@123' });
  const A = login.data.accessToken;

  // reset member 3
  for (const s of [
    "DELETE FROM wal_wallet_transaction WHERE member_id=3",
    "DELETE FROM wal_wallet WHERE member_id=3",
    "DELETE FROM wal_recharge_order WHERE member_id=3",
    "DELETE FROM pay_payment_order WHERE biz_type='RECHARGE'",
    "DELETE FROM pay_notify_log",
    "DELETE FROM oms_order_item WHERE order_no IN (SELECT order_no FROM oms_order WHERE member_id=3)",
    "DELETE FROM oms_order_status_log WHERE order_no IN (SELECT order_no FROM oms_order WHERE member_id=3)",
    "DELETE FROM oms_order WHERE member_id=3"
  ]) { sql(s); }

  console.log('[1] 下单时尝试传入伪造单价/总价');
  console.log('----------------------------------------');
  // The DTO has no price fields; extra JSON fields are ignored by Jackson by default.
  const fakeOrder = await req('POST', '/api/admin/order/orders', {
    storeId: 1, memberId: null,
    items: [{ skuId: 1, quantity: 1, unitPrice: 1, price: 1, totalAmount: 1, actualAmount: 1 }],
    totalAmount: 1, payableAmount: 1, discountAmount: 99999
  }, A);
  check('下单成功（多余字段被忽略）', fakeOrder.code, 0, fakeOrder.message);
  const realTotal = fakeOrder.data?.totalAmount;
  check('服务端忽略伪造金额，按真实门店价计算', realTotal > 1, true,
        'realTotal=' + realTotal + ' (伪造值 1)');
  const unitPriceInDb = sql("SELECT store_price FROM prd_store_sku WHERE store_id=1 AND sku_id=1");
  check('订单金额 = 数据库门店价', String(realTotal), unitPriceInDb.trim(),
        'db=' + unitPriceInDb + ' order=' + realTotal);
  // cleanup this order
  const fakeNo = fakeOrder.data?.orderNo;
  sql("DELETE FROM oms_order_item WHERE order_no='" + fakeNo + "'");
  sql("DELETE FROM oms_order_status_log WHERE order_no='" + fakeNo + "'");
  sql("DELETE FROM oms_order WHERE order_no='" + fakeNo + "'");

  console.log('\n[2] 固定充值方案：尝试传入自定义金额');
  console.log('----------------------------------------');
  // plan 1 is fixed amount 10000; passing customAmount should be ignored
  const rcFixed = await req('POST', '/api/admin/wallet/recharges', {
    memberId: 3, storeId: 1, planId: 1, customAmount: 1, payChannel: 'MOCK'
  }, A);
  check('固定方案充值创建成功', rcFixed.code, 0, rcFixed.message);
  check('固定方案忽略 customAmount，使用方案金额', rcFixed.data?.rechargeAmount, 10000, rcFixed.data?.rechargeAmount);
  check('固定方案赠送额由方案决定', rcFixed.data?.giftAmount, 1000, rcFixed.data?.giftAmount);

  console.log('\n[3] 自定义充值方案：金额越界必须拒绝');
  console.log('----------------------------------------');
  const planCustom = sql("SELECT id, min_custom_amount, max_custom_amount FROM wal_recharge_plan WHERE plan_code='RCCUSTOM'");
  const [pcId, pcMin, pcMax] = planCustom.split('\t');
  console.log('  自定义方案 id=' + pcId + ' 区间=[' + pcMin + ',' + pcMax + ']');

  const belowMin = await req('POST', '/api/admin/wallet/recharges', {
    memberId: 3, storeId: 1, planId: Number(pcId), customAmount: 100, payChannel: 'MOCK'
  }, A);
  check('低于下限被拒绝', belowMin.code !== 0, true, 'code=' + belowMin.code + ' ' + belowMin.message);

  const aboveMax = await req('POST', '/api/admin/wallet/recharges', {
    memberId: 3, storeId: 1, planId: Number(pcId), customAmount: 99999999, payChannel: 'MOCK'
  }, A);
  check('高于上限被拒绝', aboveMax.code !== 0, true, 'code=' + aboveMax.code + ' ' + aboveMax.message);

  const inRange = await req('POST', '/api/admin/wallet/recharges', {
    memberId: 3, storeId: 1, planId: Number(pcId), customAmount: 5000, payChannel: 'MOCK'
  }, A);
  check('区间内被接受', inRange.code, 0, inRange.message);
  check('自定义方案赠送额为 0（无法用金额撬动赠送）', inRange.data?.giftAmount, 0, inRange.data?.giftAmount);
  check('自定义方案充值额=客户端输入（已校验）', inRange.data?.rechargeAmount, 5000, inRange.data?.rechargeAmount);

  console.log('\n[4] 支付时传入伪造金额');
  console.log('----------------------------------------');
  const payReq = await req('POST', '/api/admin/payment/create', {
    bizNo: inRange.data.rechargeNo, bizType: 'RECHARGE',
    channel: 'WECHAT_JSAPI', amount: 1, total: 1, paymentAmount: 1
  }, A);
  check('支付创建成功', payReq.code, 0, payReq.message);
  check('支付金额由服务端取业务单金额，非客户端', payReq.data?.amount, 5000, payReq.data?.amount);

  console.log('\n[5] 支付通知金额被篡改必须拒绝');
  console.log('----------------------------------------');
  const tamperNotify = await req('POST', '/api/payment/mock/notify', {
    paymentNo: payReq.data.paymentNo, amount: 1
  });
  check('通知金额与支付单不符 → 拒绝', tamperNotify.code !== 0, true,
        'code=' + tamperNotify.code + ' ' + tamperNotify.message);
  const payStatus = sql("SELECT status FROM pay_payment_order WHERE payment_no='" + payReq.data.paymentNo + "'");
  check('支付单未被标记成功', payStatus.trim() !== 'SUCCESS', true, payStatus);

  console.log('\n[6] 正常通知后入账金额正确');
  console.log('----------------------------------------');
  const okNotify = await req('POST', '/api/payment/mock/notify', { paymentNo: payReq.data.paymentNo });
  check('正常通知成功', okNotify.code, 0, okNotify.message);
  const w = await req('GET', '/api/admin/wallet/accounts/member/3', undefined, A);
  check('入账金额 = 支付单金额', w.data.principalBalance, 5000, w.data.principalBalance);

  console.log('\n[7] 退款金额超上限必须拒绝');
  console.log('----------------------------------------');
  const ord = await req('POST', '/api/admin/order/orders', {
    storeId: 1, memberId: 3, items: [{ skuId: 1, quantity: 1 }]
  }, A);
  const ordNo = ord.data.orderNo;
  const ordTotal = ord.data.payableAmount;
  await req('POST', '/api/admin/order/orders/' + ordNo + '/pay-balance', undefined, A);

  const overRefund = await req('POST', '/api/admin/payment/refunds', {
    orderNo: ordNo, refundAmount: ordTotal + 10000, reason: '超额退款测试'
  }, A);
  check('退款金额超过实付 → 拒绝', overRefund.code !== 0, true,
        'code=' + overRefund.code + ' ' + overRefund.message);

  const refundNegative = await req('POST', '/api/admin/payment/refunds', {
    orderNo: ordNo, refundAmount: -100, reason: '负数退款'
  }, A);
  check('负数退款 → 拒绝', refundNegative.code !== 0, true,
        'code=' + refundNegative.code + ' ' + refundNegative.message);

  const okRefund = await req('POST', '/api/admin/payment/refunds', {
    orderNo: ordNo, refundAmount: ordTotal, reason: '正常退款'
  }, A);
  check('合法退款成功', okRefund.code, 0, okRefund.message);
  check('退款总额 = 申请额', okRefund.data?.refundAmount, ordTotal, okRefund.data?.refundAmount);

  console.log('\n' + '='.repeat(50));
  console.log('PASS: ' + pass + '   FAIL: ' + fail + '   TOTAL: ' + (pass + fail));
  if (failures.length) { console.log('\n失败:'); failures.forEach(f => console.log('  - ' + f)); }
})();
