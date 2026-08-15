import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { getContact } from "../../api/contacts";
import { deleteNoShowRecord, getNoShowRecord, updateNoShowRecord, waiveNoShowRecord } from "../../api/noShowRecords";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, toOptionalNumber, updateNoShowRecordSchema, type UpdateNoShowRecordFormValues } from "../../lib/validation";
import { NO_SHOW_RELATED_TYPES, type ContactDto, type NoShowRecordDto, type NoShowRelatedType } from "../../types/api";
import { NoShowRecordWaivedBadge } from "./NoShowRecordListPage";

/** datetime-local wants "YYYY-MM-DDTHH:mm" in LOCAL time - same conversion RoomBookingDetailPage's edit form establishes. */
function toDatetimeLocalValue(iso: string): string {
  const date = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export default function NoShowRecordDetailPage() {
  const { noShowRecordId } = useParams<{ noShowRecordId: string }>();
  const navigate = useNavigate();
  const [record, setRecord] = useState<NoShowRecordDto | null>(null);
  const [contact, setContact] = useState<ContactDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [waiveError, setWaiveError] = useState<string | null>(null);
  const [isWaiving, setIsWaiving] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<UpdateNoShowRecordFormValues>({ resolver: zodResolver(updateNoShowRecordSchema) });

  useEffect(() => {
    if (!noShowRecordId) return;
    let cancelled = false;
    getNoShowRecord(noShowRecordId)
      .then((data) => {
        if (cancelled) return;
        setRecord(data);
        reset({
          occurredAt: toDatetimeLocalValue(data.occurredAt),
          relatedType: data.relatedType,
          feeAmount: data.feeAmount != null ? String(data.feeAmount) : "",
          notes: data.notes ?? "",
        });
        getContact(data.contactId).then(setContact).catch(() => undefined);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this no-show record.");
      });
    return () => {
      cancelled = true;
    };
  }, [noShowRecordId, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!noShowRecordId) return;
    setFormError(null);
    try {
      const updated = await updateNoShowRecord(noShowRecordId, {
        occurredAt: new Date(values.occurredAt).toISOString(),
        relatedType: values.relatedType as NoShowRelatedType,
        feeAmount: toOptionalNumber(values.feeAmount),
        notes: blankToUndefined(values.notes),
      });
      setRecord(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function handleWaive() {
    if (!noShowRecordId) return;
    setIsWaiving(true);
    setWaiveError(null);
    try {
      const updated = await waiveNoShowRecord(noShowRecordId);
      setRecord(updated);
    } catch (err) {
      setWaiveError(err instanceof ApiError ? err.message : "Could not waive this fee.");
    } finally {
      setIsWaiving(false);
    }
  }

  async function handleDelete() {
    if (!noShowRecordId || !window.confirm("Delete this no-show record? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      await deleteNoShowRecord(noShowRecordId);
      navigate("/no-show-records");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this no-show record.");
      setIsDeleting(false);
    }
  }

  if (error && !record) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!record) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/no-show-records" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; No-Show Records
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{new Date(record.occurredAt).toLocaleString()}</h1>
            <NoShowRecordWaivedBadge waived={record.waived} />
          </div>
          {contact && (
            <p className="mt-1 text-sm text-slate-500">
              Missed by{" "}
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
        <h2 className="text-sm font-medium text-slate-500">Details</h2>
        <dl className="mt-3 flex flex-col gap-2 text-sm">
          <Row label="Type" value={record.relatedType} />
          <Row label="Fee" value={record.feeAmount != null ? `$${record.feeAmount.toFixed(2)}` : "None"} />
          <Row label="Waived at" value={record.waivedAt ? new Date(record.waivedAt).toLocaleString() : "Not yet"} />
        </dl>
      </div>

      <div className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Waive fee</h2>
        <p className="text-xs text-slate-400">
          Rejected if there's no fee on this record, or the fee has already been waived. There's no un-waive action.
        </p>
        {waiveError && <Alert variant="error">{waiveError}</Alert>}
        <div>
          <Button onClick={() => void handleWaive()} isLoading={isWaiving} disabled={record.waived || record.feeAmount == null}>
            Waive fee
          </Button>
        </div>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Edit record</h2>

        {formError && <Alert variant="error">{formError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Occurred at" type="datetime-local" error={errors.occurredAt?.message} {...register("occurredAt")} />
          <Select
            label="Type"
            options={NO_SHOW_RELATED_TYPES.map((type) => ({ value: type, label: type }))}
            error={errors.relatedType?.message}
            {...register("relatedType")}
          />
        </div>

        <TextField label="Fee amount" type="number" min={0} step="0.01" error={errors.feeAmount?.message} {...register("feeAmount")} />
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
