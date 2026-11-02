import { useEffect, useState } from 'react';
import { Plus, Pencil, Trash2 } from 'lucide-react';
import { useApp, useResource } from '../../app/AppContext';
import { useCommunityAccess } from '../community/CommunityAccess';
import { Button, Empty, Field, FormError, Modal, PageHeading, Pagination, ResourceState, Select } from '../../components/ui';
import { galleryImageUrl, validateCommunityFile } from '../../lib/community';
import { organizationChoices } from '../../lib/organizations';
import { t, useLocale } from '../../lib/i18n';
function GalleryEditor({ entry, units, onClose }) {
  const { request, refresh, notify, config } = useApp();
  const [record, setRecord] = useState(entry), [busy, setBusy] = useState(false), [error, setError] = useState(null);
  const [values, setValues] = useState({ organizationUnitId: entry?.organizationUnitId || '', title: entry?.title || '', description: entry?.description || '', eventDate: entry?.eventDate || new Intl.DateTimeFormat('en-CA', {timeZone:'Asia/Kolkata'}).format(new Date()), published: entry?.published || false });
  const [file, setFile] = useState(null), [preview, setPreview] = useState('');
  useEffect(() => { if (!file) { setPreview(''); return; } const url = URL.createObjectURL(file); setPreview(url); return () => URL.revokeObjectURL(url); }, [file]);
  const change = (key, value) => setValues(v => ({ ...v, [key]: value }));
  async function submit(e) {
    e.preventDefault(); if (busy) return; setBusy(true); setError(null);
    let metadataSaved = false;
    try {
      if (file) validateCommunityFile(file);
      if (record) {
        const updated = await request(`/gallery/${record.id}`, { method:'PUT', body:{ title:values.title, description:values.description, eventDate:values.eventDate, published:values.published, version:record.version } });
        setRecord(updated); metadataSaved = true;
        if (file) { const body = new FormData(); body.append('file',file); body.append('version',updated.version); const replaced = await request(`/gallery/${record.id}/image`, {method:'POST',body}); setRecord(replaced); }
      } else {
        validateCommunityFile(file); const body = new FormData(); Object.entries(values).forEach(([key,value]) => body.append(key,String(value))); body.append('file',file);
        await request('/gallery', {method:'POST',body});
      }
      refresh(); notify(t('Gallery saved.')); onClose();
    } catch (error) { if (metadataSaved) { refresh(); setError(new Error(t('Details saved. Image upload failed; select the image and retry.') + ' ' + error.message)); } else setError(error); }
    finally { setBusy(false); }
  }
  return <Modal wide title={record ? 'Edit gallery photo' : 'Add gallery photo'} subtitle="Publish event photos for your organization area." onClose={busy ? () => {} : onClose}>
    <form onSubmit={submit}><fieldset disabled={busy} className="wizard-fields"><Select label="Area" required disabled={!!record} value={values.organizationUnitId} onChange={e => change('organizationUnitId',e.target.value)}><option value="">{t('Choose an active unit')}</option>{units.map(u => <option key={u.id} value={u.id}>{u.path}</option>)}</Select>
      <Field label="Title" required maxLength={150} value={values.title} onChange={e => change('title',e.target.value)}/>
      <label className="community-label">{t('Description')}<textarea className="input" required maxLength={2000} rows={4} value={values.description} onChange={e => change('description',e.target.value)}/></label>
      <Field label="Event date" type="date" required value={values.eventDate} onChange={e => change('eventDate',e.target.value)}/>
      <Field label={record ? 'Replace image (optional)' : 'Event photo'} type="file" required={!record} accept=".jpg,.jpeg,.png" hint="JPG or PNG, up to 5 MB. Publish photos you have permission to share." onChange={e => { try { const selected=validateCommunityFile(e.target.files[0]);setFile(selected);setError(null); } catch(error) { e.target.value='';setFile(null);setError(error); } }}/>
      {(preview || record) && <img className="gallery-editor-preview" src={preview || galleryImageUrl(config.base, record, true)} alt={t('Image preview')}/>}
      <label className="community-check"><input type="checkbox" checked={values.published} onChange={e => change('published',e.target.checked)}/><span>{t('Publish in the public gallery')}<small>{t('Leave unchecked to save a private draft.')}</small></span></label>
    </fieldset><FormError error={error}/><div className="form-actions"><Button variant="secondary" disabled={busy} onClick={onClose}>{t('Cancel')}</Button><Button type="submit" busy={busy}>{t('Save photo')}</Button></div></form>
  </Modal>;
}
export default function GalleryManagement() {
  useLocale(); const { request, refresh, notify } = useApp(), access = useCommunityAccess();
  const [editing, setEditing] = useState(null), [removing, setRemoving] = useState(null), [busy, setBusy] = useState(false), [error, setError] = useState(null), [page,setPage] = useState(0);
  const gallery = useResource('/gallery', {page,size:12}, !!access.data?.canManageGallery), all = useResource('/public/portal/organization-units');
  const units = organizationChoices(all.data || []).filter(u => access.data?.galleryUnitIds.includes(u.id));
  if (access.loading) return <ResourceState resource={access}/>;
  if (access.error) return <ResourceState resource={access}/>;
  if (!access.data?.canManageGallery) return <Empty title="Gallery access restricted" description="An active committee president or authorized area administrator manages this area's gallery."/>;
  return <><PageHeading eyebrow="EVENTS & UPDATES" title="Manage gallery" description="Save drafts, publish photos and keep your area gallery up to date."><Button icon={Plus} disabled={!units.length} onClick={() => setEditing({})}>{t('Add photo')}</Button></PageHeading>
    <ResourceState resource={gallery}>{gallery.data?.items.length ? <div className="gallery-admin-list">{gallery.data.items.map(entry => <article key={entry.id}><div><span className={`community-status ${entry.published?'published':'draft'}`}>{t(entry.published?'Published':'Draft')}</span><h2>{entry.title}</h2><p>{entry.areaName} · {entry.eventDate}</p></div><div className="button-row"><Button variant="secondary" icon={Pencil} onClick={() => setEditing(entry)}>{t('Edit')}</Button><Button variant="ghost" icon={Trash2} onClick={() => {setRemoving(entry);setError(null);}}>{t('Remove')}</Button></div></article>)}</div> : <Empty title="No gallery photos yet" description="Add your first event photo and publish it when ready."/>}</ResourceState>
    {gallery.data && <Pagination page={page} size={12} total={gallery.data.totalElements} hasNext={gallery.data.hasNext} onChange={setPage}/>}
    {editing && <GalleryEditor entry={editing.id ? editing : null} units={units} onClose={() => setEditing(null)}/>}
    {removing && <Modal title="Remove gallery photo" onClose={busy?()=>{}:()=>setRemoving(null)}><p>{t('Remove this photo from the gallery?')}</p><strong>{removing.title}</strong><FormError error={error}/><div className="form-actions"><Button variant="secondary" disabled={busy} onClick={() => setRemoving(null)}>{t('Cancel')}</Button><Button busy={busy} onClick={async () => {setBusy(true);try{await request(`/gallery/${removing.id}`,{method:'DELETE',params:{version:removing.version}});refresh();setRemoving(null);notify(t('Photo removed.'));}catch(error){setError(error);}finally{setBusy(false);}}}>{t('Remove photo')}</Button></div></Modal>}
  </>;
}
