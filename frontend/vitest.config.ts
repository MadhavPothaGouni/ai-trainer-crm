import { defineConfig, mergeConfig } from "vitest/config";
import viteConfig from "./vite.config.ts";

// Reuses vite.config.ts (React plugin, Tailwind) rather than duplicating it,
// then layers on the jsdom environment + setup file every test needs.
export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: "jsdom",
      setupFiles: ["./src/test/setup.ts"],
      css: true,
      exclude: ["node_modules/**", "dist/**"],
    },
  }),
);
