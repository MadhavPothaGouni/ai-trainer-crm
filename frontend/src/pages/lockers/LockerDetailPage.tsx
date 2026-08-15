import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { deleteLocker, getLocker, updateLocker } from "../../api/lockers";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, updateLockerSchema, type UpdateLockerFormValues } from "../../lib/validation";
import { LOCKER_SIZES, LOCKER_STATUSES, type LockerDto, type LockerSize, type LockerStatus } from "../../types/api";

export default function LockerDetailPage() {
  const { lockerId } = useParams<{ lockerId: string }>();
  const navigate = useNavigate();
  const [locker, setLocker] = useState<LockerDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<UpdateLockerFormValues>({ resolver: zodResolver(updateLockerSchema) });

  useEffect(() => {
    if (!lockerId) return;
    let cancelled = false;
    getLocker(lockerId)
      .then((data) => {
        if (cancelled) return;
        setLocker(data);
        reset({ label: data.label, location: data.location ?? "", size: data.size, status: data.status, notes: data.notes ?? "" });
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this locker.");
      });
    return () => {
      cancelled = true;
    };
  }, [lockerId, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!lockerId) return;
    setFormError(null);
    try {
      const updated = await updateLocker(lockerId, {
        label: values.label,
        location: blankToUndefined(values.location),
        size: values.size as LockerSize,
        status: values.status as LockerStatus,
        notes: blankToUndefined(values.notes),
      });
      setLocker(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function handleDelete() {
    if (!lockerId || !window.confirm("Delete this locker? Existing assignments keep their own record, so this is safe.")) return;
    setIsDeleting(true);
    try {
      await deleteLocker(lockerId);
      navigate("/lockers");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this locker.");
      setIsDeleting(false);
    }
  }

  if (error && !locker) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!locker) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/lockers" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Lockers
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{locker.label}</h1>
            {locker.status === "ACTIVE" ? (
              <span className="inline-block rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-medium text-emerald-700">Active</span>
            ) : (
              <span className="inline-block rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-500">Out of service</span>
            )}
          </div>
        </div>
        <div className="flex gap-3">
          <Link to={`/locker-assignments/new?lockerId=${locker.id}`}>
            <Button variant="secondary">Assign locker</Button>
          </Link>
          <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
            Delete
          </Button>
        </div>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Label" error={errors.label?.message} {...register("label")} />
          <Select label="Size" options={LOCKER_SIZES.map((size) => ({ value: size, label: size }))} error={errors.size?.message} {...register("size")} />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Location" error={errors.location?.message} {...register("location")} />
          <Select
            label="Status"
            options={LOCKER_STATUSES.map((status) => ({ value: status, label: status.replace("_", " ") }))}
            error={errors.status?.message}
            {...register("status")}
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
