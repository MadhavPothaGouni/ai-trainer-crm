import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { getContact } from "../../api/contacts";
import { getIntakeForm } from "../../api/intakeForms";
import { deleteIntakeFormSubmission, getIntakeFormSubmission, updateIntakeFormSubmission } from "../../api/intakeFormSubmissions";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { TextArea } from "../../components/ui/TextArea";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, updateIntakeFormSubmissionSchema, type UpdateIntakeFormSubmissionFormValues } from "../../lib/validation";
import type { ContactDto, IntakeFormDto, IntakeFormSubmissionDto } from "../../types/api";

export default function IntakeFormSubmissionDetailPage() {
  const { intakeFormSubmissionId } = useParams<{ intakeFormSubmissionId: string }>();
  const navigate = useNavigate();
  const [submission, setSubmission] = useState<IntakeFormSubmissionDto | null>(null);
  const [contact, setContact] = useState<ContactDto | null>(null);
  const [form, setForm] = useState<IntakeFormDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<UpdateIntakeFormSubmissionFormValues>({ resolver: zodResolver(updateIntakeFormSubmissionSchema) });

  useEffect(() => {
    if (!intakeFormSubmissionId) return;
    let cancelled = false;
    getIntakeFormSubmission(intakeFormSubmissionId)
      .then((data) => {
        if (cancelled) return;
        setSubmission(data);
        reset({ responses: data.responses ?? "", notes: data.notes ?? "" });
        getContact(data.contactId).then(setContact).catch(() => undefined);
        getIntakeForm(data.formId).then(setForm).catch(() => undefined);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this intake form submission.");
      });
    return () => {
      cancelled = true;
    };
  }, [intakeFormSubmissionId, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!intakeFormSubmissionId) return;
    setFormError(null);
    try {
      const updated = await updateIntakeFormSubmission(intakeFormSubmissionId, {
        responses: blankToUndefined(values.responses),
        notes: blankToUndefined(values.notes),
      });
      setSubmission(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function handleDelete() {
    if (!intakeFormSubmissionId || !window.confirm("Delete this intake form submission? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      await deleteIntakeFormSubmission(intakeFormSubmissionId);
      navigate("/intake-form-submissions");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this intake form submission.");
      setIsDeleting(false);
    }
  }

  if (error && !submission) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!submission) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/intake-form-submissions" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Intake Form Submissions
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{form ? form.title : "Intake form submission"}</h1>
          </div>
          {contact && (
            <p className="mt-1 text-sm text-slate-500">
              From{" "}
              <Link to={`/contacts/${contact.id}`} className="text-slate-700 hover:underline">
                {contact.fullName}
              </Link>
            </p>
          )}
        </div>
        <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
          Delete
        </Button>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-500">Overview</h2>
        <dl className="mt-3 flex flex-col gap-2 text-sm">
          <Row label="Submitted" value={new Date(submission.submittedAt).toLocaleString()} />
        </dl>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Edit submission</h2>

        {formError && <Alert variant="error">{formError}</Alert>}

        <TextArea label="Responses" error={errors.responses?.message} {...register("responses")} />
        <TextArea label="Notes" error={errors.notes?.message} {...register("notes")} />

        <div className="flex justify-end">
          <Button type="submit" isLoading={isSubmitting}>
            Save changes
          </Button>
        </div>
      </form>
    </div>
  );
}

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex justify-between gap-4">
      <dt className="text-slate-500">{label}</dt>
      <dd className="text-right text-slate-900">{value ?? "—"}</dd>
    </div>
  );
}
