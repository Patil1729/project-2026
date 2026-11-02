import { RegisteredUsersProvider } from '../features/dashboard/RegisteredUsersContext';
import { useEffect, useState } from 'react';
import { Link, NavLink, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router';
import { LayoutDashboard, Network, Building2, Users, FileText, ClipboardCheck, UserRoundCheck, Settings2, Menu, UserRound, KeyRound, X, Search, Bell, CircleHelp } from 'lucide-react';
import { useApp, useResource } from './AppContext';
import { usePermissions } from './usePermissions';
import { canVisit } from '../lib/permissions';
import { t, useLocale } from '../lib/i18n';
import { humanize } from '../lib/format';
import { Avatar, IconButton, Modal, Toast } from '../components/ui';
import { PartyCursor } from '../components/BrandExtras';
import PortalHeader from '../components/PortalHeader';
import PortalFooter from '../components/PortalFooter';
import Home from '../features/portal/Home';
import SignIn from '../components/SignIn';
import SignUp from '../components/SignUp';
import ForgotPassword from '../components/ForgotPassword';
import MemberDashboard from '../components/MemberDashboard';
import MyProfile from '../features/members/MyProfile';
import Dashboard from '../features/dashboard/Dashboard';
import Organization from '../features/organization/Organization';
import Committees from '../features/committee/Committees';
import Members from '../features/members/Members';
import Applications from '../features/registration/Applications';
import Reviews from '../features/reviews/Reviews';
import Appointments from '../features/appointments/Appointments';
import Settings from '../features/settings/Settings';
import ChangePassword from '../features/settings/ChangePassword';
import RoleAssignments from '../features/administration/RoleAssignments';
import AreaAccessSummary from '../components/AreaAccessSummary';
import Gallery from '../features/gallery/Gallery';
import GalleryManagement from '../features/gallery/GalleryManagement';
import GetHelp from '../features/help/GetHelp';
import HelpRequests from '../features/help/HelpRequests';
import HelpRequestDetail from '../features/help/HelpRequestDetail';
import { useCommunityAccess } from '../features/community/CommunityAccess';
const navigation=[['/dashboard','Overview',LayoutDashboard],['/help-requests','Help requests',CircleHelp],['/gallery/manage','Manage gallery',FileText],['/applications','Applications',FileText],['/reviews','Review inbox',ClipboardCheck],['/organization','Organization units',Network],['/committees','Committees',Building2],['/members','Member directory',Users],['/appointments','Appointments',UserRoundCheck],['/role-assignments','Area administrators',UserRoundCheck],['/profile','My profile',UserRound],['/change-password','Change password',KeyRound],['/settings','Settings & connection',Settings2]];
function ProtectedRoute({children}) {
  useLocale();const {user,authLoading,loggingOut}=useApp(),location=useLocation();
  if(authLoading)return <div className="empty" role="status">{t('Loading…')}</div>;
  if(!user)return <Navigate to={loggingOut?'/':'/login'} replace/>;
  if(!canVisit(location.pathname,user))return <div className="empty"><h1>{t('Access restricted')}</h1><p>{t('Your role does not have access to this page.')}</p><Link className="btn btn-primary" to="/dashboard">{t('Return to overview')}</Link></div>;
  return children;
}
function PortalApp() {
  const language=useLocale(),{user}=useApp(),{manage, globalAdministrator}=usePermissions();
  const location=useLocation(),navigate=useNavigate(),community=useCommunityAccess();
  const [mobile,setMobile]=useState(false),[guide,setGuide]=useState(false),[notifications,setNotifications]=useState(false),[search,setSearch]=useState('');
  const home=location.pathname==='/' || (location.pathname==='/dashboard'&&!user);
  const auth=['/login','/signup','/forgot-password'].includes(location.pathname),publicPage=['/gallery','/help'].includes(location.pathname),workspace=!home&&!auth&&!publicPage;
  const updates=useResource('/admin/dashboard',{},manage&&workspace&&location.pathname!=='/dashboard');
  const links=navigation.filter(([path])=>!!user&&canVisit(path,user)&&(path!=='/gallery/manage'||community.data?.canManageGallery));
  const pageName=home?'Home':location.pathname==='/gallery'?'Gallery':location.pathname==='/help'?'Get help':location.pathname.startsWith('/help-requests/')?'Help request':navigation.find(([path])=>path===location.pathname)?.[1]||(auth?location.pathname==='/login'?'Login':location.pathname==='/forgot-password'?'Forgot password?':'Sign up':'Page');
  useEffect(()=>{setMobile(false);setNotifications(false);document.title=`${t(pageName)} — ${t('SHIV SENA LEGAL CELL')}`;if(location.hash)requestAnimationFrame(()=>document.getElementById(location.hash.slice(1))?.scrollIntoView());},[location.pathname,location.hash,language,pageName]);
  useEffect(()=>{const close=e=>{if(e.key==='Escape'){setMobile(false);setNotifications(false);}};window.addEventListener('keydown',close);return ()=>window.removeEventListener('keydown',close);},[]);
  const content=<Routes>
    <Route path="/" element={<Home language={language}/>}/>
    <Route path="/gallery" element={<Gallery/>}/><Route path="/help" element={<GetHelp/>}/>
    <Route path="/help-requests/:id" element={<ProtectedRoute><HelpRequestDetail/></ProtectedRoute>}/>
    <Route path="/dashboard" element={!user?<Home language={language}/>:manage?<Dashboard/>:<MemberDashboard/>}/>
    <Route path="/login" element={<SignIn/>}/><Route path="/signup" element={<SignUp/>}/>
    <Route path="/forgot-password" element={<ForgotPassword/>}/>
    {[
      ['/help-requests',<HelpRequests/>],['/gallery/manage',<GalleryManagement/>],['/organization',<Organization/>],['/committees',<Committees/>],['/members',<Members/>],['/applications',<Applications/>],['/reviews',<Reviews/>],['/appointments',<Appointments/>],['/role-assignments',<RoleAssignments/>],['/settings',<Settings/>],['/profile',<MyProfile/>],['/change-password',<ChangePassword/>]
    ].map(([path,element])=><Route key={path} path={path} element={<ProtectedRoute>{element}</ProtectedRoute>}/>)}
    <Route path="*" element={<div className="empty"><h1>{t('Page not found')}</h1><Link to="/">{t('Return to overview')}</Link></div>}/>
  </Routes>;
  return <div className={`portal-app ${home?'is-home':'is-workspace'}`}>
    <a className="skip-link" href="#main-content" onClick={e=>{e.preventDefault();document.getElementById('main-content')?.focus();}}>{t('Skip to content')}</a><PortalHeader/>
    {workspace?<>
      <div className="workspace-page-banner"><div className="portal-container"><span><Link to="/">{t('Home')}</Link> › {t('Workspace')}</span><div><h1>{t('Legal Cell workspace')}</h1><p>{t('Your people, committees and every application. Connected.')}</p></div></div></div>
      <div className="portal-container workspace-layout">{mobile&&<button className="workspace-scrim" aria-label={t('Close navigation')} onClick={()=>setMobile(false)}/>}
      <aside className={`workspace-sidebar ${mobile?'is-open':''}`}><div className="workspace-sidebar-heading"><span>{t('Workspace')}</span><IconButton icon={X} label={t('Close navigation')} onClick={()=>setMobile(false)}/></div><nav aria-label={t('Workspace navigation')}><div className="workspace-nav-group"><p>{t('WORKSPACE')}</p>{links.map(([path,label,Icon])=><NavLink key={path} to={path==='/applications'&&!manage?'/applications?view=my':path} className={({isActive})=>`workspace-nav-link ${isActive?'active':''}`}><Icon size={17}/><span>{t(path==='/applications'&&!manage?'My applications':label)}</span></NavLink>)}</div></nav>{user&&<div className="workspace-sidebar-bottom"><div><Avatar user={user} name={user.fullName||user.username}/><span>{user.fullName||user.username}<small>{(user.roles||[]).map(r=>humanize(typeof r==='string'?r:r.authority||r.name)).join(', ')}</small></span></div></div>}</aside>
      <div className="workspace-content"><div className="workspace-toolbar"><div><IconButton icon={Menu} label={t('Open navigation')} onClick={()=>setMobile(true)}/><span>{t(pageName)}</span></div><div className="workspace-toolbar-actions">{manage&&location.pathname!=='/dashboard'&&<form className="global-search" onSubmit={e=>{e.preventDefault();navigate('/applications?q='+encodeURIComponent(search));setSearch('');}}><Search size={15}/><input value={search} onChange={e=>setSearch(e.target.value)} placeholder={t('Search applications…')} aria-label={t(globalAdministrator ? 'Search all applications' : 'Search area applications')}/></form>}<IconButton icon={CircleHelp} label={t('Open workspace guide')} onClick={()=>setGuide(true)}/>{manage&&location.pathname!=='/dashboard'&&<div className="notification-container"><button className="icon-btn" type="button" aria-label={t('View notifications')} aria-expanded={notifications} onClick={()=>setNotifications(v=>!v)}><Bell size={18}/></button>{notifications&&<div className="notification-popover"><h3>{t('Workspace updates')}</h3>{updates.loading?<p>{t('Loading…')}</p>:updates.error?<p>{t('Connect your backend to load updates.')}</p>:<p><strong>{updates.data?.openReviewSteps||0}</strong> {t('open approval steps need a decision.')}</p>}<Link to="/reviews">{t('Open review inbox')}</Link></div>}</div>}</div></div><main id="main-content" tabIndex={-1}><AreaAccessSummary/>{content}</main></div>
      </div></>:<main id="main-content" className={home||publicPage?'portal-home-main':'portal-container portal-auth-main'} tabIndex={-1}>{content}</main>}
    <div className="portal-container"><PartyCursor/></div><PortalFooter/><Toast/>
    {guide&&<Modal title={t('Workspace guide')} onClose={()=>setGuide(false)}><p>{t('Sign in to access the pages and actions available to your account.')}</p><div className="public-feature">{links.map(([path,label])=><Link className="text-link" key={path} to={path} onClick={()=>setGuide(false)}>{t(label)}</Link>)}</div></Modal>}
  </div>;
}

export default function App(){
 const {manage}=usePermissions();const location=useLocation();
 return <RegisteredUsersProvider enabled={manage&&['/','/dashboard'].includes(location.pathname)}><PortalApp/></RegisteredUsersProvider>;
}
