import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { deleteClientCheckIn, getClientCheckIn, updateClientCheckIn, updateClientCheckInStatus } from "../../api/clientCheckIns";
import { getContact } from "../../api/contacts";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, updateClientCheckInSchema, type UpdateClientCheckInFormValues } from "../../lib/validation";
import {
  CLIENT_CHECK_IN_METHODS,
  CLIENT_CHECK_IN_STATUSES,
  type ClientCheckInDto,
  type ClientCheckInMethod,
  type ClientCheckInStatus,
  type ContactDto,
} from "../../types/api";
import { ClientCheckInStatusBadge } from "./ClientCheckInListPage";

export default function ClientCheckInDetailPage() {
  const { clientCheckInId } = useParams<{ clientCheckInId: string }>();
  const navigate = useNavigate();
  const [checkIn, setCheckIn] = useState<ClientCheckInDto | null>(null);
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
  } = useForm<UpdateClientCheckInFormValues>({ resolver: zodResolver(updateClientCheckInSchema) });

  useEffect(() => {
    if (!clientCheckInId) return;
    let cancelled = false;
    getClientCheckIn(clientCheckInId)
      .then((data) => {
        if (cancelled) return;
        setCheckIn(data);
        reset({ method: data.method, notes: data.notes ?? "" });
        getContact(data.contactId).then(setContact).catch(() => undefined);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this check-in.");
      });
    return () => {
      cancelled = true;
    };
  }, [clientCheckInId, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!clientCheckInId) return;
    setFormError(null);
    try {
      const updated = await updateClientCheckIn(clientCheckInId, {
        method: values.method as ClientCheckInMethod,
        notes: blankToUndefined(values.notes),
      });
      setCheckIn(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function handleStatusChange(status: string) {
    if (!clientCheckInId) return;
    setIsUpdatingStatus(true);
    setError(null);
    try {
      const updated = await updateClientCheckInStatus(clientCheckInId, { status: status as ClientCheckInStatus });
      setCheckIn(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update the status.");
    } finally {
      setIsUpdatingStatus(false);
    }
  }

  async function handleDelete() {
    if (!clientCheckInId || !window.confirm("Delete this check-in? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      await deleteClientCheckIn(clientCheckInId);
      navigate("/client-check-ins");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this check-in.");
      setIsDeleting(false);
    }
  }

  if (error && !checkIn) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!checkIn) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/client-check-ins" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Client Check-Ins
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{new Date(checkIn.checkedInAt).toLocaleString()}</h1>
            <ClientCheckInStatusBadge status={checkIn.status} />
          </div>
          {contact && (
            <p className="mt-1 text-sm text-slate-500">
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
        <h2 className="text-sm font-medium text-slate-500">Status</h2>
        <p className="mt-1 text-xs text-slate-400">
          Check-ins move freely between statuses - correcting a mistaken check-out is a normal correction.
        </p>
        <dl className="mt-3 flex flex-col gap-2 text-sm">
          <Row label="Checked out" value={checkIn.checkedOutAt ? new Date(checkIn.checkedOutAt).toLocaleString() : "Not yet"} />
        </dl>
        <div className="mt-3">
          <Select
            label="Status"
            options={CLIENT_CHECK_IN_STATUSES.map((status) => ({ value: status, label: status.replace("_", " ") }))}
            value={checkIn.status}
            disabled={isUpdatingStatus}
            onChange={(event) => void handleStatusChange(event.target.value)}
          />
        </div>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Edit check-in</h2>

        {formError && <Alert variant="error">{formError}</Alert>}

        <Select
          label="Method"
          options={CLIENT_CHECK_IN_METHODS.map((method) => ({ value: method, label: method.replace("_", " ") }))}
          error={errors.method?.message}
          {...register("method")}
        />
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
