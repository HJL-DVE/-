const GW='http://127.0.0.1:9000';
const adm=await fetch(GW+'/api/admin/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'admin',password:'TeaClub@123'})}).then(x=>x.json());
const tok=adm.data?.accessToken;
const r=await fetch(GW+'/api/admin/system/logs?pageNo=1&pageSize=5',{headers:{Authorization:'Bearer '+tok}}).then(x=>x.json());
console.log('code='+r.code, 'message='+r.message);
console.log('data keys=', r.data ? Object.keys(r.data) : null);
console.log(JSON.stringify(r.data).slice(0,400));