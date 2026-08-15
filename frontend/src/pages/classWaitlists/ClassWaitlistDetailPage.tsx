import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { deleteClassWaitlist, getClassWaitlist, updateClassWaitlist, updateClassWaitlistStatus } from "../../api/classWaitlists";
import { getClassSession } from "../../api/classSessions";
import { getContact } from "../../api/contacts";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, updateClassWaitlistSchema, type UpdateClassWaitlistFormValues } from "../../lib/validation";
import { CLASS_WAITLIST_STATUSES, type ClassSessionDto, type ClassWaitlistDto, type ClassWaitlistStatus, type ContactDto } from "../../types/api";
import { ClassWaitlistStatusBadge } from "./ClassWaitlistListPage";

export default function ClassWaitlistDetailPage() {
  const { classWaitlistId } = useParams<{ classWaitlistId: string }>();
  const navigate = useNavigate();
  const [entry, setEntry] = useState<ClassWaitlistDto | null>(null);
  const [session, setSession] = useState<ClassSessionDto | null>(null);
  const [contact, setContact] = useState<ContactDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [isUpdatingStatus, setIsUpdatingStatus] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<UpdateClassWaitlistFormValues>({ resolver: zodResolver(updateClassWaitlistSchema) });

  useEffect(() => {
    if (!classWaitlistId) return;
    let cancelled = false;
    getClassWaitlist(classWaitlistId)
      .then((data) => {
        if (cancelled) return;
        setEntry(data);
        reset({ notes: data.notes ?? "" });
        getClassSession(data.classSessionId).then(setSession).catch(() => undefined);
        getContact(data.contactId).then(setContact).catch(() => undefined);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this waitlist entry.");
      });
    return () => {
      cancelled = true;
    };
  }, [classWaitlistId, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!classWaitlistId) return;
    setFormError(null);
    try {
      const updated = await updateClassWaitlist(classWaitlistId, { notes: blankToUndefined(values.notes) });
      setEntry(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function handleStatusChange(status: string) {
    if (!classWaitlistId) return;
    setIsUpdatingStatus(true);
    setError(null);
    try {
      const updated = await updateClassWaitlistStatus(classWaitlistId, { status: status as ClassWaitlistStatus });
      setEntry(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update the status.");
    } finally {
      setIsUpdatingStatus(false);
    }
  }

  async function handleDelete() {
    if (!classWaitlistId || !window.confirm("Remove this waitlist entry? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      await deleteClassWaitlist(classWaitlistId);
      navigate("/class-waitlists");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not remove this waitlist entry.");
      setIsDeleting(false);
    }
  }

  if (error && !entry) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!entry) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/class-waitlists" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Class Waitlists
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">Position #{entry.position}</h1>
            <ClassWaitlistStatusBadge status={entry.status} />
          </div>
          <p className="mt-1 text-sm text-slate-500">
            {contact ? contact.fullName : "Loading client..."} &middot;{" "}
            {session ? new Date(session.startsAt).toLocaleString() : "Loading session..."}
          </p>
        </div>
        <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
          Remove
        </Button>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="grid gap-4 sm:grid-cols-2">
        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Overview</h2>
          <dl className="mt-3 flex flex-col gap-2 text-sm">
            <Row label="Position" value={`#${entry.position}`} />
            <Row label="Notified" value={entry.notifiedAt ? new Date(entry.notifiedAt).toLocaleString() : "Not yet"} />
          </dl>
        </div>

        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Status</h2>
          <p className="mt-1 text-xs text-slate-400">
            Entries move freely between statuses - moving a CONVERTED entry back to WAITING is a normal correction.
          </p>
          <div className="mt-3">
            <Select
              label="Status"
              options={CLASS_WAITLIST_STATUSES.map((status) => ({ value: status, label: status }))}
              value={entry.status}
              disabled={isUpdatingStatus}
              onChange={(event) => void handleStatusChange(event.target.value)}
            />
          </div>
        </div>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Edit notes</h2>

        {formError && <Alert variant="error">{formError}</Alert>}

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
