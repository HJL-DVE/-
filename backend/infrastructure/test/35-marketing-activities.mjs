import fs from 'node:fs';
const ROOT='H:\\奶茶店会员与储值营销系统';
const t=fs.readFileSync(ROOT+'\\backend\\infrastructure\\docker\\.env.docker','utf8');
const E={}; t.split(/\r?\n/).forEach(l=>{const m=l.match(/^([A-Z_]+)=(.*)$/);if(m)E[m[1]]=m[2];});
const GW='http://127.0.0.1:9000';
let pass=0,fail=0;
const chk=(n,ok,d)=>{ if(ok)pass++; else fail++; console.log((ok?'  [PASS] ':'  [FAIL] ')+n+'  '+d); };
const sleep=(ms)=>new Promise(x=>setTimeout(x,ms));
const req=async(m,p,{token,body}={})=>{
  const h={'Content-Type':'application/json'}; if(token)h.Authorization='Bearer '+token;
  const r=await fetch(GW+p,{method:m,headers:h,body:body===undefined?undefined:JSON.stringify(body)});
  try{return await r.json();}catch{return {};};
};
const adm=await req('POST','/api/admin/auth/login',{body:{username:'admin',password:'TeaClub@123'}});
const tok=adm.data?.accessToken;
// 用固定宽泛区间（相对 DB 服务器时间±1年），避免 Node 进程时区与 MySQL 时区不一致导致活动过期
const start='2020-01-01T00:00:00', end='2030-12-31T23:59:59';
// 本地时间（toISOString 是 UTC，会比北京时间早 8 小时，导致活动已过期）
// 固定区间已是字符串，直接透传
const iso=(d)=>typeof d==='string'?d:(()=>{const p=n=>String(n).padStart(2,'0');return d.getFullYear()+'-'+p(d.getMonth()+1)+'-'+p(d.getDate())+'T'+p(d.getHours())+':'+p(d.getMinutes())+':'+p(d.getSeconds())})();
const acts=[
  {activityName:'新人见面礼',activityType:'NEW_MEMBER_GIFT',scopeType:'ALL',startTime:iso(start),endTime:iso(end),configJson:JSON.stringify({couponTemplateIds:[1],points:100,description:'注册即送满50减10券+100积分'}),totalQuota:0,perMemberLimit:1,priority:10,status:'ENABLED',description:'新人注册自动发放见面礼'},
  {activityName:'生日专享福利',activityType:'BIRTHDAY',scopeType:'ALL',startTime:iso(start),endTime:iso(end),configJson:JSON.stringify({couponTemplateIds:[13],points:200,description:'生日当月送生日券+200积分'}),totalQuota:0,perMemberLimit:1,priority:9,status:'ENABLED',description:'生日当月可领专属券和积分'},
  {activityName:'国庆黄金周',activityType:'FESTIVAL',scopeType:'ALL',startTime:iso(start),endTime:iso(end),configJson:JSON.stringify({couponTemplateIds:[1],points:50,description:'节日下单送券+50积分'}),totalQuota:500,perMemberLimit:3,priority:8,status:'ENABLED',description:'节日限时活动，会员可主动参与'},
  {activityName:'连续签到7天礼',activityType:'CONTINUOUS_SIGN',scopeType:'ALL',startTime:iso(start),endTime:iso(end),configJson:JSON.stringify({continuousDays:7,points:150,couponTemplateIds:[1],description:'连续签到7天送150积分+满减券'}),totalQuota:0,perMemberLimit:1,priority:7,status:'ENABLED',description:'连续签到达标自动发奖'},
  {activityName:'满30送5元券',activityType:'FULL_GIFT',scopeType:'ALL',startTime:iso(start),endTime:end,configJson:JSON.stringify({thresholdAmount:3000,couponTemplateIds:[2],points:30,description:'单笔满30元送5元券+30积分'}),totalQuota:1000,perMemberLimit:5,priority:10,status:'ENABLED',description:'订单支付满额自动赠送'},
  {activityName:'会员积分商城',activityType:'POINTS_MALL',scopeType:'ALL',startTime:iso(start),endTime:iso(end),configJson:JSON.stringify({description:'积分兑换商品长期开放'}),totalQuota:0,perMemberLimit:0,priority:5,status:'ENABLED',description:'长期开放的积分兑换'},
  {activityName:'西湖店专属优惠',activityType:'STORE_SPECIAL',scopeType:'STORE',storeIds:[1],startTime:iso(start),endTime:iso(end),configJson:JSON.stringify({thresholdAmount:2000,points:20,description:'西湖店满20元送20积分'}),totalQuota:300,perMemberLimit:2,priority:6,status:'ENABLED',description:'仅西湖店订单参与'},
  {activityName:'沉睡会员召回',activityType:'SLEEPING_RECALL',scopeType:'ALL',startTime:iso(start),endTime:iso(end),configJson:JSON.stringify({sleepingDays:30,couponTemplateIds:[2],points:50,description:'30天未消费送5元券+50积分'}),totalQuota:200,perMemberLimit:1,priority:4,status:'ENABLED',description:'沉睡会员定向召回'},
];
const ids=[];
// 清理上一轮测试创建的活动（按 AC 前缀 + 测试名），避免重复堆积
const old=(await req('GET','/api/admin/marketing/activities',{token:tok})).data||[];
for(const o of old){
  if(o.activityName && /^(新人见面礼|生日专享福利|国庆黄金周|连续签到7天礼|满30送5元券|会员积分商城|西湖店专属优惠|沉睡会员召回)$/.test(o.activityName)){
    await req('DELETE','/api/admin/marketing/activities/'+o.id,{token:tok});
  }
}
for(const a of acts){
  const r=await req('POST','/api/admin/marketing/activities',{token:tok,body:a});
  chk('创建 '+a.activityType,r.code===0,'id='+r.data+' '+(r.message||''));
  if(r.code===0) ids.push(r.data);
}
const list=await req('GET','/api/admin/marketing/activities',{token:tok});
chk('活动列表至少8条',list.code===0&&(list.data||[]).length>=8,'共 '+(list.data||[]).length+' 条');
const m=await req('POST','/api/auth/wx-login',{body:{code:'actchk-'+Date.now()}});
const mtok=m.data?.token;
const ma=await req('GET','/api/marketing/activities?storeId=1',{token:mtok});
chk('会员端可见活动',ma.code===0&&(ma.data||[]).length>=6,'可见 '+(ma.data||[]).length+' 条');
const fest=(list.data||[]).find(a=>a.activityType==='FESTIVAL');
if(fest){
  const before=(await req('GET','/api/marketing/points/account',{token:mtok})).data?.totalPoints||0;
  const join=await req('POST','/api/marketing/activities/'+fest.id+'/join?storeId=1',{token:mtok});
  chk('参与节日活动',join.code===0,'code='+join.code+' '+(join.message||''));
  await sleep(1200);
  const after=(await req('GET','/api/marketing/points/account',{token:mtok})).data?.totalPoints||0;
  chk('活动积分真实到账',after>before,before+' -> '+after);
} else chk('找到节日活动',false,'未找到');
console.log(''); console.log('=== 汇总: PASS='+pass+' FAIL='+fail+' ===');
console.log('活动ID: '+ids.join(','));
if(fail>0) process.exitCode=1;
