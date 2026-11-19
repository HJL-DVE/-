#!/usr/bin/env node
/**
 * TeaClub 数据迁移校验器
 *
 * 校验项（对应规格第九节）：
 *   1. 各表源/目标行数一致
 *   2. 会员数量
 *   3. 钱包余额
 *   4. 资金流水总额
 *   5. 充值记录
 *   6. 订单记录
 *   7. 支付记录
 *   8. 退款记录
 *   9. 钱包资金不变量 I1~I6
 *
 * 用法：node verify.js
 */
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';

const ROOT = "H:\\奶茶店会员与储值营销系统";
const SRC = { host: '127.0.0.1', port: '3306', user: 'root', pass: '1234', db: 'teaclub' };
const DST = { host: '127.0.0.1', port: '3307', user: 'root', pass: null };

const envTxt = fs.readFileSync(ROOT + '\\infrastructure\\docker\\.env.docker', 'utf8');
const env = {};
envTxt.split(/\r?\n/).forEach(l => {
  const m = l.match(/^([A-Z_]+)=(.*)$/);
  if (m) env[m[1]] = m[2];
});
DST.pass = env.MYSQL_ROOT_PASSWORD;

const MYSQL = 'C:\\Program Files\\MySQL\\MySQL Server 8.4\\bin\\mysql.exe';

function sql(target, query) {
  const a = target === 'src'
    ? ['-h', SRC.host, '-P', String(SRC.port), '-u', SRC.user, '-p' + SRC.pass, SRC.db, '-N', '-B', '-e', query]
    : ['-h', DST.host, '-P', String(DST.port), '-u', DST.user, '-p' + DST.pass, '-N', '-B', '-e', query];
  try {
    return execFileSync(MYSQL, a, { encoding: 'utf8', maxBuffer: 50*1024*1024, stdio: ['pipe','pipe','pipe'] });
  } catch (e) {
    const err = e.stderr ? e.stderr.toString() : e.message;
    throw new Error(err.slice(0, 400));
  }
}

const QT = (t) => '`' + t + '`';

let pass = 0, fail = 0;
const failures = [];

function check(name, actual, expected) {
  const ok = String(actual) === String(expected);
  if (ok) pass++; else { fail++; failures.push(name + ': 期望 ' + expected + '，实际 ' + actual); }
  console.log('  ' + (ok ? '[OK]  ' : '[FAIL]') + ' ' + name.padEnd(46) + actual + (ok ? '' : '  (期望 ' + expected + ')'));
  return ok;
}

function checkTrue(name, cond, detail) {
  if (cond) pass++; else { fail++; failures.push(name + ': ' + detail); }
  console.log('  ' + (cond ? '[OK]  ' : '[FAIL]') + ' ' + name.padEnd(46) + (detail || ''));
  return cond;
}

console.log('=============================================');
console.log(' TeaClub 数据迁移校验器');
console.log('=============================================');
console.log('');

// ---------- 1. 表行数一致 ----------
console.log('【1】源表与目标表行数一致');
const TABLES = [
  ['iam_db','sys_brand'],['iam_db','sys_store'],['iam_db','sys_user'],['iam_db','sys_role'],
  ['iam_db','sys_permission'],['iam_db','sys_user_role'],['iam_db','sys_role_permission'],
  ['iam_db','sys_user_store'],['iam_db','sys_operation_log'],
  ['member_db','mem_member'],['member_db','mem_member_level'],
  ['product_db','prd_category'],['product_db','prd_spu'],['product_db','prd_sku'],
  ['product_db','prd_store_spu'],['product_db','prd_store_sku'],
  ['wallet_db','wal_wallet'],['wallet_db','wal_wallet_transaction'],['wallet_db','wal_recharge_order'],
  ['wallet_db','wal_recharge_plan'],['wallet_db','wal_recharge_plan_store'],
  ['order_db','oms_order'],['order_db','oms_order_item'],['order_db','oms_order_status_log'],
  ['payment_db','pay_payment_order'],['payment_db','pay_notify_log'],['payment_db','pay_refund_order'],
];
for (const [db, table] of TABLES) {
  const srcN = sql('src', 'SELECT COUNT(*) FROM ' + QT(table)).trim().split('\n')[0];
  let dstN = '0';
  try { dstN = sql('dst', 'SELECT COUNT(*) FROM ' + QT(db) + '.' + QT(table)).trim().split('\n')[0]; }
  catch (e) { dstN = 'ERR'; }
  check(db + '.' + table, dstN, srcN);
}
console.log('');

// ---------- 2. 关键业务指标 ----------
console.log('【2】关键业务指标');
const sum = (target, db, table, col, where) => {
  const w = where ? ' WHERE ' + where : '';
  const q = target === 'src'
    ? 'SELECT COALESCE(SUM(' + col + '),0) FROM ' + QT(table) + w
    : 'SELECT COALESCE(SUM(' + col + '),0) FROM ' + QT(db) + '.' + QT(table) + w;
  return sql(target, q).trim().split('\n')[0];
};

// 会员数量
check('会员数量 member',
  sql('dst', 'SELECT COUNT(*) FROM ' + QT('member_db') + '.' + QT('mem_member')).trim().split('\n')[0],
  sql('src', 'SELECT COUNT(*) FROM ' + QT('mem_member')).trim().split('\n')[0]);

// 钱包余额
for (const col of ['principal_balance','gift_balance','total_principal_in','total_gift_in','total_principal_out','total_gift_out','total_refund_in']) {
  check('钱包余额合计 ' + col,
    sum('dst','wallet_db','wal_wallet',col),
    sum('src','teaclub','wal_wallet',col));
}

// 资金流水
for (const col of ['principal_change','gift_change','amount_paid','gift_amount']) {
  check('资金流水合计 ' + col,
    sum('dst','wallet_db','wal_wallet_transaction',col),
    sum('src','teaclub','wal_wallet_transaction',col));
}

// 充值记录金额
check('充值订单金额合计 recharge_amount',
  sum('dst','wallet_db','wal_recharge_order','recharge_amount'),
  sum('src','teaclub','wal_recharge_order','recharge_amount'));

// 订单金额
check('订单金额合计 paid_amount',
  sum('dst','order_db','oms_order','paid_amount'),
  sum('src','teaclub','oms_order','paid_amount'));
check('订单金额合计 total_amount',
  sum('dst','order_db','oms_order','total_amount'),
  sum('src','teaclub','oms_order','total_amount'));
check('订单应付合计 payable_amount',
  sum('dst','order_db','oms_order','payable_amount'),
  sum('src','teaclub','oms_order','payable_amount'));

// 支付金额
check('支付单金额合计 amount',
  sum('dst','payment_db','pay_payment_order','amount'),
  sum('src','teaclub','pay_payment_order','amount'));

// 退款金额
check('退款单金额合计 refund_amount',
  sum('dst','payment_db','pay_refund_order','refund_amount'),
  sum('src','teaclub','pay_refund_order','refund_amount'));
console.log('');

// 订单明细快照（商品名称与单价快照不得被商品更新覆盖）
const itemSnap = sql('dst',
  'SELECT COALESCE(SUM(total_amount),0), COALESCE(SUM(quantity),0), COALESCE(SUM(unit_price),0) FROM ' + QT('order_db') + '.' + QT('oms_order_item')).trim().replace(/\n/g,' ');
const itemSnapSrc = sql('src',
  'SELECT COALESCE(SUM(total_amount),0), COALESCE(SUM(quantity),0), COALESCE(SUM(unit_price),0) FROM ' + QT('oms_order_item')).trim().replace(/\n/g,' ');
check('订单明细金额/数量/单价快照', itemSnap, itemSnapSrc);

// 商品名称与规格快照必须原样保留（商品更新不得影响历史订单）
const nameSnap = sql('dst',
  'SELECT COALESCE(SUM(CHAR_LENGTH(sku_name)),0), COALESCE(SUM(CHAR_LENGTH(spec_json)),0) FROM ' + QT('order_db') + '.' + QT('oms_order_item')).trim().replace(/\n/g,' ');
const nameSnapSrc = sql('src',
  'SELECT COALESCE(SUM(CHAR_LENGTH(sku_name)),0), COALESCE(SUM(CHAR_LENGTH(spec_json)),0) FROM ' + QT('oms_order_item')).trim().replace(/\n/g,' ');
check('订单明细名称与规格快照', nameSnap, nameSnapSrc);

console.log('');

// ---------- 3. 钱包资金不变量 ----------
console.log('【3】钱包资金不变量');
const W = QT('wallet_db') + '.' + QT('wal_wallet');
const T = QT('wallet_db') + '.' + QT('wal_wallet_transaction');

// I1 余额非负
const neg = sql('dst', 'SELECT COUNT(*) FROM ' + W + ' WHERE principal_balance < 0 OR gift_balance < 0').trim().split('\n')[0];
checkTrue('I1 每个钱包余额 >= 0', neg === '0', neg === '0' ? '无负余额' : neg + ' 个负余额');

// I2/I3 余额 = 流水累计
const i2 = sql('dst',
  'SELECT COUNT(*) FROM ' + W + ' w WHERE w.principal_balance <> COALESCE((SELECT SUM(t.principal_change) FROM ' + T + ' t WHERE t.wallet_id = w.id),0)').trim().split('\n')[0];
checkTrue('I2 principal_balance = SUM(principal_change)', i2 === '0', i2 === '0' ? '全部成立' : i2 + ' 个钱包不成立');

const i3 = sql('dst',
  'SELECT COUNT(*) FROM ' + W + ' w WHERE w.gift_balance <> COALESCE((SELECT SUM(t.gift_change) FROM ' + T + ' t WHERE t.wallet_id = w.id),0)').trim().split('\n')[0];
checkTrue('I3 gift_balance = SUM(gift_change)', i3 === '0', i3 === '0' ? '全部成立' : i3 + ' 个钱包不成立');

// I4 after = before + change
const i4 = sql('dst',
  'SELECT COUNT(*) FROM ' + T + ' WHERE principal_after <> principal_before + principal_change OR gift_after <> gift_before + gift_change').trim().split('\n')[0];
checkTrue('I4 after = before + change', i4 === '0', i4 === '0' ? '全部成立' : i4 + ' 条流水不成立');

// I5 biz_type + biz_no 唯一
const i5 = sql('dst',
  'SELECT COUNT(*) FROM (SELECT biz_type, biz_no, COUNT(*) c FROM ' + T + ' GROUP BY biz_type, biz_no HAVING c > 1) x').trim().split('\n')[0];
checkTrue('I5 (biz_type, biz_no) 唯一', i5 === '0', i5 === '0' ? '无重复' : i5 + ' 组重复');

// I6 流水链式衔接
const i6 = sql('dst',
  'SELECT COUNT(*) FROM (SELECT t.principal_before, t.gift_before, ' +
  'LAG(t.principal_after) OVER (PARTITION BY t.wallet_id ORDER BY t.id) AS prev_after_p, ' +
  'LAG(t.gift_after) OVER (PARTITION BY t.wallet_id ORDER BY t.id) AS prev_after_g ' +
  'FROM ' + T + ' t) x ' +
  'WHERE x.prev_after_p IS NOT NULL ' +
  'AND (x.principal_before <> x.prev_after_p OR x.gift_before <> x.prev_after_g)').trim().split('\n')[0];
checkTrue('I6 流水链式衔接（相邻）', i6 === '0', i6 === '0' ? '全部衔接' : i6 + ' 处断裂');
console.log('');

// ---------- 4. ID 保留验证 ----------
console.log('【4】历史 ID 保留');
const idChecks = [
  ['wallet_db','wal_wallet','id','钱包 ID'],
  ['wallet_db','wal_wallet_transaction','id','流水 ID'],
  ['wallet_db','wal_recharge_order','recharge_no','充值单号'],
  ['member_db','mem_member','id','会员 ID'],
  ['product_db','prd_sku','id','SKU ID'],
  ['order_db','oms_order','order_no','订单号'],
  ['payment_db','pay_payment_order','payment_no','支付单号'],
  ['payment_db','pay_payment_order','transaction_id','微信交易号'],
];
for (const [db, table, col, label] of idChecks) {
  let minMax = 'ERR';
  try { minMax = sql('dst', 'SELECT COALESCE(MIN(' + col + '),0), COALESCE(MAX(' + col + '),0) FROM ' + QT(db) + '.' + QT(table)).trim().replace(/\n/g, ' '); }
  catch (e) {}
  const srcMinMax = sql('src', 'SELECT COALESCE(MIN(' + col + '),0), COALESCE(MAX(' + col + '),0) FROM ' + QT(table)).trim().replace(/\n/g, ' ');
  check(label + ' 范围一致 (' + col + ')', minMax, srcMinMax);
}
console.log('');

console.log('=============================================');
console.log(' 通过 ' + pass + ' 项，失败 ' + fail + ' 项');
if (fail) {
  console.log(' 失败明细:');
  failures.forEach(f => console.log('   - ' + f));
}
console.log('=============================================');
process.exit(fail ? 1 : 0);