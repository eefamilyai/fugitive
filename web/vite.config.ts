import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: { host: true, port: 5173 },
  build: {
    target: "es2020",
    sourcemap: false,
    chunkSizeWarningLimit: 1200,
    rollupOptions: {
      output: {
        // Separate long-lived vendor chunks so app updates don't re-download the map/firebase libraries.
        manualChunks(id) {
          if (id.includes("maplibre-gl")) return "maplibre";
          if (id.includes("firebase") || id.includes("@firebase")) return "firebase";
          if (id.includes("/src/engine/")) return "engine";
          if (id.includes("node_modules/react")) return "react";
        },
      },
    },
  },
});
