/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_PAGES?: string;
  readonly VITE_BASE?: string;
  readonly VITE_API_MODE?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
