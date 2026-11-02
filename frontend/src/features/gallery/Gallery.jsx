import { useState } from 'react';
import { Link } from 'react-router';
import { CalendarDays, Image as ImageIcon, ArrowUpRight } from 'lucide-react';
import { useApp, useResource } from '../../app/AppContext';
import { useCommunityAccess } from '../community/CommunityAccess';
import { Empty, Modal, Pagination, ResourceState, Select } from '../../components/ui';
import { galleryImageUrl } from '../../lib/community';
import { organizationChoices } from '../../lib/organizations';
import { t, useLocale, getLocale } from '../../lib/i18n';
export default function Gallery({ preview = false }) {
  useLocale();
  const { config } = useApp(), access = useCommunityAccess();
  const [area, setArea] = useState(''), [page, setPage] = useState(0), [selected, setSelected] = useState(null);
  const resource = useResource('/public/gallery', { page, size: preview ? 3 : 12, areaId: area });
  const units = useResource('/public/portal/organization-units', {}, !preview);
  const Title = preview ? 'h2' : 'h1';
  return <section className={`community-page ${preview ? 'gallery-preview' : ''}`} id="gallery">
    <div className="portal-container">
      <div className="community-title"><span className="community-pill">{t('MEDIA GALLERY')}</span><Title>{t('Our gallery')}</Title><i/><p>{t('Events, updates and highlights from the Legal Cell.')}</p>
        <div className="button-row">{preview && <Link className="btn btn-secondary" to="/gallery">{t('View gallery')}<ArrowUpRight size={16}/></Link>}{access.data?.canManageGallery && <Link className="btn btn-secondary" to="/gallery/manage">{t('Manage gallery')}</Link>}</div>
      </div>
      {!preview && <div className="community-filter"><Select label="Area" value={area} onChange={e => { setArea(e.target.value); setPage(0); }}><option value="">{t('All areas')}</option>{organizationChoices(units.data || []).map(u => <option key={u.id} value={u.id}>{u.path}</option>)}</Select></div>}
      <ResourceState resource={resource}>{resource.data?.items?.length ? <div className="gallery-grid">{resource.data.items.map(entry => <article className="gallery-card" key={entry.id}>
        <button className="gallery-photo-button" type="button" onClick={() => setSelected(entry)} aria-label={`${t('Open image')}: ${entry.title}`}><img src={galleryImageUrl(config.base, entry)} alt={entry.title} loading="lazy"/></button>
        <div className="gallery-card-body"><span className="gallery-area">{entry.areaName}</span><h2><button type="button" onClick={() => setSelected(entry)}>{entry.title}</button></h2><p>{entry.description}</p><div className="gallery-meta"><span><CalendarDays size={14}/>{new Date(entry.eventDate + 'T12:00:00').toLocaleDateString(getLocale(), {day:'numeric', month:'short', year:'numeric'})}</span><span className="gallery-image-tag"><ImageIcon size={13}/>{t('Image')}</span></div></div>
      </article>)}</div> : <Empty title="No gallery photos yet" description="Published event photos will appear here."/>}</ResourceState>
      {!preview && resource.data && <Pagination page={page} size={12} total={resource.data.totalElements} hasNext={resource.data.hasNext} onChange={setPage}/>}
      {selected && <Modal wide title={selected.title} subtitle={selected.areaName} onClose={() => setSelected(null)}><figure className="gallery-lightbox"><img src={galleryImageUrl(config.base, selected)} alt={selected.title}/><figcaption>{selected.description}</figcaption></figure></Modal>}
    </div>
  </section>;
}
