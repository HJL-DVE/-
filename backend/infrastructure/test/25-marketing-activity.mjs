import fs from 'node:fs';
const ROOT='H:\\奶茶店会员与储值营销系统';
const t=fs.readFileSync(ROOT+'\\backend\\infrastructure\\docker\\.env.docker','utf8');
const E={}; t.split(/\r?\n/).forEach(l=>{const m=l.match(/^([A-Z_]+)=(.*)$/);if(m)E[m[1]]=m[2];});
const GW='http://127.0.0.1:9000';
const INTERNAL=E.INTERNAL_TOKEN;
let pass=0,fail=0;
const chk=(n,ok,d)=>{ if(ok)pass++; else fail++; console.log((ok?'  [PASS] ':'  [FAIL] ')+n+'  '+d); };
const req=async(m,p,{token,body,headers}={})=>{
  const h={'Content-Type':'application/json',...(headers||{})};
  if(token) h.Authorization='Bearer '+token;
  const r=await fetch(GW+p,{method:m,headers:h,body:body===undefined?undefined:JSON.stringify(body)});
  const x=await r.text(); let j=null; try{j=JSON.parse(x);}catch{}
  return {status:r.status,json:j,text:x.slice(0,180)};
};

console.log('=== 0. 登录 ===');
let r=await req('POST','/api/admin/auth/login',{body:{username:'admin',password:'TeaClub@123'}});
const adminTok=r.json?.data?.accessToken;
chk('管理端登录',!!adminTok,'ok');
r=await req('POST','/api/auth/wx-login',{body:{code:'act-'+Date.now()}});
const mtok=r.json?.data?.token, mid=r.json?.data?.memberId;
chk('会员登录',!!mtok,'memberId='+mid);

console.log('');
console.log('=== 1. 券模板 CRUD ===');
r=await req('POST','/api/admin/marketing/coupons/templates',{token:adminTok,body:{
  couponName:'活动满减券30-5', couponType:'FULL_REDUCTION', scopeType:'ALL',
  faceValue:500, thresholdAmount:3000, totalCount:100, perMemberLimit:1,
  validType:'RELATIVE', validDays:30, status:'ENABLED', remark:'批次3测试'
}});
const tplId=r.json?.data;
chk('新建模板',r.json?.code===0&&!!tplId,'templateId='+tplId+' '+(r.json?.message||''));
r=await req('GET','/api/admin/marketing/coupons/templates/'+tplId,{token:adminTok});
chk('模板详情',r.json?.data?.id===tplId,'name='+r.json?.data?.couponName+' status='+r.json?.data?.status);
r=await req('PUT','/api/admin/marketing/coupons/templates/'+tplId,{token:adminTok,body:{
  couponName:'活动满减券30-5(改名)', couponType:'FULL_REDUCTION', scopeType:'ALL',
  faceValue:500, thresholdAmount:3000, totalCount:200, perMemberLimit:2,
  validType:'RELATIVE', validDays:30, status:'ENABLED'
}});
chk('编辑模板',r.json?.code===0,'code='+r.json?.code+' '+(r.json?.message||''));
r=await req('PUT','/api/admin/marketing/coupons/templates/'+tplId+'/status?status=DISABLED',{token:adminTok});
chk('停用模板',r.json?.code===0,'ok');
r=await req('PUT','/api/admin/marketing/coupons/templates/'+tplId+'/status?status=ENABLED',{token:adminTok});
chk('启用模板',r.json?.code===0,'ok');

console.log('');
console.log('=== 2. 非法模板参数被拒 ===');
r=await req('POST','/api/admin/marketing/coupons/templates',{token:adminTok,body:{
  couponName:'非法满减', couponType:'FULL_REDUCTION', faceValue:5000, thresholdAmount:3000, status:'ENABLED'
}});
chk('面额>=门槛 被拒',r.json?.code!==0,'msg='+(r.json?.message||''));
r=await req('POST','/api/admin/marketing/coupons/templates',{token:adminTok,body:{
  couponName:'非法券类型', couponType:'NOT_EXIST', faceValue:100, status:'ENABLED'
}});
chk('非法券类型 被拒',r.json?.code!==0,'msg='+(r.json?.message||''));

console.log('');
console.log('=== 3. 新建营销活动（满赠 + 门店专属 + 新人礼包）===');
const now=new Date();
const iso=(d)=>d.toISOString().slice(0,19);
const start=iso(new Date(now.getTime()-3600000));
const end=iso(new Date(now.getTime()+7*86400000));
const cfg=JSON.stringify({thresholdAmount:3000,couponTemplateIds:[tplId],points:50,description:'满30送券+50积分'});

r=await req('POST','/api/admin/marketing/activities',{token:adminTok,body:{
  activityName:'满30赠券+积分', activityType:'FULL_GIFT', scopeType:'ALL',
  startTime:start, endTime:end, configJson:cfg, totalQuota:0, perMemberLimit:3,
  priority:10, status:'ENABLED', description:'批次3测试活动'
}});
const actId=r.json?.data;
chk('新建满赠活动',r.json?.code===0&&!!actId,'activityId='+actId+' '+(r.json?.message||''));

r=await req('POST','/api/admin/marketing/activities',{token:adminTok,body:{
  activityName:'新人礼包', activityType:'NEW_MEMBER_GIFT', scopeType:'ALL',
  startTime:start, endTime:end,
  configJson:JSON.stringify({couponTemplateIds:[tplId],points:100}),
  totalQuota:0, perMemberLimit:1, status:'ENABLED'
}});
chk('新建新人礼包',r.json?.code===0,'id='+r.json?.data);

r=await req('POST','/api/admin/marketing/activities',{token:adminTok,body:{
  activityName:'门店专属活动', activityType:'STORE_SPECIAL', scopeType:'STORE', storeIds:[1],
  startTime:start, endTime:end, configJson:JSON.stringify({points:20}),
  totalQuota:0, perMemberLimit:5, status:'ENABLED'
}});
chk('新建门店活动',r.json?.code===0,'id='+r.json?.data);

console.log('');
console.log('=== 4. 活动列表（管理端 / 会员端）===');
r=await req('GET','/api/admin/marketing/activities',{token:adminTok});
chk('管理端活动列表',r.json?.code===0,'count='+(r.json?.data||[]).length);
r=await req('GET','/api/marketing/activities?storeId=1',{token:mtok});
chk('会员端活动列表',r.json?.code===0,'count='+(r.json?.data||[]).length);

console.log('');
console.log('=== 5. 订单触发满赠活动（走 ORDER_PAID 事件）===');
r=await req('POST','/api/wallet/recharges',{token:mtok,body:{planId:1,storeId:1}});
const rc=r.json?.data;
r=await req('POST','/api/payment/create',{token:mtok,body:{bizType:'RECHARGE',bizNo:rc?.rechargeNo}});
const pay=r.json?.data;
await req('POST','/api/payment/mock/notify',{body:{paymentNo:pay?.paymentNo,amount:pay?.amount}});
await new Promise(x=>setTimeout(x,2500));

const before=await req('GET','/api/marketing/coupons/mine',{token:mtok});
const beforeCnt=(before.json?.data||[]).length;
const ptsBefore=await req('GET','/api/marketing/points/account',{token:mtok});
const pts0=ptsBefore.json?.data?.totalPoints||0;

r=await req('POST','/api/order/orders',{token:mtok,body:{storeId:1,items:[{skuId:1,quantity:2}]}});
const order=r.json?.data;
r=await req('POST','/api/order/orders/'+order?.orderNo+'/pay-wallet',{token:mtok,body:{}});
chk('订单支付',r.json?.data?.status==='PAID','orderNo='+order?.orderNo+' 应付='+order?.payableAmount);

let granted=false, couponCnt=beforeCnt, ptsNow=pts0;
for(let i=0;i<15;i++){
  await new Promise(x=>setTimeout(x,1200));
  const c=await req('GET','/api/marketing/coupons/mine',{token:mtok});
  couponCnt=(c.json?.data||[]).length;
  const p=await req('GET','/api/marketing/points/account',{token:mtok});
  ptsNow=p.json?.data?.totalPoints||0;
  if(couponCnt>beforeCnt && ptsNow>pts0){ granted=true; break; }
}
chk('满赠活动发放了券',couponCnt>beforeCnt,beforeCnt+' -> '+couponCnt);
chk('满赠活动发放了积分',ptsNow>pts0,pts0+' -> '+ptsNow);

console.log('');
console.log('=== 6. 积分商城：建商品 + 兑换 ===');
// 商品定价 30 分，保证当前会员（约 35 分）能兑换成功
r=await req('POST','/api/admin/marketing/points-goods',{token:adminTok,body:{
  goodsName:'30积分兑10元券', goodsType:'COUPON', pointsPrice:30,
  stock:10, templateId:tplId, exchangeLimit:1, status:'ENABLED'
}});
const goodsId=r.json?.data;
chk('新建商城商品',r.json?.code===0&&!!goodsId,'goodsId='+goodsId+' '+(r.json?.message||''));

r=await req('GET','/api/marketing/points-goods',{token:mtok});
chk('会员端商品列表',r.json?.code===0,'count='+(r.json?.data||[]).length);

const pBefore=await req('GET','/api/marketing/points/account',{token:mtok});
const p0=pBefore.json?.data?.totalPoints||0;
r=await req('POST','/api/marketing/points-goods/'+goodsId+'/exchange',{token:mtok});
const ex=r.json?.data;
chk('兑换成功',r.json?.code===0&&!!ex?.exchangeNo,'exchangeNo='+ex?.exchangeNo+' 花费='+ex?.pointsCost+' 券='+(ex?.couponNo||'无')+' '+(r.json?.message||''));
chk('积分已扣减',(ex?.remainPoints??p0)===p0-30,p0+' -> '+ex?.remainPoints);

r=await req('POST','/api/marketing/points-goods/'+goodsId+'/exchange',{token:mtok});
chk('超限兑换被拒',r.json?.code!==0,'msg='+(r.json?.message||''));

console.log('');
console.log('=== 7. 等级权益 ===');
r=await req('POST','/api/admin/marketing/level-benefits',{token:adminTok,body:{
  levelId:3, benefitCode:'POINT_RATE', benefitName:'金卡积分倍率', benefitValue:'150', status:'ENABLED'
}});
const benefitId=r.json?.data;
chk('新增等级权益',r.json?.code===0&&!!benefitId,'benefitId='+benefitId+' '+(r.json?.message||''));
r=await req('POST','/api/admin/marketing/level-benefits',{token:adminTok,body:{
  levelId:3, benefitCode:'POINT_RATE', benefitName:'金卡积分倍率(改)', benefitValue:'180', status:'ENABLED'
}});
chk('同等级同编码更新而非新增',r.json?.data===benefitId,'id='+r.json?.data);
r=await req('GET','/api/marketing/level-benefits/level/3',{token:mtok});
chk('会员端查等级权益',r.json?.code===0&&(r.json?.data||[]).length>=1,'count='+(r.json?.data||[]).length);
// 测完恢复金卡倍率 150（避免污染后续 32 号倍率测试）
r=await req('POST','/api/admin/marketing/level-benefits',{token:adminTok,body:{
  levelId:3, benefitCode:'POINT_RATE', benefitName:'金卡积分倍率', benefitValue:'150', status:'ENABLED'
}});

console.log('');
console.log('=== 8. 活动过期处理 ===');
r=await req('POST','/api/admin/marketing/activities',{token:adminTok,body:{
  activityName:'已过期活动', activityType:'FESTIVAL', scopeType:'ALL',
  startTime:'2026-01-01T00:00:00', endTime:'2026-01-02T00:00:00',
  configJson:'{}', totalQuota:0, perMemberLimit:1, status:'ENABLED'
}});
const expiredId=r.json?.data;
chk('建一个时间已过的活动',!!expiredId,'id='+expiredId);
console.log('  （等定时任务把它置为 EXPIRED，最多 30 分钟；此处只验证记录已建）');
r=await req('GET','/api/admin/marketing/activities/'+expiredId,{token:adminTok});
chk('活动可查询',r.json?.code===0,'status='+r.json?.data?.status);

console.log('');
console.log('=== 9. 清理：停用本次创建的活动 ===');
// 不清理的话，活动会随每次回归累积，导致后续订单触发一堆旧活动，
// 积分/券数量虚高，回归结果不可重复。
{
  const list=await req('GET','/api/admin/marketing/activities',{token:adminTok});
  const mine=(list.json?.data||[]).filter(a=>
    (a.activityName||'').includes('满30赠券')||
    (a.activityName||'').includes('新人礼包')||
    (a.activityName||'').includes('门店专属活动')||
    (a.activityName||'').includes('已过期活动'));
  let disabled=0;
  for(const a of mine){
    const rr=await req('PUT','/api/admin/marketing/activities/'+a.id+'/status?status=DISABLED',{token:adminTok});
    if(rr.json?.code===0) disabled++;
  }
  chk('本次创建的活动已停用',disabled===mine.length,'停用 '+disabled+'/'+mine.length);
}

console.log('');
console.log('=== 汇总: PASS='+pass+' FAIL='+fail+' ===');
console.log('memberId='+mid+' templateId='+tplId+' activityId='+actId);
if(fail>0) process.exitCode=1;
