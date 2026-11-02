import {lazy,Suspense} from 'react';
import {Link} from 'react-router';
import {useApp,useResource} from '../app/AppContext';
import {usePermissions} from '../app/usePermissions';
import {t,useLocale} from '../lib/i18n';
import {PageHeading,Panel,Badge,ResourceState} from './ui';
import ImpactStats from '../features/dashboard/ImpactStats';
const MaharashtraMap=lazy(()=>import('./MaharashtraMap'));
export default function MemberDashboard(){
 useLocale();const {user}=useApp(),{review}=usePermissions();
 const account=useResource(`/users/${user.id}`),profile=useResource(`/users/${user.id}/profile`);
 const data=account.data?{users:[account.data],profiles:profile.data?{[user.id]:profile.data}:{},locationsLoading:profile.loading,failures:profile.error&&profile.error.status!==404?[user.id]:[]}:null;
 return <><PageHeading eyebrow={t('MY WORKSPACE')} title={t('Welcome back')+', '+user.username} description={t('Your registered account and saved location.')}>
 <Link className="btn btn-primary" to="/profile">{t('My profile')}</Link></PageHeading><ImpactStats/>
 <ResourceState resource={account}><Panel title={t('Registered account')}><div className="public-feature"><strong>{account.data?.fullName||user.username}</strong><Badge value={account.data?.enabled?'ACTIVE':'INACTIVE'}/></div></Panel></ResourceState>
 <Suspense fallback={<div role="status">{t('Loading…')}</div>}><MaharashtraMap userData={data}/></Suspense>
 <Panel title={t('Your next move')}><div className="public-feature"><Link className="btn btn-secondary" to="/help-requests">{t('My help requests')}</Link><Link className="btn btn-secondary" to="/help">{t('Get help')}</Link><Link className="btn btn-primary" to="/applications?view=my&new=1">{t('New application')}</Link><Link className="btn btn-secondary" to="/change-password">{t('Change password')}</Link><Link className="btn btn-secondary" to="/applications?view=my">{t('My applications')}</Link>{review&&<Link className="btn btn-secondary" to="/reviews">{t('Review inbox')}</Link>}</div></Panel></>;
}
