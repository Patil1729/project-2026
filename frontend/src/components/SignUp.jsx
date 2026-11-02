import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router';
import { Scale } from 'lucide-react';
import { useApp, useResource } from '../app/AppContext';
import { authPaths } from '../lib/authConfig';
import { t, useLocale } from '../lib/i18n';
import { Button, Field, FormError, Select } from './ui';
import { organizationChoices, signupPayload } from '../lib/organizations';
import { humanize, items } from '../lib/format';
export default function SignUp() {
  useLocale();
  const {user,request,login,refresh}=useApp(), navigate=useNavigate();
  const units = useResource('/public/portal/organization-units', {}, !user);
  const choices = organizationChoices(items(units.data));
  const [v,setV]=useState({fullName:'',username:'',mobile:'',email:'',password:'',confirm:'',organizationUnitId:''}),[busy,setBusy]=useState(false),[error,setError]=useState(null),[created,setCreated]=useState(false);
  const change=(key)=>e=>setV(old=>({...old,[key]:e.target.value}));
  if(user)return <Navigate to="/dashboard" replace/>;
  async function submit(e) {
    e.preventDefault();setError(null);
    if(v.password!==v.confirm){setError(new Error('Passwords do not match.'));return;}
    if(new TextEncoder().encode(v.password).length>72){setError(new Error('Password must contain at most 72 UTF-8 bytes.'));return;}
    setBusy(true);
    try {
      const body=signupPayload(v, choices);
      await request(authPaths.signup,{method:'POST',body});
      setCreated(true);
      await login(body.username,body.password);
      navigate('/dashboard',{replace:true});
    } catch(e){setError(e);} finally{setBusy(false);setV(old=>({...old,password:'',confirm:''}));}
  }
  return <section className="auth-page auth-embedded"><section className="auth-card"><Scale size={36}/><h1>{t('Create account')}</h1><p>{t('Join the Maharashtra legal cell.')}</p>
    {created?<><p role="status">{t('Account created. Sign in to continue.')}</p><FormError error={error}/><Link className="btn btn-primary" to="/login">{t('Login')}</Link></>:<form onSubmit={submit}>
    <Field label={t('Full name')} value={v.fullName} onChange={change('fullName')} autoComplete="name" required maxLength={150}/>
    <Field label={t('Username')} value={v.username} onChange={change('username')} autoComplete="username" minLength={3} maxLength={150} pattern="[A-Za-z0-9][A-Za-z0-9._\-]*" required/>
    <Field label={t('Mobile number')} type="tel" value={v.mobile} onChange={change('mobile')} autoComplete="tel" pattern="\+[1-9][0-9]{7,14}" hint={t('Include country code, e.g. +919876543210')} required/>
    <Field label={t('Email address')} type="email" value={v.email} onChange={change('email')} autoComplete="email" maxLength={255}/>
    <Select label={t('Organization unit')} required value={v.organizationUnitId} onChange={change('organizationUnitId')} disabled={units.loading || !!units.error} hint={t('Choose the organization unit you belong to. This does not assign a committee position.')}>
      <option value="">{t(units.loading ? 'Loading organizations…' : 'Choose an active unit')}</option>
      {choices.map(u => <option key={u.id} value={u.id}>{u.path} · {humanize(u.type)}</option>)}
    </Select>
    {units.error && <div className="signup-organizations-error" role="alert"><p>{t('Organization choices are temporarily unavailable. Please try again.')}</p><Button variant="secondary" onClick={refresh}>{t('Try again')}</Button></div>}
    {!units.loading && !units.error && !choices.length && <p role="status">{t('No active organization units are available. Contact your administrator.')}</p>}
    <Field label={t('Password')} type="password" value={v.password} onChange={change('password')} autoComplete="new-password" minLength={8} maxLength={72} required/>
    <Field label={t('Confirm password')} type="password" value={v.confirm} onChange={change('confirm')} autoComplete="new-password" minLength={8} maxLength={72} required/>
    <p>{t('Your account starts as an applicant. Committee appointments follow the approval process.')}</p><FormError error={error}/><Button type="submit" busy={busy} disabled={!v.organizationUnitId || units.loading || !!units.error || !choices.length}>{t('Sign up')}</Button>
    </form>}
    <p className="auth-links">{t('Already have an account?')} <Link to="/login">{t('Login')}</Link></p><Link className="text-link" to="/">{t('Return to overview')}</Link>
  </section></section>;
}
