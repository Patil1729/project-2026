import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  return {
    plugins: [react()],
    server: {
      allowedHosts: (env.VITE_ALLOWED_HOSTS || "dispose-mumbling-revocable.ngrok-free.dev")
        .split(",")
        .map((host) => host.trim())
        .filter(Boolean),
      proxy: {
        "/api": {
          target: env.VITE_API_PROXY_TARGET || "http://localhost:8080",
          changeOrigin: true,
        },
      },
    },
    build: { outDir: "dist", sourcemap: false },
  };
});
