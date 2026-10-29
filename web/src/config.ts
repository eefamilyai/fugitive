// Runtime configuration, loaded from /config.json so one build runs on any server.
export interface AppConfig {
  firebase: Record<string, string> | null;
  emulatorHost: string;
  privacyPolicyUrl: string;
  mapTilerKey: string;
}

let config: AppConfig = { firebase: null, emulatorHost: "", privacyPolicyUrl: "", mapTilerKey: "" };

export async function loadConfig(): Promise<AppConfig> {
  try {
    const res = await fetch(`${import.meta.env.BASE_URL}config.json`, { cache: "no-store" });
    if (res.ok) {
      const c = await res.json();
      config = {
        firebase: c.firebase && c.firebase.apiKey && !String(c.firebase.apiKey).startsWith("AIza...") ? c.firebase : null,
        emulatorHost: c.emulatorHost ?? "",
        privacyPolicyUrl: c.privacyPolicyUrl ?? "",
        mapTilerKey: c.mapTilerKey ?? "",
      };
      if (!config.firebase && config.emulatorHost) {
        config.firebase = {
          apiKey: "demo-api-key", projectId: "demo-fugitive", appId: "1:000000000000:web:0000000000000000",
          databaseURL: `http://${config.emulatorHost}:9000?ns=demo-fugitive-default-rtdb`,
        };
      }
    }
  } catch { /* offline or missing config: online play disabled */ }
  return config;
}

export const appConfig = () => config;
