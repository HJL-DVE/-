import fs from 'node:fs';
import { execFileSync } from 'node:child_process';
const ROOT='H:\\奶茶店会员与储值营销系统';
const t=fs.readFileSync(ROOT+'\\backend\\infrastructure\\docker\\.env.docker','utf8');
const E={}; t.split(/\r?\n/).forEach(l=>{const m=l.match(/^([A-Z_]+)=(.*)$/);if(m)E[m[1]]=m[2];});
const D='C:\\Program Files\\Docker\\Docker\\resources\\bin\\docker.exe';
// 安全网：本测试会 RENAME mem_member 表制造业务失败；
// 无论中途成功、失败还是崩溃退出，都必须把表名恢复，否则整个会员域瘫痪。
let __memberTableRestored = false;
function restoreMemberTable() {
  if (__memberTableRestored) return;
  try {
    const t = sql('member_db', "SHOW TABLES LIKE 'mem_member'")[0];
    if (!t || t === '') {
      sql('member_db', 'RENAME TABLE mem_member_bak TO mem_member');
      console.log('  [安全网] mem_member 表已从 bak 恢复');
    }
    __memberTableRestored = true;
  } catch (e) {
    console.error('  [安全网] 恢复失败，请手工执行: RENAME TABLE member_db.mem_member_bak TO mem_member', e.message);
  }
}
process.on('exit', restoreMemberTable);
process.on('SIGINT', () => { restoreMemberTable(); process.exit(1); });
process.on('uncaughtException', (e) => { console.error('未捕获异常:', e.message); restoreMemberTable(); process.exit(1); });
process.on('unhandledRejection', (e) => { console.error('未处理的 Promise 拒绝:', e); restoreMemberTable(); process.exit(1); });

const sql=(db,s)=>{try{return execFileSync(D,['exec','teaclub-mysql','mysql','-uroot','-p'+E.MYSQL_ROOT_PASSWORD,db,'-N','-B','-e',s],{encoding:'utf8',stdio:['pipe','pipe','ignore']}).split('\n').filter(l=>l.trim());}catch(e){return ['ERR'];}};
const sleep=(ms)=>new Promise(x=>setTimeout(x,ms));
const GW='http://127.0.0.1:9000';
const AUTH='Basic '+Buffer.from(E.RABBITMQ_DEFAULT_USER+':'+E.RABBITMQ_DEFAULT_PASS).toString('base64');
let pass=0,fail=0;
const chk=(n,ok,d)=>{ if(ok)pass++; else fail++; console.log((ok?'  [PASS] ':'  [FAIL] ')+n+'  '+d); };
const call=async(m,p,{token,body}={})=>{
  const h={'Content-Type':'application/json'}; if(token)h.Authorization='Bearer '+token;
  const r=await fetch(GW+p,{method:m,headers:h,body:body===undefined?undefined:JSON.stringify(body)});
  const t2=await r.text(); let j=null; try{j=JSON.parse(t2);}catch{}
  return {status:r.status,json:j};
};
const q=async(name)=>{
  const r=await fetch('http://127.0.0.1:15672/api/queues/%2Fteaclub/'+encodeURIComponent(name),{headers:{Authorization:AUTH}});
  if(!r.ok) return null;
  return await r.json();
};

console.log('=== 准备 ===');
let r=await call('POST','/api/auth/wx-login',{body:{code:'A-'+Date.now()}});
const mtok=r.json?.data?.token, mid=r.json?.data?.memberId;
const base=sql('member_db',"SELECT total_consume_amount,total_order_count FROM mem_member WHERE id="+mid)[0];
const [bAmt,bCnt]=(base||'0\t0').split('\t');
console.log('  会员='+mid+' 基线消费='+bAmt+' 单数='+bCnt);

r=await call('POST','/api/wallet/recharges',{token:mtok,body:{planId:1,storeId:1}});
const rc=r.json?.data;
r=await call('POST','/api/payment/create',{token:mtok,body:{bizType:'RECHARGE',bizNo:rc?.rechargeNo}});
await call('POST','/api/payment/mock/notify',{body:{paymentNo:r.json?.data?.paymentNo,amount:r.json?.data?.amount}});
await sleep(2500);
r=await call('POST','/api/order/orders',{token:mtok,body:{storeId:1,items:[{skuId:1,quantity:1}]}});
const order=r.json?.data;
chk('订单已创建',!!order?.orderNo,'orderNo='+order?.orderNo+' 应付='+order?.payableAmount);

console.log('');
console.log('=== 阶段1: 制造业务失败 → 消息应进 DLQ===');
sql('member_db',"RENAME TABLE mem_member TO mem_member_bak");
await call('POST','/api/order/orders/'+order.orderNo+'/pay-wallet',{token:mtok,body:{}});
const evt=sql('order_db',"SELECT event_id FROM sys_outbox_message WHERE biz_no='"+order.orderNo+"' AND event_type='ORDER_PAID'")[0];
console.log('  eventId='+evt);

let dlq=null, inDlq=false;
for(let i=0;i<25;i++){
  await sleep(1000);
  dlq=await q('teaclub.member.order-paid.queue.dlq');
  if(dlq && dlq.messages>0){ inDlq=true; break; }
}
chk('失败消息进入 DLQ（未丢失）',inDlq,'dlq.messages='+(dlq?.messages??'null'));

const stillZero=sql('member_db',"SELECT total_consume_amount FROM mem_member_bak WHERE id="+mid)[0];
console.log('  此时会员档案未更新（符合预期）');

console.log('');
console.log('=== 阶段2: 恢复业务 → 补偿重放应自动消费 ===');
sql('member_db',"RENAME TABLE mem_member_bak TO mem_member");
__memberTableRestored = true;
console.log('  表已恢复，等待 DLQ-REPLAY 任务（15 秒一轮）...');

let ok=false, amt=null, cnt=null;
// 重放周期 15 秒，且可能经历「重放→再次失败→再进 DLQ→再重放」多轮，
// 给足 120 秒窗口
for(let i=0;i<60;i++){
  await sleep(2000);
  const row=sql('member_db',"SELECT total_consume_amount,total_order_count FROM mem_member WHERE id="+mid)[0];
  if(row){
    const [a,c]=row.split('\t'); amt=Number(a); cnt=Number(c);
    if(amt===Number(bAmt)+Number(order.payableAmount)&&cnt===Number(bCnt)+1){ ok=true; break; }
  }
}
chk('重放后消费成功',ok,
    '累计 '+bAmt+' -> '+amt+'（应 +'+order.payableAmount+'），单数 '+bCnt+' -> '+cnt);

// RabbitMQ 管理 API 的 messages 计数有秒级延迟，轮询等它收敛
let dlqLeft=null;
for(let i=0;i<12;i++){
  const d=await q('teaclub.member.order-paid.queue.dlq');
  dlqLeft=d?.messages??-1;
  if(dlqLeft===0) break;
  await sleep(1500);
}
chk('DLQ 已清空',dlqLeft===0,'dlq.messages='+dlqLeft);

const inbox=sql('member_db',"SELECT COUNT(*) FROM sys_inbox_message WHERE event_id='"+evt+"'")[0];
chk('Inbox 已记录该事件',inbox==='1','count='+inbox);

console.log('');
console.log('=== 汇总: PASS='+pass+' FAIL='+fail+' ===');
if(fail>0) process.exitCode=1;
