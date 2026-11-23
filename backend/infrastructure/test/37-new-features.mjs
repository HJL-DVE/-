/**
 * 37-new-features.mjs —— 验证本轮新增功能
 *  1. 操作日志 AOP（新增员工 → sys_operation_log 自动写入）
 *  2. 积分规则编辑（PUT 生效 + 数值校验）
 *  3. 8 类活动触发：新人礼包 / 签到连续 / 生日扫描 / 沉睡扫描
 */
const GW = 'http://127.0.0.1:9000';
const INTERNAL = 'pBFnyMOqQsMNxTvUfvu82rrgE5exy2aC';
let pass = 0, fail = 0;
const chk = (n, ok, d) => { ok ? pass++ : fail++; console.log((ok ? '  [PASS] ' : '  [FAIL] ') + n + '  ' + (d || '')); };
const sleep = (ms) => new Promise(r => setTimeout(r, ms));

const api = async (m, p, { token, body, headers } = {}) => {
  const h = { 'Content-Type': 'application/json', ...(headers || {}) };
  if (token) h.Authorization = 'Bearer ' + token;
  const r = await fetch(GW + p, { method: m, headers: h, body: body === undefined ? undefined : JSON.stringify(body) });
  return await r.json().catch(() => ({}));
};
const direct = async (port, m, p, { body, headers } = {}) => {
  const h = { 'Content-Type': 'application/json', 'X-Internal-Token': INTERNAL, ...(headers || {}) };
  const r = await fetch(`http://127.0.0.1:${port}${p}`, { method: m, headers: h, body: body === undefined ? undefined : JSON.stringify(body) });
  return await r.json().catch(() => ({}));
};

const adm = await api('POST', '/api/admin/auth/login', { body: { username: 'admin', password: 'TeaClub@123' } });
const tok = adm?.data?.accessToken;
if (!tok) { console.log('登录失败，无法继续'); process.exit(1); }

// ============ 1. 操作日志 AOP ============
console.log('=== 1. 操作日志 AOP ===');
const before = await api('GET', '/api/admin/system/logs?pageNo=1&pageSize=1', { token: tok });
const beforeTotal = before?.data?.total ?? 0;

const uniq = 'aop' + Date.now().toString(36);
const created = await api('POST', '/api/admin/system/users', {
  token: tok,
  body: { username: uniq, realName: 'AOP验证', password: 'Test@12345', userType: 'HQ', roleIds: [], storeIds: [] }
});
chk('新增员工', created?.code === 0, 'id=' + created?.data + ' ' + (created?.message || ''));

await sleep(1500);
const after = await api('GET', '/api/admin/system/logs?pageNo=1&pageSize=5', { token: tok });
const afterTotal = after?.data?.total ?? 0;
const list = after?.data?.records || [];
chk('AOP 自动写入操作日志', afterTotal > beforeTotal, `${beforeTotal} -> ${afterTotal}`);
const newest = list[0];
if (newest) {
  chk('日志含模块+操作', !!(newest.module && newest.operation), `${newest.module}/${newest.operation}`);
  chk('日志含操作人', !!newest.operatorName, newest.operatorName);
  chk('日志含耗时与结果', newest.costMs != null && newest.success != null, `cost=${newest.costMs}ms success=${newest.success}`);
  console.log('    最新一条:', JSON.stringify({
    module: newest.module, operation: newest.operation, operator: newest.operatorName,
    success: newest.success, costMs: newest.costMs, uri: newest.requestUri
  }));
} else {
  chk('日志含模块+操作', false, '无日志');
}
if (created?.code === 0 && created?.data) {
  await api('DELETE', '/api/admin/system/users/' + created.data, { token: tok });
}

// ============ 2. 积分规则编辑 ============
console.log('');
console.log('=== 2. 积分规则编辑 ===');
const rules = await api('GET', '/api/admin/marketing/points/rules', { token: tok });
chk('规则列表可用', rules?.code === 0, '条数=' + (rules?.data || []).length);
const rule = (rules?.data || [])[0];
chk('规则含 id', rule?.id != null, 'id=' + rule?.id + ' code=' + rule?.ruleCode);

if (rule?.id) {
  const original = rule.signInPoints;
  const target = (original ?? 0) + 3;

  const upd = await api('PUT', '/api/admin/marketing/points/rules/' + rule.id, { token: tok, body: { signInPoints: target } });
  chk('更新规则成功', upd?.code === 0, 'code=' + upd?.code + ' ' + (upd?.message || ''));

  const reread = await api('GET', '/api/admin/marketing/points/rules', { token: tok });
  const nr = (reread?.data || []).find(r => r.id === rule.id);
  chk('规则已落库生效', nr?.signInPoints === target, `${original} -> ${nr?.signInPoints}`);

  // 非法值校验
  const bad = await api('PUT', '/api/admin/marketing/points/rules/' + rule.id, { token: tok, body: { amountPerPoint: 0 } });
  chk('非法值被拒（比例为0）', bad?.code !== 0, 'code=' + bad?.code + ' ' + (bad?.message || ''));

  const badStatus = await api('PUT', '/api/admin/marketing/points/rules/' + rule.id, { token: tok, body: { status: 'WRONG' } });
  chk('非法状态被拒', badStatus?.code !== 0, 'code=' + badStatus?.code + ' ' + (badStatus?.message || ''));

  // 还原
  await api('PUT', '/api/admin/marketing/points/rules/' + rule.id, { token: tok, body: { signInPoints: original } });
  const restored = await api('GET', '/api/admin/marketing/points/rules', { token: tok });
  const rr = (restored?.data || []).find(r => r.id === rule.id);
  chk('已还原原值', rr?.signInPoints === original, 'signInPoints=' + rr?.signInPoints);
}

// ============ 3. 活动触发 ============
console.log('');
console.log('=== 3. 活动触发 ===');

// 3.1 新人礼包：手机号注册
const phone = '1' + String(Date.now()).slice(-10);
const reg = await api('POST', '/api/auth/member-register', {
  body: { phone, password: 'Test@12345', nickname: '新人礼包验证' }
});
chk('会员注册成功', reg?.code === 0, 'code=' + reg?.code + ' ' + (reg?.message || ''));
if (reg?.code === 0) {
  const mtok = reg.data.token;
  await sleep(1200);
  const coupons = await api('GET', '/api/marketing/coupons/mine?limit=5', { token: mtok });
  const acc = await api('GET', '/api/marketing/points/account', { token: mtok });
  const gotCoupon = (coupons?.data || []).length > 0;
  const gotPoints = (acc?.data?.totalPoints ?? 0) > 0;
  chk('新人礼包：券已到账', gotCoupon, '券数=' + (coupons?.data || []).length);
  chk('新人礼包：积分已到账', gotPoints, '积分=' + (acc?.data?.totalPoints ?? 0));
}

// 3.2 签到触发连续签到活动
const mi = await api('POST', '/api/auth/wx-login', { body: { code: 'act37-' + Date.now() } });
const mtok2 = mi?.data?.token;
if (mtok2) {
  const accBefore = await api('GET', '/api/marketing/points/account', { token: mtok2 });
  const pBefore = accBefore?.data?.totalPoints ?? 0;
  const sign = await api('POST', '/api/marketing/points/sign-in', { token: mtok2 });
  chk('签到成功', sign?.code === 0, 'code=' + sign?.code + ' 得分=' + sign?.data?.points);
  await sleep(800);
  const accAfter = await api('GET', '/api/marketing/points/account', { token: mtok2 });
  chk('签到积分到账', (accAfter?.data?.totalPoints ?? 0) > pBefore, `${pBefore} -> ${accAfter?.data?.totalPoints ?? 0}`);
}

// 3.3 生日 / 沉睡扫描接口（直连 member-service 内部接口）
const bd = await direct(9002, 'GET', '/api/member/internal/birthday-members?brandId=1&month=' + (new Date().getMonth() + 1));
chk('生日会员扫描接口', bd?.code === 0, 'code=' + bd?.code + ' 会员数=' + (bd?.data || []).length);
const sl = await direct(9002, 'GET', '/api/member/internal/sleeping-members?brandId=1&sleepingDays=30');
chk('沉睡会员扫描接口', sl?.code === 0, 'code=' + sl?.code + ' 会员数=' + (sl?.data || []).length);

// 3.4 手动触发一次生日/沉睡活动（验证内部接口与发奖链路）
if ((bd?.data || []).length > 0) {
  const trigger = await direct(9007, 'POST', `/api/marketing/internal/activities/trigger-birthday?brandId=1&memberId=${bd.data[0]}`);
  chk('生日活动触发接口', trigger?.code === 0, 'code=' + trigger?.code + ' 发奖数=' + trigger?.data);
}

// ============ 4. 8 类活动仍在列表 ============
console.log('');
console.log('=== 4. 活动配置完整性 ===');
const acts = await api('GET', '/api/admin/marketing/activities', { token: tok });
const actList = acts?.data || [];
const types = new Set(actList.map(a => a.activityType));
chk('活动列表可用', acts?.code === 0, '共 ' + actList.length + ' 条');
const expected = ['NEW_MEMBER_GIFT', 'BIRTHDAY', 'FESTIVAL', 'CONTINUOUS_SIGN', 'FULL_GIFT', 'POINTS_MALL', 'STORE_SPECIAL', 'SLEEPING_RECALL'];
const missing = expected.filter(t => !types.has(t));
chk('8 类活动齐全', missing.length === 0, missing.length ? '缺少: ' + missing.join(',') : '全部存在');

console.log('');
console.log('=== 汇总: PASS=' + pass + ' FAIL=' + fail + ' ===');
if (fail > 0) process.exitCode = 1;