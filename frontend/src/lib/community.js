export const helpCategories = [
  ['POLICE_FIR', 'Police / FIR', '🚔'], ['ARREST_DETENTION', 'Arrest / detention', '⚖️'],
  ['POLITICAL_HARASSMENT', 'Political harassment', '✊'], ['WOMEN_SAFETY', "Women's safety", '🛡️'],
  ['PROPERTY_DISPUTE', 'Property dispute', '🏠'], ['OTHER', 'Other legal advice', '💬'],
];
export const helpStatuses = ['UNASSIGNED', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'];
export const categoryLabel = code => helpCategories.find(([value]) => value === code)?.[1] || code;
export function validateCommunityFile(file, evidence = false) {
  if (!file || file.size < 1) throw new Error('Select a nonempty file.');
  if (file.size > 5 * 1024 * 1024) throw new Error('Each file must be 5 MB or less.');
  if (!(evidence ? /\.(jpe?g|png|pdf)$/i : /\.(jpe?g|png)$/i).test(file.name))
    throw new Error(evidence ? 'Use JPG, PNG or PDF files.' : 'Use a JPG or PNG image.');
  return file;
}
export function newSubmissionKey() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID();
  const bytes = globalThis.crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 15) | 64; bytes[8] = (bytes[8] & 63) | 128;
  const hex = [...bytes].map(n => n.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0,8)}-${hex.slice(8,12)}-${hex.slice(12,16)}-${hex.slice(16,20)}-${hex.slice(20)}`;
}
export function helpPayload(values, submissionKey, units) {
  if (!units.some(u => Number(u.id) === Number(values.organizationUnitId))) throw new Error('Select your area from the organization list.');
  if (!values.consent) throw new Error('Consent is required to submit a help request.');
  return { submissionKey, organizationUnitId: Number(values.organizationUnitId),
    category: values.category, urgent: values.urgent === true, consent: values.consent === true,
    contactName: values.contactName.trim(), contactMobile: values.contactMobile.trim(), locality: values.locality.trim(),
    subject: values.subject.trim(), description: values.description.trim() };
}
export function galleryImageUrl(base, entry, managed = false) {
  return `${base.replace(/\/+$/, '')}/${managed ? '' : 'public/'}gallery/${entry.id}/image?v=${entry.version}`;
}
