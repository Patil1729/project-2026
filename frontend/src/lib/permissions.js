// Mirrors the uploaded backend. UI visibility supplements server authorization.
export function permissionsFor(user) {
  const roles = new Set((Array.isArray(user?.roles) ? user.roles : []).map(role =>
    typeof role === 'string' ? role : role?.authority || role?.name));
  const authenticated = !!user;
  const access = user?.access;
  const matchesIdentity = access && Number(access.userId) === Number(user?.id);
  const globalAdministrator = access ? !!matchesIdentity && access.globalAdministrator === true : roles.has('PARTY_HEAD');
  const scopedAdministrator = !!matchesIdentity && access.areaAdministrator === true && access.canManageAreas === true && Array.isArray(access.scopes) && access.scopes.length > 0 && Array.isArray(access.allowedOrganizationUnitIds) && access.allowedOrganizationUnitIds.length > 0;
  const manage = globalAdministrator || scopedAdministrator;
  const review = authenticated && (manage || roles.has('ADVOCATE') || [...roles].some(role => role?.endsWith('_ADMIN')));
  const allowedUnitIds = new Set((matchesIdentity ? access.allowedOrganizationUnitIds || [] : []).map(Number));
  const scopeRootIds = new Set((matchesIdentity ? access.scopes || [] : []).map(s => Number(s.organizationUnitId)));
  return { authenticated, manage, review, globalAdministrator, scopedAdministrator,
    manageUserAccounts: globalAdministrator, manageAppointments: globalAdministrator, assignAreaRoles: globalAdministrator,
    readOrganization: authenticated, allowedUnitIds, scopeRootIds,
    canManageUnit: id => globalAdministrator || (scopedAdministrator && allowedUnitIds.has(Number(id))) };
}
export function canVisit(path, user) {
  const p = permissionsFor(user);
  if (['/', '/dashboard', '/login', '/signup', '/forgot-password', '/gallery', '/help'].includes(path)) return true;
  if (!p.authenticated) return false;
  if (path === '/help-requests' || /^\/help-requests\/[1-9][0-9]*$/.test(path) || path === '/gallery/manage') return true;
  if (['/members', '/appointments'].includes(path)) return p.manage;
  if (['/settings', '/role-assignments'].includes(path)) return p.globalAdministrator;
  if (path === '/reviews') return p.review;
  return ['/organization','/committees','/applications','/profile','/change-password'].includes(path);
}
