import { usePermissions } from "../../app/usePermissions";
import { t, useLocale } from "../../lib/i18n";
import { useState } from "react";
import { ArrowRight, Building2, Check, Edit3, Plus, Power, Users } from "lucide-react";
import { useApp, useResource } from "../../app/AppContext";
import { Badge, Button, Empty, Field, FormError, IconButton, Modal, PageHeading, Pagination, ResourceState, SearchInput, Select } from "../../components/ui";
import { committeeTypes } from "../../lib/contracts";
import { humanize, items } from "../../lib/format";
function CommitteeForm({
  committee,
  units,
  committees,
  onClose
}) {
  const {
    manage: canManage, canManageUnit
  } = usePermissions();
  useLocale();
  const {
    request,
    refresh,
    notify
  } = useApp();
  const [v, setV] = useState({
      name: committee?.name || "",
      code: committee?.code || "",
      organizationUnitId: committee?.organizationUnitId || ""
    }),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(null);
  const selected = units.find(u => String(u.id) === String(v.organizationUnitId));
  function selectUnit(id) {
    const u = units.find(u => String(u.id) === id);
    setV(x => ({
      ...x,
      organizationUnitId: id,
      ...(!committee && u ? {
        name: `${u.name} Legal Cell Committee`,
        code: `${u.code}-LEGAL`
      } : {})
    }));
  }
  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await request("/committees" + (committee ? "/" + committee.id : ""), {
        method: committee ? "PUT" : "POST",
        body: {
          ...v,
          name: v.name.trim(),
          code: v.code.trim().toUpperCase(),
          committeeType: committeeTypes[selected.type],
          organizationUnitId: Number(v.organizationUnitId)
        }
      });
      notify(committee ? "Committee updated." : "Committee created with its position template.");
      refresh();
      onClose();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return <Modal title={t(committee ? "Edit committee" : "Create committee")} subtitle={t("A dedicated team for your organization unit.")} onClose={onClose}>
      <form onSubmit={submit}>
        <FormError error={error} />
        <Select label={t("Organization unit")} value={v.organizationUnitId} onChange={e => selectUnit(e.target.value)} required>
          <option value="">{t("Choose an organization unit")}</option>
          {units.filter(u => u.active && canManageUnit(u.id) && committeeTypes[u.type] && (!committees.some(c => c.organizationUnitId === u.id) || u.id === committee?.organizationUnitId)).map(u => <option key={u.id} value={u.id}>
                {u.name} · {humanize(u.type)}
              </option>)}
        </Select>
        <Field label={t("Committee name")} required maxLength={255} value={v.name} onChange={e => setV({
        ...v,
        name: e.target.value
      })} />
        <Field label={t("Committee code")} required maxLength={100} value={v.code} onChange={e => setV({
        ...v,
        code: e.target.value
      })} />
        {t(selected && <div className="info-note">
            <Building2 size={20} />
            <p>
              <strong>{humanize(committeeTypes[selected.type])}</strong>
              <br />{t("Committee type follows the selected organization level. Positions are created by the backend.")}</p>
          </div>)}
        <div className="form-actions">
          <Button variant="secondary" onClick={onClose}>{t("Cancel")}</Button>
          <Button type="submit" busy={busy}>
            {t(committee ? "Save changes" : "Create committee")}
          </Button>
        </div>
      </form>
    </Modal>;
}
export function PositionList({
  committee,
  onClose
}) {
  const {
    manage: canManage, canManageUnit
  } = usePermissions();
  useLocale();
  const resource = useResource(`/committee-positions/committees/${committee.id}/positions`);
  const {
    request,
    refresh,
    notify
  } = useApp();
  const [error, setError] = useState(null),
    [busy, setBusy] = useState(null),
    [vacant, setVacant] = useState(false);
  async function toggle(p) {
    setBusy(p.id);
    setError(null);
    try {
      await request(`/committee-positions/${p.id}/${p.active ? "deactivate" : "activate"}`, {
        method: "PATCH"
      });
      refresh();
      notify("Position updated.");
    } catch (e) {
      setError(e);
    } finally {
      setBusy(null);
    }
  }
  const rows = items(resource.data).filter(p => !vacant || p.available);
  return <Modal title={t("Committee positions")} subtitle={t(committee.name)} onClose={onClose} wide>
      <FormError error={error} />
      <div className="detail-toolbar">
        <span>{rows.length}{t(" positions")}</span>
        <label className="checkbox-label">
          <input type="checkbox" checked={vacant} onChange={e => setVacant(e.target.checked)} />{t("Show available only")}</label>
      </div>
      <ResourceState resource={resource}>
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>{t("POSITION")}</th>
                <th>{t("CURRENT ADVOCATE")}</th>
                <th>{t("AVAILABILITY")}</th>
                <th>{t("ACTIVE")}</th>
                <th>{t("ACTION")}</th>
              </tr>
            </thead>
            <tbody>
              {rows.map(p => <tr key={p.id}>
                  <td>
                    <strong>{humanize(p.positionType)}</strong>
                    <small className="table-small">{t("Seat")}{p.positionNumber}{t(" \xB7 ID ")}{p.id}
                    </small>
                  </td>
                  <td>{t(p.currentAdvocateName || "—")}</td>
                  <td>
                    <Badge value={p.available ? "AVAILABLE" : p.occupied ? "OCCUPIED" : "INACTIVE"} />
                  </td>
                  <td>{t(p.active ? "Yes" : "No")}</td>
                  <td>
                    {canManage && <Button variant="ghost" busy={busy === p.id} disabled={p.occupied} onClick={() => toggle(p)}>
                      {t(p.active ? "Deactivate" : "Activate")}
                    </Button>}
                  </td>
                </tr>)}
            </tbody>
          </table>
          {t(!rows.length && <Empty title={t("No positions found")} />)}
        </div>
      </ResourceState>
    </Modal>;
}
export default function Committees() {
  const {
    manage: canManage, canManageUnit
  } = usePermissions();
  useLocale();
  const resource = useResource("/committees"),
    units = useResource("/organization-units"),
    {
      request,
      refresh,
      notify
    } = useApp();
  const [q, setQ] = useState(""),
    [type, setType] = useState(""),
    [page, setPage] = useState(0),
    [edit, setEdit] = useState(null),
    [positions, setPositions] = useState(null),
    [confirm, setConfirm] = useState(null),
    [error, setError] = useState(null),
    [busy, setBusy] = useState(false);
  const all = items(resource.data).filter(c => !canManage || canManageUnit(c.organizationUnitId)),
    filtered = all.filter(c => (!type || c.committeeType === type) && [c.name, c.code, c.organizationUnitName].some(v => String(v || "").toLowerCase().includes(q.toLowerCase())));
  async function toggle() {
    setBusy(true);
    setError(null);
    try {
      await request(`/committees/${confirm.id}/${confirm.active ? "deactivate" : "activate"}`, {
        method: "PATCH"
      });
      refresh();
      notify("Committee status updated.");
      setConfirm(null);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return <>
      <PageHeading eyebrow={t("CONNECTED TEAMS")} title={t("Committees with a purpose.")} description={t("Manage your committees and the positions that bring them to life.")}>
        {canManage && <Button icon={Plus} onClick={() => setEdit({})}>{t("Create committee")}</Button>}
      </PageHeading>
      <div className="list-toolbar standalone-toolbar">
        <SearchInput value={q} onChange={v => {
        setQ(v);
        setPage(0);
      }} placeholder={t("Search committees…")} />
        <Select value={type} onChange={e => {
        setType(e.target.value);
        setPage(0);
      }} aria-label={t("Filter committee type")}>
          <option value="">{t("All committee types")}</option>
          {Object.values(committeeTypes).map(t => <option value={t} key={t}>
              {humanize(t)}
            </option>)}
        </Select>
      </div>
      <ResourceState resource={resource}>
        {t(filtered.length ? <>
            <div className="committee-grid">
              {filtered.slice(page * 9, (page + 1) * 9).map(c => <article className="committee-card" key={c.id}>
                  <div className="committee-card-top">
                    <span className="committee-emblem">
                      <Building2 size={25} strokeWidth={1.4} />
                    </span>
                    <Badge value={c.active ? "ACTIVE" : "INACTIVE"} />
                  </div>
                  <span className="eyebrow">
                    {humanize(c.committeeType).replace(" Committee", "")}
                  </span>
                  <h2>{c.name}</h2>
                  <p>{c.code}</p>
                  <div className="committee-unit">
                    <span className="dot green" />
                    {c.organizationUnitName}
                  </div>
                  <div className="committee-card-footer">
                    <button className="text-link" onClick={() => setPositions(c)}>{t("View positions")}<ArrowRight size={15} />
                    </button>
                    <div className="row-actions">
                      {canManage && <IconButton icon={Edit3} label={t(`Edit ${c.name}`)} onClick={() => setEdit(c)} />}
                      {canManage && <IconButton icon={Power} label={t(`${c.active ? "Deactivate" : "Activate"} ${c.name}`)} onClick={() => {
                  setConfirm(c);
                  setError(null);
                }} />}
                    </div>
                  </div>
                </article>)}
            </div>
            <Pagination page={page} size={9} total={filtered.length} onChange={setPage} />
          </> : <div className="panel">
            <Empty title={t("No committees found")} description={t("Create an organization unit, then give it a committee.")} action={canManage && <Button icon={Plus} onClick={() => setEdit({})}>{t("Create committee")}</Button>} />
          </div>)}
      </ResourceState>
      {t(canManage && edit && (!edit.id || canManageUnit(edit.organizationUnitId)) && <CommitteeForm committee={edit.id ? edit : null} units={items(units.data)} committees={all} onClose={() => setEdit(null)} />)}{t(" ")}
      {t(positions && <PositionList committee={positions} onClose={() => setPositions(null)} />)}{t(" ")}
      {t(canManage && confirm && canManageUnit(confirm.organizationUnitId) && <Modal title={t(`${confirm.active ? "Deactivate" : "Activate"} committee?`)} subtitle={t(confirm.name)} onClose={() => setConfirm(null)}>
          <FormError error={error} />
          <p className="muted">{t("The backend checks active appointments and pending applications before applying this change.")}</p>
          <div className="form-actions">
            {canManage && <Button variant="secondary" onClick={() => setConfirm(null)}>{t("Cancel")}</Button>}
            <Button variant={confirm.active ? "danger" : "primary"} onClick={toggle} busy={busy}>{t("Confirm change")}</Button>
          </div>
        </Modal>)}
    </>;
}
