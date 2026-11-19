
import fs from 'node:fs';
const env = fs.readFileSync('H:\\奶茶店会员与储值营销系统\\backend\\infrastructure\\docker\\.env.docker','utf8');
const TOKEN = env.match(/^INTERNAL_TOKEN=(.*)$/m)[1].trim();
const W = 'http://127.0.0.1:9004/api/wallet/internal';
const H = {'X-Internal-Token':TOKEN};

async function get(path) { const r = await fetch(W+path,{headers:H}); return r.json(); }
async function post(path, body) {
  const params = new URLSearchParams();
  Object.entries(body||{}).forEach(([k,v])=>{ if(v!==null&&v!==undefined) params.set(k,String(v)); });
  const r = await fetch(W+path+'?'+params.toString(),{method:'POST',headers:{...H,'Content-Type':'application/x-www-form-urlencoded'}});
  return r.json();
}

console.log('=== 1. 冻结前余额 ===');
let j = await get('/available?brandId=1&memberId=2');
console.log('  ' + JSON.stringify(j.data));

const orderNo = 'FREEZE-TEST-' + Date.now();
console.log('');
console.log('=== 2. 冻结 100 分（实付50+赠送50）===');
j = await post('/freeze', {brandId:1,memberId:2,orderNo,freezePrincipal:50,freezeGift:50,expireMinutes:10});
console.log('  code=' + j.code + ' freezeId=' + j.data);

console.log('');
console.log('=== 3. 冻结后可用余额（账户不变，可用减少）===');
j = await get('/available?brandId=1&memberId=2');
console.log('  ' + JSON.stringify(j.data));

console.log('');
console.log('=== 4. 重复冻结同一订单（幂等）===');
j = await post('/freeze', {brandId:1,memberId:2,orderNo,freezePrincipal:50,freezeGift:50});
console.log('  code=' + j.code + ' freezeId=' + j.data);

console.log('');
console.log('=== 5. 确认冻结扣款（写流水）===');
j = await post('/freeze/confirm', {brandId:1,memberId:2,orderNo,storeId:1,operatorId:1});
console.log('  code=' + j.code + ' principalChange=' + (j.data?j.data.principalChange:'?') + ' giftChange=' + (j.data?j.data.giftChange:'?'));

console.log('');
console.log('=== 6. 确认后余额 ===');
j = await get('/available?brandId=1&memberId=2');
console.log('  ' + JSON.stringify(j.data));

// 第二个订单测试释放
const order2 = 'FREEZE-RELEASE-' + Date.now();
console.log('');
console.log('=== 7. 冻结第二笔 + 释放 ===');
j = await post('/freeze', {brandId:1,memberId:2,orderNo:order2,freezePrincipal:100,freezeGift:0,expireMinutes:10});
console.log('  冻结 code=' + j.code);
j = await get('/available?brandId=1&memberId=2');
console.log('  释放前可用实付=' + j.data.availablePrincipal);
j = await post('/freeze/release', {brandId:1,memberId:2,orderNo:order2,reason:'订单支付失败'});
console.log('  释放 code=' + j.code + ' result=' + j.data);
j = await get('/available?brandId=1&memberId=2');
console.log('  释放后可用实付=' + j.data.availablePrincipal);

console.log('');
console.log('=== 8. 余额不足冻结（应拒绝）===');
j = await post('/freeze', {brandId:1,memberId:2,orderNo:'FREEZE-FAIL-'+Date.now(),freezePrincipal:99999999,freezeGift:0});
console.log('  code=' + j.code + ' msg=' + (j.message||''));
