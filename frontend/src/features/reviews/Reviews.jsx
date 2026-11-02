import { usePermissions } from "../../app/usePermissions";
import { t, useLocale } from "../../lib/i18n";
import { useState } from "react";
import { ArrowRight, CheckCircle2, Clock3, FileCheck2, ShieldCheck } from "lucide-react";
import { useResource } from "../../app/AppContext";
import { Avatar, Badge, Button, Empty, PageHeading, Pagination, ResourceState, SearchInput } from "../../components/ui";
import { date, humanize, items } from "../../lib/format";
import ApplicationDetail from "../registration/ApplicationDetail";
export default function Reviews() {
  const {manage} = usePermissions();
  useLocale();
  const [tab, setTab] = useState("pending"),
    [page, setPage] = useState(0),
    [q, setQ] = useState(""),
    [selected, setSelected] = useState(null);
  const documents = tab === "documents";
  const resource = useResource(documents ? "/document-reviews/applications" : `/application-reviews/${tab}`, documents ? {
    status: "AWAITING_RECOMMENDATION",
    page,
    size: 10
  } : {
    page,
    size: 10
  });
  const rows = items(resource.data).filter(row => {
    const a = documents ? row : row.application;
    return [a.applicantName, a.applicationNumber, a.committeeName].some(v => String(v || "").toLowerCase().includes(q.toLowerCase()));
  });
  return <>
      <PageHeading eyebrow={t("CLARITY AT EVERY STEP")} title={t("Good decisions start here.")} description={t("Review documents, resolve pending steps and keep applications moving.")} />
      <div className="review-intro">
        <span>
          <ShieldCheck size={21} />
        </span>
        <p>
          <strong>{t("Your authority follows your appointment.")}</strong>{t("Document reviews use your current branch or parent presidency. Approval decisions use the assigned committee\u2019s current president.")}</p>
      </div>
      <div className="page-tabs">
        {[["pending", "Pending approvals", Clock3], ["escalated", "Escalated reviews", CheckCircle2], ["documents", "Document verification", FileCheck2]].map(([value, label, Icon]) => <button key={value} className={tab === value ? "active" : ""} onClick={() => {
        setTab(value);
        setPage(0);
        setQ("");
      }}>
            <Icon size={16} />
            {t(label)}
          </button>)}
      </div>
      <section className="panel">
        <div className="list-toolbar">
          <SearchInput value={q} onChange={setQ} placeholder={t("Search this page…")} />
          <span className="subtle-count">
            {t(documents ? "Applications awaiting recommendation" : "Assigned to your reviewer identity")}
          </span>
        </div>
        <ResourceState resource={resource}>
          {t(rows.length ? <>
              <div className="review-list">
                {rows.map((row, i) => {
              const a = documents ? row : row.application,
                step = row.step;
              return <article className="review-item" key={a.id}>
                      <Avatar name={a.applicantName} index={i} />
                      <div className="review-item-main">
                        <div>
                          <h3>{a.applicantName}</h3>
                          <Badge value={documents ? "AWAITING_RECOMMENDATION" : row.overdue ? "OVERDUE" : "PENDING"} />
                        </div>
                        <p>
                          {a.applicationNumber}
                          <span>·</span>
                          {humanize(a.positionType)}
                          <span>·</span>
                          {a.selectedShakhaName}
                        </p>
                        <small>
                          {t(documents ? `Created ${date(a.createdAt)}` : `Step ${step?.sequenceNumber} · ${step?.reviewerCommitteeName} · Due ${date(step?.dueAt)}`)}
                        </small>
                      </div>
                      <Button variant="secondary" onClick={() => setSelected({
                  id: a.id,
                  mode: documents ? "documents" : "review"
                })}>
                        {t(documents ? "Review documents" : "Review application")}
                        <ArrowRight size={15} />
                      </Button>
                    </article>;
            })}
              </div>
              <Pagination page={page} total={resource.data?.totalElements} hasNext={resource.data?.hasNext} onChange={setPage} />
            </> : <Empty title={t(q ? "No matches on this page" : documents ? "No applications awaiting documents" : "You’re all caught up")} description={t(q ? "Clear the search or check the next page." : documents ? "Applications waiting for recommendation verification appear here." : "New review tasks will appear here when they are assigned to you.")} />)}
        </ResourceState>
      </section>
      {t(selected && <ApplicationDetail id={selected.id} mode={selected.mode} onClose={() => setSelected(null)} />)}
    </>;
}
