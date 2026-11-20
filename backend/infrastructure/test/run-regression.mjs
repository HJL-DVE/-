#!/usr/bin/env node
/**
 * TeaClub 第六阶段 回归门禁
 *
 * 一条命令跑完全部经网关的端到端断言，任一脚本失败即整体失败（退出码 1）。
 * 只做编排与汇总，不重复实现断言——每个脚本自己打印 PASS/FAIL。
 *
 * 用法：
 *   node run-regression.mjs              # 全部
 *   node run-regression.mjs smoke        # 按标签过滤
 *   node run-regression.mjs 17 18        # 按序号过滤
 */
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const GW = 'http://127.0.0.1:9000';

/** 顺序有意义：先冒烟（最快暴露网关/契约问题），再跑资金闭环。 */
const SUITE = [
  { file: '17-phase6-smoke.mjs',              tag: 'smoke',    desc: '第六阶段冒烟（管理端/会员端/令牌隔离）' },
  { file: '18-recharge-pay-closed-loop.mjs',  tag: 'funds',    desc: '充值→支付→入账→幂等→下单→钱包支付' },
  { file: '19-refund-via-gateway.mjs',        tag: 'refund',   desc: '微信支付→退款→重复退款被拒→订单 REFUNDED' },
  { file: '20-payment-channel.mjs',           tag: 'channel',  desc: '支付渠道默认值/显式覆盖/非法拒绝/降级' },
  { file: '12-fund-invariants.mjs',           tag: 'invariant', desc: '资金不变量 I1–I6 + 流水链式衔接' },
  { file: '21-marketing-coupon.mjs',          tag: 'coupon',   desc: '优惠券领取/试算/锁定/核销/释放幂等' },
  { file: '22-marketing-points.mjs',          tag: 'points',   desc: '积分发放/签到幂等/重复投递不重发' },
  { file: '23-marketing-points-refund.mjs',   tag: 'pointsrf', desc: '退款按原流水冲正/重复退款不重冲' },
  { file: '24-nack-redelivery.mjs',           tag: 'dlq',      desc: '业务失败→DLQ→恢复→补偿重放→消费成功' },
  { file: '25-marketing-activity.mjs',        tag: 'activity', desc: '券模板CRUD/8类活动/满赠触发/积分商城/等级权益' },
  { file: '26-report.mjs',                    tag: 'report',   desc: '经营报表：总部/门店/日期/门店筛选/会员/储值/支付/退款/权限' },
  { file: '27-report-ext.mjs',                tag: 'report2',  desc: '报表扩展：每小时趋势/分类排行/商品趋势 + 越权复核' },
  { file: '28-member-level.mjs',              tag: 'level',    desc: '会员等级 CRUD 全链路（增改删回读+权限）' },
];

const filters = process.argv.slice(2).filter((a) => !a.startsWith('-'));
const picked = filters.length === 0
  ? SUITE
  : SUITE.filter((s) => filters.some((f) => s.tag === f || s.file.startsWith(f)));

if (picked.length === 0) {
  console.error('未匹配到任何脚本，可用过滤词：' + SUITE.map((s) => s.tag).join(', '));
  process.exit(2);
}

function waitGateway(timeoutMs = 60000) {
  const deadline = Date.now() + timeoutMs;
  return new Promise((resolve) => {
    const tick = async () => {
      try {
        const r = await fetch(GW + '/actuator/health');
        if (r.ok) return resolve(true);
      } catch { /* 未就绪，继续等 */ }
      if (Date.now() > deadline) return resolve(false);
      setTimeout(tick, 1500);
    };
    tick();
  });
}

function run(file) {
  return new Promise((resolve) => {
    const child = spawn(process.execPath, [file], { cwd: HERE, stdio: ['ignore', 'pipe', 'pipe'] });
    let out = '';
    child.stdout.on('data', (d) => { out += d.toString(); });
    child.stderr.on('data', (d) => { out += d.toString(); });
    child.on('close', (code) => {
      // 从脚本自己的输出里提取最后的汇总行
      const m = out.match(/汇总:\s*PASS=(\d+)\s+FAIL=(\d+)/);
      const skipped = /跳过/.test(out);
      resolve({ code, pass: m ? Number(m[1]) : 0, fail: m ? Number(m[2]) : 0, skipped, out });
    });
  });
}

const started = Date.now();
console.log('TeaClub 回归门禁');
console.log('网关: ' + GW);
console.log('脚本: ' + picked.length + ' 个\n');

const up = await waitGateway();
if (!up) {
  console.error('[致命] 网关 60 秒内未就绪，先确认容器：');
  console.error('  cd backend/infrastructure/docker');
  console.error('  docker compose -f docker-compose.yml --env-file .env.docker up -d');
  process.exit(2);
}
console.log('网关就绪\n');

const results = [];
for (const s of picked) {
  process.stdout.write('▶ ' + s.file + '  (' + s.desc + ')\n');
  const t0 = Date.now();
  const r = await run(s.file);
  const secs = ((Date.now() - t0) / 1000).toFixed(1);
  const ok = r.code === 0;
  results.push({ ...s, ...r, secs, ok });
  console.log('  ' + (ok ? '✔ 通过' : '✘ 失败') + '  PASS=' + r.pass + ' FAIL=' + r.fail + '  用时 ' + secs + 's\n');
  if (!ok) {
    // 失败时只回放该脚本的 FAIL 行与汇总，避免刷屏
    const lines = r.out.split(/\r?\n/).filter((l) => /\[FAIL\]|汇总|Error|ERR/.test(l));
    if (lines.length) console.log('  —— 失败明细 ——\n  ' + lines.slice(0, 20).join('\n  ') + '\n');
  }
}

const totalPass = results.reduce((a, r) => a + r.pass, 0);
const totalFail = results.reduce((a, r) => a + r.fail, 0);
const failed = results.filter((r) => !r.ok);

console.log('='.repeat(56));
console.log('脚本  通过 ' + (results.length - failed.length) + '/' + results.length);
console.log('断言  PASS=' + totalPass + '  FAIL=' + totalFail);
console.log('用时  ' + ((Date.now() - started) / 1000).toFixed(1) + 's');
console.log('='.repeat(56));

if (failed.length) {
  console.log('\n失败脚本：');
  failed.forEach((r) => console.log('  - ' + r.file + ' (exit=' + r.code + ')'));
  process.exit(1);
}
console.log('\n全部通过');
