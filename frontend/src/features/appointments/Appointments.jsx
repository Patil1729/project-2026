import { usePermissions } from "../../app/usePermissions";
import { t, useLocale } from "../../lib/i18n";
import { useState } from "react";
import { ArrowRight, History, Plus, RefreshCw, UserRoundCheck } from "lucide-react";
import { useApp, useResource } from "../../app/AppContext";
import { Avatar, Badge, Button, Empty, Field, FormError, Modal, PageHeading, Pagination, ResourceState, SearchInput, Select, Timeline } from "../../components/ui";
import { date, humanize, items } from "../../lib/format";
function AppointmentAction({
  appointment,
  action,
  onClose
}) {
  useLocale();
  const {
      request,
      refresh,
      notify
    } = useApp(),
    users = useResource("/users"),
    committees = useResource("/committees");
  const [user, setUser] = useState(""),
    [committee, setCommittee] = useState(""),
    [reason, setReason] = useState(""),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(null);
  const bootstrap = action === "bootstrap";
  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await request(bootstrap ? "/advocate-assignments/bootstrap-state-president" : `/advocate-assignments/${appointment.id}/${action}`, {
        method: action === "end" ? "PATCH" : "POST",
        body: {
          reason: reason.trim(),
          ...(action !== "end" ? {
            advocateUserId: Number(user)
          } : {}),
          ...(bootstrap ? {
            committeeId: Number(committee)
          } : {})
        }
      });
      refresh();
      notify(bootstrap ? "State president appointed." : action === "end" ? "Appointment ended." : "Appointment replaced.");
      onClose();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return <Modal title={t(bootstrap ? "Appoint the first state president" : action === "end" ? "End this appointment?" : "Replace an appointment")} subtitle={t(bootstrap ? "Bootstrap a state committee to start the approval hierarchy." : appointment.advocateName + " · " + appointment.committeeName)} onClose={onClose}>
      <form onSubmit={submit}>
        <FormError error={error} />
        {t(bootstrap && <ResourceState resource={committees}>
            <Select label={t("State committee")} required value={committee} onChange={e => setCommittee(e.target.value)}>
              <option value="">{t("Select state committee")}</option>
              {items(committees.data).filter(c => c.active && c.committeeType === "STATE_COMMITTEE").map(c => <option key={c.id} value={c.id}>
                    {c.name}
                  </option>)}
            </Select>
          </ResourceState>)}
        {t(action !== "end" && <ResourceState resource={users}>
            <Select label={t(bootstrap ? "State president" : "Replacement advocate")} required value={user} onChange={e => setUser(e.target.value)}>
              <option value="">{t("Select an enabled member")}</option>
              {items(users.data).filter(u => u.enabled && u.id !== appointment?.advocateUserId).map(u => <option key={u.id} value={u.id}>
                    {u.fullName} · @{u.username}
                  </option>)}
            </Select>
          </ResourceState>)}
        <Field label={t("Reason")} required maxLength={2000} value={reason} onChange={e => setReason(e.target.value)} placeholder={t("Explain why this action is needed")} />
        <div className="info-note">
          <UserRoundCheck size={19} />
          <p>
            {t(bootstrap ? "Only PARTY_HEAD can appoint the first state president. An existing active president cannot be overwritten." : "The backend validates the change and records an audit event. Replacements preserve the previous appointment’s history.")}
          </p>
        </div>
        <div className="form-actions">
          <Button variant="secondary" onClick={onClose}>{t("Cancel")}</Button>
          <Button type="submit" variant={action === "end" ? "danger" : "primary"} busy={busy}>{t("Confirm")}{t(" ")}
            {t(bootstrap ? "appointment" : action === "end" ? "end" : "replacement")}
          </Button>
        </div>
      </form>
    </Modal>;
}
function AppointmentHistory({
  appointment,
  onClose
}) {
  useLocale();
  const resource = useResource(`/advocate-assignments/${appointment.id}/history`, {
    size: 100
  });
  return <Modal title={t("Appointment history")} subtitle={t(`${appointment.advocateName} · ${appointment.committeeName}`)} onClose={onClose}>
      <ResourceState resource={resource}>
        <Timeline entries={items(resource.data)} />
        {t(resource.data?.hasNext && <p className="sample-note">{t("Showing the latest 100 events.")}</p>)}
      </ResourceState>
    </Modal>;
}
export default function Appointments() {
  useLocale();
  const { manageAppointments, canManageUnit } = usePermissions();
  const [active, setActive] = useState("true"),
    [committee, setCommittee] = useState(""),
    [q, setQ] = useState(""),
    [page, setPage] = useState(0),
    [action, setAction] = useState(null),
    [history, setHistory] = useState(null);
  const resource = useResource("/advocate-assignments", {
      active: active === "" ? undefined : active === "true",
      committeeId: committee,
      page,
      size: 10
    }),
    committees = useResource("/committees");
  const rows = items(resource.data).filter(a => [a.advocateName, a.committeeName, a.username].some(v => String(v || "").toLowerCase().includes(q.toLowerCase())));
  return <>
      <PageHeading eyebrow={t("PEOPLE IN POSITION")} title={t("Responsibility, made visible.")} description={t("Manage appointments and preserve a clear record of every handover.")}>
        {manageAppointments && <Button variant="secondary" icon={Plus} onClick={() => setAction({
        action: "bootstrap"
      })}>{t("Bootstrap state president")}</Button>}
      </PageHeading>
      <div className="info-banner">
        <UserRoundCheck size={20} />
        <p>{t("Completed application approvals create appointments automatically. Use this view for status, handovers and history.")}</p>
      </div>
      <section className="panel">
        <div className="list-toolbar">
          <SearchInput value={q} onChange={setQ} placeholder={t("Search this page…")} />
          <div>
            <Select value={active} onChange={e => {
            setActive(e.target.value);
            setPage(0);
          }} aria-label={t("Filter appointment status")}>
              <option value="true">{t("Active appointments")}</option>
              <option value="false">{t("Ended appointments")}</option>
              <option value="">{t("All appointments")}</option>
            </Select>
            <Select value={committee} onChange={e => {
            setCommittee(e.target.value);
            setPage(0);
          }} aria-label={t("Filter appointment committee")}>
              <option value="">{t("All committees")}</option>
              {items(committees.data).filter(c => canManageUnit(c.organizationUnitId)).map(c => <option key={c.id} value={c.id}>
                  {c.name}
                </option>)}
            </Select>
          </div>
        </div>
        <ResourceState resource={resource}>
          {t(rows.length ? <>
              <div className="table-scroll">
                <table>
                  <thead>
                    <tr>
                      <th>{t("ADVOCATE")}</th>
                      <th>{t("POSITION & COMMITTEE")}</th>
                      <th>{t("APPOINTED")}</th>
                      <th>{t("STATUS")}</th>
                      <th>{t("ACTIONS")}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {rows.map((a, i) => <tr key={a.id}>
                        <td>
                          <div className="person-cell">
                            <Avatar name={a.advocateName} index={i} />
                            <div>
                              <strong>{a.advocateName}</strong>
                              <small>@{a.username}</small>
                            </div>
                          </div>
                        </td>
                        <td>
                          <span className="table-main">
                            {humanize(a.positionType)}{t(" \xB7 Seat ")}{a.positionNumber}
                          </span>
                          <small className="table-small">
                            {a.committeeName}
                          </small>
                        </td>
                        <td>
                          {date(a.appointedDate)}
                          {t(a.endDate && <small className="table-small">{t("Ended")}{date(a.endDate)}
                            </small>)}
                        </td>
                        <td>
                          <Badge value={a.active ? "ACTIVE" : "ENDED"} />
                        </td>
                        <td>
                          <div className="row-actions">
                            <Button variant="ghost" icon={History} onClick={() => setHistory(a)}>{t("History")}</Button>
                            {t(manageAppointments && a.active && <>
                                <Button variant="ghost" onClick={() => setAction({
                          action: "replace",
                          appointment: a
                        })}>{t("Replace")}</Button>
                                <Button variant="ghost" onClick={() => setAction({
                          action: "end",
                          appointment: a
                        })}>{t("End")}</Button>
                              </>)}
                          </div>
                        </td>
                      </tr>)}
                  </tbody>
                </table>
              </div>
              <Pagination page={page} total={resource.data?.totalElements} hasNext={resource.data?.hasNext} onChange={setPage} />
            </> : <Empty title={t("No appointments found")} description={t("Try different filters or complete an application’s approval chain.")} />)}
        </ResourceState>
      </section>
      {t(manageAppointments && action && <AppointmentAction {...action} onClose={() => setAction(null)} />)}{t(" ")}
      {t(history && <AppointmentHistory appointment={history} onClose={() => setHistory(null)} />)}
    </>;
}
