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
  try{return await r.json();}catch{return {}};
};
const adm=await req('POST','/api/admin/auth/login',{body:{username:'admin',password:'TeaClub@123'}});
const atok=adm.data?.accessToken;

console.log('=== 等级权益生效性测试 ===');

const all=await req('GET','/api/admin/marketing/level-benefits',{token:atok});
const groups=all.data||{};
chk('1a.等级 1/2/3/4/20 均有权益',['1','2','3','4','20'].every(k=>(groups[k]||[]).length>=2),'counts='+Object.entries(groups).map(([k,v])=>k+':'+v.length).join(' '));
chk('1b.每级含积分倍率',Object.values(groups).every(v=>v.some(x=>x.benefitCode==='POINT_RATE')),'ok');

const m1=await req('POST','/api/auth/wx-login',{body:{code:'ben-'+Date.now()}});
const t1=m1.data?.token, id1=m1.data?.memberId;
const r1=await req('GET','/api/marketing/level-benefits/level/1',{token:t1});
chk('2.会员端查等级权益',r1.code===0&&(r1.data||[]).length>=2,'count='+(r1.data||[]).length);

const plans=(await req('GET','/api/wallet/plans',{token:t1})).data||[];
const plan=plans.find(p=>p.rechargeAmount>=5000)||plans[0];
const rc=(await req('POST','/api/wallet/recharges',{token:t1,body:{planId:plan.id,storeId:1}})).data;
const pay=(await req('POST','/api/payment/create',{token:t1,body:{bizType:'RECHARGE',bizNo:rc.rechargeNo}})).data;
await req('POST','/api/payment/mock/notify',{body:{paymentNo:pay.paymentNo,amount:pay.amount}});
await sleep(2000);
const o1=(await req('POST','/api/order/orders',{token:t1,body:{storeId:1,items:[{skuId:1,quantity:1}]}})).data;
await req('POST','/api/order/orders/'+o1.orderNo+'/pay-wallet',{token:t1,body:{}});
await sleep(2500);
const a1=(await req('GET','/api/marketing/points/account',{token:t1})).data;
chk('3.普通 1.0 倍积分',a1.totalPoints===Math.floor(o1.payableAmount/100),'期望='+Math.floor(o1.payableAmount/100)+' 实际='+a1.totalPoints);

const { execFileSync } = await import('node:child_process');
const D='C:\\Program Files\\Docker\\Docker\\resources\\bin\\docker.exe';
const A=(q)=>execFileSync(D,['exec','teaclub-mysql','mysql','-uroot','-p'+E.MYSQL_ROOT_PASSWORD,'member_db','-N','-B','-e',q],{stdio:['pipe','pipe','ignore']});
A('UPDATE mem_member SET level_id=3 WHERE id='+id1);
const o2=(await req('POST','/api/order/orders',{token:t1,body:{storeId:1,items:[{skuId:1,quantity:1}]}})).data;
await req('POST','/api/order/orders/'+o2.orderNo+'/pay-wallet',{token:t1,body:{}});
await sleep(2500);
const a2=(await req('GET','/api/marketing/points/account',{token:t1})).data;
const got=a2.totalPoints-a1.totalPoints;
// 金卡当前倍率动态读取（25 号脚本会把它改成 180，不能硬编码 1.5）
const lvl3=(groups['3']||[]).find(x=>x.benefitCode==='POINT_RATE');
const rate=Math.floor((Number(lvl3?lvl3.benefitValue:100)||100)/100*100)/100;
const exp2=Math.floor(o2.payableAmount/100)*Math.floor(rate*100)/100|0;
const exp2c=Math.floor(Math.floor(o2.payableAmount/100)*rate);
chk('4.金卡倍率积分('+rate+'倍)',got===exp2c,'应付='+o2.payableAmount/100+' 倍率='+rate+' 期望='+exp2c+' 实际='+got);
A('UPDATE mem_member SET level_id=1 WHERE id='+id1);
console.log('  (测试会员等级已恢复)');

console.log(''); console.log('=== 汇总: PASS='+pass+' FAIL='+fail+' ===');
if(fail>0) process.exitCode=1;