import { useEffect, useRef, useState } from 'react';
import { Camera, Upload, Trash2 } from 'lucide-react';
import { useApp, useResource } from '../../app/AppContext';
import { Avatar, Button, FormError, ResourceState } from '../../components/ui';
import { validatePicture, pictureUrl } from '../../lib/profilePictures';
import { t, useLocale } from '../../lib/i18n';

export default function ProfilePictureEditor({userId, name, onBusyChange}) {
  useLocale(); const { request, refresh, notify, config } = useApp();
  const resource = useResource(`/users/${userId}/profile-picture`);
  const [file,setFile] = useState(null), [preview,setPreview] = useState(null), [busy,setBusy] = useState(false), [error,setError] = useState(null);
  const input = useRef();
  useEffect(() => {
    if (!file) { setPreview(null); return; }
    const url = URL.createObjectURL(file); setPreview(url); return () => URL.revokeObjectURL(url);
  }, [file]);
  useEffect(() => { onBusyChange?.(busy); return () => onBusyChange?.(false); }, [busy, onBusyChange]);
  function select(value) { setError(null); try { setFile(validatePicture(value)); } catch(e) { setFile(null); if(input.current) input.current.value=''; setError(e); } }
  async function upload(event) {
    event.preventDefault(); if(!file) return; setBusy(true); setError(null);
    try { const body = new FormData(); body.set('file',file); await request(`/users/${userId}/profile-picture`,{method:'POST',body}); setFile(null); if(input.current)input.current.value=''; refresh(); notify('Profile picture updated.'); }
    catch(error) { setError(error); } finally { setBusy(false); }
  }
  async function remove() {
    setBusy(true); setError(null);
    try { await request(`/users/${userId}/profile-picture`,{method:'DELETE'}); setFile(null); if(input.current)input.current.value=''; refresh(); notify('Profile picture removed.'); }
    catch(error) {setError(error);} finally {setBusy(false);}
  }
  return <section className="profile-picture-editor" aria-label={t('Profile picture')}>
    <div className="profile-picture-preview"><Avatar name={name} src={preview || pictureUrl(config.base, resource.data)} size="large"/></div>
    <div className="profile-picture-controls"><h3><Camera size={18}/>{t('Profile picture')}</h3><p>{t('Optional. Use a JPG or PNG of 2 MB or less. Your picture is shown in authorised account and member views.')}</p>
      <ResourceState resource={resource}><form onSubmit={upload}><label className="field"><span>{t('Choose a profile picture')}</span><input ref={input} type="file" accept="image/jpeg,image/png" disabled={busy} onChange={event => event.target.files?.[0] && select(event.target.files[0])}/></label>
        <div className="button-row"><Button type="submit" icon={Upload} busy={busy} disabled={!file}>{t(resource.data?.hasProfilePicture?'Replace picture':'Upload picture')}</Button>{resource.data?.hasProfilePicture && <Button variant="ghost" icon={Trash2} disabled={busy} onClick={remove}>{t('Remove picture')}</Button>}</div>
      </form></ResourceState><FormError error={error}/>
    </div>
  </section>;
}
