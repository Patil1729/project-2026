import { useSyncExternalStore } from 'react';
import { mr } from './translations';
let language = 'en';
try { language = localStorage.getItem('legalcell.language') === 'mr' ? 'mr' : 'en'; } catch { /* Browser storage is optional. */ }
const listeners = new Set();
const subscribe = fn => {listeners.add(fn);return ()=>listeners.delete(fn);};
export const getLanguage = () => language;
export const getLocale = () => language === 'mr' ? 'mr-IN' : 'en-IN';
export function setLanguage(next) {
  language = next === 'mr' ? 'mr' : 'en';
  try {localStorage.setItem('legalcell.language',language);} catch { /* Keep the in-memory preference. */ }
  if(typeof document!=='undefined') document.documentElement.lang=language;
  listeners.forEach(fn=>fn());
}
if(typeof document!=='undefined') document.documentElement.lang=language;
export function useLocale() {return useSyncExternalStore(subscribe,getLanguage,getLanguage);}
const insensitive = Object.fromEntries(Object.entries(mr).map(([k,v])=>[k.toLowerCase(),v]));
const patterns = [
  [/^(\d+) approved applications$/, '$1 मंजूर अर्ज'],
  [/^A little progress, every day · (.*)$/, 'दररोज थोडी प्रगती · $1'],
  [/^(\d+)% of positions filled$/, '$1% पदे भरलेली'],
  [/^Select an active (.*) unit\.$/, 'सक्रिय $1 घटक निवडा.'],
  [/^(\d+) units$/, '$1 घटक'],
  [/^Created (.*)$/, 'तयार झाले: $1'],
  [/^Step (.*?) · (.*?) · Due (.*)$/, 'टप्पा $1 · $2 · अंतिम मुदत $3'],
  [/^Edit (.*)$/, '$1 संपादित करा'],
  [/^Deactivate (.*)$/, '$1 निष्क्रिय करा'],
  [/^Activate (.*)$/, '$1 सक्रिय करा'],
  [/^View (.*)$/, '$1 पहा'],
  [/^Request failed \((\d+)\)$/, 'विनंती अयशस्वी ($1)'],

  [/^(\d+) overdue review steps$/, '$1 पुनरावलोकन टप्प्यांची मुदत संपली'],
  [/^(\d+) positions available$/, '$1 पदे उपलब्ध'],
  [/^(\d+) active units$/, '$1 सक्रिय घटक'],
  [/^(\d+) of (\d+) positions filled$/, '$2 पैकी $1 पदे भरलेली'],
  [/^Page (\d+)$/, 'पृष्ठ $1'],
  [/^(.*?) of (\d+) records$/, '$2 नोंदींपैकी $1'],
  [/^Applications created over the last (\d+) days; teal bars show all applications, gold bars show applications currently approved\.$/, 'मागील $1 दिवसांत तयार झालेले अर्ज; हिरवे स्तंभ सर्व अर्ज आणि सोनेरी स्तंभ मंजूर अर्ज दर्शवतात.'],
];
export function t(value) {
  if(language!=='mr'||typeof value!=='string')return value;
  const key=value.trim();
  if(mr[key])return value.replace(key,mr[key]);
  const normalized=key.toLowerCase();
  if(insensitive[normalized])return value.replace(key,insensitive[normalized]);
  for(const [pattern,replacement] of patterns) if(pattern.test(key))return key.replace(pattern,replacement);
  return value;
}
export function LanguageSwitch() {
  useLocale();
  return <div className="language-switch" role="group" aria-label={language==='mr'?'भाषा निवडा':'Choose language'}>
    <button type="button" lang="en" aria-pressed={language==='en'} onClick={()=>setLanguage('en')}>English</button>
    <button type="button" lang="mr" aria-pressed={language==='mr'} onClick={()=>setLanguage('mr')}>मराठी</button>
  </div>;
}
