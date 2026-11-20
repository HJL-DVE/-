const GW='http://127.0.0.1:9000';
let pass=0,fail=0;
const chk=(n,ok,d)=>{ if(ok)pass++; else fail++; console.log((ok?'  [PASS] ':'  [FAIL] ')+n+'  '+d); };
const lg=await fetch(GW+'/api/admin/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'admin',password:'TeaClub@123'})}).then(x=>x.json());
const tok=lg.data?.accessToken;
const H={'Content-Type':'application/json',Authorization:'Bearer '+tok};

const list0=await fetch(GW+'/api/admin/member/levels',{headers:H}).then(x=>x.json());
chk('等级列表',list0.code===0,'count='+(list0.data||[]).length);

// 新增测试等级
const created=await fetch(GW+'/api/admin/member/levels',{method:'POST',headers:H,body:JSON.stringify({levelCode:'T'+Date.now().toString(36).toUpperCase(),levelName:'测试等级',levelNo:99,upgradeType:'CONSUME_AMOUNT',upgradeThreshold:990000,discountRate:9500,pointRate:150,benefits:'测试用',status:'ENABLED'})}).then(x=>x.json());
chk('新增等级',created.code===0,'id='+created.data+' '+(created.message||''));
const id=created.data;

if(id){
  const upd=await fetch(GW+'/api/admin/member/levels/'+id,{method:'PUT',headers:H,body:JSON.stringify({levelCode:'T'+Date.now().toString(36).toUpperCase(),levelName:'测试等级(改)',levelNo:99,upgradeType:'CONSUME_AMOUNT',upgradeThreshold:880000,discountRate:9600,pointRate:160,benefits:'测试用',status:'ENABLED'})}).then(x=>x.json());
  chk('编辑等级',upd.code===0,'code='+upd.code);
  const one=await fetch(GW+'/api/admin/member/levels/'+id,{headers:H}).then(x=>x.json());
  chk('详情回读',one.data?.levelName==='测试等级(改)'&&one.data?.pointRate===160,'name='+one.data?.levelName+' rate='+one.data?.pointRate);
  const del=await fetch(GW+'/api/admin/member/levels/'+id,{method:'DELETE',headers:H}).then(x=>x.json());
  chk('删除等级',del.code===0,'code='+del.code);
  const list1=await fetch(GW+'/api/admin/member/levels',{headers:H}).then(x=>x.json());
  chk('删除后列表复原',list1.data.length===list0.data.length,(list0.data.length)+' -> '+(list1.data.length));
}

// 收银员权限（无 member:level:list）
const clg=await fetch(GW+'/api/admin/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'cashier01',password:'TeaClub@123'})}).then(x=>x.json());
const deny=await fetch(GW+'/api/admin/member/levels',{headers:{Authorization:'Bearer '+clg.data?.accessToken}}).then(x=>x.json());
chk('收银员被拒',deny.code!==0,'code='+deny.code);

console.log(''); console.log('汇总: PASS='+pass+' FAIL='+fail);
if(fail>0) process.exitCode=1;