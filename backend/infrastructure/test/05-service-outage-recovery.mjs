
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
const ROOT = "H:\\奶茶店会员与储值营销系统";
const env = fs.readFileSync(ROOT + '\\backend\\infrastructure\\docker\\.env.docker','utf8');
const V = {};
env.split(/\r?\n/).forEach(l => { const m = l.match(/^([A-Z_]+)=(.*)$/); if (m) V[m[1]] = m[2]; });
const DOCKER = 'C:\\Program Files\\Docker\\Docker\\resources\\bin\\docker.exe';
const lr = await fetch('http://127.0.0.1:9001/api/admin/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'admin',password:'TeaClub@123'})});
const tok=(await lr.json()).data.accessToken;
const H={Authorization:'Bearer '+tok,'Content-Type':'application/json'};

console.log('=== 场景9 真实测试：钱包服务宕机时下单支付 ===');
// 创建订单
let r=await fetch('http://127.0.0.1:9000/api/admin/order/orders',{method:'POST',headers:H,body:JSON.stringify({storeId:1,memberId:1,items:[{skuId:1,quantity:1}]})});
let j=await r.json(); if(!j.data){console.log('  下单失败: '+JSON.stringify(j));process.exit(1);} const orderNo=j.data.orderNo;
console.log('  订单已创建 '+orderNo+' 状态='+j.data.status);

// 停止钱包容器
console.log('  停止 teaclub-wallet...');
execFileSync(DOCKER,['stop','teaclub-wallet'],{stdio:'pipe'});
await new Promise(x=>setTimeout(x,3000));

// 尝试支付
r=await fetch('http://127.0.0.1:9000/api/admin/order/orders/'+orderNo+'/pay-balance',{method:'POST',headers:H,body:'{}'});
j=await r.json();
console.log('  支付结果 code='+j.code+' msg='+(j.message||'').slice(0,60));

// 查订单状态
r=await fetch('http://127.0.0.1:9000/api/admin/order/orders/'+orderNo,{headers:{Authorization:'Bearer '+tok}});
j=await r.json();
console.log('  订单状态='+(j.data?j.data.status:'?')+'（应保持 UNPAID，不能是"钱扣了待支付"）');

// 恢复钱包
console.log('  恢复 teaclub-wallet...');
execFileSync(DOCKER,['start','teaclub-wallet'],{stdio:'pipe'});
await new Promise(x=>setTimeout(x,20000));

// 恢复后重试支付
r=await fetch('http://127.0.0.1:9000/api/admin/order/orders/'+orderNo+'/pay-balance',{method:'POST',headers:H,body:'{}'});
j=await r.json();
console.log('  恢复后重试支付 code='+j.code+' 状态='+(j.data?j.data.status:'?'));
