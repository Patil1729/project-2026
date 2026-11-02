import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import { ArrowRight, ShieldCheck, CheckCircle2, Phone, Siren, LockKeyhole, Hand, House, MessageCircle } from 'lucide-react';
import { useApp, useResource } from '../../app/AppContext';
import { useCurrentAccount } from '../../app/CurrentAccountContext';
import { Button, Field, FormError, Select } from '../../components/ui';
import { categoryLabel, helpCategories, helpPayload, newSubmissionKey, validateCommunityFile } from '../../lib/community';
import { organizationChoices } from '../../lib/organizations';
import { t, useLocale } from '../../lib/i18n';
const categoryIcons = { POLICE_FIR:Siren, ARREST_DETENTION:LockKeyhole, POLITICAL_HARASSMENT:Hand, WOMEN_SAFETY:ShieldCheck, PROPERTY_DISPUTE:House, OTHER:MessageCircle };
export default function GetHelp() {
  useLocale(); const { user, request, refresh } = useApp(), account = useCurrentAccount();
  const organizations = useResource('/public/portal/organization-units');
  const choices = organizationChoices(organizations.data || []);
  const [values,setValues] = useState({contactName:'',contactMobile:'',organizationUnitId:'',locality:'',subject:'',description:'',category:'',urgent:false,consent:false});
  const [files,setFiles] = useState([]), [busy,setBusy] = useState(false), [error,setError] = useState(null), [created,setCreated] = useState(null), [uploadErrors,setUploadErrors] = useState([]);
  const key=useRef(null), initialized=useRef(false), success=useRef(null);
  useEffect(() => {if(account.data && !initialized.current){initialized.current=true;setValues(v=>({...v,contactName:account.data.fullName||'',contactMobile:account.data.mobile||'',organizationUnitId:account.data.organizationUnitId||''}));}},[account.data]);
  useEffect(() => {if(created) success.current?.focus();},[created]);
  const change=(name,value)=>setValues(v=>({...v,[name]:value}));
  async function submit(e) {
    e.preventDefault();if(busy)return;setBusy(true);setError(null);
    try {
      if(!values.category) throw new Error(t('Choose the type of issue.'));
      files.forEach(file=>validateCommunityFile(file,true));
      key.current ||= newSubmissionKey();
      let detail=await request('/help-requests',{method:'POST',body:helpPayload(values,key.current,choices)});
      const failures=[];
      for(const file of files){try{const body=new FormData();body.append('file',file);body.append('version',detail.version);detail=await request(`/help-requests/${detail.summary.id}/attachments`,{method:'POST',body});}catch(error){failures.push(`${file.name}: ${error.message}`);}}
      setUploadErrors(failures);setCreated(detail);refresh();
    }catch(error){setError(error);}finally{setBusy(false);}
  }
  const helpline=import.meta.env.VITE_CONTACT_PHONE;
  return <section className="community-page get-help-page"><div className="portal-container">
    <div className="community-title"><span className="community-pill help-pill">{t('LEGAL SUPPORT')}</span><h1>{t('Tell us what happened')}</h1><i/><p>{t('Raise your concern securely with the Legal Cell.')}</p></div>
    {created ? <div className="help-success" ref={success} tabIndex={-1}><CheckCircle2 size={50}/><h2>{t('Request received')}</h2><strong className="help-reference">{created.summary.reference}</strong><p>{t('Keep this reference. Your request and updates are available in My help requests.')}</p><p>{created.summary.advocateName ? <>{t('Assigned advocate')}: <b>{created.summary.advocateName}</b> · {created.summary.routingAreaName}</> : t('No current committee member is available. A coordinator can assign your request.')}</p>{uploadErrors.length>0 && <div className="inline-error" role="alert"><div><strong>{t('Request saved. Some evidence could not be uploaded.')}</strong><ul>{uploadErrors.map(message=><li key={message}>{message}</li>)}</ul><p>{t('Open the request to retry evidence uploads. Do not submit another request.')}</p></div></div>}<Link className="btn btn-primary" to={`/help-requests/${created.summary.id}`}>{t('Track this request')}<ArrowRight size={16}/></Link></div> : <div className="help-layout">
      {user ? <form className="help-form community-card" onSubmit={submit}><fieldset className="wizard-fields" disabled={busy}>
        <h2><span>1</span>{t('About you')}</h2><div className="community-two"><Field label="Full name" required maxLength={150} value={values.contactName} onChange={e=>change('contactName',e.target.value)}/><Field label="Mobile number" type="tel" required pattern="[+]?[1-9][0-9]{7,14}" maxLength={16} hint="Include country code, for example +919876543210." value={values.contactMobile} onChange={e=>change('contactMobile',e.target.value)}/></div>
        <Select label="Your area" required value={values.organizationUnitId} onChange={e=>change('organizationUnitId',e.target.value)} hint="Choose the most local available area where the issue occurred."><option value="">{t('Select your area')}</option>{choices.map(u=><option key={u.id} value={u.id}>{u.path}</option>)}</Select>
        {organizations.error && <FormError error={organizations.error}/>}<Field label="Village / locality / address" required maxLength={200} value={values.locality} onChange={e=>change('locality',e.target.value)}/>
        <h2><span>2</span>{t('What kind of issue is it?')}</h2><div className="help-category-grid" role="group" aria-label={t('Issue category')}>{helpCategories.map(([code,label])=>{const Icon=categoryIcons[code];return <button type="button" key={code} className={`help-category ${values.category===code?'selected':''}`} aria-pressed={values.category===code} onClick={()=>change('category',code)}><span aria-hidden="true"><Icon size={25}/></span>{t(label)}</button>;})}</div>
        <label className="community-check urgent-check"><input type="checkbox" checked={values.urgent} onChange={e=>change('urgent',e.target.checked)}/><span>{t('This is urgent')}<small>{t('Arrest, detention or a deadline within 48 hours.')}</small></span></label>
        <h2><span>3</span>{t('Details')}</h2><Field label="Subject" required maxLength={150} value={values.subject} onChange={e=>change('subject',e.target.value)}/>
        <label className="community-label">{t('What happened, when and where?')}<textarea className="input" required minLength={10} maxLength={4000} rows={6} value={values.description} onChange={e=>change('description',e.target.value)}/><small>{values.description.length} / 4000</small></label>
        <h2><span>4</span>{t('Evidence (optional)')}</h2><Field label="Choose evidence files" type="file" accept=".jpg,.jpeg,.png,.pdf" multiple hint="Up to five JPG, PNG or PDF files. Maximum 5 MB per file." onChange={e=>{try{const next=[...e.target.files];if(next.length>5)throw new Error(t('Choose at most five files.'));next.forEach(f=>validateCommunityFile(f,true));setFiles(next);setError(null);}catch(error){e.target.value='';setFiles([]);setError(error);}}}/>
        {!!files.length && <ul className="help-file-list">{files.map((file,i)=><li key={i}>{file.name} <small>({Math.ceil(file.size/1024)} KB)</small></li>)}</ul>}
        <label className="community-check"><input type="checkbox" required checked={values.consent} onChange={e=>change('consent',e.target.checked)}/><span>{t('I agree to share this information with the Legal Cell.')}<small>{t('Your request is visible to you, your assigned advocate and authorized area coordinators.')}</small></span></label>
      </fieldset><FormError error={error}/><Button className="help-submit" type="submit" busy={busy} disabled={organizations.loading || !!organizations.error || !choices.length}>{t('Send help request')}<ArrowRight size={16}/></Button></form> : <div className="community-card help-signin"><ShieldCheck size={45}/><h2>{t('Sign in to request help')}</h2><p>{t('Your account keeps your case details private and lets you follow every update.')}</p><div className="button-row"><Link className="btn btn-primary" to="/login?returnTo=/help">{t('Login')}</Link><Link className="btn btn-secondary" to="/signup">{t('Create an account')}</Link></div></div>}
      <aside className="help-aside"><div className="help-urgent-card"><strong>{t('Need urgent assistance?')}</strong>{helpline && <a href={'tel:'+helpline.replace(/[^+0-9]/g,'')}><Phone size={20}/>{helpline}</a>}<p>{t('For immediate danger, contact local emergency services. This portal does not dispatch emergency assistance.')}</p></div>
        <div className="community-card"><h2>{t('What happens next')}</h2><ol className="help-next">{[['You get a ticket reference','Track updates securely in My help requests.'],['A local advocate is assigned','Current committee members in your selected area are considered first.'],['Your case moves forward','Your advocate can resolve the issue or transfer it to a higher committee with a reason.']].map(([title,copy],i)=><li key={title}><span>{i+1}</span><div><strong>{t(title)}</strong><p>{t(copy)}</p></div></li>)}</ol>{user&&<Link className="text-link" to="/help-requests">{t('My help requests')}<ArrowRight size={15}/></Link>}</div>
      </aside>
    </div>}
  </div></section>;
}
