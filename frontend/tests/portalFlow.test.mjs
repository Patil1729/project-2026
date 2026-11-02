import { test } from 'node:test';
import assert from 'node:assert/strict';
import { organizationChoices, signupPayload } from '../src/lib/organizations.js';
import { hasRecommendationUpload } from '../src/lib/documents.js';
import { stateExecutiveSeats } from '../src/lib/stateExecutive.js';

const units = [{ id: 1, name: 'Party', type: 'PARTY', parentId: null },
  { id: 2, name: 'Maharashtra', type: 'STATE', parentId: 1 },
  { id: 3, name: 'West Maharashtra', type: 'DIVISION', parentId: 2 }];
test('signup exposes meaningful organisation paths and excludes broken, inactive and cyclic branches', () => {
  const choices = organizationChoices([...units,
    { id: 4, name: 'Inactive', type: 'DISTRICT', active: false, parentId: 3 },
    { id: 5, name: 'Orphan', type: 'TALUKA', parentId: 99 },
    { id: 6, name: 'Cycle', type: 'DISTRICT', parentId: 6 }]);
  assert.deepEqual(choices.map(u => u.id), [2, 3]);
  assert.equal(choices[1].path, 'Maharashtra / West Maharashtra');
});
test('signup requires a returned organisation and sends its numeric ID without allowing role injection', () => {
  const values = { fullName: ' Test Member ', username: ' test.member ', mobile: ' +919000000001 ',
    email: '', password: 'TestPassword123!', organizationUnitId: '3', roles: ['PARTY_HEAD'] };
  const body = signupPayload(values, organizationChoices(units));
  assert.equal(body.organizationUnitId, 3); assert.equal(body.fullName, 'Test Member');
  assert.equal(body.roles, undefined); assert.equal(body.email, undefined);
  assert.throws(() => signupPayload({ ...values, organizationUnitId: '99' }, organizationChoices(units)), /active organization/);
});
test('a rejected latest recommendation cannot be masked by an older verified upload', () => {
  const old = { documentType: 'RECOMMENDATION_LETTER', documentVersion: 1, status: 'VERIFIED' };
  assert.equal(hasRecommendationUpload([old]), true);
  assert.equal(hasRecommendationUpload([old, { ...old, documentVersion: 2, status: 'REJECTED' }]), false);
  assert.equal(hasRecommendationUpload([old, { ...old, documentVersion: 2, status: 'UPLOADED' }]), true);
});
test('state roster retains every numbered vice-president seat and vacancies', () => {
  const records = Array.from({ length: 7 }, (_, i) => ({ positionId: i + 1, committeeName: 'State', positionType: 'VICE_PRESIDENT', positionNumber: 7 - i, status: i ? 'VACANT' : 'FILLED' }));
  const ordered = stateExecutiveSeats(records);
  assert.equal(ordered.length, 7); assert.deepEqual(ordered.map(r => r.positionNumber), [1, 2, 3, 4, 5, 6, 7]);
  assert.equal(ordered.filter(r => r.status === 'VACANT').length, 6);
});
