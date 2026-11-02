import test from 'node:test';
import assert from 'node:assert/strict';
import { validateCommunityFile, helpPayload, newSubmissionKey, galleryImageUrl } from '../src/lib/community.js';
import { canVisit } from '../src/lib/permissions.js';

test('gallery and help pages are public while case details require authentication', () => {
  for (const path of ['/gallery','/help']) assert.equal(canVisit(path,null),true);
  for (const path of ['/help-requests','/help-requests/123','/gallery/manage']) {
    assert.equal(canVisit(path,null),false);assert.equal(canVisit(path,{id:1,roles:['APPLICANT']}),true);
  }
  assert.equal(canVisit('/help-requests/not-a-case',{id:1}),false);
});
test('help payload uses returned area, keeps submission key, and does not forward caller identity or permissions', () => {
  const key=newSubmissionKey();assert.match(key,/^[0-9a-f-]{36}$/);
  const values={organizationUnitId:'14',contactName:' Demo Citizen ',contactMobile:' +919812345678 ',locality:' Demo village ',subject:' Help needed ',description:' Description of the issue ',category:'OTHER',urgent:true,consent:true,requesterId:99,roles:['PARTY_HEAD']};
  const result=helpPayload(values,key,[{id:14}]);
  assert.equal(result.organizationUnitId,14);assert.equal(result.submissionKey,key);assert.equal(result.contactName,'Demo Citizen');assert.equal(result.requesterId,undefined);assert.equal(result.roles,undefined);
  assert.throws(()=>helpPayload(values,key,[{id:15}]),/area/);
  assert.throws(()=>helpPayload({...values,consent:false},key,[{id:14}]),/Consent/);
});
test('evidence accepts bounded PDFs while public gallery accepts only bounded image choices', () => {
  for (const name of ['event.jpg','event.JPEG','event.png']) assert.equal(validateCommunityFile({name,size:100}).name,name);
  assert.throws(()=>validateCommunityFile({name:'evidence.pdf',size:100}),/JPG or PNG/);
  assert.equal(validateCommunityFile({name:'evidence.pdf',size:5242880},true).size,5242880);
  assert.throws(()=>validateCommunityFile({name:'evidence.pdf',size:5242881},true),/5 MB/);
  assert.throws(()=>validateCommunityFile({name:'script.svg',size:20},true),/JPG, PNG or PDF/);
  assert.throws(()=>validateCommunityFile({name:'empty.png',size:0}),/nonempty/);
});
test('gallery image links distinguish public published content from authenticated draft previews', () => {
  assert.equal(galleryImageUrl('/api/',{id:7,version:3}),'/api/public/gallery/7/image?v=3');
  assert.equal(galleryImageUrl('/api',{id:7,version:3},true),'/api/gallery/7/image?v=3');
});
