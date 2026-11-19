
import fs from 'node:fs';
import { execFileSync } from 'node:child_process';
const ROOT='H:\\奶茶店会员与储值营销系统';
const env=fs.readFileSync(ROOT+'\\backend\\infrastructure\\docker\\.env.docker','utf8');
const V={}; env.split(/\r?\n/).forEach(l=>{const m=l.match(/^([A-Z_]+)=(.*)$/);if(m)V[m[1]]=m[2];});
const DOCKER='C:\\Program Files\\Docker\\Docker\\resources\\bin\\docker.exe';
const mysql=(db,sql)=>{try{return execFileSync(DOCKER,['exec','teaclub-mysql','mysql','-uroot','-p'+V.MYSQL_ROOT_PASSWORD,db,'-N','-B','-e',sql],{encoding:'utf8',stdio:['pipe','pipe','pipe']}).split('\n').filter(l=>l.trim()&&!l.includes('Warning'));}catch(e){return ['ERR'];}};
const redis=(...args)=>execFileSync(DOCKER,['exec','teaclub-redis','redis-cli',...args],{encoding:'utf8'}).trim();
let pass=0,fail=0; const chk=(n,ok,d)=>{if(ok){pass++;console.log('  [PASS] '+n+' '+d);}else{fail++;console.log('  [FAIL] '+n+' '+d);}};

const MGMT='http://127.0.0.1:15672';
const MQ_AUTH='Basic '+Buffer.from((V.RABBITMQ_DEFAULT_USER||'teaclub')+':'+V.RABBITMQ_DEFAULT_PASS).toString('base64');

const row = mysql('order_db',"SELECT biz_no, event_id, payload FROM sys_outbox_message WHERE event_type='ORDER_PAID' ORDER BY created_at DESC LIMIT 1")[0];
if(!row) { console.log('无 ORDER_PAID 事件，请先运行 15-order-paid-consumer.mjs'); process.exit(1); }
const [bizNo, eventId] = row.split('\t');
const payload = row.split('\t').slice(2).join('\t');
const envelope = JSON.parse(payload);
console.log('目标事件 eventId='+eventId+' bizNo='+bizNo);

const stat=()=>Number(mysql('member_db',"SELECT total_consume_amount FROM mem_member WHERE id=1")[0]||'0');
const cnt=()=>Number(mysql('member_db',"SELECT total_order_count FROM mem_member WHERE id=1")[0]||'0');
const before=stat(), beforeCnt=cnt();
console.log('当前累计消费='+before+' 订单数='+beforeCnt);

console.log('');
console.log('=== 场景 A：清空 Redis 幂等键，验证第二层（DB Inbox）兜底 ===');
const del = redis('DEL','mq:consumed:'+eventId);
console.log('  Redis DEL 结果='+del);
chk('Redis 键已清除',redis('EXISTS','mq:consumed:'+eventId)==='0','EXISTS='+redis('EXISTS','mq:consumed:'+eventId));

const pubBody={
  properties:{ headers:{ 'x-event-id': eventId, 'x-event-type':'ORDER_PAID' }, delivery_mode:2, content_type:'application/json' },
  routing_key:'order.paid',
  payload: JSON.stringify(envelope.event),
  payload_encoding:'string'
};
const pr=await fetch(MGMT+'/api/exchanges/%2Fteaclub/teaclub.business.exchange/publish',{method:'POST',headers:{'Content-Type':'application/json',Authorization:MQ_AUTH},body:JSON.stringify(pubBody)});
const pj=await pr.json();
chk('消息已投递',pj.routed===true,'routed='+pj.routed);

await new Promise(x=>setTimeout(x,6000));
const after=stat(), afterCnt=cnt();
chk('数据库 Inbox 拦截了重复累加',after===before&&afterCnt===beforeCnt,'累计消费 '+before+' -> '+after+'，订单数 '+beforeCnt+' -> '+afterCnt);
chk('Inbox 仍只有 1 条记录',mysql('member_db',"SELECT COUNT(*) FROM sys_inbox_message WHERE event_id='"+eventId+"'")[0]==='1','count='+mysql('member_db',"SELECT COUNT(*) FROM sys_inbox_message WHERE event_id='"+eventId+"'")[0]);

console.log('');
console.log('=== 场景 B：Redis 键已被消费者重新写回（第一层恢复） ===');
const ttl=redis('TTL','mq:consumed:'+eventId);
chk('Redis 幂等键已重新建立',ttl!=='-2','TTL='+ttl+' 秒');

console.log('');
console.log('=== 汇总: PASS='+pass+' FAIL='+fail+' ===');
if(fail>0) process.exitCode=1;
