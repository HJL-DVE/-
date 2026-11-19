
import fs from 'node:fs';
import { execFileSync } from 'node:child_process';
const ROOT='H:\\奶茶店会员与储值营销系统';
const env=fs.readFileSync(ROOT+'\\backend\\infrastructure\\docker\\.env.docker','utf8');
const V={}; env.split(/\r?\n/).forEach(l=>{const m=l.match(/^([A-Z_]+)=(.*)$/);if(m)V[m[1]]=m[2];});
const TOKEN=V.INTERNAL_TOKEN;
const DOCKER='C:\\Program Files\\Docker\\Docker\\resources\\bin\\docker.exe';
const mysql=(db,sql)=>execFileSync(DOCKER,['exec','teaclub-mysql','mysql','-uroot','-p'+V.MYSQL_ROOT_PASSWORD,db,'-N','-B','-e',sql],{encoding:'utf8'}).split('\n').filter(l=>l.trim()&&!l.includes('Warning'));

const GW='http://127.0.0.1:9000', PAY='http://127.0.0.1:9006', AUTH='http://127.0.0.1:9001', W='http://127.0.0.1:9004/api/wallet/internal';
const WH={'X-Internal-Token':TOKEN,'Content-Type':'application/json'};
const lr=await fetch(AUTH+'/api/admin/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'admin',password:'TeaClub@123'})});
const tok=(await lr.json()).data.accessToken; const H={Authorization:'Bearer '+tok,'Content-Type':'application/json'};
let pass=0,fail=0; const chk=(n,ok,d)=>{if(ok){pass++;console.log('  [PASS] '+n+' '+d);}else{fail++;console.log('  [FAIL] '+n+' '+d);}};

const M=900000 + (Date.now() % 90000);
const balBefore=(await (await fetch(W+'/balance?brandId=1&memberId='+M,{headers:WH})).json());
console.log('会员 '+M+' 充值前: '+(balBefore.data?balBefore.data.principalBalance+'/'+balBefore.data.giftBalance:'(无钱包)'));

console.log('');
console.log('=== 1. 创建充值单：100元 + 赠20元 ===');
let r=await fetch(W+'/recharge-order?brandId=1&memberId='+M+'&storeId=1&rechargeAmount=10000&giftAmount=2000&giftExpireDays=365',{method:'POST',headers:{'X-Internal-Token':TOKEN}});
let j=await r.json(); const rechargeNo=j.data;
chk('充值单创建',j.code===0&&!!rechargeNo,'rechargeNo='+rechargeNo);
const ro1=mysql('wallet_db',"SELECT status,recharge_amount,gift_amount FROM wal_recharge_order WHERE recharge_no='"+rechargeNo+"'");
chk('充值单状态 PENDING',ro1.length===1&&ro1[0].includes('PENDING'),ro1.join(' | '));

console.log('');
console.log('=== 2. 发起充值支付 ===');
r=await fetch(GW+'/api/admin/payment/create',{method:'POST',headers:H,body:JSON.stringify({bizType:'RECHARGE',bizNo:rechargeNo,clientIp:'127.0.0.1'})});
j=await r.json();
chk('充值支付单创建',j.code===0,'paymentNo='+(j.data?j.data.paymentNo:'?')+' 金额='+(j.data?j.data.amount:'?'));
const paymentNo=j.data.paymentNo, amount=j.data.amount;

console.log('');
console.log('=== 3. 支付成功通知 ===');
r=await fetch(PAY+'/api/payment/mock/notify',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({paymentNo,amount})});
j=await r.json();
chk('通知处理成功',j.code===0,'result='+(j.data||'').slice(0,50));

console.log('');
console.log('=== 4. 校验：支付记录 / 充值单 / 钱包余额 / 资金流水 ===');
const pay=mysql('payment_db',"SELECT status,amount,provider FROM pay_payment_order WHERE payment_no='"+paymentNo+"'");
chk('微信支付记录 SUCCESS',pay.length===1&&pay[0].includes('SUCCESS'),pay.join(' | '));
const ro2=mysql('wallet_db',"SELECT status,payment_no FROM wal_recharge_order WHERE recharge_no='"+rechargeNo+"'");
chk('充值单状态 PAID',ro2.length===1&&ro2[0].includes('PAID'),ro2.join(' | '));
const bal=(await (await fetch(W+'/balance?brandId=1&memberId='+M,{headers:WH})).json()).data;
chk('实付余额 +10000',bal.principalBalance===10000,'实付='+bal.principalBalance);
chk('赠送余额 +2000',bal.giftBalance===2000,'赠送='+bal.giftBalance);
const txn=mysql('wallet_db',"SELECT biz_type,principal_change,gift_change,principal_before,principal_after FROM wal_wallet_transaction WHERE biz_no='"+rechargeNo+"'");
chk('资金流水 RECHARGE_IN',txn.length===1&&txn[0].includes('RECHARGE_IN')&&txn[0].includes('10000')&&txn[0].includes('2000'),txn.join(' | '));

console.log('');
console.log('=== 5. 反复发送相同支付成功事件（只入账一次）===');
for(let i=0;i<4;i++){
  await fetch(PAY+'/api/payment/mock/notify',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({paymentNo,amount})});
}
const bal2=(await (await fetch(W+'/balance?brandId=1&memberId='+M,{headers:WH})).json()).data;
chk('重复事件未重复入账',bal2.principalBalance===10000&&bal2.giftBalance===2000,'实付='+bal2.principalBalance+' 赠送='+bal2.giftBalance);
const txnCount=mysql('wallet_db',"SELECT COUNT(*) FROM wal_wallet_transaction WHERE biz_no='"+rechargeNo+"'");
chk('流水仅1条',txnCount[0]==='1','count='+txnCount[0]);

console.log('');
console.log('=== 汇总: PASS='+pass+' FAIL='+fail+' ===');
