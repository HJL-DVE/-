
import fs from 'node:fs';
const GW='http://127.0.0.1:9000';
let pass=0,fail=0;
const chk=(n,ok,d)=>{ if(ok)pass++; else fail++; console.log((ok?'  [PASS] ':'  [FAIL] ')+n+'  '+d); };
const call=async(method,path,{token,body}={})=>{
  const h={'Content-Type':'application/json'};
  if(token) h.Authorization='Bearer '+token;
  const res=await fetch(GW+path,{method,headers:h,body:body===undefined?undefined:JSON.stringify(body)});
  const text=await res.text(); let json=null; try{ json=JSON.parse(text);}catch{}
  return {status:res.status, json, text:text.slice(0,200)};
};
const yuan=(f)=>(f/100).toFixed(2);

console.log('=== 0. 管理端登录 ===');
let r=await call('POST','/api/admin/auth/login',{body:{username:'admin',password:'TeaClub@123'}});
const adminTok=r.json?.data?.accessToken;
chk('登录成功',!!adminTok,'token='+(adminTok?adminTok.slice(0,14)+'...':'(无)'));

console.log('');
console.log('=== 1. 下单（收银台口径：quote -> create）===');
r=await call('POST','/api/admin/order/quote',{token:adminTok,body:{storeId:1,memberId:1,items:[{skuId:1,quantity:1}]}});
const quote=r.json?.data;
chk('试算成功',r.json?.code===0,'应付='+yuan(quote?.payableAmount||0));
r=await call('POST','/api/admin/order/orders',{token:adminTok,body:{storeId:1,memberId:1,items:[{skuId:1,quantity:1}]}});
const order=r.json?.data;
chk('订单创建',r.json?.code===0&&order?.orderNo,'orderNo='+order?.orderNo+' status='+order?.status);

console.log('');
console.log('=== 2. 微信支付（Mock 渠道）===');
r=await call('POST','/api/admin/payment/create',{token:adminTok,body:{bizType:'ORDER',bizNo:order?.orderNo}});
const pay=r.json?.data;
chk('支付单创建（Native codeUrl）',r.json?.code===0&&pay?.paymentNo,'paymentNo='+pay?.paymentNo+' codeUrl='+(pay?.codeUrl?'有':'无')+' 金额='+yuan(pay?.amount||0));
r=await call('POST','/api/payment/mock/notify',{body:{paymentNo:pay?.paymentNo,amount:pay?.amount}});
chk('模拟支付回调',r.json?.code===0,'code='+r.json?.code);
r=await call('GET','/api/admin/payment/orders/'+pay?.paymentNo+'/query',{token:adminTok});
chk('支付单 SUCCESS',r.json?.data?.status==='SUCCESS','status='+r.json?.data?.status);
r=await call('GET','/api/admin/order/orders/'+order?.orderNo,{token:adminTok});
chk('订单已 PAID',r.json?.data?.status==='PAID','status='+r.json?.data?.status+' channel='+r.json?.data?.payChannel);

console.log('');
console.log('=== 3. 申请退款 ===');
r=await call('POST','/api/admin/payment/refunds',{token:adminTok,body:{orderNo:order?.orderNo,refundAmount:order?.payableAmount,reason:'联调退款'}});
const refund=r.json?.data;
chk('退款单已创建',r.json?.code===0&&refund?.refundNo,'refundNo='+refund?.refundNo+' 金额='+yuan(refund?.refundAmount||0)+' status='+refund?.status);

console.log('');
console.log('=== 4. 重复退款必须被拒 ===');
r=await call('POST','/api/admin/payment/refunds',{token:adminTok,body:{orderNo:order?.orderNo,refundAmount:order?.payableAmount,reason:'重复退款'}});
chk('重复退款被拒绝',r.json?.code!==0,'code='+r.json?.code+' msg='+(r.json?.message||''));

console.log('');
console.log('=== 5. 退款回调 ===');
r=await call('POST','/api/payment/mock/refund-notify',{body:{refundNo:refund?.refundNo}});
chk('退款回调受理',r.json?.code===0,'code='+r.json?.code+' '+(r.json?.data||''));
await new Promise(x=>setTimeout(x,1500));
r=await call('GET','/api/admin/payment/refunds?pageNo=1&pageSize=10',{token:adminTok});
const found=(r.json?.data?.records||[]).find(x=>x.refundNo===refund?.refundNo);
chk('退款单已 SUCCESS',found?.status==='SUCCESS','status='+found?.status+' total='+r.json?.data?.total);
r=await call('GET','/api/admin/order/orders/'+order?.orderNo,{token:adminTok});
chk('订单转 REFUNDED',/REFUND/.test(String(r.json?.data?.status)),'status='+r.json?.data?.status+' refunded='+yuan(r.json?.data?.refundedAmount||0));

console.log('');
console.log('=== 6. 会员端：退款状态可见 ===');
const code='refund-e2e-'+Date.now();
r=await call('POST','/api/auth/wx-login',{body:{code}});
const mtok=r.json?.data?.token;
chk('会员登录',!!mtok,'memberId='+r.json?.data?.memberId);
r=await call('GET','/api/order/orders?pageNo=1&pageSize=5',{token:mtok});
chk('会员订单接口可用',r.json?.code===0,'total='+r.json?.data?.total);

console.log('');
console.log('=== 汇总: PASS='+pass+' FAIL='+fail+' ===');
if(fail>0) process.exitCode=1;
