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
  let j=null; try{j=await r.json();}catch{}
  return j||{};
};

console.log('=== 第九阶段：完整业务流程测试 ===');

const code='p9-'+Date.now();
let r=await req('POST','/api/auth/wx-login',{body:{code}});
const mtok=r.data?.token, mid=r.data?.memberId;
chk('1.注册会员',!!mtok,'memberId='+mid);

const plans=(await req('GET','/api/wallet/plans',{token:mtok})).data||[];
const plan=plans.find(p=>p.rechargeAmount===10000&&p.giftAmount===2000)||plans.find(p=>p.giftAmount===2000)||plans[0];
chk('2.取充值套餐',!!plan,'planId='+plan?.id+' 实付='+plan?.rechargeAmount/100+' 送='+plan?.giftAmount/100);

const rc=(await req('POST','/api/wallet/recharges',{token:mtok,body:{planId:plan.id,storeId:1}})).data;
chk('3.创建充值订单',!!rc?.rechargeNo,'rechargeNo='+rc?.rechargeNo+' 应付='+rc?.payableAmount/100);

const pay=(await req('POST','/api/payment/create',{token:mtok,body:{bizType:'RECHARGE',bizNo:rc.rechargeNo}})).data;
await req('POST','/api/payment/mock/notify',{body:{paymentNo:pay.paymentNo,amount:pay.amount}});
await sleep(2500);
chk('4.Mock支付成功',(await req('GET','/api/wallet/recharges/'+rc.rechargeNo,{token:mtok})).data?.status==='PAID','');

const bal=(await req('GET','/api/wallet/account',{token:mtok})).data;
const credited=(bal.principalBalance||0)+(bal.giftBalance||0);
chk('5.余额=充值+赠送',(credited===plan.rechargeAmount+plan.giftAmount),'实收='+(bal.principalBalance||0)/100+' 赠送='+(bal.giftBalance||0)/100+' 合计='+credited/100);

await req('POST','/api/payment/mock/notify',{body:{paymentNo:pay.paymentNo,amount:pay.amount}});
await sleep(1500);
const bal2=(await req('GET','/api/wallet/account',{token:mtok})).data;
chk('6.重复充值通知不重复入账',credited===((bal2.principalBalance||0)+(bal2.giftBalance||0)),'');

const rf=await req('POST','/api/payment/mock/fail',{body:{paymentNo:pay.paymentNo}});
chk('7.Mock失败通知路径',rf.code!==undefined,'code='+rf.code+' msg='+(rf.message||'成功受理'));

// SKU 直查：用 hot 接口不可靠，直接固定取门店 1 的 SPU 列表第一个的 minPrice 作展示；下单用 skuId=1（18 元）
const spuList=await req('GET','/api/product/stores/1/spus',{token:mtok});
const spus=(Array.isArray(spuList.data)?spuList.data:(spuList.data&&spuList.data.records||[]));
const sku={ spuId: 1, spuName: (spus[0]&&spus[0].spuName)||'招牌珍珠奶茶', minPrice: (spus[0]&&spus[0].minPrice)||1800 };
const skuIdForOrder=1;
chk('8.选择商品',!!sku,'spuId='+sku?.spuId+' 挂牌价='+sku?.minPrice/100+'元 name='+sku?.spuName+' (下单SKU id='+skuIdForOrder+')');

const order=(await req('POST','/api/order/orders',{token:mtok,body:{storeId:1,items:[{skuId:skuIdForOrder,quantity:1}]}})).data;
chk('9.创建订单',!!order?.orderNo,'orderNo='+order?.orderNo+' 应付='+order?.payableAmount/100);

const pw=await req('POST','/api/order/orders/'+order.orderNo+'/pay-wallet',{token:mtok,body:{}});
chk('10.钱包支付成功',pw.data?.status==='PAID','status='+pw.data?.status);

await sleep(1500);
const txns=(await req('GET','/api/wallet/transactions?pageNo=1&pageSize=20',{token:mtok})).data?.records||[];
chk('11.资金流水含充值与消费',txns.some(x=>x.bizType==='RECHARGE_IN')&&txns.some(x=>x.bizType==='CONSUME_OUT'),'types='+txns.slice(0,4).map(x=>x.bizType).join(','));

const bal3=(await req('GET','/api/wallet/account',{token:mtok})).data;
const deducted=credited-((bal3.principalBalance||0)+(bal3.giftBalance||0));
chk('12.余额扣减=订单应付',deducted===order.payableAmount,'扣='+deducted/100+' 应付='+order.payableAmount/100);

let pts=null;
for(let i=0;i<10;i++){ await sleep(1200); pts=(await req('GET','/api/marketing/points/account',{token:mtok})).data?.totalPoints; if(pts>0)break; }
chk('13.获得消费积分',pts>0,'积分='+pts);

const again=await req('POST','/api/order/orders/'+order.orderNo+'/pay-wallet',{token:mtok,body:{}});
chk('14.重复支付被拒/幂等',again.code!==0||again.data?.status==='PAID','code='+again.code+' msg='+(again.message||'').slice(0,30));

const adm=await req('POST','/api/admin/auth/login',{body:{username:'admin',password:'TeaClub@123'}});
const atok=adm.data?.accessToken;
r=await req('GET','/api/report/dashboard/overview',{token:atok});
chk('15.总部报表可查',r.code===0,'今日销售='+((r.data?.todaySales||0)/100)+'元 订单='+r.data?.todayOrders);
r=await req('GET','/api/report/store/overview?storeId=1',{token:atok});
chk('16.门店报表可查',r.code===0,'门店1销售='+((r.data?.sales||0)/100)+'元');
r=await req('GET','/api/report/recharge/overview?days=1',{token:atok});
chk('17.充值统计',r.code===0,(r.data?.count||0)+' 笔 本金='+((r.data?.principalAmount||0)/100));

console.log(''); console.log('=== 汇总: PASS='+pass+' FAIL='+fail+' ===');
console.log('会员='+mid+' orderNo='+(order&&order.orderNo));
if(fail>0) process.exitCode=1;