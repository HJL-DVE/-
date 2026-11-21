const GW='http://127.0.0.1:9000';
const lg=await fetch(GW+'/api/auth/wx-login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({code:'imgchk-'+Date.now()})}).then(x=>x.json());
const tok=lg.data?.token;
const r=await fetch(GW+'/api/product/stores/1/spus',{headers:{Authorization:'Bearer '+tok}}).then(x=>x.json());
const list=Array.isArray(r.data)?r.data:(r.data?.records||[]);
console.log('count='+list.length);
list.slice(0,17).forEach(s=>console.log(s.spuId, s.spuName, '->', s.imageUrl));