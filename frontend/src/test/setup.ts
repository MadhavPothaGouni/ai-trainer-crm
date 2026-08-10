import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

// vitest.config.ts doesn't enable test.globals, so RTL's cleanup doesn't
// auto-register itself (it only does that when it detects a global
// `afterEach`) - do it explicitly instead, once, for every test file.
afterEach(() => {
  cleanup();
});
