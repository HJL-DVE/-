import fs from 'node:fs';
import { execFileSync } from 'node:child_process';
const ROOT='H:\\奶茶店会员与储值营销系统';
const envTxt=fs.readFileSync(ROOT+'\\backend\\infrastructure\\docker\\.env.docker','utf8');
const ENV={}; envTxt.split(/\r?\n/).forEach(l=>{const m=l.match(/^([A-Z_]+)=(.*)$/);if(m)ENV[m[1]]=m[2];});
const DOCKER='C:\\Program Files\\Docker\\Docker\\resources\\bin\\docker.exe';
const mysql=(db,sql)=>{try{return execFileSync(DOCKER,['exec','teaclub-mysql','mysql','-uroot','-p'+ENV.MYSQL_ROOT_PASSWORD,db,'-N','-B','-e',sql],{encoding:'utf8'}).split('\n').filter(l=>l.trim()&&!l.includes('Warning'));}catch(e){return ['ERR '+((e.stderr||'')+'').slice(0,80)];}};
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

console.log('=== 0. 会员登录 ===');
let r=await call('POST','/api/auth/wx-login',{body:{code:'rf-'+Date.now()}});
const mtok=r.json?.data?.token, mid=r.json?.data?.memberId;
chk('登录',!!mtok,'memberId='+mid);

console.log('');
console.log('=== 1. 签到（验证 V5 规则生效）===');
r=await call('POST','/api/marketing/points/sign-in',{token:mtok});
const s=r.json?.data;
chk('签到得积分 > 0',(s?.points||0)>0,'获得='+s?.points+' 连续='+s?.continuousDays+' 总积分='+s?.totalPoints);
const afterSign=s?.totalPoints||0;

console.log('');
console.log('=== 2. 充值 → 下单 → 钱包支付 → 发积分 ===');
r=await call('POST','/api/wallet/recharges',{token:mtok,body:{planId:1,storeId:1}});
const rc=r.json?.data;
r=await call('POST','/api/payment/create',{token:mtok,body:{bizType:'RECHARGE',bizNo:rc?.rechargeNo}});
const pay=r.json?.data;
await call('POST','/api/payment/mock/notify',{body:{paymentNo:pay?.paymentNo,amount:pay?.amount}});
await sleep(2500);

// 下一笔稍贵的单，保证积分 >= 2 以便观察部分冲正
r=await call('POST','/api/order/orders',{token:mtok,body:{storeId:1,items:[{skuId:1,quantity:2}]}});
const order=r.json?.data;
chk('订单创建',r.json?.code===0,'orderNo='+order?.orderNo+' 应付='+order?.payableAmount);
r=await call('POST','/api/order/orders/'+order?.orderNo+'/pay-wallet',{token:mtok,body:{}});
chk('钱包支付',r.json?.data?.status==='PAID','ok');

let pts=afterSign;
for(let i=0;i<15;i++){
  await sleep(1000);
  r=await call('GET','/api/marketing/points/account',{token:mtok});
  pts=r.json?.data?.totalPoints;
  if(pts>afterSign) break;
}
const earned=pts-afterSign;
chk('消费积分已发放',earned>0,'消费得 '+earned+' 总积分='+pts);

console.log('');
console.log('=== 3. 申请退款 → 退款回调 → 积分冲正 ===');
r=await call('POST','/api/admin/auth/login',{body:{username:'admin',password:'TeaClub@123'}});
const adminTok=r.json?.data?.accessToken;
r=await call('POST','/api/admin/payment/refunds',{token:adminTok,body:{orderNo:order?.orderNo,refundAmount:order?.payableAmount,reason:'积分冲正测试'}});
const refund=r.json?.data;
chk('退款单创建',r.json?.code===0,'refundNo='+refund?.refundNo);
r=await call('POST','/api/payment/mock/refund-notify',{body:{refundNo:refund?.refundNo}});
chk('退款回调受理',r.json?.code===0,'code='+r.json?.code);

// 关键：退款只冲正「消费积分（ORDER_EARN）」，不冲正活动奖励积分。
// 因此预期扣减额必须取该订单 ORDER_EARN 流水的实际值，
// 不能用「订单前后的总积分差」——那样会把同单触发的活动积分也算进去。
const orderEarnRow=mysql('marketing_db',"SELECT change_points FROM mkt_points_transaction WHERE member_id="+mid+" AND biz_type='ORDER_EARN' AND biz_no='"+order?.orderNo+"'")[0];
const orderEarn=Number(orderEarnRow||0);
let after=pts;
for(let i=0;i<20;i++){
  await sleep(1000);
  r=await call('GET','/api/marketing/points/account',{token:mtok});
  after=r.json?.data?.totalPoints;
  if(after<pts) break;
}
chk('积分已按原额冲正',after===pts-orderEarn,
    '冲正前='+pts+' 冲正后='+after+'（应扣该单消费积分 '+orderEarn+'，活动奖励不冲正）');

const rev=mysql('marketing_db',"SELECT biz_type,change_points FROM mkt_points_transaction WHERE member_id="+mid+" AND biz_type='ORDER_REFUND_REVERSE'");
chk('冲正流水存在且为负',rev.length===1&&rev[0].includes('-'),'流水='+rev.join(' | '));
const orig=mysql('marketing_db',"SELECT change_points FROM mkt_points_transaction WHERE member_id="+mid+" AND biz_type='ORDER_EARN' AND biz_no='"+order?.orderNo+"'")[0];
chk('冲正额 == 原发放额',
   rev.length===1&&orig&&Math.abs(Number(rev[0].split('\t')[1]))===Number(orig),
   '原='+orig+' 冲正='+(rev[0]||'').split('\t')[1]);

console.log('');
console.log('=== 4. 重复退款回调（不重复冲正）===');
await call('POST','/api/payment/mock/refund-notify',{body:{refundNo:refund?.refundNo}});
await sleep(4000);
r=await call('GET','/api/marketing/points/account',{token:mtok});
chk('重复退款未重复冲正',r.json?.data?.totalPoints===after,'余额='+r.json?.data?.totalPoints);
const revCnt=mysql('marketing_db',"SELECT COUNT(*) FROM mkt_points_transaction WHERE member_id="+mid+" AND biz_type='ORDER_REFUND_REVERSE'")[0];
chk('冲正流水仍为 1 条',revCnt==='1','count='+revCnt);

console.log('');
console.log('=== 5. 账户守恒校验 ===');
const acc=mysql('marketing_db',"SELECT total_points,total_earned,total_consumed,total_reversed FROM mkt_points_account WHERE member_id="+mid)[0];
const [tp,te,tc,tr]=(acc||'').split('\t').map(Number);
const sum=mysql('marketing_db',"SELECT COALESCE(SUM(change_points),0) FROM mkt_points_transaction WHERE member_id="+mid)[0];
chk('余额 == 流水累计',Number(sum)===tp,'余额='+tp+' 流水累计='+sum);
chk('余额 == 获得 - 冲正',tp===te-tr,'获得='+te+' 冲正='+tr+' 余额='+tp);

console.log('');
console.log('=== 汇总: PASS='+pass+' FAIL='+fail+' ===');
if(fail>0) process.exitCode=1;
