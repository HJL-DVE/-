
const BASE = 'http://127.0.0.1:8080';
let pass = 0, fail = 0; const fails = [];

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
function check(name, actual, expected, detail) {
  if (String(actual) === String(expected)) { pass++; console.log('  [PASS] ' + name); }
  else { fail++; fails.push(name + ' exp=' + expected + ' act=' + actual + ' ' + (detail||'')); console.log('  [FAIL] ' + name + ' exp=' + expected + ' act=' + actual + ' ' + (detail||'')); }
}

(async () => {
  console.log('=== 越权攻击面测试（多门店隔离） ===\n');

  const adminLogin = await req('POST', '/api/admin/auth/login', { username: 'admin', password: 'TeaClub@123' });
  const ADMIN = adminLogin.data.accessToken;
  const mgrLogin = await req('POST', '/api/admin/auth/login', { username: 'mgr_xihu', password: 'TeaClub@123' });
  const MGR = mgrLogin.data.accessToken;
  const cashLogin = await req('POST', '/api/admin/auth/login', { username: 'cashier01', password: 'TeaClub@123' });
  const CASH = cashLogin.data.accessToken;
  const mgrBjLogin = await req('POST', '/api/admin/auth/login', { username: 'mgr_binjiang', password: 'TeaClub@123' });
  const MGR_BJ = mgrBjLogin.data.accessToken;

  console.log('[A] 门店级账号创建/授权越权');
  // 店长(仅店1) 试图创建账号并授权到 店2 -> 必须拒绝
  let r = await req('POST', '/api/admin/system/users', {
    username: 'attack01', password: 'Attack@123', realName: '攻击者',
    userType: 'STORE', roleIds: [3], storeIds: [2], defaultStoreId: 2
  }, MGR);
  check('店长创建账号并授权他店 -> 拒绝', r.code !== 0, true, 'code=' + r.code + ' ' + r.message);

  // 店长(仅店1) 试图创建总部账号 -> 必须拒绝
  r = await req('POST', '/api/admin/system/users', {
    username: 'attack02', password: 'Attack@123', realName: '攻击者2', userType: 'HQ'
  }, MGR);
  check('店长创建总部账号 -> 拒绝', r.code !== 0, true, 'code=' + r.code);

  // 店长(仅店1) 试图给已有账号授权 店2 -> 必须拒绝
  r = await req('PUT', '/api/admin/system/users/5/stores', { storeIds: [2], defaultStoreId: 2 }, MGR);
  check('店长给他人授权他店 -> 拒绝', r.code !== 0, true, 'code=' + r.code + ' ' + r.message);

  console.log('\n[B] 跨店查询参数注入');
  r = await req('GET', '/api/admin/member/members?registerStoreId=2', undefined, MGR);
  check('店长用 registerStoreId=2 越权查询 -> 拒绝', r.code !== 0, true, 'code=' + r.code);

  r = await req('GET', '/api/admin/product/store-products?storeId=2', undefined, MGR);
  check('店长查他店商品 -> 10202', r.code, 10202, r.message);

  r = await req('GET', '/api/admin/product/store-products?storeId=3', undefined, MGR);
  check('店长查第三家店商品 -> 10202', r.code, 10202, r.message);

  console.log('\n[C] 跨店写操作');
  r = await req('PUT', '/api/admin/product/store-products/2/sku/1/price', { storePrice: 2000, isOnSale: 1 }, MGR);
  check('店长改他店价格 -> 10202', r.code, 10202, r.message);

  r = await req('PATCH', '/api/admin/product/store-products/2/spu/1/shelf', { isOnSale: 0 }, MGR);
  check('店长下架他店商品 -> 10202', r.code, 10202, r.message);

  r = await req('DELETE', '/api/admin/product/store-products/2/spu/1', undefined, MGR);
  check('店长移除他店商品 -> 10202', r.code, 10202, r.message);

  console.log('\n[D] 两店长互不可见');
  r = await req('GET', '/api/admin/store/stores?pageSize=50', undefined, MGR_BJ);
  check('滨江店长仅见 1 家', r.data.total, 1, r.data.total);
  check('滨江店长见到的是店2', r.data.records[0].id, 2, r.data.records[0].id);

  r = await req('GET', '/api/admin/store/stores/1', undefined, MGR_BJ);
  check('滨江店长查店1 -> 10202', r.code, 10202, r.message);

  console.log('\n[E] 收银员权限边界');
  r = await req('POST', '/api/admin/product/spus', {
    categoryId: 1, spuCode: 'HACK01', spuName: 'x', basePrice: 1000, minPrice: 800,
    skus: [{ skuCode: 'HACKSKU1', skuName: 'y', price: 1000 }]
  }, CASH);
  check('收银员创建商品 -> 10201', r.code, 10201, r.message);

  r = await req('PUT', '/api/admin/product/store-products/1/sku/1/price', { storePrice: 100, isOnSale: 1 }, CASH);
  check('收银员改价 -> 10201', r.code, 10201, r.message);

  r = await req('DELETE', '/api/admin/member/members/1', undefined, CASH);
  check('收银员删会员 -> 10201', r.code, 10201, r.message);

  console.log('\n[F] 无令牌 / 伪造令牌');
  r = await req('GET', '/api/admin/store/stores');
  check('无令牌 -> 10101', r.code, 10101, r.message);

  r = await req('GET', '/api/admin/store/stores', undefined, 'fake.token.value');
  check('伪造令牌 -> 10103/10101', [10103,10101].includes(r.code), true, 'code=' + r.code);

  console.log('\n[G] 总部正常能力未被误伤');
  r = await req('GET', '/api/admin/store/stores?pageSize=50', undefined, ADMIN);
  check('总部仍可见 3 家', r.data.total, 3, r.data.total);

  r = await req('GET', '/api/admin/product/store-products?storeId=3', undefined, ADMIN);
  check('总部可查第三家店商品', r.code, 0, r.message);

  console.log('\n' + '='.repeat(46));
  console.log('PASS: ' + pass + '  FAIL: ' + fail + '  TOTAL: ' + (pass + fail));
  if (fails.length) { console.log('\nFAILED:'); fails.forEach(f => console.log('  - ' + f)); }
})();
