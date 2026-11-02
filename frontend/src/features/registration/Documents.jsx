import { t, useLocale } from "../../lib/i18n";
import { useEffect, useRef, useState } from "react";
import { Check, Download, FileCheck2, FileText, UploadCloud, X } from "lucide-react";
import { useApp, useResource } from "../../app/AppContext";
import { Badge, Button, Empty, Field, FormError, ResourceState, Select } from "../../components/ui";
import { documentTypes, maxFileBytes } from "../../lib/contracts";
import { date, downloadBlob, humanize, items } from "../../lib/format";
export default function Documents({
  application,
  mode,
  onDocumentsChange,
  onBusyChange
}) {
  useLocale();
  const owner = mode === "my";
  const canVerify = mode === "documents" || mode === "review";
  const path = owner ? `/registration-applications/${application.id}/documents` : `/document-reviews/applications/${application.id}/documents`;
  const resource = useResource(path),
    {
      request,
      refresh,
      notify,
      config
    } = useApp();
  const [type, setType] = useState("RECOMMENDATION_LETTER"),
    [file, setFile] = useState(null),
    [error, setError] = useState(null),
    [busy, setBusy] = useState(null),
    [decision, setDecision] = useState(null),
    [comment, setComment] = useState("");
  const inputRef = useRef();
  const rows = items(resource.data),
    editable = application.status === "AWAITING_RECOMMENDATION",
    reviewable = !["APPROVED", "REJECTED"].includes(application.status);
  useEffect(() => { if (resource.data) onDocumentsChange?.(items(resource.data)); }, [resource.data, onDocumentsChange]);
  useEffect(() => { onBusyChange?.(busy !== null); }, [busy, onBusyChange]);
  function selectFile(next) {
    setError(null);
    if (next && next.size === 0) {
      setError(new Error('Choose a non-empty PDF, JPG or PNG file.'));
      setFile(null);
      return;
    }
    if (next && next.size > maxFileBytes) {
      setError(new Error("Choose a file no larger than 5 MB."));
      setFile(null);
      return;
    }
    if (next && !/\.(pdf|png|jpe?g)$/i.test(next.name)) {
      setError(new Error("Choose a PDF, JPG or PNG file."));
      setFile(null);
      return;
    }
    setFile(next);
  }
  async function upload(e) {
    e.preventDefault();
    if (!file) return;
    setBusy("upload");
    setError(null);
    try {
      const body = new FormData();
      body.set("documentType", type);
      body.set("file", file);
      const uploaded = await request(path, {
        method: "POST",
        body
      });
      if (uploaded?.id) onDocumentsChange?.([...rows, uploaded]);
      setFile(null);
      if (inputRef.current) inputRef.current.value = "";
      refresh();
      notify("Document uploaded. A reviewer can now verify it.");
    } catch (e) {
      setError(e);
    } finally {
      setBusy(null);
    }
  }
  async function download(doc) {
    setBusy(doc.id);
    setError(null);
    try {
      const blob = await request(`${path}/${doc.id}/download`, {
        blob: true
      });
      downloadBlob(blob, doc.originalFilename);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(null);
    }
  }
  async function decide(e) {
    e.preventDefault();
    setBusy(decision.doc.id);
    setError(null);
    try {
      await request(`${path}/${decision.doc.id}/${decision.action}`, {
        method: "PATCH",
        body: decision.action === "verify" ? {
          comment: comment.trim() || null
        } : {
          reason: comment.trim()
        }
      });
      refresh();
      notify(decision.action === "verify" ? "Document verified." : "Document rejected with a reason.");
      setDecision(null);
      setComment("");
    } catch (e) {
      setError(e);
    } finally {
      setBusy(null);
    }
  }
  return <div className="documents-section">
      {owner && editable && <div className="document-upload-heading"><h3>{t('Upload documents')}</h3><p>{t('Start with your signed recommendation letter. You can add supporting documents separately.')}</p></div>}
      <FormError error={error} />
      {t(owner && editable && <form onSubmit={upload} className="upload-box">
          <div className="upload-box-title">
            <UploadCloud size={23} />
            <div>
              <strong>{t("Add a supporting document")}</strong>
              <p>{t("PDF, JPG or PNG \xB7 Up to 5 MB each")}</p>
            </div>
          </div>
          <div className="form-grid">
            <Select label={t("Document type")} value={type} onChange={e => setType(e.target.value)}>
              {documentTypes.map(t => <option key={t} value={t}>
                  {humanize(t)}
                </option>)}
            </Select>
            <label className="field">
              <span>{t("Select a file *")}</span>
              <input className="file-input" type="file" ref={inputRef} accept=".pdf,.jpg,.jpeg,.png" required onChange={e => selectFile(e.target.files[0])} />
            </label>
          </div>
          <div className="upload-footer">
            <span>
              {t(file ? `${file.name} · ${(file.size / 1024).toFixed(0)} KB` : "A verified recommendation letter is required before submission.")}
            </span>
            <Button type="submit" icon={UploadCloud} disabled={!file} busy={busy === "upload"}>{t("Upload document")}</Button>
          </div>
        </form>)}
      {t(canVerify && editable && <div className="info-note">
          <FileCheck2 size={19} />
          <p>{t("Verify the recommendation letter before the applicant submits. Document review requires a current president appointment in this branch or a parent committee.")}</p>
        </div>)}
      <ResourceState resource={resource}>
        {t(rows.length ? <div className="document-list">
            {rows.map(doc => <article className="document-card" key={doc.id}>
                <div className="document-row">
                  <span className="document-icon">
                    <FileText size={24} />
                  </span>
                  <div className="document-name">
                    <strong>{humanize(doc.documentType)}</strong>
                    <span>
                      {doc.originalFilename} ·{t(" ")}
                      {(doc.sizeBytes / 1024).toFixed(0)}{t("KB \xB7 v")}{doc.documentVersion}
                    </span>
                    <small>{t("Uploaded ")}{date(doc.uploadedAt)}</small>
                  </div>
                  <Badge value={doc.status} />
                  <Button icon={Download} variant="ghost" busy={busy === doc.id} onClick={() => download(doc)}>{t("Download")}</Button>
                </div>
                {t(doc.reviewComment && <p className="document-comment">{t("Reviewer:")}{doc.reviewComment}
                  </p>)}
                {t(canVerify && reviewable && doc.documentVersion === Math.max(...rows.filter(d => d.documentType === doc.documentType).map(d => d.documentVersion)) && doc.status === "UPLOADED" && <div className="document-actions">
                      <Button icon={Check} variant="secondary" onClick={() => {
              setDecision({
                doc,
                action: "verify"
              });
              setComment("");
            }}>{t("Verify")}</Button>
                      <Button icon={X} variant="ghost" onClick={() => {
              setDecision({
                doc,
                action: "reject"
              });
              setComment("");
            }}>{t("Reject")}</Button>
                    </div>)}
                {t(decision?.doc.id === doc.id && <form className="document-decision" onSubmit={decide}>
                    <Field label={t(decision.action === "reject" ? "Reason for rejection" : "Review comment (optional)")} value={comment} onChange={e => setComment(e.target.value)} required={decision.action === "reject"} maxLength={2000} />
                    <div className="button-row">
                      <Button variant="secondary" onClick={() => setDecision(null)}>{t("Cancel")}</Button>
                      <Button type="submit" variant={decision.action === "reject" ? "danger" : "primary"} busy={busy === doc.id}>{t("Confirm")}{t(" ")}
                        {t(decision.action === "verify" ? "verification" : "rejection")}
                      </Button>
                    </div>
                  </form>)}
              </article>)}
          </div> : <Empty title={t("No supporting documents yet")} description={t(owner ? "Upload a recommendation letter to get your application moving." : "The applicant has not uploaded supporting documents.")} />)}
      </ResourceState>
    </div>;
}
