
import fs from 'node:fs';
const ROOT='H:\\奶茶店会员与储值营销系统';
const GW='http://127.0.0.1:9000';
let pass=0,fail=0;
const chk=(n,ok,d)=>{ if(ok)pass++; else fail++; console.log((ok?'  [PASS] ':'  [FAIL] ')+n+'  '+d); };

const call=async(method,path,{token,body}={})=>{
  const h={'Content-Type':'application/json'};
  if(token) h.Authorization='Bearer '+token;
  const res=await fetch(GW+path,{method,headers:h,body:body===undefined?undefined:JSON.stringify(body)});
  const text=await res.text();
  let json=null; try{ json=JSON.parse(text);}catch{}
  return {status:res.status, json, text:text.slice(0,240)};
};
const yuan=(fen)=> (fen/100).toFixed(2);

console.log('=== 0. 会员微信登录 ===');
const code='e2e-'+Date.now();
let r=await call('POST','/api/auth/wx-login',{body:{code,nickname:'闭环测试'}});
const tok=r.json?.data?.token;
chk('会员登录成功',!!tok,'code='+r.json?.code+' memberId='+r.json?.data?.memberId);
if(!tok){ console.log('中止'); process.exit(1); }

console.log('');
console.log('=== 1. 充值套餐 ===');
r=await call('GET','/api/wallet/plans?storeId=1',{token:tok});
const plans=r.json?.data||[];
chk('取到可用套餐',plans.length>0,'count='+plans.length);
const plan=plans.find(p=>p.rechargeAmount>0)||plans[0];
console.log('  选中套餐: '+plan.planName+' 充值='+yuan(plan.rechargeAmount)+' 赠送='+yuan(plan.giftAmount));

console.log('');
console.log('=== 2. 充值前余额 ===');
let before=r.json;
r=await call('GET','/api/wallet/account',{token:tok});
const bal0=r.json?.data;
chk('查询钱包账户',r.json?.code===0,'实付='+yuan(bal0?.principalBalance||0)+' 赠送='+yuan(bal0?.giftBalance||0));

console.log('');
console.log('=== 3. 创建充值单 ===');
r=await call('POST','/api/wallet/recharges',{token:tok,body:{planId:plan.id,storeId:1}});
const rec=r.json?.data;
chk('充值单已创建',r.json?.code===0&&rec?.rechargeNo,'rechargeNo='+rec?.rechargeNo+' 应付='+yuan(rec?.actualAmount||0)+' 赠送='+yuan(rec?.giftAmount||0));

console.log('');
console.log('=== 4. 创建支付单（微信 Mock）===');
let pay=null;
r=await call('POST','/api/payment/create',{token:tok,body:{bizType:'RECHARGE',bizNo:rec?.rechargeNo}});
pay=r.json?.data;
chk('支付单已创建',r.json?.code===0&&pay?.paymentNo,'paymentNo='+pay?.paymentNo+' 金额='+yuan(pay?.amount||0)+' codeUrl='+(pay?.codeUrl?'有':'无'));

console.log('');
console.log('=== 5. 模拟支付成功通知 ===');
r=await call('POST','/api/payment/mock/notify',{body:{paymentNo:pay?.paymentNo,amount:pay?.amount}});
chk('通知已受理',r.json?.code===0,'code='+r.json?.code+' '+(r.json?.data||''));

console.log('');
console.log('=== 6. 支付单状态 ===');
r=await call('GET','/api/payment/orders/'+pay?.paymentNo,{token:tok});
chk('支付单已 SUCCESS',r.json?.data?.status==='SUCCESS','status='+r.json?.data?.status);

console.log('');
console.log('=== 7. 充值单状态（轮询口径）===');
r=await call('GET','/api/wallet/recharges/'+rec?.rechargeNo,{token:tok});
chk('充值单已 PAID',/PAID|SUCCESS/.test(String(r.json?.data?.status)),'status='+r.json?.data?.status);

console.log('');
console.log('=== 8. 钱包到账（前端只轮询，不本地加余额）===');
let bal1=null,ok=false;
for(let i=0;i<10;i++){
  await new Promise(x=>setTimeout(x,800));
  const rr=await call('GET','/api/wallet/account',{token:tok});
  bal1=rr.json?.data;
  if((bal1?.principalBalance||0)===(bal0?.principalBalance||0)+plan.rechargeAmount){ ok=true; break; }
}
chk('实付余额按充值额增加',ok,'实付 '+yuan(bal0?.principalBalance||0)+' -> '+yuan(bal1?.principalBalance||0)+'（应 +'+yuan(plan.rechargeAmount)+'）');
chk('赠送余额按赠送额增加',(bal1?.giftBalance||0)===(bal0?.giftBalance||0)+plan.giftAmount,'赠送 '+yuan(bal0?.giftBalance||0)+' -> '+yuan(bal1?.giftBalance||0)+'（应 +'+yuan(plan.giftAmount)+'）');

console.log('');
console.log('=== 9. 重复通知（幂等）===');
r=await call('POST','/api/payment/mock/notify',{body:{paymentNo:pay?.paymentNo,amount:pay?.amount}});
await new Promise(x=>setTimeout(x,1500));
r=await call('GET','/api/wallet/account',{token:tok});
const bal2=r.json?.data;
chk('重复通知未重复入账',(bal2?.principalBalance||0)===(bal1?.principalBalance||0)&&(bal2?.giftBalance||0)===(bal1?.giftBalance||0),
    '实付='+yuan(bal2?.principalBalance||0)+' 赠送='+yuan(bal2?.giftBalance||0));

console.log('');
console.log('=== 10. 下单 → 试算 → 创建 ===');
r=await call('POST','/api/order/quote',{token:tok,body:{storeId:1,items:[{skuId:1,quantity:1}]}});
const quote=r.json?.data;
chk('服务端试算成功',r.json?.code===0,'应付='+yuan(quote?.payableAmount||0));
r=await call('POST','/api/order/orders',{token:tok,body:{storeId:1,items:[{skuId:1,quantity:1}]}});
const order=r.json?.data;
chk('订单创建成功',r.json?.code===0&&order?.orderNo,'orderNo='+order?.orderNo+' 应付='+yuan(order?.payableAmount||0)+' status='+order?.status);

console.log('');
console.log('=== 11. 钱包支付订单 ===');
const paidAmount=order?.payableAmount||0;
const balBefore=bal2;
r=await call('POST','/api/order/orders/'+(order?.orderNo)+'/pay-wallet',{token:tok,body:{}});
chk('订单支付成功',r.json?.code===0&&r.json?.data?.status==='PAID','status='+r.json?.data?.status+' channel='+r.json?.data?.payChannel);

console.log('');
console.log('=== 12. 支付后钱包扣款 ===');
let bal3=null, deducted=false;
for(let i=0;i<10;i++){
  await new Promise(x=>setTimeout(x,700));
  const rr=await call('GET','/api/wallet/account',{token:tok});
  bal3=rr.json?.data;
  const pNow=bal3?.principalBalance||0, gNow=bal3?.giftBalance||0;
  const pBefore=balBefore?.principalBalance||0, gBefore=balBefore?.giftBalance||0;
  if((pBefore-pNow)+(gBefore-gNow)===paidAmount){ deducted=true; break; }
}
chk('余额合计扣款等于应付',deducted,'实付 '+yuan(balBefore?.principalBalance||0)+'->'+yuan(bal3?.principalBalance||0)+' 赠送 '+yuan(balBefore?.giftBalance||0)+'->'+yuan(bal3?.giftBalance||0)+' 应付='+yuan(paidAmount));

console.log('');
console.log('=== 13. 我的订单与流水 ===');
r=await call('GET','/api/order/orders?pageNo=1&pageSize=5',{token:tok});
chk('订单列表含本单',(r.json?.data?.records||[]).some(o=>o.orderNo===order?.orderNo),'total='+r.json?.data?.total);
r=await call('GET','/api/wallet/transactions?pageNo=1&pageSize=10',{token:tok});
const txns=r.json?.data?.records||[];
chk('流水含充值与消费',txns.length>=2,'流水条数='+txns.length+' 类型='+txns.map(t=>t.bizType).join(','));

console.log('');
console.log('=== 汇总: PASS='+pass+' FAIL='+fail+' ===');
if(fail>0) process.exitCode=1;
