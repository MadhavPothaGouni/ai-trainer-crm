import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { deleteClientFeedback, getClientFeedback, updateClientFeedback } from "../../api/clientFeedback";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, updateClientFeedbackSchema, type UpdateClientFeedbackFormValues } from "../../lib/validation";
import { CLIENT_FEEDBACK_RELATED_TYPES, type ClientFeedbackDto, type ClientFeedbackRelatedType } from "../../types/api";

function toDatetimeLocalValue(iso: string): string {
  const date = new Date(iso);
  const offsetMs = date.getTimezoneOffset() * 60000;
  return new Date(date.getTime() - offsetMs).toISOString().slice(0, 16);
}

export default function ClientFeedbackDetailPage() {
  const { clientFeedbackId } = useParams<{ clientFeedbackId: string }>();
  const navigate = useNavigate();
  const [feedback, setFeedback] = useState<ClientFeedbackDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<UpdateClientFeedbackFormValues>({ resolver: zodResolver(updateClientFeedbackSchema) });

  useEffect(() => {
    if (!clientFeedbackId) return;
    let cancelled = false;
    getClientFeedback(clientFeedbackId)
      .then((data) => {
        if (cancelled) return;
        setFeedback(data);
        reset({
          npsScore: String(data.npsScore),
          relatedType: data.relatedType,
          submittedAt: toDatetimeLocalValue(data.submittedAt),
          comments: data.comments ?? "",
        });
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this feedback record.");
      });
    return () => {
      cancelled = true;
    };
  }, [clientFeedbackId, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!clientFeedbackId) return;
    setFormError(null);
    try {
      const updated = await updateClientFeedback(clientFeedbackId, {
        npsScore: Number(values.npsScore),
        relatedType: values.relatedType as ClientFeedbackRelatedType,
        submittedAt: new Date(values.submittedAt).toISOString(),
        comments: blankToUndefined(values.comments),
      });
      setFeedback(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function handleDelete() {
    if (!clientFeedbackId || !window.confirm("Delete this feedback record? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      await deleteClientFeedback(clientFeedbackId);
      navigate("/client-feedback");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this feedback record.");
      setIsDeleting(false);
    }
  }

  if (error && !feedback) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!feedback) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/client-feedback" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Client Feedback
          </Link>
          <h1 className="mt-1 text-2xl font-semibold text-slate-900">{feedback.npsScore} / 10</h1>
          <p className="mt-1 text-sm text-slate-500">
            {feedback.relatedType} &middot; {new Date(feedback.submittedAt).toLocaleString()}
          </p>
        </div>
        <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
          Delete
        </Button>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      {feedback.comments && (
        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Comments</h2>
          <p className="mt-2 text-sm text-slate-900">{feedback.comments}</p>
        </div>
      )}

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Edit feedback</h2>

        {formError && <Alert variant="error">{formError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Score (0-10)" type="number" min={0} max={10} error={errors.npsScore?.message} {...register("npsScore")} />
          <Select
            label="About"
            options={CLIENT_FEEDBACK_RELATED_TYPES.map((type) => ({ value: type, label: type }))}
            error={errors.relatedType?.message}
            {...register("relatedType")}
          />
        </div>

        <TextField label="Submitted at" type="datetime-local" error={errors.submittedAt?.message} {...register("submittedAt")} />

        <TextArea label="Comments" error={errors.comments?.message} {...register("comments")} />

        <div className="flex justify-end">
          <Button type="submit" isLoading={isSubmitting}>
            Save changes
          </Button>
        </div>
      </form>
    </div>
  );
}
