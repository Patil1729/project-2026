export function organizationChoices(records = []) {
  const units = new Map(records.filter(u => u.active !== false).map(u => [Number(u.id), u]));
  const label = unit => {
    const path = [], seen = new Set();
    let cursor = unit;
    while (cursor) {
      if (seen.has(Number(cursor.id))) return null;
      seen.add(Number(cursor.id));
      if (cursor.type !== 'PARTY') path.unshift(cursor.name);
      if (cursor.parentId != null && !units.has(Number(cursor.parentId))) return null;
      cursor = cursor.parentId == null ? null : units.get(Number(cursor.parentId));
    }
    return path.join(' / ');
  };
  return [...units.values()].filter(u => u.type !== 'PARTY').map(u => ({ ...u, path: label(u) }))
    .filter(u => u.path).sort((a, b) => a.path.localeCompare(b.path));
}

export function signupPayload(values, choices) {
  const organizationUnitId = Number(values.organizationUnitId);
  if (!Number.isSafeInteger(organizationUnitId) || organizationUnitId <= 0 || !choices.some(u => Number(u.id) === organizationUnitId)) {
    throw new Error('Select an active organization unit.');
  }
  return {
    fullName: values.fullName.trim(), username: values.username.trim(), mobile: values.mobile.trim(),
    password: values.password, organizationUnitId,
    ...(values.email.trim() ? { email: values.email.trim() } : {}),
  };
}
