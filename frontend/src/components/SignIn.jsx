import { Link, Navigate, useNavigate, useSearchParams } from "react-router";
import { useState } from 'react';
import { Scale } from 'lucide-react';
import { useApp } from '../app/AppContext';
import { Button, Field, FormError } from './ui';
import { t, useLocale } from '../lib/i18n';
export default function SignIn() {
  useLocale();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const destination = ['/help','/help-requests','/gallery/manage'].includes(params.get('returnTo')) ? params.get('returnTo') : '/dashboard';
  const {
    user, login,
    authError,
    loadUser
  } = useApp();
  const [username, setUsername] = useState(''),
    [password, setPassword] = useState(''),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(null);
  if(user) return <Navigate to={destination} replace/>;
  return <section className="auth-page auth-embedded"><section className="auth-card"><Scale size={40} /><h1>{t("Welcome to LegalCell")}</h1><p>{t("Sign in to your legal cell workspace.")}</p>
    <form onSubmit={async e => {
        e.preventDefault();
        setBusy(true);
        setError(null);
        try {
          await login(username, password);
          navigate(destination, {replace: true});
        } catch (e) {
          setError(e);
        } finally {
          setBusy(false);
          setPassword('');
        }
      }}>
      <Field label={t("Username")} value={username} onChange={e => setUsername(e.target.value)} autoComplete="username" required />
      <Field label={t("Password")} type="password" value={password} onChange={e => setPassword(e.target.value)} autoComplete="current-password" required />
      <div className="forgot-password-link"><Link to="/forgot-password">{t('Forgot password?')}</Link></div>
      <FormError error={error || authError} /><Button type="submit" busy={busy}>{t("Sign in")}</Button>
      {t(authError && <Button variant="secondary" onClick={loadUser}>{t("Try again")}</Button>)}
    </form><p className="auth-links">{t("New to LegalCell?")} <Link to="/signup">{t("Sign up")}</Link></p><Link className="text-link" to="/">{t("Return to overview")}</Link></section></section>;
}
