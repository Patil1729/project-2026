import { t, useLocale } from "../lib/i18n";
import { Component } from "react";
export default class ErrorBoundary extends Component {
  state = {
    failed: false
  };
  static getDerivedStateFromError() {
    return {
      failed: true
    };
  }
  render() {
    return this.state.failed ? <main style={{
      maxWidth: 650,
      padding: "80px 24px",
      margin: "auto"
    }}>
        <h1>{t("The workspace needs a fresh start.")}</h1>
        <p style={{
        margin: "15px 0 25px",
        lineHeight: 1.8,
        color: "#728168"
      }}>{t("Reload to reopen your workspace. Saved records stay available.")}</p>
        <button className="btn btn-primary" onClick={() => window.location.reload()}>{t("Reload workspace")}</button>
      </main> : this.props.children;
  }
}
