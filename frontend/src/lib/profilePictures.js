import { normalizeBase } from './api.js';
export const maxPictureBytes = 2097152;
export function pictureUrl(base, user) {
  const id = Number(user?.userId || user?.id);
  if (!user?.hasProfilePicture || !Number.isSafeInteger(id) || id < 1 || !/^[a-f0-9]{64}$/i.test(user?.profilePictureVersion || '')) return null;
  return `${normalizeBase(base)}/users/${id}/profile-picture/content?v=${encodeURIComponent(user.profilePictureVersion)}`;
}
export function validatePicture(file) {
  if (!file || !file.size) throw new Error('Select a JPG or PNG picture.');
  if (file.size > maxPictureBytes) throw new Error('Choose a picture of 2 MB or less.');
  if (!['image/png','image/jpeg'].includes(file.type)) throw new Error('Use a JPG or PNG picture.');
  return file;
}
