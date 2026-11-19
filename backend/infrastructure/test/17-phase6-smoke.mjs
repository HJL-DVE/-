
import fs from 'node:fs';
const ROOT='H:\\奶茶店会员与储值营销系统';
const GW='http://127.0.0.1:9000';
let pass=0,fail=0; const rows=[];
const chk=(name,ok,detail)=>{ if(ok)pass++; else fail++; rows.push({name,ok,detail}); console.log((ok?'  [PASS] ':'  [FAIL] ')+name+'  '+detail); };

const call=async(method,path,{token,body,headers:extra}={})=>{
  const h={'Content-Type':'application/json',...(extra||{})};
  if(token) h.Authorization='Bearer '+token;
  const res=await fetch(GW+path,{method,headers:h,body:body===undefined?undefined:JSON.stringify(body)});
  const text=await res.text();
  let json=null; try{ json=JSON.parse(text);}catch{}
  return {status:res.status, json, text:text.slice(0,300)};
};

// 等网关与各服务就绪
let ready=false;
for(let i=0;i<40;i++){
  try{ const r=await fetch(GW+'/actuator/health'); if(r.ok){ ready=true; break; } }catch{}
  await new Promise(x=>setTimeout(x,2000));
}
console.log('网关就绪='+ready);

console.log('');
console.log('=== 1. 管理端登录 ===');
const login=await call('POST','/api/admin/auth/login',{body:{username:'admin',password:'TeaClub@123'}});
const adminTok=login.json?.data?.accessToken;
chk('管理端登录',!!adminTok,'status='+login.status+' token='+(adminTok?adminTok.slice(0,18)+'...':'(无)'));

console.log('');
console.log('=== 2. 钱包管理端接口（本阶段新增）===');
for(const [name,path] of [
  ['钱包分页','/api/admin/wallet/accounts?pageNo=1&pageSize=5'],
  ['钱包统计','/api/admin/wallet/stats'],
  ['资金流水','/api/admin/wallet/transactions?pageNo=1&pageSize=5'],
  ['充值方案','/api/admin/wallet/plans'],
  ['充值订单','/api/admin/wallet/recharges?pageNo=1&pageSize=5'],
  ['可用方案','/api/admin/wallet/plans/available?storeId=1'],
]){
  const r=await call('GET',path,{token:adminTok});
  const code=r.json?.code;
  chk(name+' 经网关可用', code===0, 'HTTP='+r.status+' code='+code+' '+(code===0?''+(Array.isArray(r.json.data)?('list='+r.json.data.length):(r.json.data&&r.json.data.records?'records='+r.json.data.records.length+',total='+r.json.data.total:JSON.stringify(r.json.data).slice(0,80))):(r.json?.message||r.text)));
}

console.log('');
console.log('=== 3. 统计接口（仪表盘）===');
for(const [name,path] of [
  ['会员统计','/api/admin/member/members/stats'],
  ['订单统计','/api/admin/order/stats/summary?days=7'],
]){
  const r=await call('GET',path,{token:adminTok});
  chk(name, r.json?.code===0, 'HTTP='+r.status+' code='+r.json?.code+' '+JSON.stringify(r.json?.data).slice(0,160));
}

console.log('');
console.log('=== 4. 会员码查询（收银台扫码）===');
{
  const r=await call('GET','/api/admin/member/members/by-code?code=M20250001',{token:adminTok});
  chk('按会员号查会员', r.json?.code===0 && r.json?.data?.id, 'HTTP='+r.status+' code='+r.json?.code+' member='+(r.json?.data?.memberNo||'(无)'));
  const r2=await call('GET','/api/admin/member/members/by-code?code=NOT_EXIST_XYZ',{token:adminTok});
  chk('不存在的会员号返回失败', r2.json?.code!==0, 'code='+r2.json?.code+' msg='+r2.json?.message);
}

console.log('');
console.log('=== 5. 会员端：匿名浏览（无需登录）===');
for(const [name,path] of [
  ['门店列表','/api/auth/stores'],
  ['门店详情','/api/auth/stores/1'],
  ['商品分类','/api/product/stores/1/categories'],
  ['商品列表','/api/product/stores/1/spus?pageNo=1&pageSize=5'],
  ['热门商品','/api/product/hot?storeId=1&limit=6'],
]){
  const r=await call('GET',path);
  const ok=r.json?.code===0;
  chk(name+' 匿名可用', ok, 'HTTP='+r.status+' code='+r.json?.code+' '+(ok?JSON.stringify(r.json.data).slice(0,110):(r.json?.message||r.text)));
}

console.log('');
console.log('=== 6. 会员端：微信登录（Mock code2session）===');
let memberTok=null, memberId=null;
{
  const r=await call('POST','/api/auth/wx-login',{body:{code:'itest-code-001',nickname:'联调测试',avatarUrl:'https://x/y.png'}});
  memberTok=r.json?.data?.token; memberId=r.json?.data?.memberId;
  chk('微信登录换取会员令牌',!!memberTok,'HTTP='+r.status+' code='+r.json?.code+' memberId='+memberId+' memberNo='+r.json?.data?.memberNo+' isNew='+r.json?.data?.isNew);
  if(r.json?.code!==0) chk('失败原因',false,r.json?.message||r.text);
}

console.log('');
console.log('=== 7. 会员端：登录后接口（会员令牌过网关）===');
if(memberTok){
  for(const [name,path] of [
    ['会员资料','/api/member/profile'],
    ['会员等级','/api/member/levels'],
    ['钱包账户','/api/wallet/account'],
    ['我的流水','/api/wallet/transactions?pageNo=1&pageSize=5'],
    ['充值套餐','/api/wallet/plans?storeId=1'],
    ['我的充值记录','/api/wallet/recharges?pageNo=1&pageSize=5'],
    ['我的订单','/api/order/orders?pageNo=1&pageSize=5'],
  ]){
    const r=await call('GET',path,{token:memberTok});
    chk(name, r.json?.code===0, 'HTTP='+r.status+' code='+r.json?.code+' '+JSON.stringify(r.json?.data).slice(0,120));
  }
} else {
  chk('会员端接口', false, '跳过：无会员令牌');
}

console.log('');
console.log('=== 8. 令牌隔离：会员令牌不能访问管理端 ===');
if(memberTok){
  const r=await call('GET','/api/admin/wallet/accounts',{token:memberTok});
  const denied=(x)=>x.status===401||x.json?.code===401||x.json?.code===10201||x.json?.code===403;
  chk('会员令牌访问管理端被拒', denied(r), 'HTTP='+r.status+' code='+r.json?.code+' msg='+r.json?.message);
  const r2=await call('GET','/api/admin/member/members/stats',{token:memberTok});
  chk('会员令牌访问管理端统计被拒', denied(r2), 'HTTP='+r2.status+' code='+r2.json?.code+' msg='+r2.json?.message);
}

console.log('');
console.log('=== 9. 无令牌访问受保护接口 ===');
{
  const r=await fetch(GW+'/api/wallet/account');
  chk('无令牌被网关拦截', r.status===401, 'HTTP='+r.status);
  const r2=await fetch(GW+'/api/admin/wallet/accounts');
  chk('无令牌访问管理端被拦截', r2.status===401, 'HTTP='+r2.status);
}

console.log('');
console.log('=== 汇总: PASS='+pass+' FAIL='+fail+' ===');
fs.writeFileSync(ROOT+'\\backend\\infrastructure\\test\\17-smoke.json', JSON.stringify({pass,fail,rows},null,1));
if(fail>0) process.exitCode=1;
