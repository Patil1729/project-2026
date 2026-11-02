import { t, useLocale } from "../../lib/i18n";
import { useState } from 'react';
import { useApp } from '../../app/AppContext';
import { normalizeBase } from '../../lib/api';
import { Button, Field, FormError, PageHeading, Panel } from '../../components/ui';
export default function Settings() {
  useLocale();
  const {
    config,
    changeConfig
  } = useApp();
  const [base, setBase] = useState(config.base),
    [error, setError] = useState(null);
  return <><PageHeading eyebrow={t("WORKSPACE")} title={t("Settings & connection")} description={t("Manage your backend connection and appearance.")} />
    <Panel title={t("Backend connection")}><form onSubmit={e => {
        e.preventDefault();
        try {
          changeConfig({
            mode: 'live',
            base: normalizeBase(base)
          });
        } catch (e) {
          setError(e);
        }
      }}>
      <Field label={t("API base URL")} value={base} onChange={e => setBase(e.target.value)} required />
      <FormError error={error} /><Button type="submit">{t("Save changes")}</Button>
    </form></Panel></>;
}
