import { Link } from 'react-router';
import { ArrowLeft, KeyRound, Mail, Phone } from 'lucide-react';
import { portalContact, contactPhoneHref } from '../lib/portalContact';
import { t, useLocale } from '../lib/i18n';

export default function ForgotPassword() {
  useLocale();
  return <section className="auth-page auth-embedded"><section className="auth-card recovery-card">
    <KeyRound size={38}/><h1>{t('Forgot password?')}</h1>
    <p>{t('Get help restoring access to your account.')}</p>
    <div className="recovery-guidance">
      <h2>{t('Contact your committee administrator')}</h2>
      <p>{t('Share your username and ask your committee administrator to help you recover your account after verifying your identity.')}</p>
      <p>{t('Keep your password and one-time codes private.')}</p>
    </div>
    <div className="recovery-contact"><h2>{t('Get in touch')}</h2>
      {portalContact.phone && <a href={contactPhoneHref}><Phone size={17}/><span>{portalContact.phone}</span></a>}
      {portalContact.email && <a href={`mailto:${portalContact.email}`}><Mail size={17}/><span>{portalContact.email}</span></a>}
      <Link className="text-link" to="/#contact">{t('View head office contact details')}</Link>
    </div>
    <Link className="btn btn-secondary" to="/login"><ArrowLeft size={16}/>{t('Back to sign in')}</Link>
  </section></section>;
}
