const GW='http://127.0.0.1:9000';
const lg=await fetch(GW+'/api/auth/wx-login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({code:'probe2-'+Date.now()})}).then(x=>x.json());
const tok=lg.data?.token;
const r=await fetch(GW+'/api/product/stores/1/spus',{headers:{Authorization:'Bearer '+tok}}).then(x=>x.json());
console.log('code='+r.code);
const list=Array.isArray(r.data)?r.data:(r.data&&r.data.records||[]);
console.log('spus count='+list.length);
console.log('first:',JSON.stringify(list[0]).slice(0,400));