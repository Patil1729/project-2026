import { useEffect, useState } from 'react';
import { Link, NavLink, useLocation, useNavigate } from 'react-router';
import { ArrowUpRight, Menu, Scale, X } from 'lucide-react';
import { useApp } from '../app/AppContext';
import { usePermissions } from '../app/usePermissions';
import { LanguageSwitch, t, useLocale } from '../lib/i18n';
import { useCurrentAccount } from '../app/CurrentAccountContext';
import { Avatar, Button } from './ui';
export default function PortalHeader() {
  useLocale();
  const {user,logout,notify}=useApp(), {manage}=usePermissions();
  const [open,setOpen]=useState(false),[busy,setBusy]=useState(false);
  const location=useLocation(),navigate=useNavigate();
  useEffect(()=>setOpen(false),[location.pathname]);
  const current=useCurrentAccount();
  const name=current.data?.fullName||user?.fullName||user?.username;
  async function signout(){setBusy(true);const pending=logout();navigate('/',{replace:true});try{await pending;}catch(e){notify(t('The server could not finish logout. Please retry before closing this browser.')+' '+e.message,'error');}finally{setBusy(false);}}
  return <>
    <div className="portal-utility"><div className="portal-container"><span><Scale size={13}/>{t('Together for justice. Committed to our community.')}</span><span>{t(user?'Signed in':'Public access')}</span></div></div>
    <div className="portal-announcement"><div className="portal-container"><span className="announcement-label">{t('LEGAL CELL')}</span><p>{t('Membership, document verification and appointments. One connected legal community.')}</p><Link to={user?'/applications?view=my':'/login'}>{t('Track application')}<ArrowUpRight size={13}/></Link></div></div>
    <header className="portal-header"><div className="portal-container portal-header-inner">
      <Link to="/" className="portal-brand" aria-label={t('Shiv Sena Legal Cell home')}><img src="/images/shiv-sena-emblem.png" alt={t('Shiv Sena emblem')} width="44" height="44"/><span><strong>{t('SHIV SENA LEGAL CELL')}</strong><small>{t('JUSTICE · PROTECTION · EMPOWERMENT')}</small></span></Link>
      <nav id="portal-navigation" className={`portal-nav ${open?'is-open':''}`} aria-label={t('Portal navigation')}>
        <NavLink to="/" end>{t('Home')}</NavLink>
        <NavLink to="/gallery">{t('Gallery')}</NavLink>
        <NavLink to="/help" className="portal-help-link">{t('Get help')}</NavLink>
        <Link to={user?'/applications?view=my&new=1':'/signup'}>{t('Become a member')}</Link>
        {user&&<NavLink to="/organization">{t('Our network')}</NavLink>}
        {manage&&<NavLink to="/members">{t('Directory')}</NavLink>}
        {user&&<NavLink to="/dashboard" className="portal-workspace-link">{t('Workspace')}<ArrowUpRight size={15}/></NavLink>}
      </nav>
      <div className="portal-header-actions"><LanguageSwitch/>{user?<><Link to="/profile" className="portal-identity" aria-label={t('My profile')} title={name}><Avatar user={user} name={name} size="small"/><span>{name}</span></Link><Button variant="secondary" busy={busy} onClick={signout}>{t('Logout')}</Button></>:<><Link className="btn btn-secondary" to="/login">{t('Login')}</Link><Link className="btn btn-primary" to="/signup">{t('Sign up')}</Link></>}
        <button className="portal-menu-button" type="button" aria-label={t(open?'Close navigation':'Open navigation')} aria-expanded={open} aria-controls="portal-navigation" onClick={()=>setOpen(v=>!v)}>{open?<X size={23}/>:<Menu size={23}/>}</button>
      </div>
    </div></header>
  </>;
}
