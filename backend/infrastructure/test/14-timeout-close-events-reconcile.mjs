
import fs from 'node:fs';
import { execFileSync } from 'node:child_process';
const ROOT='H:\\奶茶店会员与储值营销系统';
const env=fs.readFileSync(ROOT+'\\backend\\infrastructure\\docker\\.env.docker','utf8');
const V={}; env.split(/\r?\n/).forEach(l=>{const m=l.match(/^([A-Z_]+)=(.*)$/);if(m)V[m[1]]=m[2];});
const DOCKER='C:\\Program Files\\Docker\\Docker\\resources\\bin\\docker.exe';
const mysql=(db,sql)=>{try{return execFileSync(DOCKER,['exec','teaclub-mysql','mysql','-uroot','-p'+V.MYSQL_ROOT_PASSWORD,db,'-N','-B','-e',sql],{encoding:'utf8',stdio:['pipe','pipe','pipe']}).split('\n').filter(l=>l.trim()&&!l.includes('Warning'));}catch(e){return ['ERR '+((e.stderr||'')+'').slice(0,120)];}};

const GW='http://127.0.0.1:9000', PAY='http://127.0.0.1:9006', AUTH='http://127.0.0.1:9001';
const lr=await fetch(AUTH+'/api/admin/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'admin',password:'TeaClub@123'})});
const tok=(await lr.json()).data.accessToken; const H={Authorization:'Bearer '+tok,'Content-Type':'application/json'};
let pass=0,fail=0; const chk=(n,ok,d)=>{if(ok){pass++;console.log('  [PASS] '+n+' '+d);}else{fail++;console.log('  [FAIL] '+n+' '+d);}};

async function newPay(){
  let r=await fetch(GW+'/api/admin/order/orders',{method:'POST',headers:H,body:JSON.stringify({storeId:1,memberId:1,items:[{skuId:1,quantity:1}]})});
  let j=await r.json(); const orderNo=j.data.orderNo, amount=j.data.payableAmount;
  r=await fetch(GW+'/api/admin/payment/create',{method:'POST',headers:H,body:JSON.stringify({bizType:'ORDER',bizNo:orderNo})});
  j=await r.json();
  return {orderNo, paymentNo:j.data.paymentNo, amount};
}

console.log('=== #2 payment 服务事件（PAYMENT_SUCCEEDED / REFUND_SUCCEEDED）===');
const p1=await newPay();
await fetch(PAY+'/api/payment/mock/notify',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({paymentNo:p1.paymentNo,amount:p1.amount})});
await new Promise(x=>setTimeout(x,3000));
let ev=mysql('payment_db',"SELECT event_type FROM sys_outbox_message GROUP BY event_type");
chk('支付成功事件已生成',ev.some(l=>l.includes('PAYMENT_SUCCEEDED')),ev.join(' | '));

let r=await fetch(GW+'/api/admin/payment/refunds',{method:'POST',headers:H,body:JSON.stringify({orderNo:p1.orderNo,refundAmount:p1.amount,reason:'事件测试'})});
let j=await r.json(); const refundNo=j.data.refundNo;
await fetch(PAY+'/api/payment/mock/refund-notify',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({refundNo})});
await new Promise(x=>setTimeout(x,3000));
ev=mysql('payment_db',"SELECT event_type FROM sys_outbox_message GROUP BY event_type");
chk('退款成功事件已生成',ev.some(l=>l.includes('REFUND_SUCCEEDED')),ev.join(' | '));

console.log('');
console.log('=== #1 支付超时关单 ===');
const p2=await newPay();
console.log('  支付单 '+p2.paymentNo+' 状态='+mysql('payment_db',"SELECT status FROM pay_payment_order WHERE payment_no='"+p2.paymentNo+"'")[0]);
mysql('payment_db',"UPDATE pay_payment_order SET create_time=DATE_SUB(NOW(3),INTERVAL 120 MINUTE) WHERE payment_no='"+p2.paymentNo+"'");
console.log('  已把 create_time 改为 120 分钟前，等待关单任务（60秒周期）...');
let closed=false;
for(let i=0;i<9;i++){
  await new Promise(x=>setTimeout(x,10000));
  const st=mysql('payment_db',"SELECT status FROM pay_payment_order WHERE payment_no='"+p2.paymentNo+"'")[0];
  if(st&&st.includes('CLOSED')){closed=true;break;}
}
chk('超时支付单被关闭',closed,'status=CLOSED');
const ordSt=mysql('order_db',"SELECT status FROM oms_order WHERE order_no='"+p2.orderNo+"'")[0];
chk('订单仍为 UNPAID（未被误改）',ordSt&&ordSt.includes('UNPAID'),'order status='+ordSt);

console.log('');
console.log('=== #3 订单-钱包跨服务对账 ===');
const rc=await (await fetch('http://127.0.0.1:9005/api/order/internal/reconcile?days=7',{headers:{'X-Internal-Token':V.INTERNAL_TOKEN}})).json();
chk('跨服务对账通过',rc.code===0&&rc.data.passed===true,'checked='+rc.data.checked+' unknown='+rc.data.unknown+' issues='+rc.data.issueCount);
if(rc.data.issues&&rc.data.issues.length) console.log('    issues='+JSON.stringify(rc.data.issues).slice(0,250));

console.log('');
console.log('=== 汇总: PASS='+pass+' FAIL='+fail+' ===');
