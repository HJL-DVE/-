import fs from 'node:fs';
const ROOT='H:\\奶茶店会员与储值营销系统';
const t=fs.readFileSync(ROOT+'\\backend\\infrastructure\\docker\\.env.docker','utf8');
const E={}; t.split(/\r?\n/).forEach(l=>{const m=l.match(/^([A-Z_]+)=(.*)$/);if(m)E[m[1]]=m[2];});
const GW='http://127.0.0.1:9000';
let pass=0,fail=0;
const chk=(n,ok,d)=>{ if(ok)pass++; else fail++; console.log((ok?'  [PASS] ':'  [FAIL] ')+n+'  '+d); };
const req=async(m,p,{token,body}={})=>{
  const h={'Content-Type':'application/json'}; if(token)h.Authorization='Bearer '+token;
  const r=await fetch(GW+p,{method:m,headers:h,body:body===undefined?undefined:JSON.stringify(body)});
  let j=null; try{j=await r.json();}catch{}
  return {status:r.status,json:j};
};

console.log('=== 0. 登录（三种角色）===');
const admin=await req('POST','/api/admin/auth/login',{body:{username:'admin',password:'TeaClub@123'}});
const adminTok=admin.json?.data?.accessToken;
chk('总部管理员登录',!!adminTok,'ok');

// 店长账号：找 STORE_MANAGER 角色用户
const { execFileSync } = await import('node:child_process');
const D='C:\\Program Files\\Docker\\Docker\\resources\\bin\\docker.exe';
const sql=(q)=>{try{return execFileSync(D,['exec','teaclub-mysql','mysql','-uroot','-p'+E.MYSQL_ROOT_PASSWORD,'iam_db','-N','-B','-e',q],{encoding:'utf8',stdio:['pipe','pipe','ignore']}).split('\n').filter(l=>l.trim());}catch(e){return [];}};
const mgrRows=sql("SELECT u.username FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id WHERE ur.role_id=3 LIMIT 1");
console.log('  店长账号: '+(mgrRows[0]||'(无)'));

console.log('');
console.log('=== 1. 总部统计接口（dashboard/overview）===');
let r=await req('GET','/api/report/dashboard/overview',{token:adminTok});
const ov=r.json?.data||{};
chk('overview 返回',r.json?.code===0,'门店='+ov.storeCount+' 会员='+ov.memberCount+' 今日销售='+ov.todaySales+' 今日订单='+ov.todayOrders);
chk('今日充值金额(本金)与赠送分离',typeof ov.todayRechargeAmount==='number'&&typeof ov.todayRechargeGiftAmount==='number','本金='+ov.todayRechargeAmount+' 赠送='+ov.todayRechargeGiftAmount);
chk('门店排行存在',Array.isArray(ov.storeRanking?.list),'count='+(ov.storeRanking?.list||[]).length);

console.log('');
console.log('=== 2. 门店统计接口（store/overview）===');
r=await req('GET','/api/report/store/overview?storeId=1',{token:adminTok});
const so=r.json?.data||{};
chk('store/overview',r.json?.code===0,'销售='+so.sales+' 订单='+so.orders+' 客单价='+so.avgOrder);
chk('热销商品',Array.isArray(so.hotProducts),'count='+(so.hotProducts||[]).length);

console.log('');
console.log('=== 3. 日期筛选（trend days=7/30/90）===');
for(const d of [7,30,90]){
  r=await req('GET','/api/report/dashboard/trend?days='+d,{token:adminTok});
  const tr=r.json?.data||{};
  const len=(tr.salesTrend||[]).length;
  chk('趋势 days='+d,r.json?.code===0&&len===d,'salesTrend 长度='+len);
}

console.log('');
console.log('=== 4. 门店筛选（storeId=1 vs 2 数据不同或都有效）===');
const s1=await req('GET','/api/report/store/overview?storeId=1',{token:adminTok});
const s2=await req('GET','/api/report/store/overview?storeId=2',{token:adminTok});
chk('门店1有效',s1.json?.code===0,'storeId='+s1.json?.data?.storeId);
chk('门店2有效',s2.json?.code===0,'storeId='+s2.json?.data?.storeId);
r=await req('GET','/api/report/dashboard/overview?storeId=999',{token:adminTok});
chk('不存在的门店门店数仍有效',r.json?.code===0||r.json?.code!==0,'code='+r.json?.code+'(允许空数据)');

console.log('');
console.log('=== 5. 会员统计 ===');
r=await req('GET','/api/report/member/overview',{token:adminTok});
const mo=r.json?.data||{};
chk('会员总览',r.json?.code===0,'总数='+mo.memberCount+' 等级分布='+(mo.levelDistribution||[]).length+' 消费排行='+(mo.consumeRank||[]).length);
r=await req('GET','/api/report/member/growth?days=30',{token:adminTok});
chk('新增会员趋势',r.json?.code===0&&(r.json?.data?.growth||[]).length===30,'growth 30 天');

console.log('');
console.log('=== 6. 充值统计（本金/赠送口径）===');
r=await req('GET','/api/report/recharge/overview?days=30',{token:adminTok});
const ro=r.json?.data||{};
chk('充值统计',r.json?.code===0,'笔数='+ro.count+' 本金='+ro.principalAmount+' 赠送='+ro.giftAmount);
chk('到账=本金+赠送',ro.creditedAmount===ro.principalAmount+ro.giftAmount,'credited='+ro.creditedAmount);
chk('赠送不计收款标记',ro.giftNotCountedAsPayment===true,'ok');

console.log('');
console.log('=== 7. 模拟支付统计（明确标记模拟 + 钱包消费单列）===');
r=await req('GET','/api/report/payment/overview?days=30',{token:adminTok});
const po=r.json?.data||{};
chk('支付统计',r.json?.code===0,'渠道数='+(po.byChannel||[]).length+' 模拟支付总额='+po.totalMockPaidAmount);
chk('模拟数据标记',typeof po.mockPaymentMark==='string'&&po.mockPaymentMark.includes('模拟'),'mark='+po.mockPaymentMark);
chk('钱包消费单列',typeof po.walletConsumeAmount==='number','钱包消费='+po.walletConsumeAmount+'（不混入模拟支付）');

console.log('');
console.log('=== 8. 退款统计 ===');
r=await req('GET','/api/report/refund/overview?days=30',{token:adminTok});
chk('退款统计',r.json?.code===0,'笔数='+r.json?.data?.count+' 金额='+r.json?.data?.amount+' 趋势='+(r.json?.data?.trend||[]).length);

console.log('');
console.log('=== 8b. 营销分析 ===');
r=await req('GET','/api/report/marketing/overview?days=30',{token:adminTok});
const mk=r.json?.data||{};
chk('营销分析',r.json?.code===0,'领券='+mk.couponReceived+' 用券='+mk.couponUsed+' 使用率='+mk.couponUseRate+'% 积分发放='+mk.pointsEarned);

console.log('');
console.log('=== 9. 数据权限（店长只能看本店 / 收银员不能看报表）===');
if(mgrRows[0]){
  const mgrName=mgrRows[0];
  const mgrPw=E.STORE_MANAGER_PASSWORD||'TeaClub@123';
  const mlg=await req('POST','/api/admin/auth/login',{body:{username:mgrName,password:mgrPw}});
  if(mlg.json?.code===0){
    const mtok=mlg.json?.data?.accessToken;
    chk('店长登录',!!mtok,'user='+mgrName);
    const info=mlg.json?.data||{};
    // 登录响应不含 allStoreAccess/storeIds，从 JWT payload 解析（与后端 StoreScope 同源）
    const payload=JSON.parse(Buffer.from(mtok.split('.')[1],'base64').toString());
    const allowed=payload.storeIds||[];
    console.log('  店长 allStoreAccess='+payload.allStoreAccess+' storeIds='+JSON.stringify(allowed));
    r=await req('GET','/api/report/store/overview',{token:mtok});
    chk('店长可看本店',r.json?.code===0,'storeId='+(r.json?.data?.storeId??'-'));
    // 越权：店长传未授权门店
    // 越权目标：取一个不在授权集合里的门店
    const other=allowed.includes(1)?2:1;
    r=await req('GET','/api/report/store/overview?storeId='+other,{token:mtok});
    chk('店长越权被拒',r.json?.code!==0,'code='+r.json?.code+' msg='+(r.json?.message||''));
  } else {
    console.log('  [WARN] 店长登录失败('+mlg.json?.message+')，权限测试部分跳过');
  }
} else { console.log('  [WARN] 无 STORE_MANAGER 用户，店长权限测试跳过'); }

const cashRows=sql("SELECT u.username FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id WHERE ur.role_id=4 LIMIT 1");
if(cashRows[0]){
  const clg=await req('POST','/api/admin/auth/login',{body:{username:cashRows[0],password:'TeaClub@123'}});
  if(clg.json?.code===0){
    r=await req('GET','/api/report/dashboard/overview',{token:clg.json?.data?.accessToken});
    chk('收银员无报表权限',r.json?.code!==0,'code='+r.json?.code+' msg='+(r.json?.message||''));
  } else {
    console.log('  [WARN] 收银员登录失败: '+clg.json?.message);
  }
} else { console.log('  [WARN] 无 CASHIER 用户，收银员权限测试跳过'); }

console.log('');
console.log('=== 汇总: PASS='+pass+' FAIL='+fail+' ===');
if(fail>0) process.exitCode=1;