export const emptyAddress = {
  houseOrFlatNumber: '', buildingName: '', street: '', area: '', landmark: '',
  city: '', village: '', taluka: '', district: '', state: 'Maharashtra', country: 'India', pinCode: '',
};
export function profileValues(profile) {
  return {
    dateOfBirth: profile?.dateOfBirth || '', education: profile?.education || '',
    assemblyConstituencyId: profile?.assemblyConstituencyId || '',
    voterIdNumber: profile?.voterIdNumber || '', voterListPartNumber: profile?.voterListPartNumber || '',
    aadhaarLastFour: profile?.aadhaarLastFour || '',
    address: Object.fromEntries(Object.keys(emptyAddress).map(key => [key, profile?.address?.[key] ?? emptyAddress[key]])),
  };
}
export function profilePayload(values) {
  if (!values.address.city?.trim() && !values.address.village?.trim()) throw new Error('Provide a city or village in the address.');
  return {
    dateOfBirth: values.dateOfBirth, education: values.education.trim(),
    assemblyConstituencyId: Number(values.assemblyConstituencyId),
    voterIdNumber: values.voterIdNumber.trim(), voterListPartNumber: Number(values.voterListPartNumber),
    aadhaarLastFour: values.aadhaarLastFour || null,
    address: Object.fromEntries(Object.keys(emptyAddress).map(key => [key, String(values.address[key] || '').trim() || null])),
  };
}
