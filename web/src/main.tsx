import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import "./styles.css";
import { loadConfig } from "./config";
import { App } from "./App";

loadConfig().then(() => {
  createRoot(document.getElementById("root")!).render(
    <StrictMode><BrowserRouter><App /></BrowserRouter></StrictMode>,
  );
});
