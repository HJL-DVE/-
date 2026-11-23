import fs from 'node:fs';
const ROOT='H:\\奶茶店会员与储值营销系统';
const t=fs.readFileSync(ROOT+'\\backend\\infrastructure\\docker\\.env.docker','utf8');
const E={}; t.split(/\r?\n/).forEach(l=>{const m=l.match(/^([A-Z_]+)=(.*)$/);if(m)E[m[1]]=m[2];});
const GW='http://127.0.0.1:9000';
let pass=0,fail=0;
const chk=(n,ok,d)=>{ if(ok)pass++; else fail++; console.log((ok?'  [PASS] ':'  [FAIL] ')+n+'  '+d); };
const sleep=(ms)=>new Promise(x=>setTimeout(x,ms));
const adm=await fetch(GW+'/api/admin/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'admin',password:'TeaClub@123'})}).then(x=>x.json());
const tok=adm.data?.accessToken;
const H={'Content-Type':'application/json',Authorization:'Bearer '+tok};
const get=(p)=>fetch(GW+p,{headers:H}).then(x=>x.json());
const send=(m,p,b)=>fetch(GW+p,{method:m,headers:H,body:b===undefined?undefined:JSON.stringify(b)}).then(x=>x.json());

console.log('=== 1. product/category 分类 CRUD ===');
let r=await get('/api/admin/product/categories');
chk('分类列表',r.code===0,'count='+(Array.isArray(r.data)?r.data.length:'?')+' tree');
const cat0=(Array.isArray(r.data)?r.data:[])[0];
r=await send('POST','/api/admin/product/categories',{categoryName:'P9测试分类',sort:99,status:'ENABLED'});
chk('新增分类',r.code===0,'id='+r.data+' '+(r.message||''));
const cid=r.data;
if(cid){
  r=await send('PUT','/api/admin/product/categories/'+cid,{categoryName:'P9测试分类(改)',sort:98,status:'ENABLED'});
  chk('编辑分类',r.code===0,'code='+r.code);
  r=await send('DELETE','/api/admin/product/categories/'+cid);
  chk('删除分类',r.code===0,'code='+r.code);
}

console.log('=== 2. order/statistics 统计 ===');
r=await get('/api/admin/order/stats/summary?days=7');
chk('订单统计',r.code===0,'gmv='+(r.data?.gmv||0)/100+' 订单='+r.data?.orderCount+' 余额支付='+(r.data?.balancePayAmount||0)/100);

console.log('=== 3. wallet/plan 套餐 CRUD ===');
r=await get('/api/admin/wallet/plans');
chk('套餐列表',r.code===0,'count='+(r.data||[]).length);
r=await send('POST','/api/admin/wallet/plans',{planCode:'P9'+Date.now().toString(36).toUpperCase().slice(-6),planName:'P9测试'+Date.now().toString(36),rechargeAmount:5000,giftAmount:500,giftExpireDays:0,customAmountEnabled:0,perMemberLimit:0,sort:99,status:'DISABLED',remark:'阶段九测试'});
chk('新增套餐',r.code===0,'id='+r.data+' '+(r.message||''));
const pid=r.data; const pcode=(pid?'':'');
if(pid){
  r=await send('PUT','/api/admin/wallet/plans/'+pid,{planCode:'P9'+Date.now().toString(36).toUpperCase().slice(-6),planName:'P9测试套餐(改)',rechargeAmount:6000,giftAmount:600,giftExpireDays:0,customAmountEnabled:0,perMemberLimit:0,sort:99,status:'DISABLED',remark:'阶段九测试'});
  chk('编辑套餐',r.code===0,'code='+r.code);
  r=await send('DELETE','/api/admin/wallet/plans/'+pid);
  chk('删除套餐',r.code===0,'code='+r.code);
}

console.log('=== 4. system/role 角色 CRUD+授权 ===');
r=await get('/api/admin/system/roles?pageNo=1&pageSize=20');
chk('角色列表',r.code===0,'total='+r.data?.total+' rows='+(r.data?.records||[]).length);
r=await get('/api/admin/system/permissions/tree');
chk('权限树',r.code===0,'top='+(r.data||[]).length);
const tree=Array.isArray(r.data)?r.data:[];
const leafIds=[];
const walk=(nodes)=>{for(const n of nodes){if(n.children&&n.children.length)walk(n.children);else leafIds.push(n.id);}};
walk(tree);
r=await send('POST','/api/admin/system/roles',{roleCode:'P9'+Date.now().toString(36).toUpperCase().slice(-6),roleName:'P9测试角色',dataScope:'STORE',sort:99,remark:'阶段九',permissionIds:leafIds.slice(0,3)});
chk('新增角色(带授权)',r.code===0,'id='+r.data+' '+(r.message||''));
const rid=r.data;
if(rid){
  const det=await get('/api/admin/system/roles/'+rid);
  chk('角色详情回读权限',det.data?.permissionIds?.length===Math.min(3,leafIds.length),'perms='+JSON.stringify(det.data?.permissionIds));
  r=await send('DELETE','/api/admin/system/roles/'+rid);
  chk('删除角色',r.code===0,'code='+r.code);
}

console.log('=== 5. system/permission 权限树（维护接口已按设计移除）===');
// 权限编码被 @PreAuthorize 与前端按钮引用，运行时增删改会破坏鉴权，已按设计移除；
// 只保留角色授权所需的全量权限树查询（system:role:grant）。
r=await get('/api/admin/system/permissions/tree');
chk('权限树查询',r.code===0,'top='+(Array.isArray(r.data)?r.data.length:'?'));
const allPerms=[];
(function walk(nodes){(nodes||[]).forEach(n=>{allPerms.push(n);walk(n.children)})})(r.data);
chk('权限树节点完整',allPerms.length>0,'共 '+allPerms.length+' 个节点');
const hasPoints = allPerms.some(n=>n.permCode==='marketing:points:rule');
chk('新权限点(积分规则编辑)已在树中',hasPoints,'marketing:points:rule 存在=' + hasPoints);

console.log('=== 6. system/log 操作日志 ===');
r=await get('/api/admin/system/logs?pageNo=1&pageSize=5');
chk('操作日志分页',r.code===0,'total='+r.data?.total+' 本页='+(r.data?.records||[]).length);
const first=(r.data?.records||[])[0];
if(first) console.log('  样例:', JSON.stringify({op:first.operatorName,module:first.module,uri:first.requestUri,ok:first.success}).slice(0,150));
r=await get('/api/admin/system/logs?pageNo=1&pageSize=5&success=false');
chk('按结果筛选',r.code===0,'失败条数='+((r.data?.records||[]).length));

console.log(''); console.log('=== 汇总: PASS='+pass+' FAIL='+fail+' ===');
if(fail>0) process.exitCode=1;