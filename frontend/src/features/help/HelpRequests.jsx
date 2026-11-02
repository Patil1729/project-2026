import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { ArrowUpRight, Plus } from 'lucide-react';
import { useResource } from '../../app/AppContext';
import { useCommunityAccess } from '../community/CommunityAccess';
import { Badge, Empty, FormError, PageHeading, Pagination, ResourceState, SearchInput, Select } from '../../components/ui';
import { categoryLabel, helpStatuses } from '../../lib/community';
import { t, useLocale, getLocale } from '../../lib/i18n';
import { humanize } from '../../lib/format';
export default function HelpRequests() {
  useLocale();const [params,setParams]=useSearchParams(),access=useCommunityAccess();
  const inbox=params.get('view')==='inbox' && !!access.data?.canUseInbox;
  const [query,setQuery]=useState(''),[search,setSearch]=useState(''),[status,setStatus]=useState(''),[urgent,setUrgent]=useState(''),[page,setPage]=useState(0);
  useEffect(()=>{const id=setTimeout(()=>{setSearch(query);setPage(0);},300);return()=>clearTimeout(id);},[query]);
  const resource=useResource('/help-requests',{view:inbox?'inbox':'mine',status,urgent,q:search,page,size:12},!access.loading);
  return <><PageHeading eyebrow="LEGAL SUPPORT" title={inbox?'Advocate inbox':'My help requests'} description={inbox?'Assigned cases and the requests you coordinate in your area.':'Follow your case, share evidence and read updates from your advocate.'}><Link className="btn btn-primary" to="/help"><Plus size={16}/>{t('New help request')}</Link></PageHeading>
    <div className="help-tabs" role="group" aria-label={t('Help request views')}><button type="button" className={!inbox?'active':''} onClick={()=>{setParams({});setPage(0);}}>{t('My help requests')}</button>{access.data?.canUseInbox && <button type="button" className={inbox?'active':''} onClick={()=>{setParams({view:'inbox'});setPage(0);}}>{t('Advocate inbox')}</button>}</div>
    {access.error&&<FormError error={access.error}/>}<div className="help-filters"><SearchInput value={query} onChange={setQuery} placeholder="Search subject or ticket reference" maxLength={100}/><Select label="Status" value={status} onChange={e=>{setStatus(e.target.value);setPage(0);}}><option value="">{t('All statuses')}</option>{helpStatuses.map(s=><option key={s} value={s}>{t(humanize(s))}</option>)}</Select><Select label="Priority" value={urgent} onChange={e=>{setUrgent(e.target.value);setPage(0);}}><option value="">{t('All priorities')}</option><option value="true">{t('Urgent')}</option><option value="false">{t('Standard')}</option></Select></div>
    <ResourceState resource={resource}>{resource.data?.items?.length ? <div className="help-request-list">{resource.data.items.map(h=><Link className="help-request-card" key={h.id} to={`/help-requests/${h.id}`}><div className="help-request-top"><span className="help-ticket-code">{h.reference}</span><div className="button-row">{h.urgent&&<span className="community-status urgent">{t('Urgent')}</span>}<Badge value={h.status}/></div></div><h2>{h.subject}<ArrowUpRight size={19}/></h2><p>{t(categoryLabel(h.category))} · {h.areaName}</p><div className="help-request-bottom"><span>{h.needsReassignment ? <b className="help-assignment-warning">{t('Coordinator assignment needed')}</b> : h.advocateName || t('Awaiting assignment')}<small>{h.routingAreaName}</small></span><time dateTime={h.lastActivityAt}>{new Date(h.lastActivityAt).toLocaleString(getLocale())}</time></div></Link>)}</div> : <Empty title="No help requests found" description="Create a request or change the filters to see cases."/>}</ResourceState>
    {resource.data&&<Pagination page={page} size={12} total={resource.data.totalElements} hasNext={resource.data.hasNext} onChange={setPage}/>}
  </>;
}
