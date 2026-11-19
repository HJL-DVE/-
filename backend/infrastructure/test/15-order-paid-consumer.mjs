
import fs from 'node:fs';
import { execFileSync } from 'node:child_process';
const ROOT='H:\\奶茶店会员与储值营销系统';
const env=fs.readFileSync(ROOT+'\\backend\\infrastructure\\docker\\.env.docker','utf8');
const V={}; env.split(/\r?\n/).forEach(l=>{const m=l.match(/^([A-Z_]+)=(.*)$/);if(m)V[m[1]]=m[2];});
const DOCKER='C:\\Program Files\\Docker\\Docker\\resources\\bin\\docker.exe';
const mysql=(db,sql)=>{try{return execFileSync(DOCKER,['exec','teaclub-mysql','mysql','-uroot','-p'+V.MYSQL_ROOT_PASSWORD,db,'-N','-B','-e',sql],{encoding:'utf8',stdio:['pipe','pipe','pipe']}).split('\n').filter(l=>l.trim()&&!l.includes('Warning'));}catch(e){return ['ERR '+((e.stderr||'')+'').slice(0,100)];}};

const GW='http://127.0.0.1:9000', AUTH='http://127.0.0.1:9001';
const MGMT='http://127.0.0.1:15672';
const MQ_USER=V.RABBITMQ_DEFAULT_USER||'teaclub', MQ_PASS=V.RABBITMQ_DEFAULT_PASS;
const MQ_AUTH='Basic '+Buffer.from(MQ_USER+':'+MQ_PASS).toString('base64');

const lr=await fetch(AUTH+'/api/admin/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'admin',password:'TeaClub@123'})});
const tok=(await lr.json()).data.accessToken; const H={Authorization:'Bearer '+tok,'Content-Type':'application/json'};
let pass=0,fail=0; const chk=(n,ok,d)=>{if(ok){pass++;console.log('  [PASS] '+n+' '+d);}else{fail++;console.log('  [FAIL] '+n+' '+d);}};

const MEMBER=1;
const stat=()=>{
  const r=mysql('member_db',"SELECT total_consume_amount,total_order_count FROM mem_member WHERE id="+MEMBER)[0]||'';
  const [amt,cnt]=r.split('\t');
  return {amt:Number(amt),cnt:Number(cnt)};
};

console.log('=== 0. 源码校验：同步 Feign 累加已移除 ===');
{
  const svc=ROOT+'\\backend\\teaclub-cloud\\order-service\\src\\main\\java\\com\\teaclub\\cloud\\order\\service\\impl\\OrderServiceImpl.java';
  const cli=ROOT+'\\backend\\teaclub-cloud\\order-service\\src\\main\\java\\com\\teaclub\\cloud\\order\\client\\MemberInternalClient.java';
  const s=fs.readFileSync(svc,'utf8'), c=fs.readFileSync(cli,'utf8');
  chk('OrderServiceImpl 无 updateMemberStats 调用',!s.includes('updateMemberStats'),'grep 结果=0');
  chk('MemberInternalClient 无 updateConsumeStats 声明',!c.includes('updateConsumeStats'),'grep 结果=0');
}

console.log('');
console.log('=== 1. 清空会员消费队列（保证基线干净）===');
{
  const pr=await fetch(MGMT+'/api/queues/%2Fteaclub/teaclub.member.order-paid.queue/contents',{method:'DELETE',headers:{Authorization:MQ_AUTH}});
  console.log('  purge status='+pr.status);
}

console.log('=== 2. 事件驱动累加会员消费档案 ===');
const before=stat();
console.log('  支付前: 累计消费='+before.amt+' 订单数='+before.cnt);

let r=await fetch(GW+'/api/admin/order/orders',{method:'POST',headers:H,body:JSON.stringify({storeId:1,memberId:MEMBER,items:[{skuId:1,quantity:1}]})});
let j=await r.json(); const orderNo=j.data.orderNo, payable=j.data.payableAmount;
r=await fetch(GW+'/api/admin/order/orders/'+orderNo+'/pay-balance',{method:'POST',headers:H,body:'{}'});
j=await r.json();
chk('余额支付成功',j.code===0&&j.data.status==='PAID','orderNo='+orderNo+' 应付='+payable);

let after=before, waited=0;
for(let i=0;i<15;i++){
  await new Promise(x=>setTimeout(x,1000)); waited++;
  after=stat();
  if(after.amt===before.amt+payable && after.cnt===before.cnt+1) break;
}
chk('消费档案由事件累加',after.amt===before.amt+payable,'累计消费 '+before.amt+' -> '+after.amt+'（应 +'+payable+'）');
chk('订单数 +1',after.cnt===before.cnt+1,'订单数 '+before.cnt+' -> '+after.cnt);
console.log('  端到端时延约 '+waited+' 秒（Outbox 1s 轮询 + MQ 投递 + 消费）');

console.log('');
console.log('=== 3. Inbox 幂等记录已落库 ===');
const evRow=mysql('order_db',"SELECT event_id FROM sys_outbox_message WHERE biz_no='"+orderNo+"' AND event_type='ORDER_PAID'")[0];
chk('订单事件已生成',!!evRow,'eventId='+evRow);
const inboxRow=mysql('member_db',"SELECT event_id,event_type FROM sys_inbox_message WHERE event_id='"+evRow+"'");
chk('Inbox 已认领该事件',inboxRow.length===1,'inbox='+(inboxRow.join(' | ')||'(无)'));

console.log('');
console.log('=== 4. 重复投递（同一 eventId 再发一次）===');
const payloadEnv=mysql('order_db',"SELECT payload FROM sys_outbox_message WHERE biz_no='"+orderNo+"' AND event_type='ORDER_PAID'")[0];
const envelope=JSON.parse(payloadEnv);
const eventJson=JSON.stringify(envelope.event);
const mid=stat();
console.log('  重复投递前: 累计消费='+mid.amt+' 订单数='+mid.cnt);

const pubBody={
  properties:{ headers:{ 'x-event-id': envelope.event.eventId, 'x-event-type':'ORDER_PAID' }, delivery_mode:2, content_type:'application/json' },
  routing_key:'order.paid',
  payload: eventJson,
  payload_encoding:'string'
};
async function publish(body){
  const res=await fetch(MGMT+'/api/exchanges/%2Fteaclub/teaclub.business.exchange/publish',{method:'POST',headers:{'Content-Type':'application/json',Authorization:MQ_AUTH},body:JSON.stringify(body)});
  const txt=await res.text();
  let j=null; try{ j=JSON.parse(txt);}catch{}
  return {status:res.status, json:j, txt:txt.slice(0,120)};
}
const pr=await publish(pubBody);
chk('重复消息已成功投递到 Broker',pr.status===200&&pr.json&&pr.json.routed===true,
    'status='+pr.status+' '+(pr.json?('routed='+pr.json.routed):pr.txt));

await new Promise(x=>setTimeout(x,6000));
const afterDup=stat();
chk('重复投递未重复累加',afterDup.amt===mid.amt&&afterDup.cnt===mid.cnt,
    '累计 '+mid.amt+' -> '+afterDup.amt+'，订单数 '+mid.cnt+' -> '+afterDup.cnt);

console.log('');
console.log('=== 5. 连发 5 次重复投递 ===');
for(let i=0;i<5;i++){ await publish(pubBody); }
await new Promise(x=>setTimeout(x,8000));
const afterMany=stat();
chk('5 次重复投递仍未重复累加',afterMany.amt===mid.amt&&afterMany.cnt===mid.cnt,'累计='+afterMany.amt+'（期望 '+mid.amt+'）');

console.log('');
console.log('=== 6. 队列与消费端状态 ===');
try{
  const q=await (await fetch(MGMT+'/api/queues/%2Fteaclub/teaclub.member.order-paid.queue',{headers:{Authorization:MQ_AUTH}})).json();
  chk('会员消费队列存在',!!q.name,'messages='+q.messages+' consumers='+q.consumers);
  chk('队列已积压清零',q.messages===0,'messages='+q.messages);
  chk('存在活跃消费者',q.consumers>=1,'consumers='+q.consumers);
  const dlq=await (await fetch(MGMT+'/api/queues/%2Fteaclub/teaclub.member.order-paid.queue.dlq',{headers:{Authorization:MQ_AUTH}})).json();
  chk('死信队列为空',dlq.messages===0,'dlq.messages='+dlq.messages);
}catch(e){ chk('队列查询',false,e.message); }

console.log('');
console.log('=== 汇总: PASS='+pass+' FAIL='+fail+' ===');
if(fail>0) process.exitCode=1;
