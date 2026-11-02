import { test } from 'node:test';
import assert from 'node:assert/strict';
import {apiRequest,clearCsrf,ApiError,normalizeBase} from '../src/lib/api.js';
const json = (data,status=200) => new Response(JSON.stringify(data),{status,headers:{'Content-Type':'application/json'}});
test('cookie and CSRF protected writes; reads do not bootstrap CSRF',async()=>{
  clearCsrf();const calls=[];globalThis.fetch=async(url,options)=>{calls.push({url,options});return url.endsWith('/csrf')?json({token:'csrf-one',headerName:'X-XSRF-TOKEN'}):json({ok:true});};
  await apiRequest('/api','/users');await apiRequest('/api','/auth/signin',{method:'POST',body:{username:'test',password:'secret'}});
  assert.equal(calls.length,3);assert.equal(calls[0].url,'/api/users');assert.ok(calls.every(c=>c.options.credentials==='include'));
  assert.equal(calls[2].options.headers['X-XSRF-TOKEN'],'csrf-one');assert.equal(calls[2].options.headers.Authorization,undefined);
});
test('multipart keeps browser boundary and sends CSRF; concurrent writes share bootstrap',async()=>{
  clearCsrf();let csrfCalls=0;const writes=[];globalThis.fetch=async(url,options)=>{if(url.endsWith('/csrf')){csrfCalls++;return json({token:'csrf',headerName:'X-XSRF-TOKEN'});}writes.push(options);return new Response(null,{status:204});};
  const form=new FormData();form.set('file',new Blob(['test']),'sample.txt');
  await Promise.all([apiRequest('/api','/documents',{method:'POST',body:form}),apiRequest('/api','/users',{method:'PATCH',body:{enabled:true}})]);
  assert.equal(csrfCalls,1);assert.equal(writes[0].headers['Content-Type'],undefined);assert.equal(writes[0].body,form);
});
test('explicit CSRF failure gets one fresh bootstrap; ordinary forbidden writes never retry',async()=>{
  clearCsrf();let csrf=0,write=0;globalThis.fetch=async(url)=>url.endsWith('/csrf')?json({token:'token-'+(++csrf),headerName:'X-XSRF-TOKEN'}):++write===1?json({message:'Invalid CSRF token'},403):json({ok:true});
  await apiRequest('/api','/users',{method:'POST',body:{}});assert.equal(csrf,2);assert.equal(write,2);
  clearCsrf();write=0;globalThis.fetch=async url=>url.endsWith('/csrf')?json({token:'one',headerName:'X-XSRF-TOKEN'}):(write++,json({message:'Forbidden'},403));
  await assert.rejects(apiRequest('/api','/users',{method:'DELETE'}),e=>e instanceof ApiError&&e.status===403);assert.equal(write,1);
});
test('unauthorized responses invalidate session and errors never masquerade as data',async()=>{
  let expired=0;globalThis.window={dispatchEvent:()=>expired++};globalThis.fetch=async()=>json({message:'Session expired'},401);
  await assert.rejects(apiRequest('/api','/users'),e=>e.status===401);assert.equal(expired,1);delete globalThis.window;
  globalThis.fetch=async()=>new Response('<html>bad route</html>',{headers:{'Content-Type':'text/html'}});
  await assert.rejects(apiRequest('/api','/users'),/instead of JSON/);
});
test('rejects credential-bearing and non HTTP API addresses',()=>{
  for(const base of ['javascript:alert(1)','https://user:pass@example.com/api','//evil.test'])assert.throws(()=>normalizeBase(base));
  assert.equal(normalizeBase('/api/'),'/api');
});
test('identity loaders can handle their own 401 without expiring a newer session',async()=>{
  let expired=0;globalThis.window={dispatchEvent:()=>expired++};
  globalThis.fetch=async()=>json({message:'Unauthenticated'},401);
  await assert.rejects(apiRequest('/api','/auth/user',{notifyUnauthorized:false}),e=>e.status===401);
  assert.equal(expired,0);delete globalThis.window;
});

test('coded CSRF rejection refreshes once and preserves the multipart body and boundary',async()=>{
  clearCsrf();const calls=[];let csrf=0,uploads=0,denied=0;
  globalThis.window={dispatchEvent:()=>denied++};
  const form=new FormData();form.set('documentType','RECOMMENDATION_LETTER');form.set('file',new Blob(['%PDF-test']),'recommendation.pdf');
  globalThis.fetch=async(url,options)=>{
    calls.push({url,options});
    if(url.endsWith('/csrf'))return json({token:'opaque-token-'+(++csrf),headerName:'X-XSRF-TOKEN'});
    return ++uploads===1?json({code:'CSRF_TOKEN_INVALID',error:'Forbidden',path:'/api/registration-applications/25/documents'},403):json({id:30},201);
  };
  try {
    assert.equal((await apiRequest('/api','/registration-applications/25/documents',{method:'POST',body:form})).id,30);
    assert.equal(csrf,2);assert.equal(uploads,2);assert.equal(denied,0);
    const writes=calls.filter(c=>!c.url.endsWith('/csrf'));
    assert.ok(writes.every(c=>c.options.body===form&&c.options.credentials==='include'&&c.options.headers['Content-Type']===undefined));
    assert.equal(writes[1].options.headers['X-XSRF-TOKEN'],'opaque-token-2');
    assert.ok(calls.filter(c=>c.url.endsWith('/csrf')).every(c=>c.options.cache==='no-store'));
  } finally {delete globalThis.window;}
});

test('persistent CSRF rejection stops after one retry and exposes the code and path',async()=>{
  clearCsrf();let reads=0,writes=0;
  globalThis.fetch=async url=>url.endsWith('/csrf')?json({token:'token-'+(++reads),headerName:'X-XSRF-TOKEN'}):(writes++,json({code:'CSRF_TOKEN_MISSING',message:'Refresh the security token and retry.',path:'/api/users/10/profile'},403));
  await assert.rejects(apiRequest('/api','/users/10/profile',{method:'PUT',body:{}}),error=>error instanceof ApiError&&error.code==='CSRF_TOKEN_MISSING'&&error.path==='/api/users/10/profile'&&error.status===403);
  assert.equal(reads,2);assert.equal(writes,2);
});

test('an ordinary permission error is never replayed and is distinguished from CSRF',async()=>{
  clearCsrf();let writes=0,denied=0;globalThis.window={dispatchEvent:event=>{if(event.type==='legalcell:access-denied')denied++;}};
  globalThis.fetch=async url=>url.endsWith('/csrf')?json({token:'token',headerName:'X-XSRF-TOKEN'}):(writes++,json({error:'AREA_ACCESS_DENIED',message:'This account cannot update this profile.',path:'/api/users/10/profile'},403));
  try {
    await assert.rejects(apiRequest('/api','/users/10/profile',{method:'PUT',body:{}}),error=>error.code==='AREA_ACCESS_DENIED'&&error.path==='/api/users/10/profile');
    assert.equal(writes,1);assert.equal(denied,1);
  } finally {delete globalThis.window;}
});
