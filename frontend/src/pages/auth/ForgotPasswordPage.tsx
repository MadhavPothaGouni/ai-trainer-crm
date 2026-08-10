import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link } from "react-router-dom";
import * as authApi from "../../api/auth";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { TextField } from "../../components/ui/TextField";
import { AuthLayout } from "../../components/layout/AuthLayout";
import { applyServerErrors } from "../../lib/formErrors";
import { forgotPasswordSchema, type ForgotPasswordFormValues } from "../../lib/validation";

export default function ForgotPasswordPage() {
  const [formError, setFormError] = useState<string | null>(null);
  const [submitted, setSubmitted] = useState(false);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ForgotPasswordFormValues>({ resolver: zodResolver(forgotPasswordSchema) });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      // The backend deliberately always returns the same 200 here, whether or not the
      // email is registered, so it can't be used to enumerate accounts - `submitted`
      // reflects that: it just means "the request went through," not "the email exists."
      await authApi.forgotPassword(values);
      setSubmitted(true);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <AuthLayout
      title="Reset your password"
      subtitle="We'll email you a link to set a new one"
      footer={
        <Link to="/login" className="font-medium text-slate-900 hover:underline">
          Back to sign in
        </Link>
      }
    >
      {submitted ? (
        <Alert variant="success">
          If that email is registered, a reset link is on its way. Check your inbox.
        </Alert>
      ) : (
        <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
          {formError && <Alert variant="error">{formError}</Alert>}

          <TextField
            label="Email"
            type="email"
            autoComplete="email"
            error={errors.email?.message}
            {...register("email")}
          />

          <Button type="submit" isLoading={isSubmitting} className="w-full">
            Send reset link
          </Button>
        </form>
      )}
    </AuthLayout>
  );
}
