import { Field, ResourceState, Select } from '../../components/ui';
import { items } from '../../lib/format';
import { t, useLocale } from '../../lib/i18n';

// Shared by the application wizard and the member's own profile editor.
export default function ProfileFields({ value, onChange, constituencies }) {
  useLocale();
  const change = key => e => { const next = e.target.value; onChange(old => ({ ...old, [key]: next })); };
  const address = key => e => { const next = e.target.value; onChange(old => ({ ...old, address: { ...old.address, [key]: next } })); };
  const choices = items(constituencies.data);
  const today = new Date(); today.setDate(today.getDate() - 1);
  const maxDate = [today.getFullYear(), String(today.getMonth() + 1).padStart(2, '0'), String(today.getDate()).padStart(2, '0')].join('-');
  return <>
    <h3 className="form-section">{t('Personal information')}</h3>
    <div className="form-grid">
      <Field label={t('Date of birth')} type="date" required max={maxDate} value={value.dateOfBirth} onChange={change('dateOfBirth')}/>
      <Field label={t('Education')} required maxLength={255} value={value.education} onChange={change('education')} placeholder={t('e.g. LL.B., University of Mumbai')}/>
    </div>
    <h3 className="form-section">{t('Address')}</h3>
    <div className="form-grid">
      {[
        ['houseOrFlatNumber', 'House / flat number', 50], ['buildingName', 'Building name', 150],
        ['street', 'Street', 200], ['area', 'Area', 150], ['landmark', 'Landmark', 200],
        ['city', 'City / town', 150], ['village', 'Village', 150], ['taluka', 'Taluka', 150],
        ['district', 'District', 150], ['state', 'State', 100], ['country', 'Country', 100],
      ].map(([key, label, maxLength]) => <Field key={key} label={t(label)} required={['taluka','district','state','country'].includes(key)} maxLength={maxLength} value={value.address[key] || ''} onChange={address(key)}/>)}
      <Field label={t('PIN code')} required pattern="[1-9][0-9]{5}" inputMode="numeric" maxLength={6} value={value.address.pinCode} onChange={address('pinCode')}/>
    </div>
    <p className="muted">{t('Provide a city or village in the address.')}</p>
    <h3 className="form-section">{t('Electoral information')}</h3>
    <ResourceState resource={constituencies}>
      <Select label={t('Assembly constituency')} required value={value.assemblyConstituencyId} onChange={change('assemblyConstituencyId')}>
        <option value="">{t('Select a constituency')}</option>
        {choices.map(c => <option key={c.id} value={c.id}>{c.constituencyNumber} · {c.name}</option>)}
      </Select>
      {!choices.length && <p className="muted" role="status">{t('No active constituencies are available. Ask your administrator to add one.')}</p>}
    </ResourceState>
    <div className="form-grid">
      <Field label={t('Voter ID number')} required maxLength={30} value={value.voterIdNumber} onChange={change('voterIdNumber')}/>
      <Field label={t('Voter list part number')} type="number" min={1} step={1} required value={value.voterListPartNumber} onChange={change('voterListPartNumber')}/>
      <Field label={t('Aadhaar last four digits')} pattern="[0-9]{4}" maxLength={4} inputMode="numeric" value={value.aadhaarLastFour} onChange={change('aadhaarLastFour')} hint={t('Only the last four digits are collected.')}/>
    </div>
  </>;
}
