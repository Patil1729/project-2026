import {test} from 'node:test';
import assert from 'node:assert/strict';
import {pictureUrl,validatePicture,maxPictureBytes} from '../src/lib/profilePictures.js';
import {leadership} from '../src/lib/leadership.js';
test('private picture URL uses only the authenticated API path and valid identifiers',()=>{
 const user={id:10,hasProfilePicture:true,profilePictureVersion:'a'.repeat(64)};
 assert.equal(pictureUrl('/api',user),'/api/users/10/profile-picture/content?v='+'a'.repeat(64));
 assert.equal(pictureUrl('/api',{...user,id:0}),null);assert.equal(pictureUrl('/api',{...user,profilePictureVersion:'../../secret'}),null);assert.equal(pictureUrl('/api',{...user,hasProfilePicture:false}),null);
});
test('picture selection accepts small JPEG/PNG and rejects oversized or unsupported files',()=>{
 assert.ok(validatePicture({size:100,type:'image/png'}));assert.ok(validatePicture({size:100,type:'image/jpeg'}));
 for(const file of [{size:0,type:'image/png'},{size:maxPictureBytes+1,type:'image/png'},{size:100,type:'image/svg+xml'},{size:100,type:'text/html'}])assert.throws(()=>validatePicture(file));
});
test('six leadership slots retain existing people and keep unspecified extra identities explicit',()=>{
 assert.equal(leadership.length,6);assert.equal(new Set(leadership.map(p=>p.id)).size,6);assert.equal(leadership.filter(p=>p.image).length,3);assert.equal(leadership.filter(p=>p.pending).length,3);
 assert.ok(leadership.every(p=>p.name.en&&p.name.mr));
});
