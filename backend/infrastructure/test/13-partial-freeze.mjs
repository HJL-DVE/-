
import fs from 'node:fs';
import { execFileSync } from 'node:child_process';
const ROOT='H:\\奶茶店会员与储值营销系统';
const env=fs.readFileSync(ROOT+'\\backend\\infrastructure\\docker\\.env.docker','utf8');
const V={}; env.split(/\r?\n/).forEach(l=>{const m=l.match(/^([A-Z_]+)=(.*)$/);if(m)V[m[1]]=m[2];});
const DOCKER='C:\\Program Files\\Docker\\Docker\\resources\\bin\\docker.exe';
const mysql=(db,sql)=>execFileSync(DOCKER,['exec','teaclub-mysql','mysql','-uroot','-p'+V.MYSQL_ROOT_PASSWORD,db,'-N','-B','-e',sql],{encoding:'utf8'}).split('\n').filter(l=>l.trim()&&!l.includes('Warning'));

const GW='http://127.0.0.1:9000', AUTH='http://127.0.0.1:9001', W='http://127.0.0.1:9004/api/wallet/internal';
const WH={'X-Internal-Token':V.INTERNAL_TOKEN,'Content-Type':'application/json'};
const lr=await fetch(AUTH+'/api/admin/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'admin',password:'TeaClub@123'})});
const tok=(await lr.json()).data.accessToken; const H={Authorization:'Bearer '+tok,'Content-Type':'application/json'};
let pass=0,fail=0; const chk=(n,ok,d)=>{if(ok){pass++;console.log('  [PASS] '+n+' '+d);}else{fail++;console.log('  [FAIL] '+n+' '+d);}};

console.log('=== #4 冻结部分确认 ===');
const M=950000+(Date.now()%9000);
await fetch(W+'/recharge-in',{method:'POST',headers:WH,body:JSON.stringify({brandId:1,memberId:M,storeId:1,rechargeNo:'PF-'+Date.now(),payAmount:10000,giftAmount:5000,operatorType:'SYSTEM'})});
const fo='PF-ORD-'+Date.now();
await fetch(W+'/freeze?brandId=1&memberId='+M+'&orderNo='+fo+'&freezePrincipal=3000&freezeGift=1000&expireMinutes=10',{method:'POST',headers:{'X-Internal-Token':V.INTERNAL_TOKEN}});
let b=(await (await fetch(W+'/available?brandId=1&memberId='+M,{headers:WH})).json()).data;
chk('冻结 3000/1000 后可用',b.availablePrincipal===7000&&b.availableGift===4000,'可用='+b.availablePrincipal+'/'+b.availableGift);

const pr=await (await fetch(W+'/freeze/confirm-partial?brandId=1&memberId='+M+'&orderNo='+fo+'&storeId=1&principal=1200&gift=300',{method:'POST',headers:{'X-Internal-Token':V.INTERNAL_TOKEN}})).json();
chk('部分确认只扣 1200/300',pr.code===0&&pr.data.principalChange===-1200&&pr.data.giftChange===-300,'实付变='+pr.data.principalChange+' 赠送变='+pr.data.giftChange);
b=(await (await fetch(W+'/available?brandId=1&memberId='+M,{headers:WH})).json()).data;
chk('剩余冻结已释放',b.availablePrincipal===8800&&b.availableGift===4700,'可用='+b.availablePrincipal+'/'+b.availableGift+'（余额8800/4700）');

console.log('');
console.log('=== #2 Outbox 业务事件 ===');
// 余额支付会发 ORDER_PAID + WALLET_DEDUCTED；充值发 RECHARGE_PAID
const before=parseInt(mysql('order_db',"SELECT COUNT(*) FROM sys_outbox_message")[0]||'0');
let r=await fetch(GW+'/api/admin/order/orders',{method:'POST',headers:H,body:JSON.stringify({storeId:1,memberId:1,items:[{skuId:1,quantity:1}]})});
let j=await r.json(); const orderNo=j.data.orderNo;
r=await fetch(GW+'/api/admin/order/orders/'+orderNo+'/pay-balance',{method:'POST',headers:H,body:'{}'});
j=await r.json();
chk('余额支付成功',j.code===0&&j.data.status==='PAID','status='+(j.data?j.data.status:'?'));
await new Promise(x=>setTimeout(x,3000));
const evTypes=mysql('order_db',"SELECT event_type,status FROM sys_outbox_message WHERE biz_no='"+orderNo+"'");
chk('订单服务发出 ORDER_PAID',evTypes.some(l=>l.includes('ORDER_PAID')),evTypes.join(' | '));

const wEvts=mysql('wallet_db',"SELECT event_type,status FROM sys_outbox_message ORDER BY id DESC LIMIT 5");
chk('钱包服务发出业务事件',wEvts.some(l=>/RECHARGE_PAID|WALLET_DEDUCTED|WALLET_FROZEN/.test(l)),wEvts.join(' | '));

const pEvts=mysql('payment_db',"SELECT event_type,status FROM sys_outbox_message ORDER BY id DESC LIMIT 5");
console.log('  支付服务事件: '+(pEvts.join(' | ')||'(暂无)'));
