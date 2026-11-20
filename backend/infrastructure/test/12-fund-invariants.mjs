
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
const ROOT = 'H:\\奶茶店会员与储值营销系统';
const env = fs.readFileSync(ROOT + '\\backend\\infrastructure\\docker\\.env.docker','utf8');
const V = {};
env.split(/\r?\n/).forEach(l => { const m = l.match(/^([A-Z_]+)=(.*)$/); if (m) V[m[1]] = m[2]; });
const DOCKER = 'C:\\Program Files\\Docker\\Docker\\resources\\bin\\docker.exe';
const q = (sql) => {
  try { return execFileSync(DOCKER, ['exec','teaclub-mysql','mysql','-uroot','-p'+V.MYSQL_ROOT_PASSWORD,'wallet_db','-N','-B','-e',sql], { encoding:'utf8' }).split('\n').filter(l=>l.trim()&&!l.includes('Warning')); }
  catch(e) { return ['ERR '+(e.stderr||e.message).toString().slice(0,120)]; }
};

let pass = 0, fail = 0;
const chk = (n, ok, d) => { if (ok) pass++; else fail++; console.log((ok ? '  [PASS] ' : '  [FAIL] ') + n + '  ' + d); };

// ==================== I1 + I2：余额非负 且 余额 == 流水累计 ====================
console.log('=== I1/I2 余额非负 + 余额守恒（逐钱包）===');
const rows = q("SELECT w.id, w.member_id, w.principal_balance, w.gift_balance, "
  + "COALESCE(s.sum_p,0), COALESCE(s.sum_g,0) "
  + "FROM wal_wallet w LEFT JOIN ("
  + "  SELECT wallet_id, SUM(principal_change) sum_p, SUM(gift_change) sum_g "
  + "  FROM wal_wallet_transaction GROUP BY wallet_id"
  + ") s ON s.wallet_id = w.id ORDER BY w.id");
let walCount = 0, negCount = 0, mismatch = 0;
for (const line of rows) {
  if (line.startsWith('ERR')) { chk('查询钱包', false, line); break; }
  const [id, memberId, pb, gb, sp, sg] = line.split('\t');
  walCount++;
  const ok1 = Number(pb) >= 0 && Number(gb) >= 0;
  const ok2 = Number(pb) === Number(sp) && Number(gb) === Number(sg);
  if (!ok1) { negCount++; console.log('    [负余额] wallet=' + id + ' member=' + memberId + ' 实付=' + pb + ' 赠送=' + gb); }
  if (!ok2) { mismatch++; console.log('    [不守恒] wallet=' + id + ' 余额(' + pb + ',' + gb + ') 流水(' + sp + ',' + sg + ')'); }
}
chk('I1 无负余额', negCount === 0, '钱包数=' + walCount + ' 负余额=' + negCount);
chk('I2 余额==流水累计', mismatch === 0, '钱包数=' + walCount + ' 不守恒=' + mismatch);

// ==================== I3：单笔自洽 after == before + change ====================
console.log('');
console.log('=== I3 单笔自洽（after == before + change）===');
const i3 = q("SELECT COUNT(*) FROM wal_wallet_transaction "
  + "WHERE principal_after <> principal_before + principal_change "
  + "   OR gift_after <> gift_before + gift_change")[0];
chk('I3 单笔自洽', i3 === '0', '不自洽笔数=' + i3);

// ==================== I4：链式衔接 ====================
console.log('');
console.log('=== I4 流水链式衔接（相邻）===');
const i4 = q("SELECT COUNT(*) FROM ("
  + "  SELECT t.principal_before, LAG(t.principal_after) OVER (PARTITION BY t.wallet_id ORDER BY t.id) pa "
  + "  FROM wal_wallet_transaction t) x WHERE x.pa IS NOT NULL AND x.principal_before <> x.pa")[0];
chk('I4 链式衔接', i4 === '0', '断裂数=' + i4);

// ==================== I5：业务唯一 (biz_type, biz_no) ====================
console.log('');
console.log('=== I5 业务唯一（biz_type + biz_no 不得重复）===');
const i5 = q("SELECT COUNT(*) FROM ("
  + "  SELECT biz_type, biz_no FROM wal_wallet_transaction "
  + "  WHERE biz_no IS NOT NULL AND biz_no <> '' "
  + "  GROUP BY biz_type, biz_no HAVING COUNT(*) > 1) x")[0];
chk('I5 业务唯一', i5 === '0', '重复组数=' + i5);

// ==================== I6：冻结可控（有效冻结额 <= 账户余额）====================
console.log('');
console.log('=== I6 冻结可控（FROZEN 记录冻结额 <= 账户余额）===');
const fz = q("SELECT COUNT(*) FROM wal_wallet_freeze f "
  + "JOIN wal_wallet w ON w.member_id = f.member_id AND w.brand_id = f.brand_id "
  + "WHERE f.status = 'FROZEN' "
  + "AND (f.freeze_principal > w.principal_balance OR f.freeze_gift > w.gift_balance)")[0];
chk('I6 冻结可控', fz === '0', '超冻笔数=' + fz);

// ==================== 冻结状态快照（信息性，不计入断言）====================
console.log('');
console.log('=== 冻结记录状态（信息性）===');
q("SELECT order_no, freeze_principal, freeze_gift, status FROM wal_wallet_freeze ORDER BY id").forEach(l => console.log('  ' + l));

console.log('');
console.log('=== 汇总: PASS=' + pass + ' FAIL=' + fail + ' ===');
if (fail > 0) process.exitCode = 1;
