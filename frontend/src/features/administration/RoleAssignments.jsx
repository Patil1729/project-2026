import { useState } from 'react';
import { History, Plus, ShieldCheck, X } from 'lucide-react';
import { useApp, useResource } from '../../app/AppContext';
import { Badge, Button, Empty, Field, FormError, Modal, PageHeading, Pagination, ResourceState, Select, Timeline } from '../../components/ui';
import { date, humanize, items } from '../../lib/format';
import { organizationChoices } from '../../lib/organizations';
import { t, useLocale } from '../../lib/i18n';

function GrantForm({ onClose }) {
  useLocale(); const { request, refresh, notify } = useApp();
  const users = useResource('/users'), units = useResource('/public/portal/organization-units'), options = useResource('/admin/role-assignments/options');
  const [v, setV] = useState({ userId: '', role: '', organizationUnitId: '', reason: '' });
  const [busy, setBusy] = useState(false), [error, setError] = useState(null);
  const choices = organizationChoices(items(units.data)).filter(u => u.type === options.data?.[v.role]);
  async function submit(e) {
    e.preventDefault(); setBusy(true); setError(null);
    try {
      await request('/admin/role-assignments', { method: 'POST', body: { userId: Number(v.userId), role: v.role, organizationUnitId: Number(v.organizationUnitId), reason: v.reason.trim() } });
      refresh(); notify('Area administration assigned.'); onClose();
    } catch (error) { setError(error); } finally { setBusy(false); }
  }
  return <Modal title={t('Assign an area administrator')} subtitle={t('Choose the person, permission and exact organisation boundary.')} onClose={busy ? () => {} : onClose}>
    <form onSubmit={submit}><fieldset className="wizard-fields" disabled={busy}>
      <ResourceState resource={users}><Select label={t('Member')} required value={v.userId} onChange={e => setV({ ...v, userId: e.target.value })}><option value="">{t('Select an enabled member')}</option>{items(users.data).filter(u => u.enabled).map(u => <option value={u.id} key={u.id}>{u.fullName} · @{u.username}</option>)}</Select></ResourceState>
      <ResourceState resource={options}><Select label={t('Area admin role')} required value={v.role} onChange={e => setV({ ...v, role: e.target.value, organizationUnitId: '' })}><option value="">{t('Choose a role')}</option>{Object.keys(options.data || {}).sort().map(role => <option key={role} value={role}>{humanize(role)}</option>)}</Select></ResourceState>
      <ResourceState resource={units}><Select label={t('Assigned organisation area')} required disabled={!v.role} value={v.organizationUnitId} onChange={e => setV({ ...v, organizationUnitId: e.target.value })}><option value="">{t('Choose an active unit')}</option>{choices.map(u => <option key={u.id} value={u.id}>{u.path}</option>)}</Select></ResourceState>
      {v.role && !units.loading && !choices.length && <p className="muted">{t('Create an active organisation unit matching this role first.')}</p>}
      <Field label={t('Reason')} required maxLength={2000} value={v.reason} onChange={e => setV({ ...v, reason: e.target.value })}/>
      <div className="info-note"><ShieldCheck size={20}/><p>{t('This grants administration within the chosen area. President appointments and approval decisions remain separate.')}</p></div>
    </fieldset><FormError error={error}/><div className="form-actions"><Button variant="secondary" onClick={onClose} disabled={busy}>{t('Cancel')}</Button><Button type="submit" busy={busy} disabled={!v.userId || !v.role || !v.organizationUnitId || users.loading || units.loading || options.loading || !!users.error || !!units.error || !!options.error}>{t('Grant area access')}</Button></div></form>
  </Modal>;
}
function HistoryDialog({ assignment, onClose }) {
  useLocale(); const resource = useResource(`/admin/role-assignments/${assignment.id}/history`, { size: 100 });
  return <Modal title={t('Area role history')} subtitle={`${assignment.fullName} · ${assignment.organizationUnitName}`} onClose={onClose}><ResourceState resource={resource}><Timeline entries={items(resource.data).map(event => ({ ...event, note: `${event.actorUsername} · ${event.reason}` }))}/></ResourceState></Modal>;
}
export default function RoleAssignments() {
  useLocale(); const { request, refresh, notify } = useApp();
  const [page, setPage] = useState(0), [userId, setUserId] = useState(''), [grant, setGrant] = useState(false), [revoke, setRevoke] = useState(null), [history, setHistory] = useState(null);
  const [reason, setReason] = useState(''), [error, setError] = useState(null), [busy, setBusy] = useState(false);
  const resource = useResource('/admin/role-assignments', { page, size: 20, userId }), users = useResource('/users');
  async function confirmRevoke(e) {
    e.preventDefault(); setBusy(true); setError(null);
    try { await request(`/admin/role-assignments/${revoke.id}/revoke`, { method: 'PATCH', body: { reason: reason.trim() } }); refresh(); notify('Area administration revoked.'); setRevoke(null); }
    catch (error) { setError(error); } finally { setBusy(false); }
  }
  return <><PageHeading eyebrow={t('ACCESS & RESPONSIBILITY')} title={t('Area administrator assignments')} description={t('Grant and revoke scoped administration with a recorded reason.')}><Button icon={Plus} onClick={() => setGrant(true)}>{t('Assign area admin')}</Button></PageHeading>
    <div className="info-note"><ShieldCheck size={20}/><p>{t('Existing role names alone do not grant area access. Each administrator needs an active assignment to an organisation unit.')}</p></div>
    <section className="panel"><div className="list-toolbar"><Select label={t('Filter by member')} value={userId} onChange={e => { setUserId(e.target.value); setPage(0); }}><option value="">{t('All members')}</option>{items(users.data).map(u => <option key={u.id} value={u.id}>{u.fullName} · @{u.username}</option>)}</Select></div>
      <ResourceState resource={resource}>{items(resource.data).length ? <><div className="table-scroll"><table><thead><tr><th>{t('MEMBER')}</th><th>{t('ROLE & AREA')}</th><th>{t('STATUS')}</th><th>{t('ASSIGNED')}</th><th>{t('ACTIONS')}</th></tr></thead><tbody>{items(resource.data).map(a => <tr key={a.id}><td><strong>{a.fullName}</strong><small className="table-small">@{a.username}</small></td><td><strong>{humanize(a.role)}</strong><small className="table-small">{a.organizationUnitName}</small></td><td><Badge value={a.effective ? 'ACTIVE' : a.active ? 'SUSPENDED' : 'REVOKED'}/></td><td>{date(a.assignedAt)}<small className="table-small">{a.assignedBy}</small></td><td><div className="row-actions"><Button variant="ghost" icon={History} onClick={() => setHistory(a)}>{t('History')}</Button>{a.active && <Button variant="ghost" icon={X} onClick={() => { setRevoke(a); setReason(''); setError(null); }}>{t('Revoke')}</Button>}</div></td></tr>)}</tbody></table></div><Pagination page={page} total={resource.data?.totalElements} hasNext={resource.data?.hasNext} onChange={setPage}/></> : <Empty title={t('No area administrator assignments yet')} description={t('Assign the first area administrator to an active organisation unit.')}/>}</ResourceState>
    </section>
    {grant && <GrantForm onClose={() => setGrant(false)}/>}{history && <HistoryDialog assignment={history} onClose={() => setHistory(null)}/>}
    {revoke && <Modal title={t('Revoke area access?')} subtitle={`${revoke.fullName} · ${revoke.organizationUnitName}`} onClose={busy ? () => {} : () => setRevoke(null)}><form onSubmit={confirmRevoke}><p>{t('This removes this administration assignment. Other active assignments and committee appointments are retained.')}</p><Field label={t('Reason')} required maxLength={2000} value={reason} onChange={e => setReason(e.target.value)}/><FormError error={error}/><div className="form-actions"><Button variant="secondary" disabled={busy} onClick={() => setRevoke(null)}>{t('Cancel')}</Button><Button type="submit" variant="danger" busy={busy}>{t('Revoke access')}</Button></div></form></Modal>}
  </>;
}
