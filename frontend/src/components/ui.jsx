import { t, useLocale, getLocale } from "../lib/i18n";
import { useEffect, useId, useRef, useState } from "react";
import { Link } from "react-router";
import { AlertCircle, ArrowLeft, ArrowRight, Check, CheckCircle2, ChevronDown, FileSearch, Loader2, Search, X } from "lucide-react";
import { humanize, initials } from "../lib/format";
import { useApp } from "../app/AppContext";
import { useCurrentAccount } from "../app/CurrentAccountContext";
import { pictureUrl } from "../lib/profilePictures";
export function Button({
  children,
  variant = "primary",
  icon: Icon,
  busy = false,
  className = "",
  ...props
}) {
  useLocale();
  return <button type="button" className={`btn btn-${variant} ${className}`} {...props} disabled={props.disabled || busy}>
      {t(busy ? <Loader2 size={16} className="spin" /> : Icon ? <Icon size={16} /> : null)}
      {children}
    </button>;
}
export function IconButton({
  icon: Icon,
  label,
  ...props
}) {
  useLocale();
  return <button type="button" className="icon-btn" title={t(label)} aria-label={t(label)} {...props}>
      <Icon size={18} />
    </button>;
}
export function Badge({
  value,
  children
}) {
  useLocale();
  return <span className={`badge badge-${String(value || "").toLowerCase().replaceAll("_", "-")}`}>
      <span className="badge-dot" />
      {t(children || humanize(value))}
    </span>;
}
export function Avatar({ name, size = "normal", index = 0, user: account, src }) {
  useLocale(); const {config} = useApp(); const current = useCurrentAccount();
  const profile = account && Number(account.id) === Number(current.data?.id) ? current.data : account;
  const image = src || pictureUrl(config.base, profile);
  const [failed, setFailed] = useState(false);
  useEffect(() => setFailed(false), [image]);
  return <span className={`avatar avatar-${index % 5} avatar-${size}`} aria-hidden="true">
    {image && !failed ? <img src={image} alt="" loading="lazy" onError={() => setFailed(true)}/> : initials(name)}
  </span>;
}
export function Field({
  label,
  hint,
  children,
  className = "",
  ...props
}) {
  useLocale();
  const id = useId();
  return <div className={`field ${className}`}>
      <label htmlFor={id}>
        {t(label)}
        {t(props.required && <b className="required"> *</b>)}
      </label>
      {t(children ? children : <input id={id} aria-describedby={hint ? id + "-hint" : undefined} {...props} className="input" />)}
      {t(hint && <small id={id + "-hint"}>{t(hint)}</small>)}
    </div>;
}
export function Select({
  label,
  hint,
  children,
  className = "",
  ...props
}) {
  useLocale();
  const id = useId();
  return <div className={`field ${className}`}>
      {t(label && <label htmlFor={id}>
          {t(label)}
          {t(props.required && <b className="required"> *</b>)}
        </label>)}
      <span className="select-wrap">
        <select id={id} aria-describedby={hint ? id + "-hint" : undefined} {...props}>
          {children}
        </select>
        <ChevronDown size={15} />
      </span>
      {t(hint && <small id={id + "-hint"}>{t(hint)}</small>)}
    </div>;
}
export function FormError({
  error
}) {
  useLocale();
  return error ? <div className="inline-error" role="alert">
      <AlertCircle size={17} />
      <span>{t(error.message || error)}</span>
    </div> : null;
}
export function Modal({
  title,
  subtitle,
  children,
  onClose,
  wide = false
}) {
  useLocale();
  const ref = useRef(),
    titleId = useId();
  useEffect(() => {
    const dialog = ref.current;
    const previous = document.activeElement;
    dialog.showModal();
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = overflow;
      dialog.close();
      previous?.focus?.();
    };
  }, []);
  return <dialog ref={ref} className={`modal ${wide ? "modal-wide" : ""}`} onCancel={e => {
    e.preventDefault();
    onClose();
  }} onClick={e => {
    if (e.target === ref.current) {
      const r = ref.current.getBoundingClientRect();
      if (e.clientX < r.left || e.clientX > r.right || e.clientY < r.top || e.clientY > r.bottom) onClose();
    }
  }} aria-labelledby={titleId}>
      <div className="modal-header">
        <div>
          <h2 id={titleId}>{t(title)}</h2>
          {t(subtitle && <p>{t(subtitle)}</p>)}
        </div>
        <IconButton label={t("Close dialog")} icon={X} onClick={onClose} />
      </div>
      <div className="modal-body">{children}</div>
    </dialog>;
}
export function Empty({
  title = "No records found",
  description = "Try another search or add your first record.",
  action
}) {
  useLocale();
  return <div className="empty">
      <span className="empty-icon">
        <FileSearch size={25} />
      </span>
      <h3>{t(title)}</h3>
      <p>{t(description)}</p>
      {action}
    </div>;
}
export function ResourceState({
  resource,
  children
}) {
  useLocale();
  const {
    refresh
  } = useApp();
  if (resource.loading) return <div className="skeleton-group" aria-label={t("Loading records")} role="status">
        <div className="skeleton skeleton-heading" />
        {[1, 2, 3, 4].map(i => <div key={i} className="skeleton" />)}
        <span className="sr-only">{t("Loading\u2026")}</span>
      </div>;
  if (resource.error) return <div className="resource-error">
        <AlertCircle size={28} />
        <h3>{t("We couldn\u2019t load this view")}</h3>
        <p>{t(resource.error.message)}</p>
        <div className="button-row">
          <Button variant="secondary" onClick={refresh}>{t("Try again")}</Button>
          <Link className="text-link" to="/settings">{t("Connection settings")}<ArrowRight size={14} />
          </Link>
        </div>
      </div>;
  return children;
}
export function SearchInput({
  value,
  onChange,
  placeholder = "Search…",
  ...props
}) {
  useLocale();
  return <div className="search-input">
      <Search size={17} />
      <input value={value} onChange={e => onChange(e.target.value)} placeholder={t(placeholder)} aria-label={t(props["aria-label"] || placeholder)} {...props} />
      {t(value && <button type="button" aria-label={t("Clear search")} onClick={() => onChange("")}>
          <X size={14} />
        </button>)}
    </div>;
}
export function PageHeading({
  eyebrow,
  title,
  description,
  children
}) {
  useLocale();
  return <div className="page-heading">
      <div>
        {t(eyebrow && <span className="eyebrow">{t(eyebrow)}</span>)}
        <h1>{t(title)}</h1>
        <p>{t(description)}</p>
      </div>
      <div className="heading-actions">{children}</div>
    </div>;
}
export function Panel({
  title,
  subtitle,
  action,
  children,
  className = ""
}) {
  useLocale();
  return <section className={`panel ${className}`}>
      <div className="panel-heading">
        <div>
          <h2>{t(title)}</h2>
          {t(subtitle && <p>{t(subtitle)}</p>)}
        </div>
        {action}
      </div>
      {children}
    </section>;
}
export function Pagination({
  page,
  size = 10,
  total,
  hasNext,
  onChange
}) {
  useLocale();
  return <div className="pagination">
      <span>
        {t(total != null ? `${total ? page * size + 1 : 0}–${Math.min((page + 1) * size, total)} of ${total} records` : `Page ${page + 1}`)}
      </span>
      <div>
        <Button variant="ghost" icon={ArrowLeft} disabled={page === 0} onClick={() => onChange(page - 1)}>{t("Previous")}</Button>
        <span className="page-number">{page + 1}</span>
        <Button variant="ghost" disabled={hasNext != null ? !hasNext : (page + 1) * size >= total} onClick={() => onChange(page + 1)}>{t("Next")}<ArrowRight size={15} />
        </Button>
      </div>
    </div>;
}
export function Toast() {
  useLocale();
  const {
    toast,
    setToast
  } = useApp();
  return toast ? <div className={`toast toast-${toast.type}`} role="status">
      {t(toast.type === "error" ? <AlertCircle size={20} /> : <CheckCircle2 size={20} />)}
      <span>{t(toast.message)}</span>
      <IconButton icon={X} label={t("Dismiss notification")} onClick={() => setToast(null)} />
    </div> : null;
}
export function Timeline({
  entries
}) {
  useLocale();
  return entries.length ? <ol className="timeline">
      {entries.map((entry, i) => <li key={entry.id || i}>
          <span className="timeline-dot">
            <Check size={11} />
          </span>
          <div>
            <strong>{humanize(entry.action || entry.status)}</strong>
            <p>
              {t(entry.note || entry.reason || entry.comment || entry.decisionComment || entry.actorUsername || entry.actorName || entry.performedByName || "Workflow event")}
            </p>
            <small>
              {t(entry.eventAt || entry.createdAt || entry.occurredAt || entry.assignedAt ? new Date(entry.eventAt || entry.createdAt || entry.occurredAt || entry.assignedAt).toLocaleString(getLocale()) : "")}
            </small>
          </div>
        </li>)}
    </ol> : <p className="muted">{t("No history recorded yet.")}</p>;
}
