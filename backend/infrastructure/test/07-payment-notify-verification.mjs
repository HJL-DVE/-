
import crypto from 'node:crypto';
const GW='http://127.0.0.1:9000', PAY='http://127.0.0.1:9006', AUTH='http://127.0.0.1:9001';
const lr=await fetch(AUTH+'/api/admin/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'admin',password:'TeaClub@123'})});
const tok=(await lr.json()).data.accessToken; const H={Authorization:'Bearer '+tok,'Content-Type':'application/json'};
let pass=0,fail=0; const chk=(n,ok,d)=>{if(ok){pass++;console.log('  [PASS] '+n+' '+d);}else{fail++;console.log('  [FAIL] '+n+' '+d);}};

const APP='mock-appid', MCH='mock-mchid', SECRET='mock-sign-secret';
const sign=(a,m,no,amt)=>crypto.createHash('sha256').update(a+'|'+m+'|'+no+'|'+amt+'|'+SECRET).digest('hex');

async function newPay(){
  let r=await fetch(GW+'/api/admin/order/orders',{method:'POST',headers:H,body:JSON.stringify({storeId:1,memberId:1,items:[{skuId:1,quantity:1}]})});
  let j=await r.json(); const orderNo=j.data.orderNo;
  r=await fetch(GW+'/api/admin/payment/create',{method:'POST',headers:H,body:JSON.stringify({bizType:'ORDER',bizNo:orderNo})});
  j=await r.json();
  return {orderNo, paymentNo:j.data.paymentNo, amount:j.data.amount};
}
const notify=(body)=>fetch(PAY+'/api/payment/mock/notify',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)}).then(r=>r.json());
const payStatus=async(no)=>{const r=await fetch(GW+'/api/admin/payment/orders/'+no+'/query',{headers:{Authorization:'Bearer '+tok}});return (await r.json()).data.status;};

console.log('=== 场景 A: AppID 不匹配 ===');
const pA=await newPay();
let j=await notify({paymentNo:pA.paymentNo,amount:pA.amount,appId:'wrong-appid',mchId:MCH,sign:sign('wrong-appid',MCH,pA.paymentNo,String(pA.amount))});
chk('AppID 不匹配被拒',j.code!==0,'code='+j.code+' msg='+(j.message||'').slice(0,45));
chk('支付单状态未变',(await payStatus(pA.paymentNo))==='PAYING','status='+(await payStatus(pA.paymentNo)));

console.log('');
console.log('=== 场景 B: 商户号不匹配 ===');
const pB=await newPay();
j=await notify({paymentNo:pB.paymentNo,amount:pB.amount,appId:APP,mchId:'wrong-mchid',sign:sign(APP,'wrong-mchid',pB.paymentNo,String(pB.amount))});
chk('商户号不匹配被拒',j.code!==0,'code='+j.code+' msg='+(j.message||'').slice(0,45));
chk('支付单状态未变',(await payStatus(pB.paymentNo))==='PAYING','status='+(await payStatus(pB.paymentNo)));

console.log('');
console.log('=== 场景 C: 签名校验失败 ===');
const pC=await newPay();
j=await notify({paymentNo:pC.paymentNo,amount:pC.amount,appId:APP,mchId:MCH,sign:'deadbeef'.repeat(8)});
chk('错误签名被拒',j.code!==0,'code='+j.code+' msg='+(j.message||'').slice(0,45));
chk('支付单状态未变',(await payStatus(pC.paymentNo))==='PAYING','status='+(await payStatus(pC.paymentNo)));

console.log('');
console.log('=== 场景 D: 正确签名应通过 ===');
const pD=await newPay();
const goodSign=sign(APP,MCH,pD.paymentNo,String(pD.amount));
j=await notify({paymentNo:pD.paymentNo,amount:pD.amount,appId:APP,mchId:MCH,sign:goodSign});
chk('正确签名通过',j.code===0,'result='+(j.data||'').slice(0,40));
chk('支付单转 SUCCESS',(await payStatus(pD.paymentNo))==='SUCCESS','status='+(await payStatus(pD.paymentNo)));

console.log('');
console.log('=== 场景 E: 宽松模式（不带校验字段仍可用，兼容原有流程）===');
const pE=await newPay();
j=await notify({paymentNo:pE.paymentNo,amount:pE.amount});
chk('不带校验字段仍可处理',j.code===0&&(await payStatus(pE.paymentNo))==='SUCCESS','status='+(await payStatus(pE.paymentNo)));

console.log('');
console.log('=== 汇总: PASS='+pass+' FAIL='+fail+' ===');
