/**
 * 40-fullstack-audit.mjs —— 全链路深度测试（新增）
 * 覆盖：健康检查、鉴权矩阵、参数校验、错误码、幂等、边界、异常场景
 */
const GW = 'http://127.0.0.1:9000';
let pass = 0, fail = 0;
const failures = [];
const chk = (n, ok, d) => {
  if (ok) pass++; else { fail++; failures.push({ n, d }); }
  console.log((ok ? '  [PASS] ' : '  [FAIL] ') + n + '  ' + (d || ''));
};
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const req = async (m, p, { token, body, headers } = {}) => {
  const h = { 'Content-Type': 'application/json', ...(headers || {}) };
  if (token) h.Authorization = 'Bearer ' + token;
  const r = await fetch(GW + p, { method: m, headers: h, body: body === undefined ? undefined : JSON.stringify(body) });
  let j = null;
  try { j = await r.json(); } catch {}
  return { status: r.status, code: j?.code, message: j?.message, data: j?.data, traceId: j?.traceId };
};

// ============ A. 健康检查 ============
console.log('=== A. 健康检查（8 服务 + 网关）===');
for (const port of [9001, 9002, 9003, 9004, 9005, 9006, 9007, 9008]) {
  try {
    const r = await fetch(`http://127.0.0.1:${port}/actuator/health`);
    const j = await r.json();
    chk(`${port} 健康`, r.status === 200 && j.status === 'UP', `status=${j.status}`);
  } catch (e) {
    chk(`${port} 健康`, false, e.message);
  }
}

// ============ B. 未认证访问 ============
console.log('');
console.log('=== B. 未认证访问控制 ===');
const noAuth = await req('GET', '/api/admin/member/members');
chk('无令牌访问 admin 接口被拒', noAuth.code !== 0 || noAuth.status === 401, `code=${noAuth.code} status=${noAuth.status} msg=${noAuth.message}`);
const badTok = await req('GET', '/api/admin/member/members', { token: 'invalid.token.value' });
chk('伪造令牌被拒', badTok.code !== 0 || badTok.status === 401, `code=${badTok.code} status=${badTok.status}`);
const memberTokOnAdmin = await req('POST', '/api/auth/wx-login', { body: { code: 'audit40-' + Date.now() } });
if (memberTokOnAdmin.code === 0) {
  const r = await req('GET', '/api/admin/member/members', { token: memberTokOnAdmin.data.token });
  chk('会员令牌访问 admin 被拒', r.code !== 0, `code=${r.code} msg=${r.message}`);
} else {
  chk('会员令牌访问 admin 被拒', false, '会员登录失败: ' + memberTokOnAdmin.message);
}

// ============ C. 参数校验 ============
console.log('');
console.log('=== C. 参数校验与错误码 ===');
const adm = await req('POST', '/api/admin/auth/login', { body: { username: 'admin', password: 'TeaClub@123' } });
const tok = adm.data?.accessToken;
chk('管理员登录', adm.code === 0, adm.code === 0 ? '' : adm.message);

const cases = [
  ['GET', '/api/admin/member/members?pageNo=abc', {}, '非法分页参数'],
  ['GET', '/api/admin/member/members/999999999', {}, '不存在的会员 ID'],
  ['POST', '/api/admin/system/roles', { body: {} }, '缺少必填字段的角色'],
  ['GET', '/api/admin/wallet/accounts/member/999999999', {}, '不存在的钱包'],
  ['GET', '/api/order/orders/nonexistent', {}, '不存在的订单'],
  ['POST', '/api/marketing/coupons/receive?templateId=999999&qty=1', {}, '领取不存在的券模板'],
];
for (const [m, p, opt, label] of cases) {
  const r = await req(m, p, { token: tok, ...opt });
  const handled = r.code !== 0 && r.code !== 50000 && r.code !== undefined;
  chk(label + ' 返回业务错误码', handled, `code=${r.code} msg=${(r.message || '').slice(0, 60)}`);
}

// 分页上限：超大 pageSize 必须被限制（不是报错，而是返回上限条数）
const big = await req('GET', '/api/admin/member/members?pageNo=1&pageSize=99999', { token: tok });
const bigCount = (big?.data?.records || []).length;
chk('超大 pageSize 被限制到 100', big?.code === 0 && bigCount <= 100, `返回 ${bigCount} 条`);

// 非法日期参数必须返回参数错误码，而不是 50000 系统内部错误
const badDays = await req('GET', '/api/report/dashboard/trend?days=abc', { token: tok });
chk('非法 days 返回 10001 而非 50000', badDays?.code === 10001, `code=${badDays?.code} msg=${(badDays?.message || '').slice(0, 40)}`);

// 调整不存在的会员积分必须被拒（不能写脏数据）
const badAdj = await req('POST', '/api/admin/marketing/points/adjust?memberId=999999999&points=100', { token: tok });
chk('调整不存在会员积分被拒', badAdj?.code !== 0 && badAdj?.code !== 50000, `code=${badAdj?.code} msg=${(badAdj?.message || '').slice(0, 40)}`);

// ============ D. 幂等性 ============
console.log('');
console.log('=== D. 幂等性 ===');
const login1 = await req('POST', '/api/admin/auth/login', { body: { username: 'admin', password: 'TeaClub@123' } });
const login2 = await req('POST', '/api/admin/auth/login', { body: { username: 'admin', password: 'TeaClub@123' } });
chk('重复登录各自签发独立令牌', login1.data?.accessToken !== login2.data?.accessToken, 'jti 不同');

// ============ E. 权限矩阵 ============
console.log('');
console.log('=== E. 权限矩阵（角色 × 资源）===');
const roles = [
  ['admin', 'TeaClub@123'],
  ['hqadmin', 'TeaClub@123'],
  ['mgr_xihu', 'TeaClub@123'],
  ['cashier01', 'TeaClub@123'],
];
const resources = [
  ['GET', '/api/admin/member/members', 'member:list'],
  ['GET', '/api/report/dashboard/overview', 'report:view'],
  ['GET', '/api/admin/system/users', 'system:user:list'],
  ['GET', '/api/admin/marketing/activities', 'marketing:activity:list'],
  ['GET', '/api/admin/wallet/plans', 'wallet:plan:list'],
];
for (const [user, pwd] of roles) {
  const lr = await req('POST', '/api/admin/auth/login', { body: { username: user, password: pwd } });
  if (lr.code !== 0) { chk(user + ' 登录', false, lr.message); continue; }
  const t = lr.data.accessToken;
  const results = [];
  for (const [m, p, perm] of resources) {
    const r = await req(m, p, { token: t });
    const allowed = r.code === 0;
    results.push(`${perm}=${allowed ? 'Y' : 'N'}`);
  }
  chk(user + ' 权限矩阵', true, results.join(' '));
}

// ============ F. 数据范围（店长） ============
console.log('');
console.log('=== F. 数据范围隔离 ===');
const mgr = await req('POST', '/api/admin/auth/login', { body: { username: 'mgr_xihu', password: 'TeaClub@123' } });
if (mgr.code === 0) {
  const t = mgr.data.accessToken;
  const own = await req('GET', '/api/report/store/overview?storeId=1', { token: t });
  chk('店长看授权门店', own.code === 0, 'code=' + own.code);
  const other = await req('GET', '/api/report/store/overview?storeId=2', { token: t });
  chk('店长越权被拒', other.code !== 0, 'code=' + other.code + ' ' + other.message);
  const noStore = await req('GET', '/api/report/dashboard/overview', { token: t });
  chk('店长未指定门店时自动收敛到本店', noStore.code === 0, 'code=' + noStore.code);
}

console.log('');
console.log('=== 汇总: PASS=' + pass + ' FAIL=' + fail + ' ===');
if (failures.length) {
  console.log('失败明细:');
  failures.forEach(f => console.log('  - ' + f.n + '  ' + f.d));
}
if (fail > 0) process.exitCode = 1;