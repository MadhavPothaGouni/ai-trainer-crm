import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useSearchParams } from "react-router-dom";
import * as authApi from "../../api/auth";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { TextField } from "../../components/ui/TextField";
import { AuthLayout } from "../../components/layout/AuthLayout";
import { applyServerErrors } from "../../lib/formErrors";
import { resetPasswordSchema, type ResetPasswordFormValues } from "../../lib/validation";

export default function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token");
  const [formError, setFormError] = useState<string | null>(null);
  const [succeeded, setSucceeded] = useState(false);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ResetPasswordFormValues>({ resolver: zodResolver(resetPasswordSchema) });

  const onSubmit = handleSubmit(async (values) => {
    if (!token) {
      setFormError("This reset link is missing its token. Request a new one.");
      return;
    }
    setFormError(null);
    try {
      await authApi.resetPassword({ token, newPassword: values.newPassword });
      setSucceeded(true);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <AuthLayout title="Set a new password">
      {succeeded ? (
        <div className="flex flex-col gap-4">
          <Alert variant="success">
            Your password has been reset. Every other device has been signed out for safety.
          </Alert>
          <Link to="/login">
            <Button className="w-full">Sign in</Button>
          </Link>
        </div>
      ) : (
        <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
          {!token && (
            <Alert variant="error">
              This link is missing a reset token.{" "}
              <Link to="/forgot-password" className="font-medium underline">
                Request a new one
              </Link>
              .
            </Alert>
          )}
          {formError && <Alert variant="error">{formError}</Alert>}

          <TextField
            label="New password"
            type="password"
            autoComplete="new-password"
            error={errors.newPassword?.message}
            {...register("newPassword")}
          />
          <TextField
            label="Confirm new password"
            type="password"
            autoComplete="new-password"
            error={errors.confirmPassword?.message}
            {...register("confirmPassword")}
          />

          <Button type="submit" isLoading={isSubmitting} className="w-full">
            Reset password
          </Button>
        </form>
      )}
    </AuthLayout>
  );
}
