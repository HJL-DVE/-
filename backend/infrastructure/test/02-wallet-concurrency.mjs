
import fs from 'node:fs';
const env = fs.readFileSync('H:\\奶茶店会员与储值营销系统\\backend\\infrastructure\\docker\\.env.docker','utf8');
const TOKEN = env.match(/^INTERNAL_TOKEN=(.*)$/m)[1].trim();
const W = 'http://127.0.0.1:9004/api/wallet/internal';
const H = {'X-Internal-Token':TOKEN,'Content-Type':'application/json'};
async function post(path, body) { return (await fetch(W+path,{method:'POST',headers:H,body:JSON.stringify(body)})).json(); }
async function get(path) { return (await fetch(W+path,{headers:H})).json(); }

const memberId = 900200;
console.log('=== 准备：充值 10000 分 ===');
let j = await post('/recharge-in', {brandId:1,memberId,storeId:1,rechargeNo:'CONC-INIT-'+Date.now(),payAmount:10000,giftAmount:0,operatorType:'SYSTEM'});
console.log('  code=' + j.code + ' 余额=' + (j.data?j.data.principalAfter:'?'));
const before = (await get('/balance?brandId=1&memberId='+memberId)).data.principalBalance;
console.log('  起始余额=' + before);

console.log('');
console.log('=== 场景A：并发20笔不同订单，每笔300分（共6000，应全部成功）===');
const stamp = Date.now();
const tasksA = Array.from({length:20}, (_,i) =>
  post('/consume-out', {brandId:1,memberId,storeId:1,orderNo:'CONC-A-'+stamp+'-'+i,payableAmount:300,operatorType:'SYSTEM'})
);
const resultsA = await Promise.all(tasksA);
const okA = resultsA.filter(r=>r.code===0).length;
const failA = resultsA.filter(r=>r.code!==0).length;
console.log('  成功=' + okA + ' 失败=' + failA);
let afterA = (await get('/balance?brandId=1&memberId='+memberId)).data.principalBalance;
console.log('  扣款后余额=' + afterA + ' 实际扣减=' + (before-afterA) + '（期望6000）');

console.log('');
console.log('=== 场景B：并发30笔相同订单号，每笔100分（只应扣1次）===');
const sameNo = 'CONC-SAME-'+stamp;
const beforeB = afterA;
const tasksB = Array.from({length:30}, () =>
  post('/consume-out', {brandId:1,memberId,storeId:1,orderNo:sameNo,payableAmount:100,operatorType:'SYSTEM'})
);
const resultsB = await Promise.all(tasksB);
const okB = resultsB.filter(r=>r.code===0).length;
const hitB = resultsB.filter(r=>r.data&&r.data.idempotentHit).length;
console.log('  成功=' + okB + ' 幂等命中=' + hitB);
let afterB = (await get('/balance?brandId=1&memberId='+memberId)).data.principalBalance;
console.log('  扣款后余额=' + afterB + ' 实际扣减=' + (beforeB-afterB) + '（期望100）');

console.log('');
console.log('=== 场景C：并发超余额扣款（余额' + afterB + '，20笔各2000分，不能扣成负数）===');
const tasksC = Array.from({length:20}, (_,i) =>
  post('/consume-out', {brandId:1,memberId,storeId:1,orderNo:'CONC-C-'+stamp+'-'+i,payableAmount:2000,operatorType:'SYSTEM'})
);
const resultsC = await Promise.all(tasksC);
const okC = resultsC.filter(r=>r.code===0).length;
const rejC = resultsC.filter(r=>r.code!==0).length;
console.log('  成功=' + okC + ' 拒绝=' + rejC);
let afterC = (await get('/balance?brandId=1&memberId='+memberId)).data.principalBalance;
console.log('  最终余额=' + afterC + '（必须>=0，期望=' + (afterB-Math.floor(afterB/2000)*2000) + '）');
console.log('  是否出现负余额: ' + (afterC < 0 ? '是【严重错误】' : '否 ✅'));
