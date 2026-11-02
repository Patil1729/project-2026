import { ShieldCheck, RefreshCw } from 'lucide-react';
import { useApp } from '../app/AppContext';
import { usePermissions } from '../app/usePermissions';
import { humanize } from '../lib/format';
import { t, useLocale } from '../lib/i18n';

export default function AreaAccessSummary() {
  useLocale(); const { user, refreshAccess } = useApp(); const { manage, globalAdministrator } = usePermissions();
  if (!manage) return null;
  return <aside className="area-access-summary" aria-label={t('Administration access')}>
    <ShieldCheck size={20}/><div><strong>{t(globalAdministrator ? 'Organisation-wide administration' : 'Administration limited to your assigned areas')}</strong>
      {!globalAdministrator && <div className="area-scope-chips">{(user.access?.scopes || []).map(scope => <span key={scope.assignmentId}>{humanize(scope.role)} · {scope.organizationUnitName}</span>)}</div>}
      <p>{t(globalAdministrator ? 'You can assign area administrators and manage global operations.' : 'Private records are filtered by your active organisation assignments and their descendants.')}</p>
    </div><button className="btn btn-ghost" type="button" onClick={refreshAccess}><RefreshCw size={15}/>{t('Refresh access')}</button>
  </aside>;
}
