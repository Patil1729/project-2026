import { usePermissions } from "../../app/usePermissions";
import { t, useLocale } from "../../lib/i18n";
import { useEffect, useState } from "react";
import { useSearchParams } from "react-router";
import { ArrowDownToLine, ArrowUpRight, FileText, Plus, SlidersHorizontal } from "lucide-react";
import { useApp, useResource } from "../../app/AppContext";
import { Avatar, Badge, Button, Empty, Field, FormError, Modal, PageHeading, Pagination, ResourceState, SearchInput, Select } from "../../components/ui";
import { statuses } from "../../lib/contracts";
import { date, exportCsv, humanize, items } from "../../lib/format";
import ApplicationDetail from "./ApplicationDetail";
import NewApplication from "./NewApplication";
export default function Applications() {
  const {manage, canManageUnit, globalAdministrator} = usePermissions();
  useLocale();
  const [params, setParams] = useSearchParams();
  const {
    notify
  } = useApp();
  const [q, setQ] = useState(params.get("q") || ""),
    [debounced, setDebounced] = useState(q),
    [status, setStatus] = useState(""),
    [page, setPage] = useState(0),
    [showFilters, setShowFilters] = useState(false),
    [from, setFrom] = useState(""),
    [to, setTo] = useState(""),
    [unit, setUnit] = useState(""),
    [create, setCreate] = useState(params.get("new") === "1"),
    [selected, setSelected] = useState(params.get("application") ? {
      id: Number(params.get("application")),
      mode: manage ? "admin" : "my"
    } : null);
  const view = !manage || params.get("view") === "my" ? "my" : "all",
    mine = view === "my";
  useEffect(() => {
    const t = setTimeout(() => {
      setDebounced(q);
      setPage(0);
    }, 250);
    return () => clearTimeout(t);
  }, [q]);
  useEffect(() => {
    setQ(params.get("q") || "");
    if (params.get("new") === "1") setCreate(true);
    if (params.get("application")) setSelected({
      id: Number(params.get("application")),
      mode: manage ? "admin" : "my"
    });
  }, [params]);
  const validRange = !from || !to || from <= to;
  const resource = useResource(mine ? "/registration-applications/my" : "/admin/registration-applications", mine ? {} : {
    q: debounced,
    status,
    page,
    size: 10,
    createdFrom: from,
    createdTo: to,
    organizationUnitId: unit,
    includeDescendants: true
  }, validRange);
  const units = useResource("/organization-units");
  const records = items(resource.data).filter(a => !mine || (!status || a.status === status) && [a.applicantName, a.applicationNumber].some(s => String(s || "").toLowerCase().includes(q.toLowerCase())));
  const visible = mine ? records.slice(page * 10, (page + 1) * 10) : records;
  function close() {
    setSelected(null);
    setCreate(false);
    const next = new URLSearchParams(params);
    next.delete("new");
    next.delete("application");
    setParams(next, {
      replace: true
    });
  }
  function switchView(value) {
    setPage(0);
    setParams(value === "my" ? {
      view: "my"
    } : {});
  }
  return <>
      <PageHeading eyebrow={t("FROM INTENT TO IMPACT")} title={t("Every application has a journey.")} description={t("Follow registrations from the first recommendation to the final decision.")}>
        <Button variant="secondary" icon={ArrowDownToLine} disabled={!records.length} onClick={() => {
        exportCsv("legalcell-applications-page.csv", records.map(a => ({
          application: a.applicationNumber,
          applicant: a.applicantName,
          committee: a.committeeName,
          position: a.positionType,
          status: a.status,
          createdAt: a.createdAt
        })));
        notify("Current page exported.");
      }}>{t("Export page")}</Button>
        <Button icon={Plus} onClick={() => setCreate(true)}>{t("New application")}</Button>
      </PageHeading>
      <div className="page-tabs">
        {manage && <button className={!mine ? "active" : ""} onClick={() => switchView("all")}>{t(globalAdministrator ? "All applications" : "Area applications")}</button>}
        <button className={mine ? "active" : ""} onClick={() => switchView("my")}>{t("My applications")}</button>
      </div>
      <section className="panel">
        <div className="list-toolbar">
          <SearchInput value={q} onChange={setQ} placeholder={t("Search applicant or application number…")} />
          <div>
            <Select value={status} onChange={e => {
            setStatus(e.target.value);
            setPage(0);
          }} aria-label={t("Filter application status")}>
              <option value="">{t("All statuses")}</option>
              {statuses.map(s => <option key={s} value={s}>
                  {humanize(s)}
                </option>)}
            </Select>
            {t(!mine && <Button variant={showFilters ? "primary" : "secondary"} icon={SlidersHorizontal} onClick={() => setShowFilters(!showFilters)}>{t("Filters")}</Button>)}
          </div>
        </div>
        {t(showFilters && !mine && <div className="advanced-filters">
            <Field label={t("Created from")} type="date" value={from} onChange={e => {
          setFrom(e.target.value);
          setPage(0);
        }} />
            <Field label={t("Created to")} type="date" value={to} min={from} onChange={e => {
          setTo(e.target.value);
          setPage(0);
        }} />
            <Select label={t("Organization unit")} value={unit} onChange={e => {
          setUnit(e.target.value);
          setPage(0);
        }}>
              <option value="">{t("All units and descendants")}</option>
              {items(units.data).filter(u => canManageUnit(u.id)).map(u => <option key={u.id} value={u.id}>
                  {u.name}
                </option>)}
            </Select>
            <Button variant="ghost" onClick={() => {
          setFrom("");
          setTo("");
          setUnit("");
          setStatus("");
          setQ("");
        }}>{t("Clear filters")}</Button>
          </div>)}
        {t(!validRange ? <FormError error="The start date must be on or before the end date." /> : <ResourceState resource={resource}>
            {t(visible.length ? <>
                <div className="table-scroll">
                  <table>
                    <thead>
                      <tr>
                        <th>{t("APPLICANT")}</th>
                        <th>{t("COMMITTEE & POSITION")}</th>
                        <th>{t("CREATED")}</th>
                        <th>{t("STATUS")}</th>
                        <th>{t("DETAILS")}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {visible.map((a, i) => <tr key={a.id}>
                          <td>
                            <div className="person-cell">
                              <Avatar name={a.applicantName} index={i} />
                              <div>
                                <strong>{a.applicantName}</strong>
                                <small>{a.applicationNumber}</small>
                              </div>
                            </div>
                          </td>
                          <td>
                            <span className="table-main">
                              {t(a.organizationUnitName || a.selectedShakhaName || a.committeeName)}
                            </span>
                            <small className="table-small">
                              {humanize(a.positionType)}{t(" \xB7 Seat")}{t(" ")}
                              {a.positionNumber}
                            </small>
                          </td>
                          <td>{date(a.createdAt)}</td>
                          <td>
                            <Badge value={a.status} />
                          </td>
                          <td>
                            <Button variant="ghost" onClick={() => setSelected({
                      id: a.id,
                      mode: mine ? "my" : "admin"
                    })}>{t("View")}<ArrowUpRight size={15} />
                            </Button>
                          </td>
                        </tr>)}
                    </tbody>
                  </table>
                </div>
                <Pagination page={page} total={mine ? records.length : resource.data?.totalElements} hasNext={mine ? undefined : resource.data?.hasNext} onChange={setPage} />
              </> : <Empty title={t("No applications found")} description={t("Adjust the filters or start a new application.")} action={<Button icon={Plus} onClick={() => setCreate(true)}>{t("New application")}</Button>} />)}
          </ResourceState>)}
      </section>
      {t(create && <NewApplication onClose={close} onCreated={id => {
      setCreate(false);
      setSelected({
        id,
        mode: "my"
      });
      setParams({
        view: "my"
      }, {
        replace: true
      });
    }} />)}
      {t(selected && !create && <ApplicationDetail id={selected.id} mode={selected.mode} onClose={close} />)}
    </>;
}
