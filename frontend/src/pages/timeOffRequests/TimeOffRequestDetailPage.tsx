import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { deleteTimeOffRequest, getTimeOffRequest, updateTimeOffRequest, updateTimeOffRequestStatus } from "../../api/timeOffRequests";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, updateTimeOffRequestSchema, type UpdateTimeOffRequestFormValues } from "../../lib/validation";
import {
  TIME_OFF_REQUEST_STATUSES,
  TIME_OFF_REQUEST_TYPES,
  type TimeOffRequestDto,
  type TimeOffRequestStatus,
  type TimeOffRequestType,
} from "../../types/api";
import { TimeOffRequestStatusBadge } from "./TimeOffRequestListPage";

export default function TimeOffRequestDetailPage() {
  const { timeOffRequestId } = useParams<{ timeOffRequestId: string }>();
  const navigate = useNavigate();
  const [timeOffRequest, setTimeOffRequest] = useState<TimeOffRequestDto | null>(null);
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
  } = useForm<UpdateTimeOffRequestFormValues>({ resolver: zodResolver(updateTimeOffRequestSchema) });

  useEffect(() => {
    if (!timeOffRequestId) return;
    let cancelled = false;
    getTimeOffRequest(timeOffRequestId)
      .then((data) => {
        if (cancelled) return;
        setTimeOffRequest(data);
        reset({
          startDate: data.startDate,
          endDate: data.endDate,
          type: data.type,
          reason: data.reason ?? "",
          notes: data.notes ?? "",
        });
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this time-off request.");
      });
    return () => {
      cancelled = true;
    };
  }, [timeOffRequestId, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!timeOffRequestId) return;
    setFormError(null);
    try {
      const updated = await updateTimeOffRequest(timeOffRequestId, {
        startDate: values.startDate,
        endDate: values.endDate,
        type: values.type as TimeOffRequestType,
        reason: blankToUndefined(values.reason),
        notes: blankToUndefined(values.notes),
      });
      setTimeOffRequest(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function handleStatusChange(status: string) {
    if (!timeOffRequestId) return;
    setIsUpdatingStatus(true);
    setError(null);
    try {
      const updated = await updateTimeOffRequestStatus(timeOffRequestId, { status: status as TimeOffRequestStatus });
      setTimeOffRequest(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update the status.");
    } finally {
      setIsUpdatingStatus(false);
    }
  }

  async function handleDelete() {
    if (!timeOffRequestId || !window.confirm("Delete this time-off request? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      await deleteTimeOffRequest(timeOffRequestId);
      navigate("/time-off-requests");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this time-off request.");
      setIsDeleting(false);
    }
  }

  if (error && !timeOffRequest) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!timeOffRequest) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/time-off-requests" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Time-Off Requests
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">
              {timeOffRequest.startDate} – {timeOffRequest.endDate}
            </h1>
            <TimeOffRequestStatusBadge status={timeOffRequest.status} />
          </div>
          <p className="mt-1 text-sm text-slate-500">{timeOffRequest.type}</p>
        </div>
        <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
          Delete
        </Button>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-500">Status</h2>
        <p className="mt-1 text-xs text-slate-400">
          Requests move freely between statuses - reinstating a denied request or re-approving one is a normal correction.
        </p>
        <dl className="mt-3 flex flex-col gap-2 text-sm">
          <Row label="Approved" value={timeOffRequest.approvedAt ? new Date(timeOffRequest.approvedAt).toLocaleString() : "Not yet"} />
        </dl>
        <div className="mt-3">
          <Select
            label="Status"
            options={TIME_OFF_REQUEST_STATUSES.map((status) => ({ value: status, label: status }))}
            value={timeOffRequest.status}
            disabled={isUpdatingStatus}
            onChange={(event) => void handleStatusChange(event.target.value)}
          />
        </div>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Edit request</h2>

        {formError && <Alert variant="error">{formError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Start date" type="date" error={errors.startDate?.message} {...register("startDate")} />
          <TextField label="End date" type="date" error={errors.endDate?.message} {...register("endDate")} />
        </div>

        <Select
          label="Type"
          options={TIME_OFF_REQUEST_TYPES.map((type) => ({ value: type, label: type }))}
          error={errors.type?.message}
          {...register("type")}
        />

        <TextArea label="Reason" error={errors.reason?.message} {...register("reason")} />
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
