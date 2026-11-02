import { ShieldCheck, UserRound, Users } from 'lucide-react';
import { useResource } from '../../app/AppContext';
import { Avatar } from '../../components/ui';
import { humanize, items } from '../../lib/format';
import { t, useLocale } from '../../lib/i18n';
import { stateExecutiveSeats } from '../../lib/stateExecutive';

export default function StateExecutive() {
  useLocale();
  const resource = useResource('/public/portal/state-executive/positions');
  const members = stateExecutiveSeats(items(resource.data));
  const filled = members.filter(m => m.status === 'FILLED').length;
  return <section className="portal-section portal-executive" id="state-executive"><div className="portal-container">
    <div className="portal-section-heading"><div><span className="portal-overline">{t('OUR PEOPLE')}</span><h2>{t('State Executive Committee')}</h2><p>{t('Every position on the state committee, with its current appointment.')}</p></div></div>
    {!resource.loading && !resource.error && members.length > 0 && <div className="executive-summary"><span>{t('Positions')}: <strong>{members.length}</strong></span><span>{t('Appointed')}: <strong>{filled}</strong></span><span>{t('Vacant')}: <strong>{members.filter(m => m.status === 'VACANT').length}</strong></span></div>}
    {resource.loading ? <p className="portal-empty-network" role="status">{t('Loading…')}</p> : resource.error ? <p className="portal-empty-network" role="status">{t('State committee details are temporarily unavailable.')}</p> : members.length ? <div className="portal-executive-grid">
      {members.map((member, i) => <article className={`portal-executive-card ${member.status === 'FILLED' ? '' : 'executive-unfilled'}`} key={member.positionId}>
        {member.status === 'FILLED' ? <><Avatar name={member.advocateName} size="large" index={i}/><span className="executive-verified" aria-label={t('Current appointment')}><ShieldCheck size={17}/></span></> : <span className="executive-vacancy-icon" aria-hidden="true"><UserRound size={29}/></span>}
        <h3>{member.status === 'FILLED' ? member.advocateName : t(member.status === 'VACANT' ? 'Vacant position' : 'Appointment unavailable')}</h3><span className="appointment-position">{humanize(member.positionType)}{member.positionType === 'VICE_PRESIDENT' ? ` · ${t('Seat')} ${member.positionNumber}` : ''}</span>
        {member.responsibilityUnitName && <p>{member.responsibilityUnitName}</p>}<small>{member.committeeName}</small>
      </article>)}
    </div> : <div className="portal-empty-network"><Users size={28}/><p>{t('State committee positions will appear here once the committee is created.')}</p></div>}
  </div></section>;
}
