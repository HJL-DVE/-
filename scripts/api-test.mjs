
const BASE = 'http://127.0.0.1:8080';
let pass = 0, fail = 0;
const failures = [];

async function req(method, path, body, token) {
  const headers = {};
  if (token) headers['Authorization'] = 'Bearer ' + token;
  if (body !== undefined) headers['Content-Type'] = 'application/json; charset=utf-8';
  try {
    const res = await fetch(BASE + path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const text = await res.text();
    try { return JSON.parse(text); } catch { return { code: -1, message: 'NON_JSON: ' + text.slice(0, 200) }; }
  } catch (e) {
    return { code: -2, message: 'FETCH_ERR: ' + e.message };
  }
}

function check(name, actual, expected, detail) {
  if (String(actual) === String(expected)) {
    pass++;
    console.log('  [PASS] ' + name);
  } else {
    fail++;
    failures.push(name + ' | expected=' + expected + ' actual=' + actual + ' | ' + (detail || ''));
    console.log('  [FAIL] ' + name + '  expected=' + expected + ' actual=' + actual + '  ' + (detail || ''));
  }
}

const H = (t) => '\n=== ' + t + ' ===';

(async () => {
  // 预清理：硬删除历史测试数据，保证脚本可重复执行
  const { execSync } = await import('node:child_process');
  const preCleanupSql = "DELETE FROM sys_store WHERE store_code IN ('S004','S900','S999'); " +
                        "DELETE FROM sys_user WHERE username IN ('testmgr01','badphone01','redactcheck'); " +
                        "DELETE FROM sys_role WHERE role_code='TEST_ROLE'; " +
                        "DELETE FROM prd_spu WHERE spu_code IN ('SPU999','SPU998'); " +
                        "DELETE FROM prd_sku WHERE sku_code LIKE 'SKU99%'; " +
                        "DELETE FROM prd_category WHERE category_name LIKE '测试分类%'; " +
                        "DELETE FROM mem_member_level WHERE level_code='DIAMOND';";
  try {
    const mysql = 'C:/Program Files/MySQL/MySQL Server 8.4/bin/mysql.exe';
    for (const stmt of preCleanupSql.split(';').map(s => s.trim()).filter(Boolean)) {
      execSync('"' + mysql + '" -u teaclub -h 127.0.0.1 -D teaclub --default-character-set=utf8mb4 -e "' + stmt + '"',
        { env: { ...process.env, MYSQL_PWD: process.env.MYSQL_PASSWORD || process.env.MYSQL_PWD || '' }, stdio: 'pipe' });
    }
    console.log('  [SETUP] 历史测试数据已清理');
  } catch (e) {
    console.log('  [SETUP] 清理失败(可忽略): ' + e.message);
  }

  console.log(H('1. 认证与安全'));

  let r = await req('GET', '/api/admin/system/users');
  check('未登录访问受保护接口 -> 10101', r.code, 10101, r.message);

  r = await req('POST', '/api/admin/auth/login', { username: 'admin', password: 'WrongPass123' });
  check('错误密码 -> 10104', r.code, 10104, r.message);

  r = await req('POST', '/api/admin/auth/login', { username: 'admin', password: 'TeaClub@123' });
  check('admin 登录成功', r.code, 0, r.message);
  const ADMIN = r.data.accessToken;

  r = await req('POST', '/api/admin/auth/login', { username: 'mgr_xihu', password: 'TeaClub@123' });
  check('店长登录成功', r.code, 0, r.message);
  const MGR = r.data.accessToken;
  const MGR_STORES = r.data.userInfo.stores;

  r = await req('POST', '/api/admin/auth/login', { username: 'cashier01', password: 'TeaClub@123' });
  check('收银员登录成功', r.code, 0, r.message);
  const CASH = r.data.accessToken;

  r = await req('GET', '/api/admin/auth/me', undefined, MGR);
  check('店长 dataScope=STORE', r.data.dataScope, 'STORE', r.data.dataScope);
  check('店长仅 1 家授权门店', r.data.stores.length, 1, JSON.stringify(r.data.stores));

  r = await req('GET', '/api/admin/auth/menus', undefined, MGR);
  check('店长菜单树可获取', r.code, 0, r.message);

  console.log(H('2. 多门店数据隔离'));
  r = await req('GET', '/api/admin/store/stores?pageSize=50', undefined, ADMIN);
  check('总部可见 3 家门店', r.data.total, 3, r.data.total);

  r = await req('GET', '/api/admin/store/stores?pageSize=50', undefined, MGR);
  check('店长仅可见 1 家门店', r.data.total, 1, r.data.total);
  check('店长可见门店 id=1', r.data.records[0].id, 1, r.data.records[0].id);

  r = await req('GET', '/api/admin/store/stores/2', undefined, MGR);
  check('店长越权查他店详情 -> 10202', r.code, 10202, r.message);

  r = await req('GET', '/api/admin/store/stores/1', undefined, MGR);
  check('店长查本店详情成功', r.code, 0, r.message);

  r = await req('POST', '/api/admin/store/stores', { storeCode: 'S999', storeName: '越权店', address: 'X' }, MGR);
  check('店长新增门店 -> 10201', r.code, 10201, r.message);

  r = await req('DELETE', '/api/admin/store/stores/1', undefined, MGR);
  check('店长删除门店 -> 10201', r.code, 10201, r.message);

  console.log(H('3. 按钮级权限'));
  r = await req('GET', '/api/admin/system/users', undefined, CASH);
  check('收银员访问员工管理 -> 10201', r.code, 10201, r.message);

  r = await req('GET', '/api/admin/member/members', undefined, CASH);
  check('收银员可访问会员列表', r.code, 0, r.message);

  r = await req('GET', '/api/admin/store/brand', undefined, MGR);
  check('店长访问品牌配置 -> 10201', r.code, 10201, r.message);

  r = await req('GET', '/api/admin/system/roles', undefined, MGR);
  check('店长访问角色管理 -> 10201', r.code, 10201, r.message);

  console.log(H('4. 品牌管理'));
  r = await req('GET', '/api/admin/store/brand', undefined, ADMIN);
  check('获取品牌信息', r.code, 0, r.message);
  check('扣款顺序 GIFT_FIRST', r.data.walletDeductOrder, 'GIFT_FIRST', r.data.walletDeductOrder);

  r = await req('PUT', '/api/admin/store/brand', {
    brandName: 'TeaClub 茶研社', contactPhone: '400-800-1234',
    walletDeductOrder: 'GIFT_FIRST'
  }, ADMIN);
  check('更新品牌信息', r.code, 0, r.message);

  r = await req('PUT', '/api/admin/store/brand', {
    brandName: '', walletDeductOrder: 'INVALID'
  }, ADMIN);
  check('品牌参数校验 -> 10001', r.code, 10001, r.message);

  console.log(H('5. 门店 CRUD'));
  r = await req('POST', '/api/admin/store/stores', {
    storeCode: 'S004', storeName: 'TeaClub·测试新店', address: '杭州市测试路 1 号', city: '杭州市', status: 'OPEN'
  }, ADMIN);
  check('新增门店', r.code, 0, r.message);
  const NEW_STORE = r.data;

  r = await req('POST', '/api/admin/store/stores', {
    storeCode: 'S004', storeName: '重复', address: 'X'
  }, ADMIN);
  check('门店编码重复 -> 30002', r.code, 30002, r.message);

  r = await req('PUT', '/api/admin/store/stores/' + NEW_STORE, {
    storeCode: 'S004', storeName: 'TeaClub·测试新店改', address: '杭州市测试路 2 号', status: 'OPEN'
  }, ADMIN);
  check('编辑门店', r.code, 0, r.message);

  r = await req('GET', '/api/admin/store/stores/' + NEW_STORE, undefined, ADMIN);
  check('门店名称已更新', r.data.storeName, 'TeaClub·测试新店改', r.data.storeName);

  r = await req('PATCH', '/api/admin/store/stores/' + NEW_STORE + '/status', { status: 'SUSPENDED' }, ADMIN);
  check('变更门店状态', r.code, 0, r.message);

  console.log(H('6. 用户管理'));
  r = await req('GET', '/api/admin/system/users?pageSize=50', undefined, ADMIN);
  check('员工分页查询', r.code, 0, r.message);
  check('员工总数 5', r.data.total, 5, r.data.total);

  r = await req('POST', '/api/admin/system/users', {
    username: 'testmgr01', password: 'Test@12345', realName: '测试店长',
    phone: '13900001111', userType: 'STORE', roleIds: [3], storeIds: [1], defaultStoreId: 1
  }, ADMIN);
  check('新增员工', r.code, 0, r.message);
  const NEW_USER = r.data;

  r = await req('POST', '/api/admin/system/users', {
    username: 'testmgr01', password: 'Test@12345', realName: '重复', userType: 'STORE'
  }, ADMIN);
  check('账号重复 -> 20001', r.code, 20001, r.message);

  r = await req('POST', '/api/admin/system/users', {
    username: 'badphone01', password: 'Test@12345', realName: '手机号错', phone: '123', userType: 'STORE'
  }, ADMIN);
  check('手机号校验 -> 10001', r.code, 10001, r.message);

  r = await req('PUT', '/api/admin/system/users/' + NEW_USER, {
    realName: '测试店长改', phone: '13900002222', roleIds: [3], storeIds: [1], defaultStoreId: 1
  }, ADMIN);
  check('编辑员工', r.code, 0, r.message);

  r = await req('POST', '/api/admin/system/users/' + NEW_USER + '/reset-password', { newPassword: 'NewPass@123' }, ADMIN);
  check('重置密码', r.code, 0, r.message);

  r = await req('POST', '/api/admin/auth/login', { username: 'testmgr01', password: 'NewPass@123' });
  check('新密码可登录', r.code, 0, r.message);

  r = await req('PUT', '/api/admin/system/users/' + NEW_USER + '/stores', { storeIds: [1, 2], defaultStoreId: 2 }, ADMIN);
  check('设置门店授权', r.code, 0, r.message);

  r = await req('GET', '/api/admin/system/users/' + NEW_USER + '/stores', undefined, ADMIN);
  check('授权后门店数 2', r.data.length, 2, JSON.stringify(r.data));

  r = await req('DELETE', '/api/admin/system/users/1', undefined, ADMIN);
  check('不能删除自己 -> 20007', r.code, 20007, r.message);

  r = await req('PATCH', '/api/admin/system/users/' + NEW_USER + '/status', { status: 'DISABLED' }, ADMIN);
  check('禁用员工', r.code, 0, r.message);

  r = await req('DELETE', '/api/admin/system/users/' + NEW_USER, undefined, ADMIN);
  check('删除员工', r.code, 0, r.message);

  console.log(H('7. 角色与权限'));
  r = await req('GET', '/api/admin/system/roles?pageSize=50', undefined, ADMIN);
  check('角色分页查询', r.code, 0, r.message);
  check('内置角色 4 个', r.data.total, 4, r.data.total);

  r = await req('GET', '/api/admin/system/permissions/tree', undefined, ADMIN);
  check('权限树查询', r.code, 0, r.message);

  r = await req('GET', '/api/admin/system/permissions/mine', undefined, CASH);
  check('收银员权限树', r.code, 0, r.message);

  r = await req('POST', '/api/admin/system/roles', {
    roleCode: 'TEST_ROLE', roleName: '测试角色', dataScope: 'STORE', sort: 99, permissionIds: [1, 10, 11]
  }, ADMIN);
  check('新增角色', r.code, 0, r.message);
  const NEW_ROLE = r.data;

  r = await req('POST', '/api/admin/system/roles', { roleCode: 'TEST_ROLE', roleName: '重复', dataScope: 'STORE' }, ADMIN);
  check('角色编码重复 -> 20003', r.code, 20003, r.message);

  r = await req('PUT', '/api/admin/system/roles/' + NEW_ROLE + '/permissions', [1, 10, 11, 30, 31], ADMIN);
  check('角色授权', r.code, 0, r.message);

  r = await req('GET', '/api/admin/system/roles/' + NEW_ROLE, undefined, ADMIN);
  check('角色权限数 5', r.data.permissionCount, 5, r.data.permissionCount);

  r = await req('PUT', '/api/admin/system/roles/' + NEW_ROLE, {
    roleCode: 'TEST_ROLE', roleName: '测试角色改', dataScope: 'STORE', sort: 99, permissionIds: [1]
  }, ADMIN);
  check('编辑角色', r.code, 0, r.message);

  r = await req('DELETE', '/api/admin/system/roles/1', undefined, ADMIN);
  check('内置角色不可删 -> 20006', r.code, 20006, r.message);

  r = await req('DELETE', '/api/admin/system/roles/' + NEW_ROLE, undefined, ADMIN);
  check('删除角色', r.code, 0, r.message);

  console.log(H('8. 会员管理'));
  r = await req('GET', '/api/admin/member/members?pageSize=50', undefined, ADMIN);
  check('会员分页查询', r.code, 0, r.message);
  check('会员总数 3', r.data.total, 3, r.data.total);
  const masked = r.data.records[0].phone;
  check('列表手机号已脱敏', masked.includes('****'), true, masked);

  r = await req('GET', '/api/admin/member/members/by-phone?phone=13800000001', undefined, ADMIN);
  check('按手机号查询会员', r.code, 0, r.message);
  check('详情手机号明文', r.data.phone, '13800000001', r.data.phone);
  const MEMBER = r.data.id;

  r = await req('GET', '/api/admin/member/members/' + MEMBER, undefined, ADMIN);
  check('会员详情', r.code, 0, r.message);
  check('会员带等级名称', r.data.levelName != null, true, r.data.levelName);

  r = await req('PUT', '/api/admin/member/members/' + MEMBER, { nickname: '张小明改', gender: 'MALE', levelId: 2 }, ADMIN);
  check('编辑会员资料', r.code, 0, r.message);

  r = await req('PATCH', '/api/admin/member/members/' + MEMBER + '/status', { status: 'FROZEN', reason: '测试' }, ADMIN);
  check('冻结会员', r.code, 0, r.message);

  r = await req('PATCH', '/api/admin/member/members/' + MEMBER + '/status', { status: 'NORMAL' }, ADMIN);
  check('解冻会员', r.code, 0, r.message);

  r = await req('GET', '/api/admin/member/members?pageSize=50', undefined, MGR);
  check('店长会员列表受门店过滤', r.code, 0, r.message);

  console.log(H('9. 会员等级'));
  r = await req('GET', '/api/admin/member/levels', undefined, ADMIN);
  check('等级列表', r.code, 0, r.message);
  check('等级数 4', r.data.length, 4, r.data.length);

  r = await req('POST', '/api/admin/member/levels', {
    levelCode: 'DIAMOND', levelName: '钻石会员', levelNo: 5, upgradeType: 'CONSUME_AMOUNT',
    upgradeThreshold: 1000000, discountRate: 8800, pointRate: 250, status: 'ENABLED'
  }, ADMIN);
  check('新增等级', r.code, 0, r.message);
  const NEW_LEVEL = r.data;

  r = await req('POST', '/api/admin/member/levels', {
    levelCode: 'BADCODE', levelName: '非法', levelNo: 6, upgradeType: 'CONSUME_AMOUNT',
    upgradeThreshold: 0, discountRate: 99999, pointRate: 100
  }, ADMIN);
  check('折扣率越界 -> 10001', r.code, 10001, r.message);

  r = await req('PUT', '/api/admin/member/levels/' + NEW_LEVEL, {
    levelCode: 'DIAMOND', levelName: '钻石会员改', levelNo: 5, upgradeType: 'CONSUME_AMOUNT',
    upgradeThreshold: 1200000, discountRate: 8700, pointRate: 260, status: 'ENABLED'
  }, ADMIN);
  check('编辑等级', r.code, 0, r.message);

  r = await req('DELETE', '/api/admin/member/levels/2', undefined, ADMIN);
  check('在用等级不可删 -> 40005', r.code, 40005, r.message);

  r = await req('DELETE', '/api/admin/member/levels/' + NEW_LEVEL, undefined, ADMIN);
  check('删除等级', r.code, 0, r.message);

  console.log(H('10. 商品分类'));
  r = await req('GET', '/api/admin/product/categories', undefined, ADMIN);
  check('分类树', r.code, 0, r.message);
  check('顶级分类 4 个', r.data.length, 4, r.data.length);

  r = await req('POST', '/api/admin/product/categories', { parentId: 0, categoryName: '测试分类', sort: 99, status: 'ENABLED' }, ADMIN);
  check('新增分类', r.code, 0, r.message);
  const NEW_CAT = r.data;

  r = await req('PUT', '/api/admin/product/categories/' + NEW_CAT, { parentId: 0, categoryName: '测试分类改', sort: 98, status: 'ENABLED' }, ADMIN);
  check('编辑分类', r.code, 0, r.message);

  r = await req('DELETE', '/api/admin/product/categories/' + NEW_CAT, undefined, ADMIN);
  check('删除分类', r.code, 0, r.message);

  r = await req('DELETE', '/api/admin/product/categories/1', undefined, ADMIN);
  check('有商品的分类不可删 -> 50003', r.code, 50003, r.message);

  console.log(H('11. 商品 SPU/SKU'));
  r = await req('GET', '/api/admin/product/spus?pageSize=50', undefined, ADMIN);
  check('商品分页', r.code, 0, r.message);
  check('商品总数 6', r.data.total, 6, r.data.total);

  r = await req('GET', '/api/admin/product/spus/1', undefined, ADMIN);
  check('商品详情含 SKU', r.code, 0, r.message);
  check('SPU1 有 2 个 SKU', r.data.skus.length, 2, r.data.skus.length);

  r = await req('POST', '/api/admin/product/spus', {
    categoryId: 1, spuCode: 'SPU999', spuName: '测试新品', subtitle: '自动化测试',
    basePrice: 1500, minPrice: 1200, unit: '杯', status: 'ON_SALE', sort: 99,
    skus: [
      { skuCode: 'SKU99901', skuName: '测试新品/中杯', price: 1500, costPrice: 600, sort: 1, status: 'ENABLED' },
      { skuCode: 'SKU99902', skuName: '测试新品/大杯', price: 1900, costPrice: 800, sort: 2, status: 'ENABLED' }
    ]
  }, ADMIN);
  check('新增商品(2 SKU)', r.code, 0, r.message);
  const NEW_SPU = r.data;

  r = await req('GET', '/api/admin/product/spus/' + NEW_SPU, undefined, ADMIN);
  check('新商品 SKU 数 2', r.data.skus.length, 2, r.data.skus.length);

  r = await req('POST', '/api/admin/product/spus', {
    categoryId: 1, spuCode: 'SPU998', spuName: '保护价错', basePrice: 1000, minPrice: 2000,
    skus: [{ skuCode: 'SKU99801', skuName: 'x', price: 1000 }]
  }, ADMIN);
  check('保护价高于售价 -> 10002', r.code, 10002, r.message);

  r = await req('POST', '/api/admin/product/spus', {
    categoryId: 1, spuCode: 'SPU999', spuName: '重复', basePrice: 1000, minPrice: 800,
    skus: [{ skuCode: 'SKU99701', skuName: 'x', price: 1000 }]
  }, ADMIN);
  check('商品编码重复 -> 50005', r.code, 50005, r.message);

  r = await req('PUT', '/api/admin/product/spus/' + NEW_SPU, {
    categoryId: 1, spuCode: 'SPU999', spuName: '测试新品改', basePrice: 1600, minPrice: 1200,
    unit: '杯', status: 'ON_SALE', sort: 99,
    skus: [
      { skuCode: 'SKU99901', skuName: '测试新品/中杯', price: 1600, costPrice: 600, sort: 1, status: 'ENABLED' },
      { skuCode: 'SKU99903', skuName: '测试新品/超大杯', price: 2100, costPrice: 900, sort: 3, status: 'ENABLED' }
    ]
  }, ADMIN);
  check('编辑商品(替换SKU)', r.code, 0, r.message);

  r = await req('GET', '/api/admin/product/spus/' + NEW_SPU, undefined, ADMIN);
  check('编辑后 SKU 数 2', r.data.skus.length, 2, r.data.skus.length);

  r = await req('PATCH', '/api/admin/product/spus/' + NEW_SPU + '/status', { status: 'OFF_SALE' }, ADMIN);
  check('下架商品', r.code, 0, r.message);

  console.log(H('12. 门店商品（差异化定价与隔离）'));
  r = await req('GET', '/api/admin/product/store-products?storeId=1&pageSize=50', undefined, ADMIN);
  check('西湖店商品查询', r.code, 0, r.message);
  check('西湖店 6 个商品', r.data.total, 6, r.data.total);

  r = await req('GET', '/api/admin/product/store-products?storeId=2&pageSize=50', undefined, ADMIN);
  check('滨江店 3 个商品', r.data.total, 3, r.data.total);

  r = await req('GET', '/api/admin/product/store-products?storeId=2&pageSize=50', undefined, MGR);
  check('店长访问他店商品 -> 10202', r.code, 10202, r.message);

  r = await req('GET', '/api/admin/product/store-products?storeId=1&pageSize=50', undefined, MGR);
  check('店长访问本店商品成功', r.code, 0, r.message);

  r = await req('PUT', '/api/admin/product/store-products/1/sku/1/price', { storePrice: 1750, isOnSale: 1 }, ADMIN);
  check('设置差异化定价', r.code, 0, r.message);

  r = await req('PUT', '/api/admin/product/store-products/1/sku/1/price', { storePrice: 1000, isOnSale: 1 }, ADMIN);
  check('低于保护价 -> 50008', r.code, 50008, r.message);

  r = await req('GET', '/api/admin/product/store-products/1/spu/1', undefined, ADMIN);
  const sku1 = r.data.skus.find(s => s.skuId === 1);
  check('SKU1 门店价已更新 1750', sku1.storePrice, 1750, sku1.storePrice);

  r = await req('PATCH', '/api/admin/product/store-products/1/spu/' + NEW_SPU + '/shelf', { isOnSale: 1, sort: 50 }, ADMIN);
  check('新增门店商品上架', r.code, 0, r.message);

  r = await req('DELETE', '/api/admin/product/store-products/1/spu/' + NEW_SPU, undefined, ADMIN);
  check('移除门店商品', r.code, 0, r.message);

  console.log(H('13. 清理'));
  r = await req('DELETE', '/api/admin/product/spus/' + NEW_SPU, undefined, ADMIN);
  check('删除测试商品', r.code, 0, r.message);

  r = await req('DELETE', '/api/admin/store/stores/' + NEW_STORE, undefined, ADMIN);
  check('删除测试门店', r.code, 0, r.message);

  console.log('\n==============================================');
  console.log('PASS: ' + pass);
  console.log('FAIL: ' + fail);
  console.log('TOTAL: ' + (pass + fail));
  if (failures.length) {
    console.log('\nFAILED CASES:');
    failures.forEach(f => console.log('  - ' + f));
  }
})();