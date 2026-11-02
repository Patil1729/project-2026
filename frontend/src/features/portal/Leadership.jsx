import { useState } from 'react';
import { UserRound } from 'lucide-react';
import { leadership } from '../../lib/leadership';
function Portrait({person,language}) {
  const [failed,setFailed]=useState(false);
  return <div className="portal-portrait">{person.image && !failed ? <img src={person.image} alt={person.name[language]} width="300" height="300" loading="lazy" onError={()=>setFailed(true)}/> : <div className="leader-placeholder"><UserRound size={64} strokeWidth={1}/><span>{language==='mr'?'नेतृत्व':'LEADERSHIP'}</span></div>}</div>;
}
export default function Leadership({language='en'}) {
  const mr=language==='mr';
  return <section className="portal-section portal-leadership" id="leadership"><div className="portal-container"><div className="portal-section-heading"><div><span className="portal-overline">{mr?'आमची प्रेरणा':'OUR INSPIRATION'}</span><h2>{mr?'प्रेरणास्थान आणि नेतृत्व':'Inspiration & leadership'}</h2></div></div><div className="portal-leaders leadership-six">{leadership.map(person=><article key={person.id} data-leader-slot={person.id}><Portrait person={person} language={language}/><span className="leader-prefix">{person.prefix[language]}</span><h3>{person.name[language]}</h3><p>{person.role[language]}</p></article>)}</div></div></section>;
}
