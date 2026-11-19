#!/usr/bin/env node
/**
 * TeaClub 单体 -> 微服务 数据迁移执行器
 *
 * 用法：node migrate.js --dry-run   仅打印计划
 *       node migrate.js             实际执行
 *
 * 设计要点：
 *   1. 保留历史 ID：目标表不做自增重排，直接搬原值
 *   2. 不触发业务事件：只做 INSERT，不调用任何业务接口
 *   3. 幂等：目标业务表先 TRUNCATE（不动 flyway 表），可重复执行
 *   4. 分库隔离：每个服务只搬属于自己的表
 */
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';

const ROOT = "H:\\奶茶店会员与储值营销系统";
const DRY = process.argv.includes('--dry-run');
const FORCE = process.argv.includes('--force');

const SRC = { host: '127.0.0.1', port: '3306', user: 'root', pass: '1234', db: 'teaclub' };
const DST = { host: '127.0.0.1', port: '3307', user: 'root', pass: null };

const envTxt = fs.readFileSync(ROOT + '\\backend\\infrastructure\\docker\\.env.docker', 'utf8');
const env = {};
envTxt.split(/\r?\n/).forEach(l => {
  const m = l.match(/^([A-Z_]+)=(.*)$/);
  if (m) env[m[1]] = m[2];
});
DST.pass = env.MYSQL_ROOT_PASSWORD;

const MYSQL = 'C:\\Program Files\\MySQL\\MySQL Server 8.4\\bin\\mysql.exe';

const PLAN = {
  'auth-service': {
    db: 'iam_db',
    tables: ['sys_brand','sys_store','sys_user','sys_role','sys_permission',
             'sys_user_role','sys_role_permission','sys_user_store','sys_config','sys_operation_log'],
  },
  'member-service': {
    db: 'member_db',
    tables: ['mem_member','mem_member_level','mem_member_tag','mem_member_tag_rel'],
  },
  'product-service': {
    db: 'product_db',
    tables: ['prd_category','prd_spu','prd_sku','prd_store_spu','prd_store_sku','prd_inventory'],
  },
  'wallet-service': {
    db: 'wallet_db',
    tables: ['wal_wallet','wal_wallet_transaction','wal_recharge_order','wal_recharge_plan','wal_recharge_plan_store'],
  },
  'order-service': {
    db: 'order_db',
    tables: ['oms_order','oms_order_item','oms_order_status_log'],
  },
  'payment-service': {
    db: 'payment_db',
    tables: ['pay_payment_order','pay_notify_log','pay_refund_order'],
  },
};

function mysqlArgs(target, query) {
  const a = target === 'src'
    ? ['-h', SRC.host, '-P', String(SRC.port), '-u', SRC.user, '-p' + SRC.pass, SRC.db, '-N', '-B', '-e', query]
    : ['-h', DST.host, '-P', String(DST.port), '-u', DST.user, '-p' + DST.pass, '-N', '-B', '-e', query];
  return a;
}

function sql(target, query) {
  try {
    return execFileSync(MYSQL, mysqlArgs(target, query), {
      encoding: 'utf8', maxBuffer: 100 * 1024 * 1024, stdio: ['pipe', 'pipe', 'pipe']
    });
  } catch (e) {
    const err = e.stderr ? e.stderr.toString() : e.message;
    throw new Error('SQL failed: ' + query.slice(0, 140) + '\n' + err.slice(0, 400));
  }
}

const QT = (t) => '`' + t + '`';

function countIn(target, table, db) {
  const q = target === 'src'
    ? 'SELECT COUNT(*) FROM ' + QT(table)
    : 'SELECT COUNT(*) FROM ' + QT(db) + '.' + QT(table);
  const r = sql(target, q).trim();
  return Number(r.split('\n')[0] || 0);
}

/**
 * 导出一张表并导入目标库。
 * 源(3306)与目标(3307)是不同实例，只能通过 mysqldump | mysql 管道搬运。
 */
function pipeTable(table, dstDb) {
  const dump = 'C:\\Program Files\\MySQL\\MySQL Server 8.4\\bin\\mysqldump.exe';
  const dumpArgs = [
    '-h', SRC.host, '-P', String(SRC.port), '-u', SRC.user, '-p' + SRC.pass,
    '--no-create-info', '--skip-triggers', '--single-transaction',
    '--set-gtid-purged=OFF', '--complete-insert', '--skip-extended-insert',
    SRC.db, table,
  ];
  const out = execFileSync(dump, dumpArgs, { encoding: 'buffer', maxBuffer: 100*1024*1024, stdio: ['pipe','pipe','pipe'] });

  // 去掉 mysqldump 输出的 USE / SET 语句，改为写入目标库
  const sqlText = out.toString('utf8')
    .split(/\r?\n/)
    .filter(line => !/^USE /.test(line) && !/^\/\*!.*\*\/$/.test(line) && !/^SET /.test(line))
    .join('\n');

  const importArgs = [
    '-h', DST.host, '-P', String(DST.port), '-u', DST.user, '-p' + DST.pass,
    dstDb, '-N', '-B',
  ];
  execFileSync(MYSQL, importArgs, { input: sqlText, encoding: 'utf8', maxBuffer: 100*1024*1024, stdio: ['pipe','pipe','pipe'] });
}

function listTables(target, db) {
  const q = "SELECT table_name FROM information_schema.tables WHERE table_schema='" + db + "'";
  return sql(target, q).trim().split('\n').filter(Boolean).map(s => s.trim());
}

console.log('=============================================');
console.log(' TeaClub 数据迁移执行器');
console.log(' 模式: ' + (DRY ? 'DRY-RUN（不写入）' : '实际执行'));
console.log('=============================================');
console.log('');
console.log('源: ' + SRC.host + ':' + SRC.port + '/' + SRC.db);
console.log('目标: ' + DST.host + ':' + DST.port);
console.log('');

let totalRows = 0;
const report = [];

for (const [service, cfg] of Object.entries(PLAN)) {
  console.log('--- ' + service + ' -> ' + cfg.db + ' ---');
  const srcTables = listTables('src', SRC.db);

  for (const table of cfg.tables) {
    if (!srcTables.includes(table)) {
      console.log('  [SKIP] ' + table + ' 源库不存在');
      continue;
    }
    const srcCount = countIn('src', table);
    let dstCount;
    try { dstCount = countIn('dst', table, cfg.db); } catch (e) { dstCount = -1; }

    if (!FORCE && dstCount === srcCount && srcCount > 0) {
      console.log('  [SAME] ' + table.padEnd(24) + srcCount + ' 行（已一致，跳过）');
      report.push({ service, table, src: srcCount, dst: dstCount, status: 'SKIP' });
      continue;
    }

    if (DRY) {
      console.log('  [PLAN] ' + table.padEnd(24) + srcCount + ' -> ' + cfg.db);
      report.push({ service, table, src: srcCount, dst: dstCount, status: 'PLAN' });
      continue;
    }

    // 实际迁移：清空目标 -> 导出源表 -> 导入目标（保留原 ID，不触发任何业务事件）
    // 跨实例（3306 -> 3307）无法直接 INSERT...SELECT，必须走 dump | import 管道
    sql('dst', 'SET FOREIGN_KEY_CHECKS=0; TRUNCATE TABLE ' + QT(cfg.db) + '.' + QT(table) + ';');
    pipeTable(table, cfg.db);

    const after = countIn('dst', table, cfg.db);
    const ok = after === srcCount;
    console.log('  ' + (ok ? '[OK]  ' : '[FAIL]') + ' ' + table.padEnd(24) + ' ' + srcCount + ' -> ' + after);
    totalRows += after;
    report.push({ service, table, src: srcCount, dst: after, status: ok ? 'OK' : 'FAIL' });
  }
  console.log('');
}

console.log('=============================================');
console.log(' 共迁移 ' + totalRows + ' 行');
const fails = report.filter(r => r.status === 'FAIL');
if (fails.length) {
  console.log(' 失败 ' + fails.length + ' 项:');
  fails.forEach(f => console.log('   ' + f.table + ': ' + f.src + ' -> ' + f.dst));
  process.exit(1);
}
console.log(' 全部成功');
console.log('=============================================');