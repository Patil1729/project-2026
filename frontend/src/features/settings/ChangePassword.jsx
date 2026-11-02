import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { KeyRound } from 'lucide-react';
import { useApp } from '../../app/AppContext';
import { Button, Field, FormError, PageHeading, Panel } from '../../components/ui';
import { authPaths } from '../../lib/authConfig';
import { passwordError } from '../../lib/password';
import { t, useLocale } from '../../lib/i18n';

export default function ChangePassword() {
  useLocale();
  const { user, request, endSession, notify } = useApp(), navigate = useNavigate();
  const [values, setValues] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' });
  const [error, setError] = useState(null), [busy, setBusy] = useState(false);
  const change = key => e => { const next = e.target.value; setValues(old => ({ ...old, [key]: next })); };
  async function submit(e) {
    e.preventDefault(); const invalid = passwordError(values); setError(invalid); if (invalid) return;
    setBusy(true);
    try {
      await request(authPaths.changePassword, { method: 'POST', body: values });
      endSession(); notify('Password changed. Sign in with your new password.'); navigate('/login', { replace: true });
    } catch (error) { setError(error); }
    finally { setBusy(false); setValues({ currentPassword: '', newPassword: '', confirmPassword: '' }); }
  }
  return <><PageHeading eyebrow={t('ACCOUNT SECURITY')} title={t('Change password')} description={t('Confirm your current password, then choose a new one.')}/>
    <Panel title={t('Password & sign-in')}><form className="password-change-form" onSubmit={submit}>
      <div className="info-note"><KeyRound size={21}/><p>{t('After changing your password, sign in again on each device with the new password.')}</p></div>
      <input className="sr-only" tabIndex={-1} aria-hidden="true" name="username" autoComplete="username" value={user.username} readOnly/>
      <fieldset className="wizard-fields" disabled={busy}>
        <Field label={t('Current password')} type="password" autoComplete="current-password" required value={values.currentPassword} onChange={change('currentPassword')}/>
        <Field label={t('New password')} type="password" autoComplete="new-password" required minLength={8} maxLength={72} value={values.newPassword} onChange={change('newPassword')} hint={t('Use 8–72 characters. Longer Unicode passwords may reach the 72-byte limit sooner.')}/>
        <Field label={t('Confirm new password')} type="password" autoComplete="new-password" required minLength={8} maxLength={72} value={values.confirmPassword} onChange={change('confirmPassword')}/>
      </fieldset><FormError error={error}/><div className="form-actions"><Link className="btn btn-secondary" to="/profile">{t('Back to profile')}</Link><Button type="submit" busy={busy}>{t('Update password')}</Button></div>
    </form></Panel>
  </>;
}
