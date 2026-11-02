import {test} from 'node:test';
import assert from 'node:assert/strict';
import {permissionsFor,canVisit} from '../src/lib/permissions.js';
const user=(...roles)=>({id:1,username:'member',roles});
test('anonymous visitors can only visit the dashboard and authentication pages',()=>{
  for(const path of ['/','/dashboard','/login','/signup'])assert.equal(canVisit(path,null),true);
  for(const path of ['/members','/applications','/reviews','/organization','/committees','/appointments','/settings','/profile'])assert.equal(canVisit(path,null),false);
});
test('applicant has own workspace and read-only organization access, without admin or review access',()=>{
  const applicant=user('APPLICANT');
  for(const path of ['/','/applications','/organization','/committees','/profile'])assert.equal(canVisit(path,applicant),true);
  for(const path of ['/members','/reviews','/appointments','/settings'])assert.equal(canVisit(path,applicant),false);
  assert.equal(permissionsFor(applicant).manage,false);
});
test('PARTY_HEAD alone grants administrative navigation and mutation controls',()=>{
  const head=user('PARTY_HEAD');
  for(const path of ['/members','/applications','/reviews','/organization','/committees','/appointments','/settings','/profile'])assert.equal(canVisit(path,head),true);
  assert.equal(permissionsFor(head).manage,true);
  for(const role of ['ADMIN','STATE_ADMIN','DISTRICT_ADMIN','ADVOCATE','FIELD_WORKER','UNKNOWN'])assert.equal(permissionsFor(user(role)).manage,false);
});
test('review-capable roles can see assigned inbox, without obtaining global administrative access',()=>{
  for(const role of ['ADVOCATE','STATE_ADMIN','DIVISION_ADMIN','DISTRICT_ADMIN','DIVISIONAL_DISTRICT_ADMIN','CONSTITUENCY_ADMIN','TALUKA_ADMIN','MUNICIPAL_CORPORATION_ADMIN','MUNICIPAL_COUNCIL_ADMIN','NAGAR_PANCHAYAT_ADMIN','WARD_ADMIN']){
    assert.equal(canVisit('/reviews',user(role)),true,role);
    assert.equal(canVisit('/members',user(role)),false,role);
  }
});
test('no browser-selected role or legacy ADMIN string elevates privileges',()=>{
  assert.equal(permissionsFor({id:1,role:'PARTY_HEAD',roles:['APPLICANT']}).manage,false);
  assert.equal(permissionsFor(user('ROLE_PARTY_HEAD')).manage,false);
  assert.equal(canVisit('/not-a-real-page',user('PARTY_HEAD')),false);
  assert.equal(permissionsFor({id:1,roles:[{authority:'PARTY_HEAD'}]}).manage,true);
});
const scoped=(role='DISTRICT_ADMIN')=>({...user(role),access:{userId:1,globalAdministrator:false,areaAdministrator:true,canManageAreas:true,allowedOrganizationUnitIds:[4,5,6],scopes:[{assignmentId:20,role,organizationUnitId:4}]}});
test('active server scope enables area workspace without global account or appointment actions',()=>{
  const district=scoped(),p=permissionsFor(district);
  for(const path of ['/members','/appointments','/organization','/committees','/applications','/reviews'])assert.equal(canVisit(path,district),true,path);
  for(const path of ['/settings','/role-assignments'])assert.equal(canVisit(path,district),false,path);
  assert.equal(p.canManageUnit(4),true);assert.equal(p.canManageUnit('6'),true);assert.equal(p.canManageUnit(8),false);
  assert.equal(p.manageUserAccounts,false);assert.equal(p.manageAppointments,false);assert.equal(p.assignAreaRoles,false);
});
test('revocation, mismatched identity and missing scope fail closed',()=>{
  for(const change of [{areaAdministrator:false},{userId:999},{canManageAreas:false},{scopes:[]},{allowedOrganizationUnitIds:[]}]){
    const district=scoped();Object.assign(district.access,change);assert.equal(permissionsFor(district).manage,false,JSON.stringify(change));
  }
});
test('explicit server access overrides stale local role flags after revocation',()=>{
  const head={...user('PARTY_HEAD'),access:{userId:1,globalAdministrator:false,areaAdministrator:false,allowedOrganizationUnitIds:[],scopes:[]}};
  assert.equal(permissionsFor(head).manage,false);assert.equal(canVisit('/role-assignments',head),false);
});
test('all nine scoped area roles receive the same scoped capabilities',()=>{
  for(const role of ['STATE_ADMIN','DIVISION_ADMIN','DISTRICT_ADMIN','DIVISIONAL_DISTRICT_ADMIN','CONSTITUENCY_ADMIN','TALUKA_ADMIN','MUNICIPAL_CORPORATION_ADMIN','MUNICIPAL_COUNCIL_ADMIN','NAGAR_PANCHAYAT_ADMIN']){
    assert.equal(permissionsFor(scoped(role)).manage,true,role);assert.equal(permissionsFor(scoped(role)).manageAppointments,false,role);
  }
});
