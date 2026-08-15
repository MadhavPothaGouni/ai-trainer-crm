import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import {
  deleteCompensationRecord,
  getCompensationRecord,
  updateCompensationRecord,
  updateCompensationRecordStatus,
} from "../../api/compensationRecords";
import { getUser } from "../../api/users";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import {
  blankToUndefined,
  toOptionalNumber,
  updateCompensationRecordSchema,
  type UpdateCompensationRecordFormValues,
} from "../../lib/validation";
import {
  COMPENSATION_RECORD_STATUSES,
  type CompensationRecordDto,
  type CompensationRecordStatus,
  type UserDto,
} from "../../types/api";
import { CompensationRecordStatusBadge } from "./CompensationRecordListPage";

export default function CompensationRecordDetailPage() {
  const { compensationRecordId } = useParams<{ compensationRecordId: string }>();
  const navigate = useNavigate();
  const [record, setRecord] = useState<CompensationRecordDto | null>(null);
  const [staffUser, setStaffUser] = useState<UserDto | null>(null);
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
  } = useForm<UpdateCompensationRecordFormValues>({ resolver: zodResolver(updateCompensationRecordSchema) });

  useEffect(() => {
    if (!compensationRecordId) return;
    let cancelled = false;
    getCompensationRecord(compensationRecordId)
      .then((data) => {
        if (cancelled) return;
        setRecord(data);
        reset({
          payPeriodStart: data.payPeriodStart,
          payPeriodEnd: data.payPeriodEnd,
          hoursWorked: String(data.hoursWorked),
          hourlyRate: String(data.hourlyRate),
          commissionAmount: String(data.commissionAmount),
          bonusAmount: String(data.bonusAmount),
          notes: data.notes ?? "",
        });
        getUser(data.staffUserId).then(setStaffUser).catch(() => undefined);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this compensation record.");
      });
    return () => {
      cancelled = true;
    };
  }, [compensationRecordId, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!compensationRecordId) return;
    setFormError(null);
    try {
      const updated = await updateCompensationRecord(compensationRecordId, {
        payPeriodStart: values.payPeriodStart,
        payPeriodEnd: values.payPeriodEnd,
        hoursWorked: Number(values.hoursWorked),
        hourlyRate: Number(values.hourlyRate),
        commissionAmount: toOptionalNumber(values.commissionAmount),
        bonusAmount: toOptionalNumber(values.bonusAmount),
        notes: blankToUndefined(values.notes),
      });
      setRecord(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function handleStatusChange(status: string) {
    if (!compensationRecordId) return;
    setIsUpdatingStatus(true);
    setError(null);
    try {
      const updated = await updateCompensationRecordStatus(compensationRecordId, { status: status as CompensationRecordStatus });
      setRecord(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update the status.");
    } finally {
      setIsUpdatingStatus(false);
    }
  }

  async function handleDelete() {
    if (!compensationRecordId || !window.confirm("Delete this compensation record? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      await deleteCompensationRecord(compensationRecordId);
      navigate("/compensation-records");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this compensation record.");
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
          <Link to="/compensation-records" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Compensation Records
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">
              {record.payPeriodStart} &ndash; {record.payPeriodEnd}
            </h1>
            <CompensationRecordStatusBadge status={record.status} />
          </div>
          {staffUser && <p className="mt-1 text-sm text-slate-500">For {staffUser.fullName}</p>}
        </div>
        <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
          Delete
        </Button>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="grid gap-4 sm:grid-cols-2">
        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Pay breakdown</h2>
          <dl className="mt-3 flex flex-col gap-2 text-sm">
            <Row label="Hours worked" value={record.hoursWorked.toFixed(2)} />
            <Row label="Hourly rate" value={`$${record.hourlyRate.toFixed(2)}`} />
            <Row label="Commission" value={`$${record.commissionAmount.toFixed(2)}`} />
            <Row label="Bonus" value={`$${record.bonusAmount.toFixed(2)}`} />
            <Row label="Total" value={`$${record.totalAmount.toFixed(2)}`} />
            <Row label="Paid" value={record.paidAt ? new Date(record.paidAt).toLocaleString() : "Not yet"} />
          </dl>
        </div>

        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Status</h2>
          <p className="mt-1 text-xs text-slate-400">Records move freely between statuses - moving back to DRAFT after APPROVED is a normal correction.</p>
          <div className="mt-3">
            <Select
              label="Status"
              options={COMPENSATION_RECORD_STATUSES.map((status) => ({ value: status, label: status }))}
              value={record.status}
              disabled={isUpdatingStatus}
              onChange={(event) => void handleStatusChange(event.target.value)}
            />
          </div>
        </div>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Edit record</h2>

        {formError && <Alert variant="error">{formError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Pay period start" type="date" error={errors.payPeriodStart?.message} {...register("payPeriodStart")} />
          <TextField label="Pay period end" type="date" error={errors.payPeriodEnd?.message} {...register("payPeriodEnd")} />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Hours worked" type="number" min={0} step="0.01" error={errors.hoursWorked?.message} {...register("hoursWorked")} />
          <TextField label="Hourly rate" type="number" min={0} step="0.01" error={errors.hourlyRate?.message} {...register("hourlyRate")} />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Commission" type="number" min={0} step="0.01" error={errors.commissionAmount?.message} {...register("commissionAmount")} />
          <TextField label="Bonus" type="number" min={0} step="0.01" error={errors.bonusAmount?.message} {...register("bonusAmount")} />
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

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex justify-between gap-4">
      <dt className="text-slate-500">{label}</dt>
      <dd className="text-right text-slate-900">{value ?? "—"}</dd>
    </div>
  );
}
