export const locationKey = value => String(value || '').normalize('NFKC').toLowerCase().replace(/[.\-_]/g,' ').replace(/\s+/g,' ').trim();
const districtAliases = {
 'पुणे':'pune','मुंबई':'mumbai','मुंबई शहर':'mumbai','मुंबई उपनगर':'mumbai','mumbai city':'mumbai','mumbai suburban':'mumbai',
 'ठाणे':'thane','पालघर':'palghar','रायगड':'raigad','रत्नागिरी':'ratnagiri','सिंधुदुर्ग':'sindhudurg','सातारा':'satara','सांगली':'sangli','कोल्हापूर':'kolhapur','सोलापूर':'solapur',
 'नाशिक':'nashik','धुळे':'dhule','नंदुरबार':'nandurbar','जळगाव':'jalgaon','अहिल्यानगर':'ahmednagar','अहमदनगर':'ahmednagar','ahilyanagar':'ahmednagar',
 'छत्रपती संभाजीनगर':'aurangabad','chhatrapati sambhajinagar':'aurangabad','sambhajinagar':'aurangabad','जालना':'jalna','बीड':'beed','bid':'beed','लातूर':'latur','धाराशिव':'osmanabad','dharashiv':'osmanabad','नांदेड':'nanded','परभणी':'parbhani','हिंगोली':'hingoli',
 'अकोला':'akola','बुलढाणा':'buldhana','buldana':'buldhana','वाशिम':'washim','अमरावती':'amravati','यवतमाळ':'yavatmal','वर्धा':'wardha','नागपूर':'nagpur','भंडारा':'bhandara','गोंदिया':'gondia','gondiya':'gondia','चंद्रपूर':'chandrapur','गडचिरोली':'gadchiroli'
};
export const canonicalDistrict = value => districtAliases[locationKey(value)] || locationKey(value).replace(/\s+district$/,'');
const cities = [
 ['Pune',73.86,18.52,'pune'],['Mumbai',72.88,19.08,'mumbai'],['Nagpur',79.09,21.15,'nagpur'],['Nashik',73.79,20,'nashik'],['Kolhapur',74.24,16.70,'kolhapur'],['Chhatrapati Sambhajinagar',75.34,19.88,'aurangabad']
];
const mh = new Set(['maharashtra','महाराष्ट्र','mh','महा','महाराष्ट्र राज्य']);
export function userStatistics(users) {
 return {registered:users.length,active:users.filter(u=>u.enabled===true).length,inactive:users.filter(u=>u.enabled===false).length};
}
// Address-derived locations are approximate city/district centres, never home coordinates.
export function groupUserLocations(users, profiles, districtPoints) {
 const groups=new Map(); let unmapped=0;
 for(const user of users){
  if(user.enabled!==true)continue;
  const profile=profiles[user.id],address=profile?.address;
  if(!address || (address.state && !mh.has(locationKey(address.state)))){unmapped++;continue;}
  const district=canonicalDistrict(address.district),city=canonicalDistrict(address.city);
  const known=cities.find(([name,,,key])=>(canonicalDistrict(name)===city||key===city)&&(!district||district===key));
  const point=known?{key:'city:'+known[3],district:known[3],name:known[0],lon:known[1],lat:known[2],precision:'city'}:districtPoints[district]||districtPoints[city];
  if(!point){unmapped++;continue;}
  const key=point.key;
  if(!groups.has(key))groups.set(key,{...point,count:0});
  groups.get(key).count++;
 }
 return {groups:[...groups.values()].sort((a,b)=>b.count-a.count||a.name.localeCompare(b.name)||a.key.localeCompare(b.key)),unmapped};
}
export async function loadActiveProfiles(users, request, signal, concurrency=4) {
 const profiles={},failures=[];const active=users.filter(u=>u.enabled===true);let cursor=0;
 await Promise.all(Array.from({length:Math.min(concurrency,active.length)},async()=>{
  while(cursor<active.length&&!signal?.aborted){const user=active[cursor++];try{profiles[user.id]=await request(`/users/${user.id}/profile`,{signal});}catch(error){if(signal?.aborted)throw error;if(error.status!==404)failures.push(user.id);}}
 }));return {profiles,failures};
}
