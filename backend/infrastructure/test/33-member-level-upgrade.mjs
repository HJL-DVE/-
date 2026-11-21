import fs from 'node:fs';
const ROOT='H:\\奶茶店会员与储值营销系统';
const t=fs.readFileSync(ROOT+'\\backend\\infrastructure\\docker\\.env.docker','utf8');
const E={}; t.split(/\r?\n/).forEach(l=>{const m=l.match(/^([A-Z_]+)=(.*)$/);if(m)E[m[1]]=m[2];});
const GW='http://127.0.0.1:9000';
let pass=0,fail=0;
const chk=(n,ok,d)=>{ if(ok)pass++; else fail++; console.log((ok?'  [PASS] ':'  [FAIL] ')+n+'  '+d); };
const sleep=(ms)=>new Promise(x=>setTimeout(x,ms));
const req=async(m,p,{token,body}={})=>{
  const h={'Content-Type':'application/json'}; if(token)h.Authorization='Bearer '+token;
  const r=await fetch(GW+p,{method:m,headers:h,body:body===undefined?undefined:JSON.stringify(body)});
  try{return await r.json();}catch{return {}};
};
console.log('=== 会员等级自动升级测试 ===');
const lg=await req('POST','/api/auth/wx-login',{body:{code:'lvlup-'+Date.now()}});
const tok=lg.data?.token, mid=lg.data?.memberId;
let prof=await req('GET','/api/member/profile',{token:tok});
chk('新会员初始为普通等级',prof.data?.levelId===1,'level='+prof.data?.levelName);

const plans=(await req('GET','/api/wallet/plans',{token:tok})).data||[];
const plan=plans.find(p=>p.rechargeAmount>=50000)||plans[plans.length-1];
const rc=(await req('POST','/api/wallet/recharges',{token:tok,body:{planId:plan.id,storeId:1}})).data;
const pay=(await req('POST','/api/payment/create',{token:tok,body:{bizType:'RECHARGE',bizNo:rc.rechargeNo}})).data;
await req('POST','/api/payment/mock/notify',{body:{paymentNo:pay.paymentNo,amount:pay.amount}});
await sleep(2000);

// 消费跨过银卡门槛（500 元）：skuId=1 单价 18 元 × 29 = 522 元
const order=(await req('POST','/api/order/orders',{token:tok,body:{storeId:1,items:[{skuId:1,quantity:29}]}})).data;
chk('创建跨门槛订单',!!order?.orderNo,'应付='+order?.payableAmount/100+'元');
await req('POST','/api/order/orders/'+order.orderNo+'/pay-wallet',{token:tok,body:{}});

let up=null;
for(let i=0;i<15;i++){
  await sleep(1500);
  prof=await req('GET','/api/member/profile',{token:tok});
  if(prof.data?.levelId>1){ up=prof.data; break; }
}
chk('消费后自动升级为银卡',up?.levelId===2,'升级后='+(up?.levelName||prof.data?.levelName)+' 累计消费='+((prof.data?.totalConsumeAmount||0)/100)+'元');
const lv=await req('GET','/api/marketing/level-benefits/level/2',{token:tok});
chk('升级后权益生效',lv.code===0&&(lv.data||[]).length>=2,'银卡权益='+(lv.data||[]).length+'项');

console.log(''); console.log('=== 汇总: PASS='+pass+' FAIL='+fail+' ===');
if(fail>0) process.exitCode=1;