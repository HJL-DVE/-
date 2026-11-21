// 本脚本验证 V6 种子活动真实生效（活动数据由 Flyway V6 幂等种子写入）
const GW='http://127.0.0.1:9000';
let pass=0,fail=0;
const chk=(n,ok,d)=>{if(ok)pass++;else fail++;console.log((ok?'  [PASS] ':'  [FAIL] ')+n+'  '+d)};
const req=async(m,p,{token,body}={})=>{const h={'Content-Type':'application/json'};if(token)h.Authorization='Bearer '+token;const r=await fetch(GW+p,{method:m,headers:h,body:body===undefined?undefined:JSON.stringify(body)});try{return await r.json()}catch{return {}}};
const adm=await req('POST','/api/admin/auth/login',{body:{username:'admin',password:'TeaClub@123'}});
const tok=adm.data?.accessToken;
const list=await req('GET','/api/admin/marketing/activities',{token:tok});
chk('管理端活动列表',list.code===0&&(list.data||[]).length>=8,'共 '+(list.data||[]).length+' 条');
const m=await req('POST','/api/auth/wx-login',{body:{code:'actv2-'+Date.now()}});
const mtok=m.data?.token;
const ma=await req('GET','/api/marketing/activities?storeId=1',{token:mtok});
chk('会员端可见活动',ma.code===0&&(ma.data||[]).length>=6,'可见 '+(ma.data||[]).length+' 条');
const nowStr=new Date().toISOString().slice(0,10);
const fest=(list.data||[]).find(a=>a.activityType==='FESTIVAL'&&a.status==='ENABLED');
if(fest){
  const before=(await req('GET','/api/marketing/points/account',{token:mtok})).data?.totalPoints||0;
  const join=await req('POST','/api/marketing/activities/'+fest.id+'/join?storeId=1',{token:mtok});
  chk('参与节日活动',join.code===0,'code='+join.code+' '+(join.message||''));
  await new Promise(r=>setTimeout(r,1500));
  const after=(await req('GET','/api/marketing/points/account',{token:mtok})).data?.totalPoints||0;
  chk('活动积分真实到账',after>before,before+' -> '+after);
}
const stats=await req('GET','/api/report/marketing/overview',{token:tok});
chk('活动统计接口可用',stats.code===0,'券领取='+stats.data?.couponReceived+' 积分发放='+stats.data?.pointsEarned);
console.log('');console.log('汇总: PASS='+pass+' FAIL='+fail);
if(fail>0) process.exitCode=1;
