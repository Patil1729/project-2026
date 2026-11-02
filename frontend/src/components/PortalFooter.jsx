import { Link } from 'react-router';
import { ArrowUpRight, Mail, MapPin, Phone } from 'lucide-react';
import { useApp } from '../app/AppContext';
import { usePermissions } from '../app/usePermissions';
import { t, useLocale } from '../lib/i18n';
import { SocialLinks } from './BrandExtras';
import { portalContact, contactPhoneHref } from '../lib/portalContact';
export default function PortalFooter() {
  const language=useLocale();const {user}=useApp(),{manage}=usePermissions();
  return <footer className="portal-footer"><div className="portal-container"><div className="portal-footer-grid">
    <div className="portal-footer-about"><Link to="/" className="portal-brand"><img src="/images/shiv-sena-emblem.png" alt="" width="46" height="46"/><span><strong>{t('SHIV SENA LEGAL CELL')}</strong><small>{t('JUSTICE · PROTECTION · EMPOWERMENT')}</small></span></Link><p>{t('A community of advocates connected by a shared commitment to legal awareness, service and justice.')}</p><span className="footer-location"><MapPin size={14}/>{t('Maharashtra, India')}</span><SocialLinks/></div>
    <div><h3>{t('Membership')}</h3><Link to="/gallery">{t('Gallery')}</Link><Link to="/help">{t('Get help')}</Link>{user&&<Link to="/help-requests">{t('My help requests')}</Link>}<Link to={user?'/applications?view=my&new=1':'/signup'}>{t('Become a member')}</Link><Link to={user?'/applications?view=my':'/login'}>{t('Track application')}</Link>{manage&&<Link to="/members">{t('Member directory')}</Link>}</div>
    <div><h3>{t('Our organization')}</h3><Link to={user?'/organization':'/login'}>{t('Organization hierarchy')}</Link><Link to={user?'/committees':'/login'}>{t('Committees & positions')}</Link>{manage&&<Link to="/appointments">{t('Appointments')}</Link>}<Link to="/#network" onClick={()=>document.getElementById('network')?.scrollIntoView({behavior:'smooth'})}>{t('Maharashtra state map')}</Link></div>
    <div className="portal-footer-workspace"><h3>{t('A connected workspace')}</h3><p>{t('Follow every step, from the first application to the final appointment.')}</p><Link to={user?'/dashboard':'/login'}>{t('Open workspace')}<ArrowUpRight size={15}/></Link></div>
    <div className="portal-footer-contact" id="contact"><h3>{t('Get in touch')}</h3><div><span className="footer-contact-label"><MapPin size={15}/>{t('Head office')}</span><address>{portalContact.address[language]}</address></div><div><span className="footer-contact-label"><Phone size={15}/>{t('Helpline')}</span>{portalContact.phone ? <a href={contactPhoneHref}>{portalContact.phone}</a> : <span>{t('Contact your committee administrator')}</span>}</div>{portalContact.email && <a href={`mailto:${portalContact.email}`}><Mail size={15}/>{portalContact.email}</a>}</div>
    </div><div className="portal-footer-bottom"><span>© {new Date().getFullYear()} {t('SHIV SENA LEGAL CELL')}</span><span>{t(user?'Signed in':'Public access')} · v2.8.0</span></div></div></footer>;
}
