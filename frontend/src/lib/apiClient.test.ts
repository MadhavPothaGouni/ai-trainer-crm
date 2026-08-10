import { describe, expect, it } from "vitest";
import { ApiError, unwrap } from "./apiClient";
import type { ApiResponse } from "../types/api";

describe("unwrap", () => {
  it("resolves with the envelope's data on success", async () => {
    const response = { data: { success: true, data: { id: "1" }, message: null, timestamp: "now" } as ApiResponse<{ id: string }> };

    const result = await unwrap(Promise.resolve(response));

    expect(result).toEqual({ id: "1" });
  });

  it("wraps a structured backend error response in an ApiError", async () => {
    const axiosLikeError = {
      isAxiosError: true,
      response: {
        status: 404,
        data: {
          success: false,
          errorCode: "RESOURCE_NOT_FOUND",
          message: "Account not found",
          status: 404,
          path: "/api/v1/accounts/x",
          timestamp: "now",
          fieldErrors: null,
          traceId: "abc",
        },
      },
    };

    await expect(unwrap(Promise.reject(axiosLikeError))).rejects.toMatchObject({
      message: "Account not found",
      errorCode: "RESOURCE_NOT_FOUND",
      status: 404,
    });
  });

  it("falls back to a connectivity message when the backend never responded", async () => {
    const axiosLikeError = { isAxiosError: true, response: undefined };

    await expect(unwrap(Promise.reject(axiosLikeError))).rejects.toMatchObject({
      message: "Could not reach the server. Check your connection and try again.",
      status: null,
    });
  });

  it("wraps a completely unexpected throw in a generic ApiError", async () => {
    await expect(unwrap(Promise.reject("not even an error object"))).rejects.toBeInstanceOf(ApiError);
  });
});
