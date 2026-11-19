
const BASE = 'http://127.0.0.1:8080';
const { execSync } = await import('node:child_process');
const MYSQL = 'C:/Program Files/MySQL/MySQL Server 8.4/bin/mysql.exe';
const PWD = process.env.MYSQL_PASSWORD || process.env.MYSQL_PWD || '';
if (!PWD) { console.error('请先设置 MYSQL_PASSWORD'); process.exit(1); }

let pass = 0, fail = 0; const failures = [];
function check(name, actual, expected, detail) {
  if (String(actual) === String(expected)) { pass++; console.log('  [PASS] ' + name); }
  else { fail++; failures.push(name + ' | exp=' + expected + ' act=' + actual + ' | ' + (detail||'')); console.log('  [FAIL] ' + name + ' exp=' + expected + ' act=' + actual + ' ' + (detail||'')); }
}
function sql(q) {
  try { return execSync('"' + MYSQL + '" -u teaclub -h 127.0.0.1 -D teaclub -N -B --default-character-set=utf8mb4 -e "' + q + '"',
    { env: { ...process.env, MYSQL_PWD: PWD }, encoding: 'utf8' }).trim(); } catch (e) { return 'ERR'; }
}
async function req(m, p, b, t) {
  const h = {}; if (t) h['Authorization'] = 'Bearer ' + t;
  if (b !== undefined) h['Content-Type'] = 'application/json; charset=utf-8';
  const res = await fetch(BASE + p, { method: m, headers: h, body: b === undefined ? undefined : JSON.stringify(b) });
  const x = await res.text();
  try { return JSON.parse(x); } catch { return { code: -1, message: x.slice(0,150) }; }
}

(async () => {
  console.log('=== 会员可见口径（B 方案）专项测试 ===\n');
  const A  = (await req('POST','/api/admin/auth/login',{username:'admin',password:'TeaClub@123'})).data.accessToken;
  const M  = (await req('POST','/api/admin/auth/login',{username:'mgr_xihu',password:'TeaClub@123'})).data.accessToken;
  const MB = (await req('POST','/api/admin/auth/login',{username:'mgr_binjiang',password:'TeaClub@123'})).data.accessToken;
  const C  = (await req('POST','/api/admin/auth/login',{username:'cashier01',password:'TeaClub@123'})).data.accessToken;

  // 会员2 注册滨江(2)；确保她在西湖(1)有一笔订单
  const mem2Store = sql("SELECT register_store_id FROM mem_member WHERE id=2");
  console.log('  会员2 注册门店 = ' + mem2Store + ' (滨江)');
  const orderCount = sql("SELECT COUNT(*) FROM oms_order WHERE member_id=2 AND store_id=1 AND deleted=0");
  if (Number(orderCount) === 0) {
    const o = await req('POST','/api/admin/order/orders',{storeId:1,memberId:2,orderSource:'POS',items:[{skuId:1,quantity:1}]},A);
    await req('POST','/api/admin/order/orders/' + o.data.orderNo + '/pay-balance',undefined,A);
    console.log('  已在西湖店为会员2创建消费订单');
  } else {
    console.log('  会员2 已有 ' + orderCount + ' 笔西湖店订单');
  }

  console.log('\n[A] 跨店消费过的会员应可见');
  const list = await req('GET','/api/admin/member/members?pageSize=50',undefined,M);
  check('西湖店长可见李小雨(注册滨江+西湖消费过)', list.data.records.some(r => r.id === 2), true, 'ids=' + list.data.records.map(r=>r.id));
  const phone = await req('GET','/api/admin/member/members/by-phone?phone=13800000002',undefined,M);
  check('按手机号可查(跨店会员)', phone.code, 0, phone.message);
  const det = await req('GET','/api/admin/member/members/2',undefined,M);
  check('会员详情可查', det.code, 0, det.message);
  const wal = await req('GET','/api/admin/wallet/accounts/member/2',undefined,M);
  check('钱包可查(与会员可见口径一致)', wal.code, 0, wal.message);
  const txn = await req('GET','/api/admin/wallet/transactions/member/2',undefined,M);
  check('资金流水可查', txn.code, 0, txn.message);

  console.log('\n[B] 未跨店消费的会员仍不可见（隔离仍生效）');
  // 会员3(王大力) 注册西湖(1)，滨江店长未服务过他
  const listB = await req('GET','/api/admin/member/members?pageSize=50',undefined,MB);
  check('滨江店长不可见王大力(注册西湖且未在滨江消费)', listB.data.records.some(r=>r.id===3), false, 'ids=' + listB.data.records.map(r=>r.id));
  const detB = await req('GET','/api/admin/member/members/3',undefined,MB);
  check('滨江店长查王大力详情被拒', detB.code !== 0, true, 'code=' + detB.code);

  console.log('\n[C] 越权参数必须显式拒绝（不得静默忽略）');
  const cross = await req('GET','/api/admin/member/members?registerStoreId=2',undefined,M);
  check('西湖店长用 registerStoreId=2 越权查询被拒', cross.code !== 0, true, 'code=' + cross.code + ' ' + cross.message);
  const own = await req('GET','/api/admin/member/members?registerStoreId=1',undefined,M);
  check('西湖店长用 registerStoreId=1 正常查询', own.code, 0, own.message);

  console.log('\n[D] 总部与收银员');
  const listA = await req('GET','/api/admin/member/members?pageSize=50',undefined,A);
  check('总部可见全部 3 个会员', listA.data.total, 3, listA.data.total);
  const listC = await req('GET','/api/admin/member/members?pageSize=50',undefined,C);
  check('收银员(西湖)可见跨店会员', listC.data.records.some(r=>r.id===2), true, 'ids=' + listC.data.records.map(r=>r.id));

  console.log('\n[E] 写操作权限不受 B 口径放宽');
  const del = await req('DELETE','/api/admin/member/members/2',undefined,C);
  check('收银员无删除会员权限', del.code, 10201, del.message);

  console.log('\n' + '='.repeat(50));
  console.log('PASS: ' + pass + '   FAIL: ' + fail + '   TOTAL: ' + (pass + fail));
  if (failures.length) { console.log('\n失败:'); failures.forEach(f => console.log('  - ' + f)); }
})();
