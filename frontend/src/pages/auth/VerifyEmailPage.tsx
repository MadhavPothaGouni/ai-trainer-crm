import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import * as authApi from "../../api/auth";
import { Alert } from "../../components/ui/Alert";
import { AuthLayout } from "../../components/layout/AuthLayout";
import { ApiError } from "../../lib/apiClient";

type Status = "verifying" | "success" | "error";

export default function VerifyEmailPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token");
  const [status, setStatus] = useState<Status>("verifying");
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!token) {
      setStatus("error");
      setMessage("This verification link is missing its token.");
      return;
    }

    let cancelled = false;
    authApi
      .verifyEmail({ token })
      .then(() => {
        if (!cancelled) setStatus("success");
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        setStatus("error");
        setMessage(error instanceof ApiError ? error.message : "Verification failed.");
      });

    return () => {
      cancelled = true;
    };
  }, [token]);

  return (
    <AuthLayout
      title="Verify your email"
      footer={
        <Link to="/login" className="font-medium text-slate-900 hover:underline">
          Back to sign in
        </Link>
      }
    >
      {status === "verifying" && <p className="text-sm text-slate-500">Verifying your email address...</p>}
      {status === "success" && <Alert variant="success">Your email has been verified. You can sign in now.</Alert>}
      {status === "error" && <Alert variant="error">{message ?? "This link is invalid or has expired."}</Alert>}
    </AuthLayout>
  );
}
