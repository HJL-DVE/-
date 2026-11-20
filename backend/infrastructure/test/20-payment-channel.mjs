const GW='http://127.0.0.1:9000';
let pass=0,fail=0;
const chk=(n,ok,d)=>{ if(ok)pass++; else fail++; console.log((ok?'  [PASS] ':'  [FAIL] ')+n+'  '+d); };
const call=async(m,p,{token,body}={})=>{
  const h={'Content-Type':'application/json'};
  if(token) h.Authorization='Bearer '+token;
  const r=await fetch(GW+p,{method:m,headers:h,body:body===undefined?undefined:JSON.stringify(body)});
  const t=await r.text(); let j=null; try{j=JSON.parse(t);}catch{}
  return {status:r.status,json:j,text:t.slice(0,200)};
};

let r=await call('POST','/api/admin/auth/login',{body:{username:'admin',password:'TeaClub@123'}});
const tok=r.json?.data?.accessToken;
console.log('=== 管理端（收银台）：不传 channel ===');
r=await call('POST','/api/admin/order/orders',{token:tok,body:{storeId:1,memberId:1,items:[{skuId:1,quantity:1}]}});
const o1=r.json?.data;
r=await call('POST','/api/admin/payment/create',{token:tok,body:{bizType:'ORDER',bizNo:o1?.orderNo}});
const p1=r.json?.data;
chk('默认渠道=Native', p1?.channel==='WECHAT_NATIVE', 'channel='+p1?.channel+' codeUrl='+(p1?.codeUrl?'有':'无')+' invokeParams='+(p1?.invokeParams?'有':'无'));
chk('返回 codeUrl', !!p1?.codeUrl, 'codeUrl='+(p1?.codeUrl||'无'));

console.log('');
console.log('=== 管理端：显式传 JSAPI ===');
r=await call('POST','/api/admin/order/orders',{token:tok,body:{storeId:1,memberId:1,items:[{skuId:1,quantity:1}]}});
const o2=r.json?.data;
r=await call('POST','/api/admin/payment/create',{token:tok,body:{bizType:'ORDER',bizNo:o2?.orderNo,channel:'WECHAT_JSAPI'}});
const p2=r.json?.data;
chk('显式 JSAPI 生效', p2?.channel==='WECHAT_JSAPI', 'channel='+p2?.channel+' invokeParams='+(p2?.invokeParams?'有':'无')+' codeUrl='+(p2?.codeUrl?'有':'无'));

console.log('');
console.log('=== 非法 channel 应被拒 ===');
r=await call('POST','/api/admin/payment/create',{token:tok,body:{bizType:'ORDER',bizNo:o2?.orderNo,channel:'BITCOIN'}});
chk('非法渠道被拒', r.json?.code!==0, 'code='+r.json?.code+' msg='+(r.json?.message||''));

console.log('');
console.log('=== 会员端：不传 channel（有 openid → JSAPI）===');
r=await call('POST','/api/auth/wx-login',{body:{code:'ch-'+Date.now()}});
const mtok=r.json?.data?.token;
r=await call('POST','/api/wallet/recharges',{token:mtok,body:{planId:1,storeId:1}});
const rc=r.json?.data;
r=await call('POST','/api/payment/create',{token:mtok,body:{bizType:'RECHARGE',bizNo:rc?.rechargeNo,openid:'mock_openid_x'}});
const p3=r.json?.data;
chk('会员端默认 JSAPI', p3?.channel==='WECHAT_JSAPI', 'channel='+p3?.channel+' invokeParams='+(p3?.invokeParams?'有':'无'));

console.log('');
console.log('=== 会员端：无 openid → 降级 Native ===');
r=await call('POST','/api/wallet/recharges',{token:mtok,body:{planId:1,storeId:1}});
const rc2=r.json?.data;
r=await call('POST','/api/payment/create',{token:mtok,body:{bizType:'RECHARGE',bizNo:rc2?.rechargeNo}});
const p4=r.json?.data;
chk('无 openid 降级 Native', p4?.channel==='WECHAT_NATIVE', 'channel='+p4?.channel+' codeUrl='+(p4?.codeUrl?'有':'无'));

console.log('');
console.log('=== 汇总: PASS='+pass+' FAIL='+fail+' ===');
if(fail>0) process.exitCode=1;
