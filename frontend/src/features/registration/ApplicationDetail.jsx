import { usePermissions } from "../../app/usePermissions";
import { t, useLocale } from "../../lib/i18n";
import { useState } from "react";
import { ArrowRight, Check, CheckCircle2, Clock3, FileText, GitBranch, Send, ShieldCheck, X } from "lucide-react";
import { useApp, useResource } from "../../app/AppContext";
import { Avatar, Badge, Button, Field, FormError, Modal, ResourceState, Timeline } from "../../components/ui";
import { date, humanize, items } from "../../lib/format";
import Documents from "./Documents";
export default function ApplicationDetail({
  id,
  mode = "admin",
  onClose
}) {
  useLocale();
  const {manage, globalAdministrator} = usePermissions();
  const review = mode === "review",
    owner = mode === "my";
  const path = review ? `/application-reviews/applications/${id}` : owner ? `/registration-applications/${id}` : mode === "documents" ? `/document-reviews/applications/${id}` : `/admin/registration-applications/${id}`;
  const resource = useResource(path),
    history = useResource(`${owner ? "/registration-applications" : "/admin/registration-applications"}/${id}/history`, {
      size: 100
    }, owner || manage);
  const stepsResource = useResource(`/registration-applications/${id}/approval-steps`, {}, owner);
  const {
    user,
    request,
    refresh,
    notify
  } = useApp();
  const [tab, setTab] = useState(mode === "documents" || owner ? "documents" : "overview"),
    [action, setAction] = useState(null),
    [comment, setComment] = useState(""),
    [error, setError] = useState(null),
    [busy, setBusy] = useState(false);
  const app = review ? resource.data?.application : resource.data;
  const steps = review ? resource.data?.approvalSteps || [] : items(stepsResource.data);
  const currentStep = steps.find(s => ["PENDING", "OVERDUE"].includes(s.status));
  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      if (action === "submit") await request(`/registration-applications/${id}/submit`, {
        method: "POST"
      });else if (action === "reassign") await request(`/admin/registration-applications/${id}/reassign-reviewer`, {
        method: "POST",
        body: {
          reason: comment.trim()
        }
      });else await request(`/application-reviews/applications/${id}/steps/${currentStep.id}/${action}`, {
        method: "PATCH",
        body: action === "approve" ? {
          comment: comment.trim() || null
        } : {
          reason: comment.trim()
        }
      });
      notify(action === "submit" ? "Application submitted for review." : action === "reassign" ? "Reviewer reassignment completed." : "Review decision saved.");
      setAction(null);
      setComment("");
      refresh();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return <Modal title={t(app?.applicationNumber || "Application details")} subtitle={t("A clear view of every step, document and decision.")} onClose={onClose} wide>
      <ResourceState resource={resource}>
        {t(app && <>
            <div className="application-person">
              <Avatar name={app.applicantName} />
              <div>
                <h3>{app.applicantName}</h3>
                <p>
                  {humanize(app.positionType)} · {app.committeeName}
                </p>
              </div>
              <Badge value={app.status} />
            </div>
            <div className="page-tabs detail-tabs">
              {[["overview", "Overview"], ["documents", "Documents"], ["history", "Activity history"]].filter(([value]) => value !== "history" || owner || manage).map(([value, label]) => <button key={value} className={tab === value ? "active" : ""} onClick={() => setTab(value)}>
                  {t(label)}
                </button>)}
            </div>
            {t(tab === "overview" && <>
                <div className="detail-grid">
                  <div>
                    <span>{t("Organization unit")}</span>
                    <strong>
                      {t(app.organizationUnitName || app.selectedShakhaName)}
                    </strong>
                  </div>
                  <div>
                    <span>{t("Requested position")}</span>
                    <strong>
                      {humanize(app.positionType)}{t(" \xB7 Seat ")}{app.positionNumber}
                    </strong>
                  </div>
                  <div>
                    <span>{t("Created on")}</span>
                    <strong>{date(app.createdAt)}</strong>
                  </div>
                  <div>
                    <span>{t("Submitted on")}</span>
                    <strong>{date(app.submittedAt)}</strong>
                  </div>
                  <div>
                    <span>{t("Applicant ID")}</span>
                    <strong>#{app.applicantId}</strong>
                  </div>
                  <div>
                    <span>{t("Application ID")}</span>
                    <strong>#{app.id}</strong>
                  </div>
                </div>
                {t(app.rejectionReason && <div className="inline-error">
                    <X size={18} />
                    <span>{app.rejectionReason}</span>
                  </div>)}
                <h3 className="form-section">{t("Approval journey")}</h3>
                {t(owner && stepsResource.error ? <ResourceState resource={stepsResource} /> : steps.length ? <div className="approval-journey">
                    {steps.map(step => <div className="approval-step" key={step.id}>
                        <span className={`approval-step-icon ${step.status.toLowerCase()}`}>
                          {t(step.status === "APPROVED" ? <Check size={16} /> : <Clock3 size={16} />)}
                        </span>
                        <div>
                          <strong>
                            {step.sequenceNumber}. {step.reviewerCommitteeName}
                          </strong>
                          <p>
                            {t(step.assignedReviewerName || "Awaiting reviewer assignment")}
                          </p>
                          <small>{t("Due")}{date(step.dueAt)}
                            {t(step.decisionComment ? ` · ${step.decisionComment}` : "")}
                          </small>
                        </div>
                        <Badge value={step.status} />
                      </div>)}
                  </div> : <div className="info-note">
                    <GitBranch size={20} />
                    <p>
                      {t(app.status === "AWAITING_RECOMMENDATION" ? "Upload your recommendation letter, have it verified, then submit to create the approval chain." : mode === "admin" ? "Open this application from the review inbox to inspect and act on its approval steps." : "No approval steps are available.")}
                    </p>
                  </div>)}
                {t(owner && app.status === "AWAITING_RECOMMENDATION" && <div className="detail-callout">
                    <div>
                      <ShieldCheck size={21} />
                      <p>
                        <strong>{t("Ready for the next step?")}</strong>
                        <br />{t("Your recommendation letter must be verified before submission.")}</p>
                    </div>
                    <Button icon={Send} onClick={() => {
              setAction("submit");
              setError(null);
            }}>{t("Submit application")}</Button>
                  </div>)}
                {t(review && app.status === "UNDER_REVIEW" && currentStep && Number(currentStep.assignedReviewerId) === Number(user?.id) && <div className="detail-callout">
                    <p>{t("Record a decision for the active approval step. The backend checks your reviewer authority.")}</p>
                    <div className="button-row">
                      <Button icon={X} variant="secondary" onClick={() => {
                setAction("reject");
                setComment("");
                setError(null);
              }}>{t("Reject")}</Button>
                      <Button icon={Check} onClick={() => {
                setAction("approve");
                setComment("");
                setError(null);
              }}>{t("Approve step")}</Button>
                    </div>
                  </div>)}
                {t(globalAdministrator && mode === "admin" && app.status === "UNDER_REVIEW" && <Button variant="secondary" onClick={() => {
            setAction("reassign");
            setComment("");
            setError(null);
          }}>{t("Reassign to current president")}</Button>)}
                {t(action && <form onSubmit={submit} className="action-form">
                    <h3>
                      {t(action === "submit" ? "Submit this application?" : action === "reassign" ? "Reassign the active reviewer" : action === "approve" ? "Approve this step" : "Reject this application")}
                    </h3>
                    <FormError error={error} />
                    {t(action !== "submit" ? <Field label={t(action === "approve" ? "Comment (optional)" : "Reason")} required={action !== "approve"} value={comment} onChange={e => setComment(e.target.value)} maxLength={action === "reassign" ? 1500 : 2000} /> : <p>{t("Your documents and requested position will be checked before the review begins.")}</p>)}
                    <div className="form-actions">
                      <Button variant="secondary" onClick={() => setAction(null)}>{t("Cancel")}</Button>
                      <Button type="submit" variant={action === "reject" ? "danger" : "primary"} busy={busy}>{t("Confirm")}{t(" ")}
                        {t(action === "reassign" ? "reassignment" : action === "submit" ? "submission" : "decision")}
                      </Button>
                    </div>
                  </form>)}
              </>)}
            {t(tab === "documents" && <Documents application={app} mode={mode} />)}{t(" ")}
            {t(tab === "history" && <ResourceState resource={history}>
                <Timeline entries={items(history.data)} />
                {t(history.data?.hasNext && <p className="sample-note">{t("Showing the latest 100 events.")}</p>)}
              </ResourceState>)}
          </>)}
      </ResourceState>
    </Modal>;
}
