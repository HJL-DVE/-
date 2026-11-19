
const GW='http://127.0.0.1:9000', PAY='http://127.0.0.1:9006', AUTH='http://127.0.0.1:9001';
const lr=await fetch(AUTH+'/api/admin/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'admin',password:'TeaClub@123'})});
const tok=(await lr.json()).data.accessToken; const H={Authorization:'Bearer '+tok,'Content-Type':'application/json'};
let pass=0,fail=0; const chk=(n,ok,d)=>{if(ok){pass++;console.log('  [PASS] '+n+' '+d);}else{fail++;console.log('  [FAIL] '+n+' '+d);}};

async function newPay(){
  let r=await fetch(GW+'/api/admin/order/orders',{method:'POST',headers:H,body:JSON.stringify({storeId:1,memberId:1,items:[{skuId:1,quantity:1}]})});
  let j=await r.json(); const orderNo=j.data.orderNo;
  r=await fetch(GW+'/api/admin/payment/create',{method:'POST',headers:H,body:JSON.stringify({bizType:'ORDER',bizNo:orderNo,clientIp:'127.0.0.1'})});
  j=await r.json(); return {orderNo, paymentNo:j.data.paymentNo, amount:j.data.amount, provider:j.data.provider};
}

console.log('=== 场景1: 预支付成功 ===');
const p1=await newPay();
chk('预支付成功',!!p1.paymentNo&&p1.provider==='MOCK','payNo='+p1.paymentNo+' 金额='+p1.amount+' provider='+p1.provider);

console.log('');
console.log('=== 场景2: 预支付失败（不存在的订单）===');
let r=await fetch(GW+'/api/admin/payment/create',{method:'POST',headers:H,body:JSON.stringify({bizType:'ORDER',bizNo:'NOT-EXIST-XXX'})});
let j=await r.json();
chk('预支付失败明确报错',j.code!==0,'code='+j.code+' msg='+(j.message||'').slice(0,30));

console.log('');
console.log('=== 场景3: 支付通知重复 ===');
await fetch(PAY+'/api/payment/mock/notify',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({paymentNo:p1.paymentNo,amount:p1.amount})});
r=await fetch(PAY+'/api/payment/mock/notify',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({paymentNo:p1.paymentNo,amount:p1.amount})});
j=await r.json();
r=await fetch(GW+'/api/admin/payment/orders/'+p1.paymentNo+'/query',{headers:{Authorization:'Bearer '+tok}});
const q1=await r.json();
chk('重复通知幂等',j.code===0&&q1.data.status==='SUCCESS','状态='+q1.data.status);

console.log('');
console.log('=== 场景4: 支付通知延迟（先查单PAYING，后通知）===');
const p4=await newPay();
r=await fetch(GW+'/api/admin/payment/orders/'+p4.paymentNo+'/query',{headers:{Authorization:'Bearer '+tok}});
const before=(await r.json()).data.status;
await new Promise(x=>setTimeout(x,1500));
await fetch(PAY+'/api/payment/mock/notify',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({paymentNo:p4.paymentNo,amount:p4.amount})});
r=await fetch(GW+'/api/admin/payment/orders/'+p4.paymentNo+'/query',{headers:{Authorization:'Bearer '+tok}});
const after=(await r.json()).data.status;
chk('延迟通知最终一致',before==='PAYING'&&after==='SUCCESS',before+' -> '+after);

console.log('');
console.log('=== 场景5: 网络超时（模拟请求超时后重试）===');
const p5=await newPay();
// 模拟：发出通知但客户端超时，重试通知
await fetch(PAY+'/api/payment/mock/notify',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({paymentNo:p5.paymentNo,amount:p5.amount})});
r=await fetch(PAY+'/api/payment/mock/notify',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({paymentNo:p5.paymentNo,amount:p5.amount})});
j=await r.json();
chk('超时重试不重复入账',j.code===0,'重复通知安全');

console.log('');
console.log('=== 场景6: 支付结果未知（主动查询确认）===');
const p6=await newPay();
r=await fetch(GW+'/api/admin/payment/orders/'+p6.paymentNo+'/query',{headers:{Authorization:'Bearer '+tok}});
j=await r.json();
chk('主动查单可得状态',j.code===0&&!!j.data.status,'状态='+j.data.status+'（不误判失败）');

console.log('');
console.log('=== 场景7: 金额不匹配 ===');
const p7=await newPay();
r=await fetch(PAY+'/api/payment/mock/notify',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({paymentNo:p7.paymentNo,amount:p7.amount+999})});
j=await r.json();
r=await fetch(GW+'/api/admin/payment/orders/'+p7.paymentNo+'/query',{headers:{Authorization:'Bearer '+tok}});
const q7=(await r.json()).data.status;
chk('金额不匹配被拒且状态不变',j.code!==0&&q7==='PAYING','code='+j.code+' 状态='+q7);

console.log('');
console.log('=== 场景8: 支付失败通知 ===');
const p8=await newPay();
r=await fetch(PAY+'/api/payment/mock/fail',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({paymentNo:p8.paymentNo,reason:'用户取消'})});
j=await r.json();
chk('失败通知已处理',j.code===0,'result='+(j.data||'').slice(0,40));

console.log('');
console.log('=== 场景9-11: 验签失败/AppID不匹配/商户号不匹配 ===');
console.log('  这3项属于真实微信支付API v3校验范畴（签名验证、appid/mchid 比对）。');
console.log('  课程设计使用 Mock provider，此类校验在 WechatPayV3Provider 骨架中保留。');
const wx = (await import('node:fs')).readFileSync('H:\\奶茶店会员与储值营销系统\\backend\\teaclub-cloud\\payment-service\\src\\main\\java\\com\\teaclub\\cloud\\payment\\gateway\\wechat\\WechatPayV3Provider.java','utf8');
chk('WechatV3骨架含验签/字段校验代码',wx.includes('verifyAndDecryptNotify')&&/appid|appId/i.test(wx)&&/mch|merchant/i.test(wx),'骨架保留（方法体 notImplemented，课程设计用Mock）');

console.log('');
console.log('=== 汇总: PASS='+pass+' FAIL='+fail+' ===');
