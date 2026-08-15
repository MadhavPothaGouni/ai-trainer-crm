import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { deleteRefundRecord, getRefundRecord, updateRefundRecord, updateRefundRecordStatus } from "../../api/refundRecords";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, updateRefundRecordSchema, type UpdateRefundRecordFormValues } from "../../lib/validation";
import { REFUND_RECORD_REASONS, REFUND_RECORD_STATUSES, type RefundRecordDto, type RefundRecordReason, type RefundRecordStatus } from "../../types/api";
import { RefundRecordStatusBadge } from "./RefundRecordListPage";

export default function RefundRecordDetailPage() {
  const { refundRecordId } = useParams<{ refundRecordId: string }>();
  const navigate = useNavigate();
  const [refund, setRefund] = useState<RefundRecordDto | null>(null);
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
  } = useForm<UpdateRefundRecordFormValues>({ resolver: zodResolver(updateRefundRecordSchema) });

  useEffect(() => {
    if (!refundRecordId) return;
    let cancelled = false;
    getRefundRecord(refundRecordId)
      .then((data) => {
        if (cancelled) return;
        setRefund(data);
        reset({ amount: String(data.amount), reason: data.reason, notes: data.notes ?? "" });
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this refund record.");
      });
    return () => {
      cancelled = true;
    };
  }, [refundRecordId, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!refundRecordId) return;
    setFormError(null);
    try {
      const updated = await updateRefundRecord(refundRecordId, {
        amount: Number(values.amount),
        reason: values.reason as RefundRecordReason,
        notes: blankToUndefined(values.notes),
      });
      setRefund(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function handleStatusChange(status: string) {
    if (!refundRecordId) return;
    setIsUpdatingStatus(true);
    setError(null);
    try {
      const updated = await updateRefundRecordStatus(refundRecordId, { status: status as RefundRecordStatus });
      setRefund(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update the status.");
    } finally {
      setIsUpdatingStatus(false);
    }
  }

  async function handleDelete() {
    if (!refundRecordId || !window.confirm("Delete this refund record? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      await deleteRefundRecord(refundRecordId);
      navigate("/refund-records");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this refund record.");
      setIsDeleting(false);
    }
  }

  if (error && !refund) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!refund) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/refund-records" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Refund Records
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">${refund.amount.toFixed(2)} refund</h1>
            <RefundRecordStatusBadge status={refund.status} />
          </div>
          <p className="mt-1 text-sm text-slate-500">Against payment {refund.paymentId}</p>
        </div>
        <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
          Delete
        </Button>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-500">Status</h2>
        <p className="mt-1 text-xs text-slate-400">
          Refunds move freely between statuses - moving PROCESSED back to REQUESTED is a normal correction and won't clear the processed timestamp
          below.
        </p>
        <div className="mt-3 grid gap-4 sm:grid-cols-2">
          <Select
            label="Status"
            options={REFUND_RECORD_STATUSES.map((status) => ({ value: status, label: status }))}
            value={refund.status}
            disabled={isUpdatingStatus}
            onChange={(event) => void handleStatusChange(event.target.value)}
          />
          <div>
            <p className="text-xs font-medium uppercase tracking-wide text-slate-400">Processed at</p>
            <p className="mt-2 text-sm text-slate-900">{refund.processedAt ? new Date(refund.processedAt).toLocaleString() : "Not yet"}</p>
          </div>
        </div>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Edit refund</h2>

        {formError && <Alert variant="error">{formError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Amount" type="number" min={0} step="0.01" error={errors.amount?.message} {...register("amount")} />
          <Select
            label="Reason"
            options={REFUND_RECORD_REASONS.map((reason) => ({ value: reason, label: reason.replace("_", " ") }))}
            error={errors.reason?.message}
            {...register("reason")}
          />
        </div>

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
