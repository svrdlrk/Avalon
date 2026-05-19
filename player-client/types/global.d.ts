declare interface ImportMetaEnv {
    readonly VITE_AVALON_SERVER_URL?: string;
    readonly VITE_AVALON_LAUNCHER_CONTROL_URL?: string;
}

declare interface ImportMeta {
    readonly env: ImportMetaEnv;
}

declare const process: {
    cwd(): string;
};
