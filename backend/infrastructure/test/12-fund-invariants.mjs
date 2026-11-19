
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

console.log('=== 资金不变量复核（所有钱包）===');
q("SELECT w.member_id, w.principal_balance, w.gift_balance, COALESCE(SUM(t.principal_change),0) sum_p, COALESCE(SUM(t.gift_change),0) sum_g, (w.principal_balance=COALESCE(SUM(t.principal_change),0) AND w.gift_balance=COALESCE(SUM(t.gift_change),0)) ok FROM wal_wallet w LEFT JOIN wal_wallet_transaction t ON t.wallet_id=w.id GROUP BY w.member_id,w.principal_balance,w.gift_balance").forEach(l=>console.log('  '+l));

console.log('');
console.log('=== 冻结记录状态 ===');
q("SELECT order_no, freeze_principal, freeze_gift, status FROM wal_wallet_freeze ORDER BY id").forEach(l=>console.log('  '+l));

console.log('');
console.log('=== 流水链式衔接（相邻）===');
q("SELECT COUNT(*) FROM (SELECT t.principal_before, LAG(t.principal_after) OVER (PARTITION BY t.wallet_id ORDER BY t.id) pa FROM wal_wallet_transaction t) x WHERE x.pa IS NOT NULL AND x.principal_before<>x.pa").forEach(l=>console.log('  断裂数='+l));
