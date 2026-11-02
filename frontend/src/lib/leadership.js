// Public homepage content. Put approved portraits in public/images/ and edit these six entries.
// Remove one of the pending entries to display five people. Private member pictures are not published here.
export const leadership = [
  {id:'balasaheb',name:{en:'Balasaheb Thackeray',mr:'बाळासाहेब ठाकरे'},prefix:{en:'Hinduhridaysamrat',mr:'हिंदुहृदयसम्राट'},role:{en:'Founder, Shiv Sena',mr:'संस्थापक, शिवसेना'},image:'/images/balasaheb-thackeray.jpg'},
  {id:'anand',name:{en:'Anand Dighe',mr:'आनंद दिघे'},prefix:{en:'Dharmaveer',mr:'धर्मवीर'},role:{en:'Inspiration',mr:'प्रेरणास्थान'},image:'/images/anand-dighe.jpg'},
  {id:'eknath',name:{en:'Eknath Shinde',mr:'एकनाथ शिंदे'},prefix:{en:'Hon.',mr:'मा.'},role:{en:'Shiv Sena leadership',mr:'शिवसेना नेतृत्व'},image:'/images/eknath-shinde.jpg'},
  ...[4,5,6].map(number => ({id:`leadership-${number}`,name:{en:`Leadership member ${number}`,mr:`नेतृत्व सदस्य ${number}`},prefix:{en:'',mr:''},role:{en:'Details to be announced',mr:'माहिती लवकरच जाहीर होईल'},image:null,pending:true})),
];
