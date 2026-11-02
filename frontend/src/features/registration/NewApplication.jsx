import { useEffect, useRef, useState } from 'react';
import { ArrowLeft, ArrowRight, Check, FileText, ShieldCheck } from 'lucide-react';
import { useApp, useResource } from '../../app/AppContext';
import { Button, FormError, Modal, ResourceState, Select } from '../../components/ui';
import ProfileFields from '../members/ProfileFields';
import { profilePayload, profileValues } from '../../lib/profile';
import { humanize, items } from '../../lib/format';
import { t, useLocale } from '../../lib/i18n';
import Documents from './Documents';
import ProfilePictureEditor from '../members/ProfilePictureEditor';
import { hasRecommendationUpload } from '../../lib/documents';

const steps = ['Organization & committee', 'User profile', 'Upload documents'];
export default function NewApplication({ onClose, onCreated }) {
  useLocale();
  const { user, request, refresh, notify } = useApp();
  const units = useResource('/organization-units'), committees = useResource('/committees');
  const profile = useResource(`/users/${user.id}/profile`);
  const account = useResource(`/users/${user.id}`);
  const constituencies = useResource('/assembly-constituencies', { stateCode: 'MH' });
  const [step, setStep] = useState(0), [unit, setUnit] = useState(''), [committee, setCommittee] = useState(''), [position, setPosition] = useState('');
  const [values, setValues] = useState(() => profileValues()), [hasProfile, setHasProfile] = useState(false);
  const [application, setApplication] = useState(null), [documents, setDocuments] = useState([]);
  const [busy, setBusy] = useState(false), [uploading, setUploading] = useState(false), [error, setError] = useState(null);
  const initialized = useRef(false), organizationInitialized = useRef(false), heading = useRef(null);
  const positions = useResource(`/committee-positions/committees/${committee}/positions/vacant`, {}, !!committee && !application);
  useEffect(() => {
    if (!initialized.current && (profile.data || profile.error?.status === 404)) {
      initialized.current = true; setHasProfile(!!profile.data); setValues(profileValues(profile.data));
    }
  }, [profile.data, profile.error]);
  useEffect(() => {
    if (account.data && !organizationInitialized.current) {
      organizationInitialized.current = true;
      if (!unit && account.data.organizationUnitId) setUnit(String(account.data.organizationUnitId));
    }
  }, [account.data, unit]);
  useEffect(() => {
    heading.current?.focus({ preventScroll: true });
    const dialog = heading.current?.closest('dialog');
    if (dialog) dialog.scrollTop = 0;
  }, [step]);
  const profileReady = !profile.loading && (!profile.error || profile.error.status === 404);
  const committeeOptions = items(committees.data).filter(c => c.active && c.organizationUnitId === Number(unit));
  const recommendation = hasRecommendationUpload(documents);
  async function next(e) {
    e.preventDefault(); if (busy || uploading) return; setError(null);
    if (step === 0) { setStep(1); return; }
    setBusy(true);
    try {
      await request(`/users/${user.id}/profile`, { method: hasProfile ? 'PUT' : 'POST', body: profilePayload(values) });
      setHasProfile(true);
      if (!application) {
        const created = await request('/registration-applications', { method: 'POST', body: { selectedShakhaId: Number(unit), requestedPositionId: Number(position) } });
        if (!created?.id) throw new Error('The server did not return an application ID. Check My applications before trying again.');
        setApplication(created);
      }
      setStep(2); refresh(); notify('Profile saved. Upload your documents next.');
    } catch (error) { setError(error); }
    finally { setBusy(false); }
  }
  function back() { setError(null); setStep(s => Math.max(0, s - 1)); }
  return <Modal wide title={t('Start a new application')} subtitle={t('Choose your committee, complete your profile, then upload documents.')} onClose={busy || uploading ? () => {} : onClose}>
    <ol className="application-stepper" aria-label={t('Application progress')}>
      {steps.map((label, i) => <li key={label} className={i === step ? 'current' : i < step ? 'complete' : ''} aria-current={i === step ? 'step' : undefined}>
        <span>{i < step ? <Check size={15}/> : i + 1}</span><strong>{t(label)}</strong>
      </li>)}
    </ol>
    <h3 className="wizard-step-heading" ref={heading} tabIndex={-1}>{t(steps[step])}</h3>
    <FormError error={error}/>
    {step === 1 && <ProfilePictureEditor userId={user.id} name={account.data?.fullName || user.username} onBusyChange={setUploading}/>}
    {step < 2 ? <form onSubmit={next}>
      <fieldset disabled={busy || uploading} className="wizard-fields">
        {step === 0 ? <>
          <div className="info-note"><FileText size={20}/><p>{t(application ? 'Your application is saved. Organization and position are fixed for this application.' : 'Select an active organization unit, its committee and an available position.')}</p></div>
          <ResourceState resource={units}><Select label={t('Selected shakha / organization unit')} required value={unit} disabled={!!application} onChange={e => {
            setUnit(e.target.value); setCommittee(''); setPosition('');
          }}><option value="">{t('Choose an active unit')}</option>{items(units.data).filter(u => u.active && u.type !== 'PARTY').map(u => <option key={u.id} value={u.id}>{u.name} · {humanize(u.type)}</option>)}</Select></ResourceState>
          <ResourceState resource={committees}><Select label={t('Committee')} required value={committee} disabled={!unit || !!application} onChange={e => { setCommittee(e.target.value); setPosition(''); }}>
            <option value="">{t('Choose a committee')}</option>{committeeOptions.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
          </Select>{unit && !committeeOptions.length && <p className="muted">{t('Create an active committee for this unit before applying.')}</p>}</ResourceState>
          {committee && (application ? <p className="wizard-selection">{t('Requested position')}: {humanize(application.positionType)} · {application.positionNumber}</p> : <ResourceState resource={positions}><Select label={t('Requested position')} required value={position} onChange={e => setPosition(e.target.value)} hint={t(items(positions.data).length ? 'Only available positions can be requested.' : 'There are no vacant positions in this committee.')}>
            <option value="">{t('Choose an available position')}</option>{items(positions.data).map(p => <option key={p.id} value={p.id}>{humanize(p.positionType)} · {t('Seat')} {p.positionNumber}</option>)}
          </Select></ResourceState>)}
        </> : <>
          <div className="wizard-account"><ShieldCheck size={20}/><div><strong>{account.data?.fullName || user.username}</strong><span>{[account.data?.mobile, account.data?.email].filter(Boolean).join(' · ') || user.username}</span></div></div>
          {profileReady ? <ProfileFields value={values} onChange={setValues} constituencies={constituencies}/> : <ResourceState resource={profile}/>}
        </>}
      </fieldset>
      <div className="form-actions wizard-actions">
        <Button variant="secondary" onClick={step ? back : onClose} disabled={busy || uploading} icon={step ? ArrowLeft : undefined}>{t(step ? 'Back' : 'Cancel')}</Button>
        <Button type="submit" busy={busy} disabled={uploading || (step === 0 ? !unit || !committee || !position || units.loading || !!units.error || committees.loading || !!committees.error || (!application && (positions.loading || !!positions.error)) : !profileReady || constituencies.loading || !!constituencies.error || !items(constituencies.data).length)}>
          {t(step === 0 ? 'Next: user profile' : 'Next: upload documents')}<ArrowRight size={16}/>
        </Button>
      </div>
    </form> : <>
      <div className="info-note"><ShieldCheck size={20}/><p>{t('Your application is saved. Upload the signed recommendation letter and any supporting documents. A reviewer must verify your recommendation before you submit the application.')}</p></div>
      <Documents application={application} mode="my" onDocumentsChange={setDocuments} onBusyChange={setUploading}/>
      <div className="form-actions wizard-actions">
        <Button variant="secondary" icon={ArrowLeft} onClick={back} disabled={uploading}>{t('Back')}</Button>
        <div className="button-row"><Button variant="secondary" onClick={onClose} disabled={uploading}>{t('Save and close')}</Button><Button disabled={!recommendation || uploading} onClick={() => onCreated(application.id)}>{t('Finish & view application')}<ArrowRight size={16}/></Button></div>
      </div>
      {!recommendation && <p className="muted">{t('Upload a recommendation letter to finish. You can also save and return later from My applications.')}</p>}
    </>}
  </Modal>;
}
