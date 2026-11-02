import React from "react";
import { createRoot } from "react-dom/client";
import { HashRouter } from "react-router";
import { AppProvider } from "./app/AppContext";
import App from "./app/App";
import { CurrentAccountProvider } from "./app/CurrentAccountContext";
import ErrorBoundary from "./app/ErrorBoundary";
import "./styles.css";
import "./portal.css";
import "./community.css";
import { CommunityAccessProvider } from "./features/community/CommunityAccess";

createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <ErrorBoundary>
      <HashRouter>
        <AppProvider>
          <CommunityAccessProvider><CurrentAccountProvider><App /></CurrentAccountProvider></CommunityAccessProvider>
        </AppProvider>
      </HashRouter>
    </ErrorBoundary>
  </React.StrictMode>,
);
