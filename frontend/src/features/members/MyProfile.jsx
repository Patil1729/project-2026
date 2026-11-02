import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import { Edit3, KeyRound } from 'lucide-react';
import { useApp, useResource } from '../../app/AppContext';
import { t, useLocale } from '../../lib/i18n';
import { humanize, date, items } from '../../lib/format';
import { Button, FormError, Panel, PageHeading, ResourceState } from '../../components/ui';
import { profilePayload, profileValues } from '../../lib/profile';
import ProfileFields from './ProfileFields';
import ProfilePictureEditor from './ProfilePictureEditor';

export default function MyProfile() {
  useLocale();
  const { user, request, refresh, notify } = useApp();
  const account = useResource(`/users/${user.id}`), profile = useResource(`/users/${user.id}/profile`);
  const [editing, setEditing] = useState(false), [values, setValues] = useState(() => profileValues());
  const [busy, setBusy] = useState(false), [error, setError] = useState(null);
  const savedProfile = useRef(null);
  const constituencies = useResource('/assembly-constituencies', { stateCode: 'MH' }, editing);
  useEffect(() => { if (profile.data) savedProfile.current = profile.data; }, [profile.data]);
  function edit() { setValues(profileValues(profile.data || savedProfile.current)); setError(null); setEditing(true); }
  async function save(e) {
    e.preventDefault(); setError(null); setBusy(true);
    try {
      const result = await request(`/users/${user.id}/profile`, { method: profile.data || savedProfile.current ? 'PUT' : 'POST', body: profilePayload(values) });
      savedProfile.current = result; setEditing(false); refresh(); notify('Member profile saved.');
    } catch (error) { setError(error); }
    finally { setBusy(false); }
  }
  return <><PageHeading eyebrow={t('MY WORKSPACE')} title={t('My profile')} description={t('Your account and member profile.')}>
    <Link className="btn btn-secondary" to="/change-password"><KeyRound size={16}/>{t('Change password')}</Link>
    <Link className="btn btn-primary" to="/applications?view=my&new=1">{t('New application')}</Link>
  </PageHeading>
    <Panel title={t('Profile picture')}><ProfilePictureEditor userId={user.id} name={account.data?.fullName || user.username}/></Panel>
    <ResourceState resource={account}><Panel title={t('Personal information')}><dl className="profile-summary">{[
      ['Full name', account.data?.fullName || user.username], ['Username', user.username], ['Mobile number', account.data?.mobile],
      ['Email address', account.data?.email], ['Roles', (user.roles || []).map(r => humanize(typeof r === 'string' ? r : r.authority || r.name)).join(', ')],
    ].map(([label, value]) => <div key={label}><dt>{t(label)}</dt><dd>{value || '—'}</dd></div>)}</dl></Panel></ResourceState>
    <Panel title={t('Member profile')} action={!editing && !profile.loading && (!profile.error || profile.error.status === 404) && <Button variant="secondary" icon={Edit3} onClick={edit}>{t(profile.error?.status === 404 ? 'Complete profile' : 'Edit profile')}</Button>}>
      {editing ? <form className="self-profile-form" onSubmit={save}>
        <FormError error={error}/><fieldset className="wizard-fields" disabled={busy}><ProfileFields value={values} onChange={setValues} constituencies={constituencies}/></fieldset>
        <div className="form-actions"><Button variant="secondary" disabled={busy} onClick={() => setEditing(false)}>{t('Cancel')}</Button><Button type="submit" busy={busy} disabled={constituencies.loading || !!constituencies.error || !items(constituencies.data).length}>{t('Save profile')}</Button></div>
      </form> : profile.error?.status === 404 ? <div className="public-feature"><p>{t('Complete your member profile here or during a new application.')}</p></div> : <ResourceState resource={profile}><dl className="profile-summary">{[
        ['Date of birth', date(profile.data?.dateOfBirth)], ['Education', profile.data?.education], ['Assembly constituency', profile.data?.assemblyConstituencyName],
        ['Address', [profile.data?.address?.city, profile.data?.address?.village, profile.data?.address?.district].filter(Boolean).join(', ')],
      ].map(([label, value]) => <div key={label}><dt>{t(label)}</dt><dd>{value || '—'}</dd></div>)}</dl></ResourceState>}
    </Panel>
  </>;
}
