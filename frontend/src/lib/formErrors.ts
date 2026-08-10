import type { FieldValues, UseFormSetError, Path } from "react-hook-form";
import { ApiError } from "./apiClient";

/**
 * Maps ApiError.fieldErrors (from a VALIDATION_FAILED response) onto the matching
 * react-hook-form fields, and returns a leftover top-level message for anything that
 * isn't a per-field error (wrong-password, account-locked, duplicate-email, network, ...).
 * Call this in the form's submit catch block; render the return value in an <Alert>.
 */
export function applyServerErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
): string {
  if (!(error instanceof ApiError)) {
    return "Something unexpected went wrong. Please try again.";
  }

  if (error.fieldErrors && error.fieldErrors.length > 0) {
    for (const fieldError of error.fieldErrors) {
      setError(fieldError.field as Path<T>, { type: "server", message: fieldError.message });
    }
    return "Please fix the highlighted fields.";
  }

  return error.message;
}
