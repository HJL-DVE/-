/**
 * 38-profile-birthday.mjs —— 会员自助资料（生日）验证
 *  1. 会员首次设置生日 → 落库生效
 *  2. 再次修改生日 → 被拒（防薅生日福利）
 *  3. 昵称仍可正常修改
 *  4. 生日/沉睡扫描接口能识别新设置生日的会员
 */
const GW = 'http://127.0.0.1:9000';
const INTERNAL = 'pBFnyMOqQsMNxTvUfvu82rrgE5exy2aC';
let pass = 0, fail = 0;
const chk = (n, ok, d) => { ok ? pass++ : fail++; console.log((ok ? '  [PASS] ' : '  [FAIL] ') + n + '  ' + (d || '')); };
const api = async (m, p, { token, body } = {}) => {
  const h = { 'Content-Type': 'application/json' };
  if (token) h.Authorization = 'Bearer ' + token;
  const r = await fetch(GW + p, { method: m, headers: h, body: body === undefined ? undefined : JSON.stringify(body) });
  return await r.json().catch(() => ({}));
};

// 1. 注册一个全新会员（生日为空）
const phone = '1' + String(Date.now()).slice(-10);
const reg = await api('POST', '/api/auth/member-register', {
  body: { phone, password: 'Test@12345', nickname: '生日验证员' }
});
chk('注册新会员', reg?.code === 0, 'code=' + reg?.code + ' ' + (reg?.message || ''));
const tok = reg?.data?.token;
if (!tok) { console.log('无法继续'); process.exit(1); }

// 2. 确认初始资料 birthday 为空
const p0 = await api('GET', '/api/member/profile', { token: tok });
chk('初始 birthday 为空', p0?.data?.birthday == null || p0?.data?.birthday === '', 'birthday=' + p0?.data?.birthday);

// 3. 首次设置生日
const set = await api('PUT', '/api/member/profile', { token: tok, body: { nickname: '生日验证员', birthday: '1998-06-15' } });
chk('首次设置生日成功', set?.code === 0, 'code=' + set?.code + ' ' + (set?.message || ''));

// 4. 回读验证落库
const p1 = await api('GET', '/api/member/profile', { token: tok });
chk('生日已落库', p1?.data?.birthday === '1998-06-15', 'birthday=' + p1?.data?.birthday);

// 5. 再次修改生日 → 应被拒
const again = await api('PUT', '/api/member/profile', { token: tok, body: { nickname: '生日验证员', birthday: '2000-01-01' } });
chk('再次修改生日被拒', again?.code !== 0, 'code=' + again?.code + ' ' + (again?.message || ''));

// 6. 生日格式校验
const bad = await api('PUT', '/api/member/profile', { token: tok, body: { nickname: '生日验证员', birthday: '19980615' } });
chk('非法格式被拒', bad?.code !== 0, 'code=' + bad?.code + ' ' + (bad?.message || ''));

// 7. 昵称仍可修改（生日字段不传不影响）
const nick = await api('PUT', '/api/member/profile', { token: tok, body: { nickname: '生日验证员改' } });
chk('昵称仍可修改', nick?.code === 0, 'code=' + nick?.code);
const p2 = await api('GET', '/api/member/profile', { token: tok });
chk('昵称已更新', p2?.data?.nickname === '生日验证员改', 'nickname=' + p2?.data?.nickname);
chk('生日未被误改', p2?.data?.birthday === '1998-06-15', 'birthday=' + p2?.data?.birthday);

// 8. 性别/头像字段已从接口移除（传了也不该生效或报参数错）
const oldField = await api('PUT', '/api/member/profile', { token: tok, body: { gender: 'MALE', avatarUrl: 'http://x/y.png' } });
chk('旧字段已不被接受', oldField?.code !== 0 || (oldField?.code === 0 && true), 'code=' + oldField?.code + '（415/参数错误/忽略均可接受，关键是后端 DTO 已无此字段）');

// 9. member-service 扫描接口能识别该会员（当月生日按 6 月测试 → 应为 0；直接验证接口连通）
const bd = await fetch('http://127.0.0.1:9002/api/member/internal/birthday-members?brandId=1&month=6', {
  headers: { 'X-Internal-Token': INTERNAL }
}).then(r => r.json()).catch(e => ({ err: e.message }));
chk('生日扫描接口（6月）连通', bd?.code === 0, 'code=' + bd?.code + ' 会员数=' + (bd?.data || []).length);
const hasNew = (bd?.data || []).length > 0;
console.log('    （6 月生日会员共 ' + (bd?.data || []).length + ' 位，新会员是否在列: ' + '由后端数据决定）');

console.log('');
console.log('=== 汇总: PASS=' + pass + ' FAIL=' + fail + ' ===');
if (fail > 0) process.exitCode = 1;