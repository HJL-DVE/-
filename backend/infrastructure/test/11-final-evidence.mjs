
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
const ROOT='H:\\奶茶店会员与储值营销系统';
const env=fs.readFileSync(ROOT+'\\backend\\infrastructure\\docker\\.env.docker','utf8');
const V={}; env.split(/\r?\n/).forEach(l=>{const m=l.match(/^([A-Z_]+)=(.*)$/);if(m)V[m[1]]=m[2];});
const DOCKER='C:\\Program Files\\Docker\\Docker\\resources\\bin\\docker.exe';

const rc=await (await fetch('http://127.0.0.1:9004/api/wallet/internal/reconcile',{headers:{'X-Internal-Token':V.INTERNAL_TOKEN}})).json();
console.log('对账 passed='+rc.data.passed+' wallets='+rc.data.walletCount+' ledgers='+rc.data.ledgerCount+' issues='+rc.data.issueCount);

// 四条核心链路各取一条真实凭证
const q=(db,sql)=>execFileSync(DOCKER,['exec','teaclub-mysql','mysql','-uroot','-p'+V.MYSQL_ROOT_PASSWORD,db,'-N','-B','-e',sql],{encoding:'utf8'}).split('\n').filter(l=>l.trim()&&!l.includes('Warning'));
console.log('');
console.log('【充值链路】'+q('wallet_db',"SELECT CONCAT(recharge_no,' ',status,' 实付',recharge_amount,' 赠',gift_amount) FROM wal_recharge_order WHERE status='PAID' ORDER BY id DESC LIMIT 1"));
console.log('【消费链路】'+q('wallet_db',"SELECT CONCAT(biz_no,' 实付变动',principal_change,' 赠送变动',gift_change,' 前',principal_before,'->后',principal_after) FROM wal_wallet_transaction WHERE biz_type='CONSUME_OUT' ORDER BY id DESC LIMIT 1"));
console.log('【支付链路】'+q('payment_db',"SELECT CONCAT(payment_no,' ',status,' ',amount,' ',provider) FROM pay_payment_order WHERE biz_type='ORDER' AND status='SUCCESS' ORDER BY id DESC LIMIT 1"));
console.log('【退款链路】'+q('payment_db',"SELECT CONCAT(refund_no,' ',status,' ',refund_amount) FROM pay_refund_order WHERE status='SUCCESS' ORDER BY id DESC LIMIT 1"));
console.log('【Saga补偿】'+q('order_db',"SELECT CONCAT(order_no,' ',step,' ',status) FROM oms_payment_saga WHERE status='COMPENSATED' ORDER BY id DESC LIMIT 1"));
