import fs from 'node:fs';
const ROOT='H:\\奶茶店会员与储值营销系统';
const envTxt=fs.readFileSync(ROOT+'\\backend\\infrastructure\\docker\\.env.docker','utf8');
const ENV={}; envTxt.split(/\r?\n/).forEach(l=>{const m=l.match(/^([A-Z_]+)=(.*)$/);if(m)ENV[m[1]]=m[2];});
const GW='http://127.0.0.1:9000';
const MKT='http://127.0.0.1:9007';
// 内部令牌必须取真实配置值；写死默认值会得到 10402
const INTERNAL=ENV.INTERNAL_TOKEN;
let pass=0,fail=0;
const chk=(n,ok,d)=>{ if(ok)pass++; else fail++; console.log((ok?'  [PASS] ':'  [FAIL] ')+n+'  '+d); };
const req=async(base,m,p,{token,body,headers}={})=>{
  const h={'Content-Type':'application/json',...(headers||{})};
  if(token) h.Authorization='Bearer '+token;
  const r=await fetch(base+p,{method:m,headers:h,body:body===undefined?undefined:JSON.stringify(body)});
  const t=await r.text(); let j=null; try{j=JSON.parse(t);}catch{}
  return {status:r.status,json:j,text:t.slice(0,200)};
};
const call=(m,p,o)=>req(GW,m,p,o);
const inner=(m,p)=>req(MKT,m,p,{headers:{'X-Internal-Token':INTERNAL}});

console.log('=== 0. 重新登录（拿新授予的营销权限）===');
let r=await call('POST','/api/admin/auth/login',{body:{username:'admin',password:'TeaClub@123'}});
const adminTok=r.json?.data?.accessToken;
const perms=JSON.parse(Buffer.from(adminTok.split('.')[1],'base64url').toString()).permissions||[];
chk('admin 权限含 marketing',perms.filter(p=>p.startsWith('marketing:')).length>=17,'营销权限数='+perms.filter(p=>p.startsWith('marketing:')).length);

r=await call('POST','/api/auth/wx-login',{body:{code:'cp-'+Date.now()}});
const mtok=r.json?.data?.token, mid=r.json?.data?.memberId;
chk('会员登录',!!mtok,'memberId='+mid);

console.log('');
console.log('=== 1. 管理端模板列表 ===');
r=await call('GET','/api/admin/marketing/coupons/templates?pageNo=1&pageSize=10',{token:adminTok});
chk('模板列表可用',r.json?.code===0,'total='+r.json?.data?.total+' '+(r.json?.message||''));

console.log('');
console.log('=== 2. 领券（限领 2，连领两次应都成功）===');
r=await call('POST','/api/marketing/coupons/receive?templateId=1&qty=1',{token:mtok});
const c1=r.json?.data?.[0];
chk('第 1 次领券',r.json?.code===0,'couponNo='+(c1?.couponNo||'')+' '+(r.json?.message||''));
await new Promise(x=>setTimeout(x,1000));
r=await call('POST','/api/marketing/coupons/receive?templateId=1&qty=1',{token:mtok});
const c2=r.json?.data?.[0];
chk('第 2 次领券（限领2内，应成功）',r.json?.code===0,'couponNo='+(c2?.couponNo||'')+' code='+r.json?.code+' '+(r.json?.message||''));
await new Promise(x=>setTimeout(x,200));
r=await call('POST','/api/marketing/coupons/receive?templateId=1&qty=1',{token:mtok});
chk('第 3 次被限领拒绝',r.json?.code!==0,'code='+r.json?.code+' msg='+(r.json?.message||''));

console.log('');
console.log('=== 3. 试算（直连服务，内部令牌）===');
r=await inner('POST','/api/marketing/internal/coupons/quote?couponNo='+(c1?.couponNo||'')+'&memberId='+mid+'&orderAmount=8000');
chk('满 50 减 10 在 80 元订单可用',r.json?.data?.available===true,'可用='+r.json?.data?.available+' 抵扣='+r.json?.data?.discountAmount);
r=await inner('POST','/api/marketing/internal/coupons/quote?couponNo='+(c1?.couponNo||'')+'&memberId='+mid+'&orderAmount=3000');
chk('未满门槛不可用',r.json?.data?.available===false,'原因='+r.json?.data?.reasonCode);

console.log('');
console.log('=== 4. 锁定 → 核销 → 幂等 ===');
const orderNo='ORDT'+Date.now();
r=await inner('POST','/api/marketing/internal/coupons/lock?couponNo='+(c1?.couponNo||'')+'&memberId='+mid+'&orderNo='+orderNo);
chk('锁定成功',r.json?.data===true,'ok');
r=await inner('POST','/api/marketing/internal/coupons/lock?couponNo='+(c1?.couponNo||'')+'&memberId='+mid+'&orderNo='+orderNo);
chk('重复锁定幂等',r.json?.data===true,'ok');
r=await inner('POST','/api/marketing/internal/coupons/use?couponNo='+(c1?.couponNo||'')+'&orderNo='+orderNo+'&discountAmount=1000');
chk('核销成功',r.json?.data===true,'ok');
r=await inner('POST','/api/marketing/internal/coupons/use?couponNo='+(c1?.couponNo||'')+'&orderNo='+orderNo+'&discountAmount=1000');
chk('重复核销幂等',r.json?.data===true,'ok');
r=await inner('POST','/api/marketing/internal/coupons/use?couponNo='+(c1?.couponNo||'')+'&orderNo=OTHER&discountAmount=1000');
chk('其他订单核销被拒',r.json?.code!==0,'code='+r.json?.code+' msg='+(r.json?.message||''));

console.log('');
console.log('=== 5. 订单取消释放券 ===');
const oc='ORDR'+Date.now();
r=await inner('POST','/api/marketing/internal/coupons/lock?couponNo='+(c2?.couponNo||'')+'&memberId='+mid+'&orderNo='+oc);
chk('锁定第2张',r.json?.data===true,'ok');
r=await inner('POST','/api/marketing/internal/coupons/release?orderNo='+oc);
chk('按订单释放成功',r.json?.data===true,'ok');
r=await call('GET','/api/marketing/coupons/mine?status=UNUSED',{token:mtok});
chk('释放后券回到 UNUSED',(r.json?.data||[]).some(c=>c.couponNo===c2?.couponNo),'UNUSED 数='+(r.json?.data||[]).length);

console.log('');
console.log('=== 汇总: PASS='+pass+' FAIL='+fail+' ===');
if(fail>0) process.exitCode=1;
