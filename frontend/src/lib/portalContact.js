// Contact copy supplied in legal-cell-portal-final(2).html.
export const portalContact = {
  address: {
    en: 'C-2, Brahmagiri (Balasaheb Bhavan), Madam Cama Road, Opposite Mantralaya, Nariman Point, Mumbai 21.',
    mr: 'सी-२, ब्रह्मगिरी (बाळासाहेब भवन), मादाम कामा रोड, मंत्रालयासमोर, नरिमन पॉईंट, मुंबई २१.',
  },
  phone: import.meta.env?.VITE_CONTACT_PHONE?.trim() || '',
  email: import.meta.env?.VITE_CONTACT_EMAIL || '',
};
export const contactPhoneHref = portalContact.phone ? 'tel:' + portalContact.phone.replace(/[^+0-9]/g, '') : '';
