// Second pass: use a valid ADMIN token to get past the gateway auth filter,
// so we can tell "route missing in service (404)" apart from "gateway 401".
const BASE = 'http://127.0.0.1:9000';
const paths = [
  ['POST', '/api/auth/wx-login', { code: 'probe' }],
  ['GET', '/api/auth/stores', null],
  ['GET', '/api/auth/stores/1', null],
  ['GET', '/api/auth/member/me', null],
  ['GET', '/api/member/profile', null],
  ['GET', '/api/member/levels', null],
  ['GET', '/api/wallet/account', null],
  ['GET', '/api/wallet/transactions?pageNo=1&pageSize=10', null],
  ['GET', '/api/wallet/plans?storeId=1', null],
  ['GET', '/api/wallet/recharges?pageNo=1&pageSize=10', null],
  ['POST', '/api/wallet/recharges', { planId: 1 }],
  ['GET', '/api/wallet/recharges/R0001', null],
  ['GET', '/api/product/stores/1/categories', null],
  ['GET', '/api/product/stores/1/spus?pageNo=1&pageSize=10', null],
  ['GET', '/api/product/stores/1/spus/1', null],
  ['GET', '/api/product/hot?storeId=1&limit=6', null],
  ['POST', '/api/order/quote', { storeId: 1, items: [{ spuId: 1, skuId: 1, quantity: 1 }] }],
  ['POST', '/api/order/orders', { storeId: 1, items: [{ spuId: 1, skuId: 1, quantity: 1 }] }],
  ['GET', '/api/order/orders?pageNo=1&pageSize=10', null],
  ['GET', '/api/order/orders/NO0001', null],
  ['POST', '/api/order/orders/NO0001/cancel', null],
  ['POST', '/api/order/orders/NO0001/pay-wallet', null],
  ['POST', '/api/payment/create', { bizType: 'RECHARGE', bizNo: 'PROBE1' }],
  ['GET', '/api/payment/orders/PAY0001', null],
];

(async () => {
  const lr = await fetch(BASE + '/api/admin/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'TeaClub@123' }),
  });
  const lj = await lr.json();
  const token = lj && lj.data && lj.data.accessToken;
  if (!token) { console.log('admin login failed: ' + JSON.stringify(lj).slice(0, 200)); process.exit(1); }
  console.log('admin token OK (len=' + token.length + ')\n');
  const rows = [];
  for (const [m, p, b] of paths) {
    let status = 'ERR', code = null, msg = '', raw = '';
    try {
      const res = await fetch(BASE + p, {
        method: m,
        headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token },
        body: b ? JSON.stringify(b) : undefined,
      });
      status = res.status;
      raw = await res.text();
      try { const j = JSON.parse(raw); code = j.code; msg = j.message; } catch {}
    } catch (e) { msg = e.message; }
    rows.push({ m, p, status, code, msg });
    const mark = (status === 200 && code === 0) ? 'OK  ' : (status === 404 ? '404 ' : 'FAIL');
    console.log(`[${mark}] ${m.padEnd(4)} ${p} -> http=${status} code=${code} ${(msg||'').slice(0,40)}`);
  }
  console.log('\n=== 200+code0: ' + rows.filter(r => r.status === 200 && r.code === 0).length + ' / ' + rows.length + ' ===');
  console.log('=== 404 (route absent): ' + rows.filter(r => r.status === 404).length + ' ===');
  console.log('=== other fail: ' + rows.filter(r => r.status !== 200 && r.status !== 404).length + ' ===');
})();
