import { test } from 'node:test';
import assert from 'node:assert/strict';
import { profilePayload, profileValues } from '../src/lib/profile.js';
import { passwordError } from '../src/lib/password.js';
import { canVisit } from '../src/lib/permissions.js';

test('profile edit preserves optional address values and sends only request fields', () => {
  const values = profileValues({ id: 99, dateOfBirth: '1996-01-01', education: ' LL.B. ', assemblyConstituencyId: 9,
    voterIdNumber: ' ABC123 ', voterListPartNumber: 2, address: { id: 55, city: ' Pune ', buildingName: 'Old Building', area: 'Test Area', landmark: 'Test Landmark' } });
  const body = profilePayload(values);
  assert.equal(body.address.buildingName, 'Old Building'); assert.equal(body.address.area, 'Test Area');
  assert.equal(body.address.landmark, 'Test Landmark'); assert.equal(body.address.city, 'Pune');
  assert.equal(body.address.id, undefined); assert.equal(body.id, undefined); assert.equal(body.aadhaarLastFour, null);
  assert.equal(body.assemblyConstituencyId, 9); assert.equal(body.education, 'LL.B.');
});
test('profile requires a city or village and converts electoral numbers', () => {
  const values = profileValues(); assert.throws(() => profilePayload(values), /city or village/);
  values.address.village = 'Test Village'; values.assemblyConstituencyId = '8'; values.voterListPartNumber = '3';
  const body = profilePayload(values); assert.equal(body.assemblyConstituencyId, 8); assert.equal(body.voterListPartNumber, 3);
});
test('password change rejects mismatch, reuse, short passwords and oversized Unicode before submission', () => {
  const valid = { currentPassword: 'OldPassword123!', newPassword: 'NewPassword123!', confirmPassword: 'NewPassword123!' };
  assert.equal(passwordError(valid), null);
  assert.match(passwordError({ ...valid, confirmPassword: 'DifferentPassword' }), /match/);
  assert.match(passwordError({ ...valid, currentPassword: valid.newPassword }), /different/);
  assert.match(passwordError({ ...valid, newPassword: 'short', confirmPassword: 'short' }), /at least 8/);
  assert.match(passwordError({ ...valid, newPassword: 'अ'.repeat(25), confirmPassword: 'अ'.repeat(25) }), /72 UTF-8 bytes/);
});
test('password change is accessible to each signed-in role and protected from guests', () => {
  assert.equal(canVisit('/change-password', null), false);
  for (const role of ['APPLICANT','ADVOCATE','FIELD_WORKER','DISTRICT_ADMIN','PARTY_HEAD']) {
    assert.equal(canVisit('/change-password', { id: 7, roles: [role] }), true);
  }
});
