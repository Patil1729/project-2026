import { usePermissions } from "../../app/usePermissions";
import { t, useLocale } from "../../lib/i18n";
import { useState } from "react";
import { ArrowDownToLine, ChevronDown, ChevronRight, Edit3, GitBranch, List, Network, Plus, Power } from "lucide-react";
import { useApp, useResource } from "../../app/AppContext";
import { Badge, Button, Empty, Field, FormError, IconButton, Modal, PageHeading, Pagination, ResourceState, SearchInput, Select } from "../../components/ui";
import { parentType } from "../../lib/contracts";
import { date, exportCsv, humanize, items } from "../../lib/format";
function UnitForm({
  unit,
  units,
  onClose
}) {
  const {
    manage: canManage, globalAdministrator, canManageUnit, scopeRootIds
  } = usePermissions();
  useLocale();
  const {
    request,
    refresh,
    notify
  } = useApp();
  const availableTypes = Object.keys(parentType).filter(type => globalAdministrator || units.some(u => u.active && canManageUnit(u.id) && u.type === parentType[type]));
  const rootLocked = !!unit && !globalAdministrator && scopeRootIds.has(Number(unit.id));
  const [values, setValues] = useState({
      name: unit?.name || "",
      code: unit?.code || "",
      type: unit?.type || availableTypes[0] || "",
      parentId: unit?.parentId || ""
    }),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(null);
  const change = (name, value) => setValues(v => ({
    ...v,
    [name]: value,
    ...(name === "type" ? {
      parentId: ""
    } : {})
  }));
  const parents = units.filter(u => u.active && canManageUnit(u.id) && u.type === parentType[values.type] && u.id !== unit?.id);
  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await request("/organization-units" + (unit ? "/" + unit.id : ""), {
        method: unit ? "PUT" : "POST",
        body: {
          ...values,
          name: values.name.trim(),
          code: values.code.trim().toUpperCase(),
          parentId: values.type === "PARTY" ? null : Number(values.parentId)
        }
      });
      refresh();
      notify(unit ? "Organization unit updated." : "Organization unit created.");
      onClose();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return <Modal title={t(unit ? "Edit organization unit" : "Create organization unit")} subtitle={t("Give every branch a place in your legal network.")} onClose={onClose}>
      <form onSubmit={submit}>
        <FormError error={error} />
        <Field label={t("Unit name")} value={values.name} onChange={e => change("name", e.target.value)} maxLength={100} required placeholder={t("e.g. Thane East")} />
        <div className="form-grid">
          <Field label={t("Unit code")} value={values.code} onChange={e => change("code", e.target.value)} maxLength={50} required placeholder={t("e.g. MH-THA-E")} />
          <Select disabled={rootLocked} label={t("Organization level")} value={values.type} onChange={e => change("type", e.target.value)} required>
            {[...new Set([...(unit ? [unit.type] : []), ...availableTypes])].map(t => <option key={t} value={t}>
                {humanize(t)}
              </option>)}
          </Select>
        </div>
        {t(values.type !== "PARTY" && <Select disabled={rootLocked} label={t("Parent organization unit")} value={values.parentId} onChange={e => change("parentId", e.target.value)} required hint={t(`Select an active ${humanize(parentType[values.type]).toLowerCase()} unit.`)}>
            <option value="">{t("Choose parent unit")}</option>
            {rootLocked && !parents.some(u => u.id === unit.parentId) && <option value={unit.parentId}>{unit.parentName || unit.parentId}</option>}
            {parents.map(u => <option key={u.id} value={u.id}>
                {u.name} · {u.code}
              </option>)}
          </Select>)}
        <div className="info-note">
          <GitBranch size={18} />
          <p>{t("Party \u2192 State \u2192 Division \u2192 District \u2192 Divisional District \u2192 local units. The backend validates the hierarchy.")}</p>
        </div>
        <div className="form-actions">
          <Button variant="secondary" onClick={onClose}>{t("Cancel")}</Button>
          <Button type="submit" busy={busy}>
            {t(unit ? "Save changes" : "Create unit")}
          </Button>
        </div>
      </form>
    </Modal>;
}
function TreeNode({
  unit,
  units,
  onEdit,
  level = 0
}) {
  const {
    manage: canManage, globalAdministrator, canManageUnit, scopeRootIds
  } = usePermissions();
  useLocale();
  const [open, setOpen] = useState(level < 2);
  const children = units.filter(u => u.parentId === unit.id);
  return <div className="tree-node">
      <button className="tree-item" style={{
      paddingLeft: 18 + level * 26
    }} onClick={() => children.length ? setOpen(!open) : onEdit(unit)}>
        {t(children.length ? open ? <ChevronDown size={15} /> : <ChevronRight size={15} /> : <span className="tree-spacer" />)}
        <span className={`tree-unit-icon level-${unit.type.toLowerCase()}`}>
          <Network size={17} />
        </span>
        <span>
          <strong>{unit.name}</strong>
          <small>
            {humanize(unit.type)} · {unit.code}
          </small>
        </span>
        <Badge value={unit.active ? "ACTIVE" : "INACTIVE"} />
        <span className="tree-child-count">
          {t(children.length ? `${children.length} units` : "")}
        </span>
      </button>
      {t(open && children.map(u => <TreeNode key={u.id} unit={u} units={units} onEdit={onEdit} level={level + 1} />))}
    </div>;
}
export default function Organization() {
  const {
    manage: canManage, globalAdministrator, canManageUnit, scopeRootIds
  } = usePermissions();
  useLocale();
  const resource = useResource("/organization-units"),
    units = items(resource.data).filter(u => !canManage || canManageUnit(u.id)),
    {
      request,
      refresh,
      notify
    } = useApp();
  const [q, setQ] = useState(""),
    [type, setType] = useState(""),
    [view, setView] = useState("list"),
    [editing, setEditing] = useState(null),
    [page, setPage] = useState(0),
    [confirm, setConfirm] = useState(null),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(null);
  const canCreate = canManage && (globalAdministrator || units.some(u => u.active && Object.values(parentType).includes(u.type)));
  const filtered = units.filter(u => (!type || u.type === type) && [u.name, u.code, u.parentName].some(v => String(v || "").toLowerCase().includes(q.toLowerCase())));
  async function toggle() {
    setBusy(true);
    setError(null);
    try {
      await request(`/organization-units/${confirm.id}${confirm.active ? "" : "/activate"}`, {
        method: confirm.active ? "DELETE" : "PATCH"
      });
      notify(confirm.active ? "Organization unit deactivated." : "Organization unit activated.");
      setConfirm(null);
      refresh();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return <>
      <PageHeading eyebrow={t("YOUR ORGANIZATION")} title={t("A place for every branch.")} description={t("Build and manage the hierarchy that connects your legal cell.")}>
        <Button variant="secondary" icon={ArrowDownToLine} onClick={() => exportCsv("organization-units.csv", filtered.map(u => ({
        name: u.name,
        code: u.code,
        type: u.type,
        parent: u.parentName,
        active: u.active
      })))} disabled={!filtered.length}>{t("Export")}</Button>
        {canCreate && <Button icon={Plus} onClick={() => setEditing({})}>{t("Create unit")}</Button>}
      </PageHeading>
      <div className="mini-stat-row">
        <div>
          <Network size={18} />
          <strong>{units.length}</strong>
          <span>{t("Total units")}</span>
        </div>
        <div>
          <span className="dot green" />
          <strong>{units.filter(u => u.active).length}</strong>
          <span>{t("Active units")}</span>
        </div>
        <div>
          <GitBranch size={18} />
          <strong>{units.filter(u => u.type === "DIVISION").length}</strong>
          <span>{t("Divisions")}</span>
        </div>
      </div>
      <section className="panel">
        <div className="list-toolbar">
          <SearchInput value={q} onChange={v => {
          setQ(v);
          setPage(0);
          setView("list");
        }} placeholder={t("Search unit name, code or parent…")} />
          <div>
            <Select value={type} onChange={e => {
            setType(e.target.value);
            setPage(0);
            setView("list");
          }} aria-label={t("Filter organization level")}>
              <option value="">{t("All levels")}</option>
              {Object.keys(parentType).map(t => <option key={t} value={t}>
                  {humanize(t)}
                </option>)}
            </Select>
            <div className="segmented">
              <button className={view === "list" ? "selected" : ""} onClick={() => setView("list")} aria-label={t("List view")} aria-pressed={view === "list"}>
                <List size={17} />
              </button>
              <button className={view === "tree" ? "selected" : ""} onClick={() => setView("tree")} aria-label={t("Hierarchy view")} aria-pressed={view === "tree"}>
                <Network size={17} />
              </button>
            </div>
          </div>
        </div>
        <ResourceState resource={resource}>
          {t(view === "tree" ? <div className="tree-view">
              {units.filter(u => !u.parentId || !units.some(p => p.id === u.parentId)).map(u => <TreeNode key={u.id} unit={u} units={units} onEdit={canManage ? setEditing : () => {}} />)}
            </div> : filtered.length ? <>
              <div className="table-scroll">
                <table>
                  <thead>
                    <tr>
                      <th>{t("ORGANIZATION UNIT")}</th>
                      <th>{t("LEVEL")}</th>
                      <th>{t("PARENT UNIT")}</th>
                      <th>{t("STATUS")}</th>
                      <th>{t("ACTIONS")}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filtered.slice(page * 10, (page + 1) * 10).map(u => <tr key={u.id}>
                        <td>
                          <div className="unit-cell">
                            <span className="unit-icon">
                              <Network size={18} />
                            </span>
                            <div>
                              <strong>{u.name}</strong>
                              <small>{u.code}</small>
                            </div>
                          </div>
                        </td>
                        <td>
                          <span className="level-label">
                            {humanize(u.type)}
                          </span>
                        </td>
                        <td>{t(u.parentName || "Root organization")}</td>
                        <td>
                          <Badge value={u.active ? "ACTIVE" : "INACTIVE"} />
                        </td>
                        <td>
                          <div className="row-actions">
                            {canManage && <IconButton icon={Edit3} label={t(`Edit ${u.name}`)} onClick={() => setEditing(u)} />}
                            {canManage && (globalAdministrator || !scopeRootIds.has(Number(u.id))) && <IconButton icon={Power} label={t(`${u.active ? "Deactivate" : "Activate"} ${u.name}`)} onClick={() => {
                        setConfirm(u);
                        setError(null);
                      }} />}
                          </div>
                        </td>
                      </tr>)}
                  </tbody>
                </table>
              </div>
              <Pagination page={page} total={filtered.length} onChange={setPage} />
            </> : <Empty title={t("No units found")} action={canCreate && <Button icon={Plus} onClick={() => setEditing({})}>{t("Create unit")}</Button>} />)}
        </ResourceState>
      </section>
      {t(canManage && editing && (!editing.id || canManageUnit(editing.id)) && <UnitForm unit={editing.id ? editing : null} units={units} onClose={() => setEditing(null)} />)}{t(" ")}
      {t(canManage && confirm && canManageUnit(confirm.id) && <Modal title={t(`${confirm.active ? "Deactivate" : "Activate"} ${confirm.name}?`)} subtitle={t(confirm.active ? "This unit will become inactive. The backend checks for active children and dependencies." : "The parent and organization hierarchy must be valid.")} onClose={() => setConfirm(null)}>
          <FormError error={error} />
          <div className="form-actions">
            {canManage && <Button variant="secondary" onClick={() => setConfirm(null)}>{t("Cancel")}</Button>}
            <Button variant={confirm.active ? "danger" : "primary"} busy={busy} onClick={toggle}>
              {t(confirm.active ? "Deactivate unit" : "Activate unit")}
            </Button>
          </div>
        </Modal>)}
    </>;
}
