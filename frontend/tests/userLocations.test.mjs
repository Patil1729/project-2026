import {test} from 'node:test';
import assert from 'node:assert/strict';
import {userStatistics,groupUserLocations,loadActiveProfiles,canonicalDistrict} from '../src/lib/userLocations.js';
const users=[{id:1,enabled:true},{id:2,enabled:true},{id:3,enabled:false},{id:4,enabled:true},{id:5,enabled:true}];
const profile=(city,district='Pune',state='Maharashtra')=>({address:{city,district,state}});
const points={pune:{key:'district:pune',district:'pune',name:'Pune',lon:74,lat:18.5,precision:'district'},thane:{key:'district:thane',district:'thane',name:'Thane',lon:73,lat:19,precision:'district'}};
test('registered-user counts include inactive accounts without using application data',()=>{
 assert.deepEqual(userStatistics(users),{registered:5,active:4,inactive:1});
});
test('Pune users produce a green-marker group at the known Pune city centre',()=>{
 const result=groupUserLocations(users,{1:profile('Pune'),2:profile('पुणे','पुणे','महाराष्ट्र'),3:profile('Pune'),4:profile('Nagpur','Nagpur')},points);
 const pune=result.groups.find(g=>g.name==='Pune');assert.equal(pune.count,2);assert.equal(pune.lon,73.86);assert.equal(pune.lat,18.52);assert.equal(pune.precision,'city');assert.equal(result.unmapped,1);
 assert.equal(result.groups.reduce((sum,g)=>sum+g.count,0),3);
});
test('missing and outside-state addresses are not placed at made-up locations',()=>{
 const result=groupUserLocations(users,{1:profile('Pune','Pune','Karnataka'),2:profile('Unknown','Unknown'),4:null},points);
 assert.deepEqual(result.groups,[]);assert.equal(result.unmapped,4);
});
test('unrecognised cities use an available district interior point and label precision',()=>{
 const result=groupUserLocations([{id:1,enabled:true}],{1:profile('Village near Pune')},points);
 assert.equal(result.groups[0].key,'district:pune');assert.equal(result.groups[0].precision,'district');
});
test('city and district reference locations remain distinct and are not order-dependent',()=>{
 const profiles={1:profile('Pune'),2:profile('Village near Pune')};
 const forward=groupUserLocations(users.slice(0,2),profiles,points),reverse=groupUserLocations(users.slice(0,2).reverse(),profiles,points);
 assert.deepEqual(forward,reverse);assert.equal(forward.groups.length,2);
});
test('historical and Marathi district names are canonicalised',()=>{
 assert.equal(canonicalDistrict('अहिल्यानगर'),'ahmednagar');assert.equal(canonicalDistrict('Chhatrapati Sambhajinagar'),'aurangabad');assert.equal(canonicalDistrict(' Pune District '),'pune');
});
test('profile loading is bounded, skips inactive users, and tolerates missing profiles',async()=>{
 let concurrent=0,max=0;const requested=[];
 const result=await loadActiveProfiles(users,async path=>{requested.push(path);concurrent++;max=Math.max(max,concurrent);await new Promise(r=>setTimeout(r,5));concurrent--;if(path==='/users/4/profile')throw Object.assign(new Error('Missing'),{status:404});if(path==='/users/5/profile')throw Object.assign(new Error('Unavailable'),{status:503});return profile('Pune');},undefined,2);
 assert.ok(max<=2);assert.ok(!requested.includes('/users/3/profile'));assert.deepEqual(result.failures,[5]);assert.ok(result.profiles[1]);assert.ok(result.profiles[2]);
});
