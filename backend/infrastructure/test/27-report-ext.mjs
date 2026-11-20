import fs from 'node:fs';
const ROOT='H:\\奶茶店会员与储值营销系统';
const t=fs.readFileSync(ROOT+'\\backend\\infrastructure\\docker\\.env.docker','utf8');
const E={}; t.split(/\r?\n/).forEach(l=>{const m=l.match(/^([A-Z_]+)=(.*)$/);if(m)E[m[1]]=m[2];});
const GW='http://127.0.0.1:9000';
let pass=0,fail=0;
const chk=(n,ok,d)=>{ if(ok)pass++; else fail++; console.log((ok?'  [PASS] ':'  [FAIL] ')+n+'  '+d); };

const lg=await fetch(GW+'/api/admin/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'admin',password:'TeaClub@123'})}).then(x=>x.json());
const tok=lg.data?.accessToken;
const req=async(p)=>{const r=await fetch(GW+p,{headers:{Authorization:'Bearer '+tok}});return await r.json();};

let r=await req('/api/report/store/hourly');
chk('每小时订单趋势',r.code===0&&Array.isArray(r.data?.list)&&r.data.list.length===24,'24 小时补零，有单时段示例='+JSON.stringify((r.data?.list||[]).find(x=>x.orders>0)||'无'));

r=await req('/api/report/store/hourly?storeId=1');
chk('每小时+门店筛选',r.code===0,'storeId 过滤 OK');

r=await req('/api/report/product/category-ranking?days=30&metric=qty');
chk('商品分类排行',r.code===0&&Array.isArray(r.data?.list),'count='+(r.data?.list||[]).length+' 首项='+JSON.stringify((r.data?.list||[])[0]||null));

r=await req('/api/report/product/category-ranking?days=30&metric=amount');
chk('分类排行金额口径',r.code===0&&r.data?.metric==='amount','metric='+r.data?.metric);

r=await req('/api/report/product/trend?days=30');
chk('商品销售趋势',r.code===0&&(r.data?.trend||[]).length===30,'30 天补零 trend[29]='+JSON.stringify((r.data?.trend||[])[29]||null));

// 越权复核（店长仍被拦）
const mg=await fetch(GW+'/api/admin/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'mgr_xihu',password:'TeaClub@123'})}).then(x=>x.json());
const mtok=mg.data?.accessToken;
r=await fetch(GW+'/api/report/store/hourly?storeId=2',{headers:{Authorization:'Bearer '+mtok}}).then(x=>x.json());
chk('店长越权 hourly 被拒',r.code!==0,'code='+r.code+' '+r.message);
r=await fetch(GW+'/api/report/product/category-ranking?storeId=2',{headers:{Authorization:'Bearer '+mtok}}).then(x=>x.json());
chk('店长越权 category 被拒',r.code!==0,'code='+r.code+' '+r.message);

console.log('');
console.log('=== 汇总: PASS='+pass+' FAIL='+fail+' ===');
if(fail>0) process.exitCode=1;