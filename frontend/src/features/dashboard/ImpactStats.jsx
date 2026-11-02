import { Users, CircleCheckBig, Landmark } from 'lucide-react';
import { useResource } from '../../app/AppContext';
import { number } from '../../lib/format';
import { t, useLocale } from '../../lib/i18n';

export default function ImpactStats() {
  useLocale();
  const summary = useResource('/public/portal/summary');
  const cards = [
    ['registeredAdvocates', 'Registered advocates', Users, 'Advocate memberships'],
    ['grievancesResolved', 'Grievances Resolved', CircleCheckBig, 'Issues brought to resolution'],
    ['activeLegalClinics', 'Active Legal Clinics', Landmark, 'Legal support in our communities'],
  ];
  return <section className="impact-section" aria-label={t('Legal cell impact')}>
    <div className="impact-stats">{cards.map(([key, label, Icon, description]) => {
      const value = summary.data?.[key], known = Number.isSafeInteger(value) && value >= 0;
      return <article className={`impact-card impact-${key}`} key={key}>
        <span className="impact-icon"><Icon size={23}/></span><div><strong data-impact-stat={key}>{summary.loading ? '…' : known ? number(value) : '—'}</strong>
          <h2>{t(label)}</h2><p>{t(summary.loading ? 'Loading…' : known ? description : 'Not yet available')}</p></div>
      </article>;
    })}</div>
    {summary.error && <p className="impact-status" role="status">{t('Impact figures are temporarily unavailable.')}</p>}
  </section>;
}
