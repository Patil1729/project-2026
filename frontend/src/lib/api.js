import { authPaths } from "./authConfig.js";
export class ApiError extends Error {
  constructor(message, status = 0, fields = {}, metadata = {}) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.fields = fields;
    this.code = metadata.code || null;
    this.path = metadata.path || null;
  }
}
export function normalizeBase(value) {
  const base = String(value || "/api")
    .trim()
    .replace(/\/+$/, "");
  if (base.startsWith("/") && !base.startsWith("//")) return base;
  const url = new URL(base);
  if (
    !["http:", "https:"].includes(url.protocol) ||
    url.username ||
    url.password ||
    url.search ||
    url.hash
  )
    throw new Error(
      "Use an HTTP(S) API address without credentials, query parameters or fragments.",
    );
  return base;
}
const csrfRequests = new Map();
export function clearCsrf() { csrfRequests.clear(); }
export async function csrfToken(base) {
  const key = normalizeBase(base);
  if (!csrfRequests.has(key)) {
    const pending = apiRequest(base, authPaths.csrf).then(data => {
      if (!data?.token || !data?.headerName) throw new ApiError("Invalid CSRF response.");
      return data;
    }).catch(error => { csrfRequests.delete(key); throw error; });
    csrfRequests.set(key, pending);
  }
  return csrfRequests.get(key);
}
export async function apiRequest(
  base,
  path,
  { method = "GET", body, params, signal, blob = false, csrfRetry = true, notifyUnauthorized = true } = {},
) {
  const query = new URLSearchParams();
  Object.entries(params || {}).forEach(([key, value]) => {
    if (value !== "" && value != null) query.set(key, String(value));
  });
  const url = normalizeBase(base) + path + (query.size ? "?" + query : "");
  const controller = new AbortController();
  const abort = () => controller.abort();
  signal?.addEventListener("abort", abort, { once: true });
  if (signal?.aborted) controller.abort();
  const timer = setTimeout(() => controller.abort(), 15000);
  try {
    const csrf = !["GET", "HEAD", "OPTIONS"].includes(method.toUpperCase())
      ? await csrfToken(base) : null;
    const response = await fetch(url, {
      method,
      credentials: "include",
      ...(path === authPaths.csrf ? { cache: "no-store" } : {}),
      signal: controller.signal,
      headers: {
        ...(csrf ? { [csrf.headerName]: csrf.token } : {}),
        Accept: blob ? "*/*" : "application/json",
        ...(body && !(body instanceof FormData)
          ? { "Content-Type": "application/json" }
          : {}),
      },
      body:
        body instanceof FormData
          ? body
          : body === undefined
            ? undefined
            : JSON.stringify(body),
    });
    if (!response.ok) {
      const data = await response.json().catch(() => ({}));
      const errorCode = data.code || data.error || "";
      const csrfFailure = ["CSRF_TOKEN_MISSING", "CSRF_TOKEN_INVALID", "CSRF_INVALID", "INVALID_CSRF_TOKEN", "MISSING_CSRF_TOKEN"].includes(errorCode)
        || ((!errorCode || errorCode === "Forbidden") && /csrf\s+token/i.test(data.message || ""));
      if (response.status === 403 && csrf && csrfRetry && csrfFailure) {
        clearCsrf();
        return apiRequest(base, path, { method, body, params, signal, blob, notifyUnauthorized, csrfRetry: false });
      }
      if (response.status === 401 && notifyUnauthorized && path !== authPaths.login && typeof window !== "undefined") {
        window.dispatchEvent(new Event("legalcell:unauthorized"));
      }
      if (response.status === 403 && !csrfFailure && path !== '/auth/access' && typeof window !== 'undefined') {
        window.dispatchEvent(new Event('legalcell:access-denied'));
      }
      const fields = data.fieldErrors || data.errors || {};
      const details = Array.isArray(fields)
        ? fields
            .map((x) =>
              typeof x === "string" ? x : x.defaultMessage || x.message,
            )
            .join(" · ")
        : typeof fields === "object" && fields
          ? Object.entries(fields)
              .map(([k, v]) => `${k}: ${v}`)
              .join(" · ")
          : String(fields || "");
      throw new ApiError(
        [data.message || `Request failed (${response.status})`, details]
          .filter(Boolean)
          .join(" — "),
        response.status,
        fields,
        { code: errorCode, path: data.path },
      );
    }
    if (response.status === 204) return null;
    if (blob) return response.blob();
    if (!response.headers.get("content-type")?.includes("application/json"))
      throw new ApiError(
        "The server returned a web page instead of JSON. Check the API address and start your Spring Boot backend.",
      );
    return await response.json();
  } catch (error) {
    if (error instanceof ApiError) throw error;
    if (error.name === "AbortError")
      throw new ApiError(
        signal?.aborted
          ? "Request cancelled."
          : "The request timed out. Check that your backend is running.",
      );
    throw new ApiError(
      "Unable to reach the API. Check the address, backend availability and CORS configuration.",
    );
  } finally {
    clearTimeout(timer);
    signal?.removeEventListener("abort", abort);
  }
}
