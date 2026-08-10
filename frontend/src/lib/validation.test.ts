import { describe, expect, it } from "vitest";
import { loginSchema, toOptionalNumber } from "./validation";

describe("toOptionalNumber", () => {
  it("returns undefined for an empty string", () => {
    expect(toOptionalNumber("")).toBeUndefined();
  });

  it("returns undefined for undefined", () => {
    expect(toOptionalNumber(undefined)).toBeUndefined();
  });

  it("parses a numeric string", () => {
    expect(toOptionalNumber("42.5")).toBe(42.5);
  });
});

describe("loginSchema", () => {
  it("rejects an invalid email", () => {
    const result = loginSchema.safeParse({ email: "not-an-email", password: "x" });
    expect(result.success).toBe(false);
  });

  it("accepts a valid login payload", () => {
    const result = loginSchema.safeParse({ email: "user@example.com", password: "x" });
    expect(result.success).toBe(true);
  });
});
