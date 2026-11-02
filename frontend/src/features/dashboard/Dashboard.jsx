import { usePermissions } from '../../app/usePermissions';
import AreaWorkload from './AreaWorkload';
import {lazy,Suspense} from 'react';
import {Link} from 'react-router';
import {useRegisteredUsers} from './RegisteredUsersContext';
import UserSummary from './UserSummary';
import ImpactStats from './ImpactStats';
import {t,useLocale} from '../../lib/i18n';
import {date,exportCsv} from '../../lib/format';
import {PageHeading,Panel,ResourceState,Badge,Button,Empty} from '../../components/ui';
const MaharashtraMap=lazy(()=>import('../../components/MaharashtraMap'));
export default function Dashboard(){
 useLocale();const {scopedAdministrator}=usePermissions();const resource=useRegisteredUsers();const users=resource.data?.users||[];
 const recent=[...users].sort((a,b)=>(Date.parse(b.createdAt)||0)-(Date.parse(a.createdAt)||0)||b.id-a.id).slice(0,10);
 return <><PageHeading eyebrow={t('REGISTERED USER OVERVIEW')} title={t('Your registered users, at a glance.')} description={t(scopedAdministrator ? 'Registered accounts whose home organisation is in your assigned areas.' : 'Real account registrations and enabled users across Maharashtra.')}>
  <Button variant="secondary" disabled={!users.length} onClick={()=>exportCsv('registered-users.csv',users.map(user=>({id:user.id,name:user.fullName,username:user.username,enabled:user.enabled,registeredAt:user.createdAt})))}>{t('Export registered users')}</Button><Link className="btn btn-primary" to="/members">{t('Member directory')}</Link>
 </PageHeading><AreaWorkload/><h2 className="section-label">{t('Organisation-wide impact')}</h2><ImpactStats/><h2 className="section-label">{t(scopedAdministrator ? 'Members in your assigned areas' : 'Registered users')}</h2><UserSummary/>
 <Suspense fallback={<div role="status">{t('Loading…')}</div>}><MaharashtraMap/></Suspense>
 <Panel title={t('Recently registered users')} subtitle={t('Latest registered accounts, independently of application status.')}><ResourceState resource={resource}>{recent.length?<div className="table-scroll"><table><thead><tr><th>{t('Full name')}</th><th>{t('Username')}</th><th>{t('STATUS')}</th><th>{t('Registered on')}</th></tr></thead><tbody>{recent.map(user=><tr key={user.id}><td>{user.fullName||user.username}</td><td>{user.username}</td><td><Badge value={user.enabled?'ACTIVE':'INACTIVE'}/></td><td>{date(user.createdAt)}</td></tr>)}</tbody></table></div>:<Empty title={t('No registered users yet')}/>}</ResourceState></Panel></>;
}
