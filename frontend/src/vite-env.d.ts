/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Absolute backend origin for production builds where the frontend isn't served behind the same proxy as the API (e.g. "https://api.example.com"). Leave unset in dev - the Vite proxy in vite.config.ts handles /api/* locally. */
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
