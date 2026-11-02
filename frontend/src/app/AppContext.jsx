import { useCallback, useContext, useEffect, useRef, useState } from "react";
import { Context } from "./context";
import { apiRequest } from "../lib/api";
import { clearCsrf } from "../lib/api";
import { authPaths } from "../lib/authConfig";


export function AppProvider({ children }) {
  const [config, setConfig] = useState({ mode: "live", base: import.meta.env.VITE_API_BASE_URL || "/api" });
  const [user, setUser] = useState(null);
  const [authLoading, setAuthLoading] = useState(true);
  const [authError, setAuthError] = useState(null);
  const [loggingOut, setLoggingOut] = useState(false);
  const authVersion = useRef(0);
  const [revision, setRevision] = useState(0),
    [toast, setToast] = useState(null);
  const timer = useRef();
  const notify = useCallback((message, type = "success") => {
    clearTimeout(timer.current);
    setToast({ message, type });
    timer.current = setTimeout(() => setToast(null), 5000);
  }, []);
  useEffect(() => () => clearTimeout(timer.current), []);
  const request = useCallback((path, options) => apiRequest(config.base, path, options), [config.base]);
  const loadUser = useCallback(async () => {
    const version = ++authVersion.current;
    setAuthLoading(true); setAuthError(null);
    try {
      const data = await request(authPaths.user, { notifyUnauthorized: false });
      const current = data?.data || data;
      if (!current?.username && !current?.fullName) throw new Error("Invalid current-user response.");
      const access = await request('/auth/access', { notifyUnauthorized: false });
      if (Number(access?.userId) !== Number(current.id)) throw new Error('Your account access could not be confirmed. Sign in again.');
      if (version !== authVersion.current) return null;
      const identified = { ...current, access };
      setUser(identified); return identified;
    } catch (error) {
      if (version === authVersion.current) {
        setUser(null); if (error.status !== 401) setAuthError(error);
      }
    }
    finally { if (version === authVersion.current) setAuthLoading(false); }
  }, [request]);
  const accessKey = JSON.stringify(user?.access || null);
  const previousAccessKey = useRef(accessKey);
  useEffect(() => {
    if (previousAccessKey.current !== accessKey) {
      previousAccessKey.current = accessKey; setRevision(value => value + 1);
    }
  }, [accessKey]);
  const accessRefresh = useRef(null);
  const refreshAccess = useCallback(async () => {
    if (!user || accessRefresh.current) return;
    accessRefresh.current = request('/auth/access', { notifyUnauthorized: true }).then(access => {
      // Refresh resources only when the server changes access; avoid a 403/refetch loop.
      setUser(previous => {
        if (!previous || Number(previous.id) !== Number(access?.userId) || JSON.stringify(previous.access) === JSON.stringify(access)) return previous;
        return { ...previous, access };
      });
    }).catch(() => { /* A failed refresh never invents a new permission grant. */ }).finally(() => { accessRefresh.current = null; });
    return accessRefresh.current;
  }, [request, user?.id]);
  useEffect(() => {
    window.addEventListener('focus', refreshAccess);
    window.addEventListener('legalcell:access-denied', refreshAccess);
    return () => { window.removeEventListener('focus', refreshAccess); window.removeEventListener('legalcell:access-denied', refreshAccess); };
  }, [refreshAccess]);
  useEffect(() => { loadUser(); }, [loadUser]);
  const endSession = useCallback(() => {
    // Invalidate pending identity reads before clearing every signed-in view.
    ++authVersion.current;
    setUser(null); setAuthLoading(false); setAuthError(null);
    clearCsrf(); setRevision(x => x + 1);
  }, []);
  useEffect(() => {
    const expire = () => endSession();
    window.addEventListener("legalcell:unauthorized", expire);
    return () => window.removeEventListener("legalcell:unauthorized", expire);
  }, [endSession]);
  const login = async (username, password) => {
    await request(authPaths.login, { method: "POST", body: { [import.meta.env.VITE_AUTH_USERNAME_FIELD || "username"]: username, password } });
    clearCsrf();
    const current = await loadUser();
    if (!current) throw new Error("Your session could not be established. Check the current-user endpoint.");
  };
  const logout = async () => {
    setLoggingOut(true);
    endSession();
    try { await request(authPaths.logout, { method: "POST" }); }
    finally { clearCsrf(); setLoggingOut(false); }
  };
  const refresh = useCallback(() => setRevision((x) => x + 1), []);
  const changeConfig = useCallback((next) => {
    clearCsrf();
    setConfig(next);
    setRevision((x) => x + 1);
  }, []);
  return (
    <Context.Provider
      value={{
        user, authLoading, authError, loggingOut, login, logout, loadUser, endSession, refreshAccess,
        config,
        changeConfig,
        request,
        revision,
        refresh,
        notify,
        toast,
        setToast,
      }}
    >
      {children}
    </Context.Provider>
  );
}
export const useApp = () => useContext(Context);
export function useResource(path, params = {}, enabled = true) {
  const { request, revision } = useApp();
  const [state, setState] = useState({
    data: null,
    loading: true,
    error: null,
  });
  const serialized = JSON.stringify(params);
  useEffect(() => {
    if (!enabled) {
      setState({ data: null, loading: false, error: null });
      return;
    }
    const controller = new AbortController();
    setState({ data: null, loading: true, error: null });
    request(path, { params: JSON.parse(serialized), signal: controller.signal })
      .then((data) => {
        if (!controller.signal.aborted)
          setState({ data, loading: false, error: null });
      })
      .catch((error) => {
        if (!controller.signal.aborted)
          setState({ data: null, loading: false, error });
      });
    return () => controller.abort();
  }, [request, path, serialized, revision, enabled]);
  return state;
}
