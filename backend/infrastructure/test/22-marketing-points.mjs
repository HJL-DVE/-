import fs from 'node:fs';
const ROOT='H:\\奶茶店会员与储值营销系统';
const envTxt=fs.readFileSync(ROOT+'\\backend\\infrastructure\\docker\\.env.docker','utf8');
const ENV={}; envTxt.split(/\r?\n/).forEach(l=>{const m=l.match(/^([A-Z_]+)=(.*)$/);if(m)ENV[m[1]]=m[2];});
import { execFileSync } from 'node:child_process';
const DOCKER='C:\\Program Files\\Docker\\Docker\\resources\\bin\\docker.exe';
const mysql=(db,sql)=>{try{return execFileSync(DOCKER,['exec','teaclub-mysql','mysql','-uroot','-p'+ENV.MYSQL_ROOT_PASSWORD,db,'-N','-B','-e',sql],{encoding:'utf8'}).split('\n').filter(l=>l.trim()&&!l.includes('Warning'));}catch(e){return ['ERR'];}};

const GW='http://127.0.0.1:9000';
let pass=0,fail=0;
const chk=(n,ok,d)=>{ if(ok)pass++; else fail++; console.log((ok?'  [PASS] ':'  [FAIL] ')+n+'  '+d); };
const call=async(m,p,{token,body}={})=>{
  const h={'Content-Type':'application/json'};
  if(token) h.Authorization='Bearer '+token;
  const r=await fetch(GW+p,{method:m,headers:h,body:body===undefined?undefined:JSON.stringify(body)});
  const t=await r.text(); let j=null; try{j=JSON.parse(t);}catch{}
  return {status:r.status,json:j,text:t.slice(0,200)};
};
const sleep=(ms)=>new Promise(x=>setTimeout(x,ms));

console.log('=== 0. 登录 ===');
let r=await call('POST','/api/auth/wx-login',{body:{code:'pts-'+Date.now()}});
const mtok=r.json?.data?.token, mid=r.json?.data?.memberId;
chk('会员登录',!!mtok,'memberId='+mid);

console.log('');
console.log('=== 1. 积分账户初始为 0 ===');
r=await call('GET','/api/marketing/points/account',{token:mtok});
chk('账户查询',r.json?.code===0,'积分='+r.json?.data?.totalPoints);

console.log('');
console.log('=== 2. 签到（首签）===');
r=await call('POST','/api/marketing/points/sign-in',{token:mtok});
const s1=r.json?.data;
chk('签到成功',r.json?.code===0&&s1?.firstTime===true,'获得='+s1?.points+' 连续='+s1?.continuousDays+' 总积分='+s1?.totalPoints);
const signPoints=s1?.points||0;
await sleep(300);
r=await call('POST','/api/marketing/points/sign-in',{token:mtok});
const s2=r.json?.data;
chk('重复签到幂等',s2?.firstTime===false,'获得='+s2?.points+' 提示='+s2?.bonusRemark);

console.log('');
console.log('=== 3. 下单并钱包支付 → 触发 ORDER_PAID → 发积分 ===');
r=await call('POST','/api/admin/auth/login',{body:{username:'admin',password:'TeaClub@123'}});
const adminTok=r.json?.data?.accessToken;
// 给会员充值以便钱包支付
r=await call('POST','/api/wallet/recharges',{token:mtok,body:{planId:1,storeId:1}});
const rc=r.json?.data;
r=await call('POST','/api/payment/create',{token:mtok,body:{bizType:'RECHARGE',bizNo:rc?.rechargeNo}});
const pay=r.json?.data;
await call('POST','/api/payment/mock/notify',{body:{paymentNo:pay?.paymentNo,amount:pay?.amount}});
await sleep(2500);
r=await call('GET','/api/wallet/account',{token:mtok});
chk('充值到账（用于支付）',r.json?.code===0,'余额='+r.json?.data?.principalBalance);

r=await call('POST','/api/order/quote',{token:mtok,body:{storeId:1,items:[{skuId:1,quantity:1}]}});
const quote=r.json?.data;
r=await call('POST','/api/order/orders',{token:mtok,body:{storeId:1,items:[{skuId:1,quantity:1}]}});
const order=r.json?.data;
chk('订单创建',r.json?.code===0,'orderNo='+order?.orderNo+' 应付='+order?.payableAmount);
r=await call('POST','/api/order/orders/'+order?.orderNo+'/pay-wallet',{token:mtok,body:{}});
chk('钱包支付成功',r.json?.data?.status==='PAID','status='+r.json?.data?.status);

console.log('');
console.log('=== 4. 等待 MQ 消费发积分 ===');
let pts=null;
for(let i=0;i<15;i++){
  await sleep(1000);
  r=await call('GET','/api/marketing/points/account',{token:mtok});
  pts=r.json?.data?.totalPoints;
  if(pts>signPoints) break;
}
const expectedEarn=Math.floor((order?.payableAmount||0)/100);
// 断言"本次消费的积分确实到账"，不要用 signPoints 做基准
// （签到可能因规则缺失/并发返回 0，与本用例无关）
chk('消费积分已发放',(pts||0)>=expectedEarn&&expectedEarn>0,
    '消费得'+expectedEarn+' 当前总积分='+pts);
const txn=mysql('marketing_db',"SELECT biz_type,change_points FROM mkt_points_transaction WHERE member_id="+mid+" ORDER BY id");
chk('流水含 ORDER_EARN',txn.some(l=>l.includes('ORDER_EARN')),'流水='+txn.join(' | '));

console.log('');
console.log('=== 5. 重复投递同一 ORDER_PAID 事件（不重复发积分）===');
const before=pts;
const evRow=mysql('order_db',"SELECT event_id,payload FROM sys_outbox_message WHERE biz_no='"+order?.orderNo+"' AND event_type='ORDER_PAID'")[0];
if(evRow){
  const [evId]=evRow.split('\t');
  const payload=evRow.split('\t').slice(1).join('\t');
  const envelope=JSON.parse(payload);
  const auth='Basic '+Buffer.from(ENV.RABBITMQ_DEFAULT_USER+':'+ENV.RABBITMQ_DEFAULT_PASS).toString('base64');
  const body={
    properties:{headers:{'x-event-id':evId,'x-event-type':'ORDER_PAID'},delivery_mode:2,content_type:'application/json'},
    routing_key:'order.paid',payload:JSON.stringify(envelope.event),payload_encoding:'string'
  };
  const pr=await fetch('http://127.0.0.1:15672/api/exchanges/%2Fteaclub/teaclub.business.exchange/publish',{method:'POST',headers:{'Content-Type':'application/json',Authorization:auth},body:JSON.stringify(body)});
  const pj=await pr.json();
  chk('重复事件已投递',pj.routed===true,'routed='+pj.routed);
  await sleep(6000);
  r=await call('GET','/api/marketing/points/account',{token:mtok});
  chk('积分未重复发放',r.json?.data?.totalPoints===before,'重复前='+before+' 重复后='+r.json?.data?.totalPoints);
  const cnt=mysql('marketing_db',"SELECT COUNT(*) FROM mkt_points_transaction WHERE biz_type='ORDER_EARN' AND biz_no='"+order?.orderNo+"'")[0];
  chk('ORDER_EARN 流水仍为 1 条',cnt==='1','count='+cnt);
} else {
  chk('找到 ORDER_PAID 事件',false,'未找到');
}

console.log('');
console.log('=== 汇总: PASS='+pass+' FAIL='+fail+' ===');
console.log('memberId='+mid+' orderNo='+order?.orderNo);
if(fail>0) process.exitCode=1;
