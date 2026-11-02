export function passwordError({ currentPassword, newPassword, confirmPassword }) {
  if (!currentPassword || !newPassword || !confirmPassword) return 'Complete all password fields.';
  if (newPassword.length < 8) return 'Use at least 8 characters for your new password.';
  if (new TextEncoder().encode(newPassword).length > 72) return 'Password must contain at most 72 UTF-8 bytes.';
  if (newPassword !== confirmPassword) return 'Passwords do not match.';
  if (currentPassword === newPassword) return 'Choose a new password different from your current password.';
  return null;
}
