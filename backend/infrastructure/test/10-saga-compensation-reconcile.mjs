
import fs from 'node:fs';
import { execFileSync } from 'node:child_process';
const ROOT = "H:\\奶茶店会员与储值营销系统";
const env = fs.readFileSync(ROOT + '\\backend\\infrastructure\\docker\\.env.docker','utf8');
const V = {};
env.split(/\r?\n/).forEach(l => { const m = l.match(/^([A-Z_]+)=(.*)$/); if (m) V[m[1]] = m[2]; });
const TOKEN = V.INTERNAL_TOKEN;
const DOCKER = 'C:\\Program Files\\Docker\\Docker\\resources\\bin\\docker.exe';
const mysql = (db, sql) => execFileSync(DOCKER, ['exec','teaclub-mysql','mysql','-uroot','-p'+V.MYSQL_ROOT_PASSWORD,db,'-N','-B','-e',sql], { encoding:'utf8' }).split('\n').filter(l=>l.trim()&&!l.includes('Warning'));

const GW='http://127.0.0.1:9000', AUTH='http://127.0.0.1:9001', W='http://127.0.0.1:9004/api/wallet/internal';
const lr=await fetch(AUTH+'/api/admin/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'admin',password:'TeaClub@123'})});
const tok=(await lr.json()).data.accessToken; const H={Authorization:'Bearer '+tok,'Content-Type':'application/json'};
const WH={'X-Internal-Token':TOKEN,'Content-Type':'application/json'};
let pass=0,fail=0; const chk=(n,ok,d)=>{if(ok){pass++;console.log('  [PASS] '+n+' '+d);}else{fail++;console.log('  [FAIL] '+n+' '+d);}};

console.log('=== 1. 正常支付应产生 COMPLETED Saga ===');
let r=await fetch(GW+'/api/admin/order/orders',{method:'POST',headers:H,body:JSON.stringify({storeId:1,memberId:1,items:[{skuId:1,quantity:1}]})});
let j=await r.json(); const orderNo=j.data.orderNo;
r=await fetch(GW+'/api/admin/order/orders/'+orderNo+'/pay-balance',{method:'POST',headers:H,body:'{}'});
j=await r.json();
chk('余额支付成功',j.code===0&&j.data.status==='PAID','status='+(j.data?j.data.status:'?'));
const sagaRows=mysql('order_db',"SELECT order_no,step,status,principal_amount FROM oms_payment_saga WHERE order_no='"+orderNo+"'");
chk('Saga 记录 COMPLETED',sagaRows.length===1&&sagaRows[0].includes('COMPLETED'),sagaRows.join(' | '));

console.log('');
console.log('=== 2. 资金对账 ===');
r=await fetch(W+'/reconcile',{headers:WH});
j=await r.json();
chk('对账通过（无不一致）',j.code===0&&j.data.passed===true,'wallets='+j.data.walletCount+' ledgers='+j.data.ledgerCount+' issues='+j.data.issueCount);
if(j.data.issues&&j.data.issues.length) console.log('    issues: '+JSON.stringify(j.data.issues).slice(0,300));

console.log('');
console.log('=== 3. 真实 Saga 补偿：模拟已扣款但订单未支付 ===');
// 建单（不支付）
r=await fetch(GW+'/api/admin/order/orders',{method:'POST',headers:H,body:JSON.stringify({storeId:1,memberId:1,items:[{skuId:1,quantity:1}]})});
j=await r.json(); const o2=j.data.orderNo, amt=j.data.payableAmount;
// 真实扣款（模拟钱包扣款成功）
await fetch(W+'/consume-out',{method:'POST',headers:WH,body:JSON.stringify({brandId:1,memberId:1,storeId:1,orderNo:o2,payableAmount:amt,operatorType:'SYSTEM'})});
let bal=(await (await fetch(W+'/balance?brandId=1&memberId=1',{headers:WH})).json()).data;
const balAfterDeduct=bal.principalBalance;
console.log('  已扣款 '+amt+' 分，当前实付余额='+balAfterDeduct+'，订单保持 UNPAID');
// 插入滞留 Saga（模拟进程崩溃，来不及补偿）
mysql('order_db',"INSERT INTO oms_payment_saga (saga_id,order_no,step,status,principal_amount,gift_amount,create_time,update_time) VALUES ('TEST-SAGA-"+Date.now()+"','"+o2+"','DEDUCTED','RUNNING',"+amt+",0,DATE_SUB(NOW(3),INTERVAL 300 SECOND),DATE_SUB(NOW(3),INTERVAL 300 SECOND))");
console.log('  已插入滞留 Saga（DEDUCTED/RUNNING，update_time 300秒前）');
console.log('  等待补偿任务（30秒周期）...');
let compensated=false, balFinal=0;
for(let i=0;i<8;i++){
  await new Promise(x=>setTimeout(x,10000));
  const s=mysql('order_db',"SELECT status FROM oms_payment_saga WHERE order_no='"+o2+"'");
  if(s.length&&s[0].includes('COMPENSATED')){
    compensated=true;
    bal=(await (await fetch(W+'/balance?brandId=1&memberId=1',{headers:WH})).json()).data;
    balFinal=bal.principalBalance;
    break;
  }
}
chk('补偿任务自动回冲',compensated,'saga=COMPENSATED');
chk('资金已退回会员',balFinal===balAfterDeduct+amt,'余额 '+balAfterDeduct+' -> '+balFinal+'（应+'+amt+'）');

console.log('');
console.log('=== 汇总: PASS='+pass+' FAIL='+fail+' ===');
