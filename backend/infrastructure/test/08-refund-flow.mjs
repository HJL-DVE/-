
const GW='http://127.0.0.1:9000', PAY='http://127.0.0.1:9006', AUTH='http://127.0.0.1:9001';
const lr=await fetch(AUTH+'/api/admin/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'admin',password:'TeaClub@123'})});
const tok=(await lr.json()).data.accessToken; const H={Authorization:'Bearer '+tok,'Content-Type':'application/json'};
let pass=0,fail=0; const chk=(n,ok,d)=>{if(ok){pass++;console.log('  [PASS] '+n+' '+d);}else{fail++;console.log('  [FAIL] '+n+' '+d);}};

// 建单并支付
let r=await fetch(GW+'/api/admin/order/orders',{method:'POST',headers:H,body:JSON.stringify({storeId:1,memberId:1,items:[{skuId:1,quantity:1}]})});
let j=await r.json(); const orderNo=j.data.orderNo, payable=j.data.payableAmount;
r=await fetch(GW+'/api/admin/payment/create',{method:'POST',headers:H,body:JSON.stringify({bizType:'ORDER',bizNo:orderNo})});
j=await r.json(); const paymentNo=j.data.paymentNo;
await fetch(PAY+'/api/payment/mock/notify',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({paymentNo,amount:payable})});
console.log('订单 '+orderNo+' 应付='+payable+' 已支付');

console.log('');
console.log('=== 微信退款：申请 -> PROCESSING -> 平台确认 -> SUCCESS ===');
r=await fetch(GW+'/api/admin/payment/refunds',{method:'POST',headers:H,body:JSON.stringify({orderNo,refundAmount:payable,reason:'全额退款'})});
j=await r.json(); const refundNo=j.data.refundNo;
chk('退款申请成功（PROCESSING）',j.code===0&&j.data.status==='PROCESSING','refundNo='+refundNo+' status='+j.data.status);

// 此时订单仍 PAID（等平台确认）
r=await fetch(GW+'/api/admin/order/orders/'+orderNo,{headers:{Authorization:'Bearer '+tok}});
j=await r.json();
chk('平台确认前订单保持PAID（不提前置为已退）',j.data.status==='PAID','status='+j.data.status);

// 平台确认通知
r=await fetch(PAY+'/api/payment/mock/refund-notify',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({refundNo})});
j=await r.json();
chk('退款通知已处理',j.code===0,'result='+(j.data||'').slice(0,50));

await new Promise(x=>setTimeout(x,1200));

// 确认后订单应 REFUNDED
r=await fetch(GW+'/api/admin/order/orders/'+orderNo,{headers:{Authorization:'Bearer '+tok}});
j=await r.json();
chk('平台确认后订单REFUNDED',j.data.status==='REFUNDED','status='+j.data.status+' 已退='+j.data.refundedAmount);

console.log('');
console.log('=== 部分退款后仍可再退剩余 ===');
r=await fetch(GW+'/api/admin/order/orders',{method:'POST',headers:H,body:JSON.stringify({storeId:1,memberId:1,items:[{skuId:1,quantity:2}]})});
j=await r.json(); const o2=j.data.orderNo, pay2=j.data.payableAmount;
r=await fetch(GW+'/api/admin/payment/create',{method:'POST',headers:H,body:JSON.stringify({bizType:'ORDER',bizNo:o2})});
j=await r.json();
await fetch(PAY+'/api/payment/mock/notify',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({paymentNo:j.data.paymentNo,amount:pay2})});
const half=Math.floor(pay2/2);
r=await fetch(GW+'/api/admin/payment/refunds',{method:'POST',headers:H,body:JSON.stringify({orderNo:o2,refundAmount:half,reason:'部分退款'})});
j=await r.json();
chk('部分退款成功',j.code===0,'退='+half+'/'+pay2);
r=await fetch(GW+'/api/admin/payment/refunds',{method:'POST',headers:H,body:JSON.stringify({orderNo:o2,refundAmount:pay2,reason:'再退全部（应拒绝，超可退）'})});
j=await r.json();
chk('超出剩余可退被拒',j.code!==0,'msg='+(j.message||'').slice(0,50));
r=await fetch(GW+'/api/admin/payment/refunds',{method:'POST',headers:H,body:JSON.stringify({orderNo:o2,refundAmount:pay2-half,reason:'退剩余'})});
j=await r.json();
chk('退剩余金额成功',j.code===0,'退='+(pay2-half));

console.log('');
console.log('=== 汇总: PASS='+pass+' FAIL='+fail+' ===');
