
const GW='http://127.0.0.1:9000', AUTH='http://127.0.0.1:9001';
const lr=await fetch(AUTH+'/api/admin/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'admin',password:'TeaClub@123'})});
const tok=(await lr.json()).data.accessToken; const H={Authorization:'Bearer '+tok,'Content-Type':'application/json'};

// 创建新订单并支付
let r=await fetch(GW+'/api/admin/order/orders',{method:'POST',headers:H,body:JSON.stringify({storeId:1,memberId:1,items:[{skuId:1,quantity:1}]})});
let j=await r.json(); const orderNo=j.data.orderNo, payable=j.data.payableAmount;
r=await fetch(GW+'/api/admin/payment/create',{method:'POST',headers:H,body:JSON.stringify({bizType:'ORDER',bizNo:orderNo})});
j=await r.json(); const paymentNo=j.data.paymentNo;
await fetch('http://127.0.0.1:9006/api/payment/mock/notify',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({paymentNo,amount:payable})});

console.log('订单 '+orderNo+' 应付='+payable);
console.log('');
console.log('=== 严格重复退款验证 ===');
const rf=(amount)=>fetch(GW+'/api/admin/payment/refunds',{method:'POST',headers:H,body:JSON.stringify({orderNo,refundAmount:amount,reason:'测试'})}).then(r=>r.json());
const a=await rf(payable);
console.log('第1次全额退款: code='+a.code+' refundNo='+(a.data?a.data.refundNo:'?')+' 状态='+(a.data?a.data.status:'?'));
const b=await rf(payable);
console.log('第2次全额退款: code='+b.code+' msg='+(b.message||'')+' refundNo='+(b.data?b.data.refundNo:'(无)'));
// 查订单已退金额
r=await fetch(GW+'/api/admin/order/orders/'+orderNo,{headers:{Authorization:'Bearer '+tok}});
j=await r.json();
console.log('订单状态='+j.data.status+' 已退金额='+j.data.refundedAmount+' 实付='+j.data.paidAmount);
const 累计退款 = j.data.refundedAmount;
console.log('累计退款是否等于单次退款额: '+(累计退款===payable?'是【正确，未重复】':'否【异常】'));
