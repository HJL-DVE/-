/**
 * 39-auth-permission.mjs —— 验证 JwtAuthenticationFilter 权限解析修复
 *  1. 登录后 /me 返回完整权限（令牌声明为权威来源）
 *  2. 各角色权限正确（admin 全量 / 店长仅本店 / 收银员无报表权限）
 *  3. 越权访问被拒
 *  4. 会员端令牌不受影响
 */
const GW = 'http://127.0.0.1:9000';
let pass = 0, fail = 0;
const chk = (n, ok, d) => { ok ? pass++ : fail++; console.log((ok ? '  [PASS] ' : '  [FAIL] ') + n + '  ' + (d || '')); };
const req = async (m, p, { token, body } = {}) => {
  const h = { 'Content-Type': 'application/json' };
  if (token) h.Authorization = 'Bearer ' + token;
  const r = await fetch(GW + p, { method: m, headers: h, body: body === undefined ? undefined : JSON.stringify(body) });
  return await r.json().catch(() => ({}));
};

console.log('=== 1. 登录与 /me 权限 ===');
const adm = await req('POST', '/api/admin/auth/login', { body: { username: 'admin', password: 'TeaClub@123' } });
chk('管理员登录', adm?.code === 0, adm?.code === 0 ? '' : adm?.message);
const tok = adm?.data?.accessToken;
if (!tok) { console.log('登录失败，退出'); process.exit(1); }

const me = await req('GET', '/api/admin/auth/me', { token: tok });
const perms = me?.data?.permissions || [];
const roles = me?.data?.roles || [];
chk('/me 返回权限列表', perms.length > 0, '权限数=' + perms.length);
chk('/me 返回角色', roles.length > 0, '角色=' + roles.join(','));
chk('含关键权限点', perms.includes('system:user:list') || perms.includes('report:view'), '抽样=' + perms.slice(0, 3).join(','));

const me2 = await req('GET', '/api/admin/auth/me', { token: tok });
chk('重复请求权限稳定', (me2?.data?.permissions || []).length === perms.length, perms.length + ' -> ' + (me2?.data?.permissions || []).length);

console.log('');
console.log('=== 2. 三角色权限差异 ===');
const cases = [
  { u: 'hqadmin', expect: 'report:view' },
  { u: 'mgr_xihu', expect: 'report:view' },
  { u: 'cashier01', expect: null },
];
for (const c of cases) {
  const r = await req('POST', '/api/admin/auth/login', { body: { username: c.u, password: 'TeaClub@123' } });
  if (r?.code !== 0) { chk(c.u + ' 登录', false, r?.message); continue; }
  const t = r.data.accessToken;
  const m = await req('GET', '/api/admin/auth/me', { token: t });
  const ps = m?.data?.permissions || [];
  if (c.expect) {
    chk(c.u + ' 拥有 ' + c.expect, ps.includes(c.expect), '权限数=' + ps.length);
  } else {
    chk(c.u + ' 无报表权限', !ps.includes('report:view'), '权限=' + ps.join(',') || '(空)');
    const rep = await req('GET', '/api/report/dashboard/overview', { token: t });
    chk(c.u + ' 访问报表被拒', rep?.code !== 0, 'code=' + rep?.code + ' ' + (rep?.message || ''));
  }
}

console.log('');
console.log('=== 3. 越权防护 ===');
const mgr = await req('POST', '/api/admin/auth/login', { body: { username: 'mgr_xihu', password: 'TeaClub@123' } });
if (mgr?.code === 0) {
  const mt = mgr.data.accessToken;
  const rep = await req('GET', '/api/report/store/overview?storeId=2', { token: mt });
  chk('店长越权看其它门店被拒', rep?.code !== 0, 'code=' + rep?.code + ' ' + (rep?.message || ''));
  const own = await req('GET', '/api/report/store/overview?storeId=1', { token: mt });
  chk('店长看本店正常', own?.code === 0, 'code=' + own?.code);
}

console.log('');
console.log('=== 4. 会员端令牌不受影响 ===');
const mi = await req('POST', '/api/auth/wx-login', { body: { code: 'perm39-' + Date.now() } });
chk('会员登录', mi?.code === 0, mi?.code === 0 ? 'memberId=' + mi.data?.memberId : mi?.message);
if (mi?.code === 0) {
  const mt = mi.data.token;
  const mp = await req('GET', '/api/member/profile', { token: mt });
  chk('会员资料可读', mp?.code === 0, 'code=' + mp?.code);
  const md = await req('GET', '/api/admin/auth/me', { token: mt });
  chk('会员令牌不能访问管理端', md?.code !== 0, 'code=' + md?.code + ' ' + (md?.message || ''));
}

console.log('');
console.log('=== 汇总: PASS=' + pass + ' FAIL=' + fail + ' ===');
if (fail > 0) process.exitCode = 1;
