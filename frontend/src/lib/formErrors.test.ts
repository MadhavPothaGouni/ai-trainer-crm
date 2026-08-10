import { describe, expect, it, vi } from "vitest";
import { ApiError } from "./apiClient";
import { applyServerErrors } from "./formErrors";

interface DummyForm {
  email: string;
  password: string;
}

describe("applyServerErrors", () => {
  it("returns a generic message for a non-ApiError", () => {
    const setError = vi.fn();
    const message = applyServerErrors(new Error("boom"), setError);
    expect(message).toBe("Something unexpected went wrong. Please try again.");
    expect(setError).not.toHaveBeenCalled();
  });

  it("maps each field error onto the form and returns a summary message", () => {
    const setError = vi.fn();
    const error = new ApiError(
      {
        message: "Validation failed",
        errorCode: "VALIDATION_FAILED",
        fieldErrors: [
          { field: "email", message: "Enter a valid email address", rejectedValue: "bad" },
          { field: "password", message: "Password is required", rejectedValue: "" },
        ],
      },
      400,
    );

    const message = applyServerErrors<DummyForm>(error, setError);

    expect(message).toBe("Please fix the highlighted fields.");
    expect(setError).toHaveBeenCalledTimes(2);
    expect(setError).toHaveBeenCalledWith("email", { type: "server", message: "Enter a valid email address" });
    expect(setError).toHaveBeenCalledWith("password", { type: "server", message: "Password is required" });
  });

  it("falls back to the error's own message when there are no field errors", () => {
    const setError = vi.fn();
    const error = new ApiError({ message: "Invalid email or password", errorCode: "INVALID_CREDENTIALS" }, 401);

    const message = applyServerErrors<DummyForm>(error, setError);

    expect(message).toBe("Invalid email or password");
    expect(setError).not.toHaveBeenCalled();
  });
});
