import { useResource } from '../../app/AppContext';
import { Panel, ResourceState } from '../../components/ui';
import { t, useLocale } from '../../lib/i18n';
import { number } from '../../lib/format';

export default function AreaWorkload() {
  useLocale(); const resource = useResource('/admin/dashboard');
  const cards = [['totalApplications', 'Applications'], ['pendingApplications', 'Pending applications'], ['overdueReviewSteps', 'Overdue reviews'], ['activeCommittees', 'Active committees'], ['vacantPositions', 'Vacant positions']];
  return <section className="area-workload" aria-label={t('Application workload in your area')}><h2 className="section-label">{t('Application workload in your area')}</h2><p className="muted">{t('Applications follow their selected branch; members follow their home organisation. Approval actions still require the assigned presidency.')}</p><ResourceState resource={resource}><div className="area-workload-grid">{cards.map(([key,label]) => <Panel key={key} title={t(label)}><strong className="registered-user-count">{number(resource.data?.[key] || 0)}</strong></Panel>)}</div></ResourceState></section>;
}
