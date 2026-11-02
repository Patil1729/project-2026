import { usePermissions } from "../../app/usePermissions";
import { t, useLocale } from "../../lib/i18n";
import { useEffect, useState } from "react";
import { ArrowDownToLine, Edit3, MapPin, Plus, Power, ShieldCheck, UserRound, Users } from "lucide-react";
import { useApp, useResource } from "../../app/AppContext";
import { Avatar, Badge, Button, Empty, Field, FormError, IconButton, Modal, PageHeading, Pagination, ResourceState, SearchInput, Select } from "../../components/ui";
import { date, exportCsv, humanize, items } from "../../lib/format";
function MemberForm({
  member,
  units,
  onClose
}) {
  useLocale();
  const {
    request,
    refresh,
    notify
  } = useApp();
  const [v, setV] = useState({
      fullName: member?.fullName || "",
      username: member?.username || "",
      mobile: member?.mobile || "+91",
      email: member?.email || "",
      organizationUnitId: member?.organizationUnitId || "",
      ...(!member ? {
        password: ""
      } : {})
    }),
    [error, setError] = useState(null),
    [busy, setBusy] = useState(false);
  const update = (key, value) => setV(x => ({
    ...x,
    [key]: value
  }));
  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    if (v.password && new TextEncoder().encode(v.password).length > 72) {
      setError(new Error("Password must contain at most 72 UTF-8 bytes."));
      setBusy(false);
      return;
    }
    try {
      await request("/users" + (member ? "/" + member.id : ""), {
        method: member ? "PUT" : "POST",
        body: {
          ...v,
          fullName: v.fullName.trim(),
          username: v.username.trim(),
          mobile: v.mobile.trim(),
          email: v.email.trim() || null,
          organizationUnitId: v.organizationUnitId ? Number(v.organizationUnitId) : null
        }
      });
      notify(member ? "Member account updated." : "Member account created. Complete their profile next.");
      refresh();
      onClose();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return <Modal title={t(member ? "Edit member account" : "Add a member")} subtitle={t("Bring another person into your legal network.")} onClose={onClose}>
      <form onSubmit={submit}>
        <FormError error={error} />
        <Field label={t("Full name")} required maxLength={150} value={v.fullName} onChange={e => update("fullName", e.target.value)} placeholder={t("e.g. Aditi Deshmukh")} />
        <Field label={t("Username")} required minLength={3} maxLength={150} pattern="[A-Za-z0-9][A-Za-z0-9._\-]*" value={v.username} onChange={e => update("username", e.target.value)} placeholder={t("e.g. aditi.deshmukh")} />
        {t(!member && <Field label={t("Initial password")} type="password" required minLength={8} maxLength={72} autoComplete="new-password" value={v.password} onChange={e => update("password", e.target.value)} hint={t("8–72 characters; the backend securely hashes the password.")} />)}
        <div className="form-grid">
          <Field label={t("Mobile number")} required type="tel" pattern="\+[1-9][0-9]{7,14}" value={v.mobile} onChange={e => update("mobile", e.target.value)} hint={t("Include country code, e.g. +919876543210")} />
          <Field label={t("Email address")} type="email" maxLength={255} value={v.email} onChange={e => update("email", e.target.value)} />
        </div>
        <Select label={t("Organization unit")} value={v.organizationUnitId} onChange={e => update("organizationUnitId", e.target.value)}>
          <option value="">{t("No organization assigned")}</option>
          {units.filter(u => u.active).map(u => <option key={u.id} value={u.id}>
                {u.name}
              </option>)}
        </Select>
        <div className="info-note">
          <ShieldCheck size={18} />
          <p>{t("The backend assigns the initial APPLICANT role. Appointments and permissions follow the approval workflow.")}</p>
        </div>
        <div className="form-actions">
          <Button variant="secondary" onClick={onClose}>{t("Cancel")}</Button>
          <Button type="submit" busy={busy}>
            {t(member ? "Save changes" : "Create member")}
          </Button>
        </div>
      </form>
    </Modal>;
}
function ProfileEditor({
  member,
  onClose,
  readOnly = false
}) {
  useLocale();
  const resource = useResource(`/users/${member.id}/profile`),
    constituencies = useResource("/assembly-constituencies"),
    {
      request,
      notify,
      refresh
    } = useApp();
  const [v, setV] = useState({
      dateOfBirth: "",
      education: "",
      assemblyConstituencyId: "",
      voterIdNumber: "",
      voterListPartNumber: "",
      aadhaarLastFour: "",
      address: {
        houseOrFlatNumber: "",
        street: "",
        city: "",
        taluka: "",
        district: "",
        state: "Maharashtra",
        country: "India",
        pinCode: ""
      }
    }),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(null);
  useEffect(() => {
    if (resource.data) {
      const d = resource.data;
      setV({
        dateOfBirth: d.dateOfBirth || "",
        education: d.education || "",
        assemblyConstituencyId: d.assemblyConstituencyId || "",
        voterIdNumber: d.voterIdNumber || "",
        voterListPartNumber: d.voterListPartNumber || "",
        aadhaarLastFour: d.aadhaarLastFour || "",
        address: {
          ...v.address,
          ...d.address
        }
      });
    }
  }, [resource.data]);
  const change = (key, value) => setV(x => ({
    ...x,
    [key]: value
  }));
  const address = (key, value) => setV(x => ({
    ...x,
    address: {
      ...x.address,
      [key]: value
    }
  }));
  async function submit(e) {
    e.preventDefault();
    setError(null);
    if (!v.address.city?.trim() && !v.address.village?.trim()) {
      setError(new Error("Provide a city or village in the address."));
      return;
    }
    setBusy(true);
    try {
      await request(`/users/${member.id}/profile`, {
        method: resource.data ? "PUT" : "POST",
        body: {
          ...v,
          assemblyConstituencyId: Number(v.assemblyConstituencyId),
          voterListPartNumber: Number(v.voterListPartNumber),
          aadhaarLastFour: v.aadhaarLastFour || null
        }
      });
      notify("Member profile saved.");
      refresh();
      onClose();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  const profile404 = resource.error?.status === 404;
  return <Modal title={t("Member profile")} subtitle={t(member.fullName)} onClose={onClose} wide>
      {t(resource.loading ? <ResourceState resource={resource} /> : resource.error && !profile404 ? <ResourceState resource={resource} /> : <form onSubmit={submit}>
          <FormError error={error} />
          {t(profile404 && <div className="info-note">
              <UserRound size={18} />
              <p>{t("This member has no profile yet. Complete it before creating a registration application.")}</p>
            </div>)}
          <fieldset disabled={readOnly} className="profile-fields"><h3 className="form-section">{t("Personal information")}</h3>
          <div className="form-grid">
            <Field label={t("Date of birth")} type="date" required max={new Date(Date.now() - 86400000).toISOString().slice(0, 10)} value={v.dateOfBirth} onChange={e => change("dateOfBirth", e.target.value)} />
            <Field label={t("Education")} required maxLength={255} value={v.education} onChange={e => change("education", e.target.value)} placeholder={t("e.g. LL.B., University of Mumbai")} />
          </div>
          <h3 className="form-section">{t("Address")}</h3>
          <div className="form-grid">
            <Field label={t("House / flat number")} maxLength={50} value={v.address.houseOrFlatNumber || ""} onChange={e => address("houseOrFlatNumber", e.target.value)} />
            <Field label={t("Street")} maxLength={200} value={v.address.street || ""} onChange={e => address("street", e.target.value)} />
            {[["city", "City / town"], ["village", "Village"], ["taluka", "Taluka"], ["district", "District"], ["state", "State"], ["country", "Country"]].map(([key, label]) => <Field key={key} label={t(label)} required={!["city", "village"].includes(key)} value={v.address[key] || ""} onChange={e => address(key, e.target.value)} maxLength={key === "country" || key === "state" ? 100 : 150} />)}
            <Field label={t("PIN code")} required pattern="[1-9][0-9]{5}" inputMode="numeric" maxLength={6} value={v.address.pinCode} onChange={e => address("pinCode", e.target.value)} />
          </div>
          <h3 className="form-section">{t("Electoral information")}</h3>
          <ResourceState resource={constituencies}>
            <Select label={t("Assembly constituency")} required value={v.assemblyConstituencyId} onChange={e => change("assemblyConstituencyId", e.target.value)}>
              <option value="">{t("Select a constituency")}</option>
              {items(constituencies.data).filter(c => c.active).map(c => <option key={c.id} value={c.id}>
                    {c.constituencyNumber} · {c.name}
                  </option>)}
            </Select>
          </ResourceState>
          <div className="form-grid">
            <Field label={t("Voter ID number")} required maxLength={30} value={v.voterIdNumber} onChange={e => change("voterIdNumber", e.target.value)} />
            <Field label={t("Voter list part number")} type="number" min={1} required value={v.voterListPartNumber} onChange={e => change("voterListPartNumber", e.target.value)} />
            <Field label={t("Aadhaar last four digits")} pattern="[0-9]{4}" maxLength={4} inputMode="numeric" value={v.aadhaarLastFour} onChange={e => change("aadhaarLastFour", e.target.value)} hint={t("Only the last four digits are collected.")} />
          </div>
          </fieldset><div className="form-actions">
            <Button variant="secondary" onClick={onClose}>{t(readOnly ? "Close" : "Cancel")}</Button>
            {!readOnly && <Button type="submit" busy={busy} disabled={constituencies.loading || !!constituencies.error}>{t("Save profile")}</Button>}
          </div>
        </form>)}
    </Modal>;
}
function Constituencies() {
  useLocale();
  const { manageUserAccounts } = usePermissions();
  const resource = useResource("/assembly-constituencies"),
    {
      request,
      refresh,
      notify
    } = useApp();
  const [q, setQ] = useState(""),
    [show, setShow] = useState(false),
    [v, setV] = useState({
      name: "",
      stateCode: "MH",
      constituencyNumber: ""
    }),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(null);
  const rows = items(resource.data).filter(c => [c.name, c.stateCode, c.constituencyNumber].some(v => String(v).toLowerCase().includes(q.toLowerCase())));
  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await request("/assembly-constituencies", {
        method: "POST",
        body: {
          ...v,
          stateCode: v.stateCode.toUpperCase(),
          constituencyNumber: Number(v.constituencyNumber)
        }
      });
      refresh();
      notify("Assembly constituency created.");
      setShow(false);
      setV({
        name: "",
        stateCode: "MH",
        constituencyNumber: ""
      });
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return <>
      <section className="panel">
        <div className="list-toolbar">
          <SearchInput value={q} onChange={setQ} placeholder={t("Search constituencies…")} />
          {manageUserAccounts && <Button icon={Plus} onClick={() => {
          setShow(true);
          setError(null);
        }}>{t("Add constituency")}</Button>}
        </div>
        <ResourceState resource={resource}>
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>{t("CONSTITUENCY")}</th>
                  <th>{t("NUMBER")}</th>
                  <th>{t("STATE")}</th>
                  <th>{t("STATUS")}</th>
                </tr>
              </thead>
              <tbody>
                {rows.map(c => <tr key={c.id}>
                    <td>
                      <div className="unit-cell">
                        <span className="unit-icon">
                          <MapPin size={18} />
                        </span>
                        <strong>{c.name}</strong>
                      </div>
                    </td>
                    <td>{c.constituencyNumber}</td>
                    <td>{c.stateCode}</td>
                    <td>
                      <Badge value={c.active ? "ACTIVE" : "INACTIVE"} />
                    </td>
                  </tr>)}
              </tbody>
            </table>
            {t(!rows.length && <Empty title={t("No constituencies found")} />)}
          </div>
        </ResourceState>
      </section>
      {t(show && <Modal title={t("Add assembly constituency")} subtitle={t("Electoral reference data used by member profiles.")} onClose={() => setShow(false)}>
          <form onSubmit={submit}>
            <FormError error={error} />
            <Field label={t("Constituency name")} required maxLength={255} value={v.name} onChange={e => setV({
          ...v,
          name: e.target.value
        })} />
            <div className="form-grid">
              <Field label={t("State code")} required maxLength={10} value={v.stateCode} onChange={e => setV({
            ...v,
            stateCode: e.target.value
          })} />
              <Field label={t("Constituency number")} required type="number" min={1} value={v.constituencyNumber} onChange={e => setV({
            ...v,
            constituencyNumber: e.target.value
          })} />
            </div>
            <div className="form-actions">
              <Button variant="secondary" onClick={() => setShow(false)}>{t("Cancel")}</Button>
              <Button type="submit" busy={busy}>{t("Add constituency")}</Button>
            </div>
          </form>
        </Modal>)}
    </>;
}
export default function Members() {
  useLocale();
  const { manageUserAccounts } = usePermissions();
  const resource = useResource("/users"),
    units = useResource("/organization-units"),
    {
      request,
      refresh,
      notify
    } = useApp();
  const [tab, setTab] = useState("members"),
    [q, setQ] = useState(""),
    [page, setPage] = useState(0),
    [edit, setEdit] = useState(null),
    [profile, setProfile] = useState(null),
    [deactivate, setDeactivate] = useState(null),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(null);
  const all = items(resource.data),
    filtered = all.filter(u => [u.fullName, u.username, u.mobile, u.organizationUnitName].some(v => String(v || "").toLowerCase().includes(q.toLowerCase())));
  async function disable() {
    setBusy(true);
    setError(null);
    try {
      await request(`/users/${deactivate.id}/deactivate`, {
        method: "PATCH"
      });
      refresh();
      notify("Member account deactivated.");
      setDeactivate(null);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return <>
      <PageHeading eyebrow={t("PEOPLE FIRST")} title={t("The people behind the purpose.")} description={t(manageUserAccounts ? "Manage member accounts, profiles and electoral reference data." : "View members whose home organisation falls within your assigned areas.")}>
        <Button variant="secondary" icon={ArrowDownToLine} disabled={!filtered.length} onClick={() => exportCsv("legalcell-members.csv", filtered.map(u => ({
        name: u.fullName,
        username: u.username,
        mobile: u.mobile,
        email: u.email,
        organization: u.organizationUnitName,
        enabled: u.enabled
      })))}>{t("Export members")}</Button>
        {manageUserAccounts && <Button icon={Plus} onClick={() => setEdit({})}>{t("Add member")}</Button>}
      </PageHeading>
      <div className="page-tabs">
        <button className={tab === "members" ? "active" : ""} onClick={() => setTab("members")}>
          <Users size={16} />{t("Member directory")}<span>{all.length}</span>
        </button>
        <button className={tab === "constituencies" ? "active" : ""} onClick={() => setTab("constituencies")}>
          <MapPin size={16} />{t("Assembly constituencies")}</button>
      </div>
      {t(tab === "constituencies" ? <Constituencies /> : <section className="panel">
          <div className="list-toolbar">
            <SearchInput value={q} onChange={v => {
          setQ(v);
          setPage(0);
        }} placeholder={t("Search by name, username or mobile…")} />
            <span className="subtle-count">
              {filtered.length}{t("members in your network")}</span>
          </div>
          <ResourceState resource={resource}>
            {t(filtered.length ? <>
                <div className="table-scroll">
                  <table>
                    <thead>
                      <tr>
                        <th>{t("MEMBER")}</th>
                        <th>{t("CONTACT")}</th>
                        <th>{t("ORGANIZATION")}</th>
                        <th>{t("STATUS")}</th>
                        <th>{t("PROFILE & ACTIONS")}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {filtered.slice(page * 10, (page + 1) * 10).map((m, i) => <tr key={m.id}>
                            <td>
                              <div className="person-cell">
                                <Avatar user={m} name={m.fullName} index={i} />
                                <div>
                                  <strong>{m.fullName}</strong>
                                  <small>@{m.username}</small>
                                </div>
                              </div>
                            </td>
                            <td>
                              <span className="table-main">{m.mobile}</span>
                              <small className="table-small">
                                {t(m.email || "No email provided")}
                              </small>
                            </td>
                            <td>{t(m.organizationUnitName || "Unassigned")}</td>
                            <td>
                              <Badge value={m.enabled ? "ACTIVE" : "INACTIVE"} />
                            </td>
                            <td>
                              <div className="row-actions">
                                <Button variant="ghost" onClick={() => setProfile(m)}>{t("Profile")}</Button>
                                {manageUserAccounts && <IconButton icon={Edit3} label={t(`Edit ${m.fullName}`)} onClick={() => setEdit(m)} />}
                                {manageUserAccounts && <IconButton icon={Power} label={t(`Deactivate ${m.fullName}`)} disabled={!m.enabled} onClick={() => {
                        setDeactivate(m);
                        setError(null);
                      }} />}
                              </div>
                            </td>
                          </tr>)}
                    </tbody>
                  </table>
                </div>
                <Pagination page={page} total={filtered.length} onChange={setPage} />
              </> : <Empty title={t("No members found")} action={manageUserAccounts && <Button icon={Plus} onClick={() => setEdit({})}>{t("Add member")}</Button>} />)}
          </ResourceState>
        </section>)}
      {t(manageUserAccounts && edit && <MemberForm member={edit.id ? edit : null} units={items(units.data)} onClose={() => setEdit(null)} />)}{t(" ")}
      {t(profile && <ProfileEditor readOnly={!manageUserAccounts} member={profile} onClose={() => setProfile(null)} />)}{t(" ")}
      {t(manageUserAccounts && deactivate && <Modal title={t("Deactivate member account?")} subtitle={t(deactivate.fullName)} onClose={() => setDeactivate(null)}>
          <FormError error={error} />
          <p className="muted">{t("Active appointments and pending workflows may prevent this change. The backend checks those dependencies.")}</p>
          <div className="form-actions">
            <Button variant="secondary" onClick={() => setDeactivate(null)}>{t("Cancel")}</Button>
            <Button variant="danger" busy={busy} onClick={disable}>{t("Deactivate member")}</Button>
          </div>
        </Modal>)}
    </>;
}
