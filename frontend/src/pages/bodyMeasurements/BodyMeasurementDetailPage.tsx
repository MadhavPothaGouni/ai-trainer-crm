import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { deleteBodyMeasurement, getBodyMeasurement, updateBodyMeasurement } from "../../api/bodyMeasurements";
import { listContacts } from "../../api/contacts";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, toOptionalNumber, updateBodyMeasurementSchema, type UpdateBodyMeasurementFormValues } from "../../lib/validation";
import type { BodyMeasurementDto, ContactDto } from "../../types/api";

export default function BodyMeasurementDetailPage() {
  const { bodyMeasurementId } = useParams<{ bodyMeasurementId: string }>();
  const navigate = useNavigate();
  const [measurement, setMeasurement] = useState<BodyMeasurementDto | null>(null);
  const [contacts, setContacts] = useState<ContactDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);

  useEffect(() => {
    if (!bodyMeasurementId) return;
    let cancelled = false;
    getBodyMeasurement(bodyMeasurementId)
      .then((data) => {
        if (!cancelled) setMeasurement(data);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this check-in.");
      });
    listContacts({ size: 100, sort: "lastName,asc" })
      .then((res) => {
        if (!cancelled) setContacts(res.content);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [bodyMeasurementId]);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
    setError: setEditFieldError,
  } = useForm<UpdateBodyMeasurementFormValues>({ resolver: zodResolver(updateBodyMeasurementSchema) });

  useEffect(() => {
    if (!measurement) return;
    reset({
      measuredAt: measurement.measuredAt,
      weightValue: measurement.weightValue != null ? String(measurement.weightValue) : "",
      weightUnit: measurement.weightUnit ?? "",
      bodyFatPercent: measurement.bodyFatPercent != null ? String(measurement.bodyFatPercent) : "",
      chestCm: measurement.chestCm != null ? String(measurement.chestCm) : "",
      waistCm: measurement.waistCm != null ? String(measurement.waistCm) : "",
      hipsCm: measurement.hipsCm != null ? String(measurement.hipsCm) : "",
      notes: measurement.notes ?? "",
    });
  }, [measurement, reset]);

  const onSaveEdits = handleSubmit(async (values) => {
    if (!bodyMeasurementId) return;
    setEditError(null);
    try {
      const updated = await updateBodyMeasurement(bodyMeasurementId, {
        measuredAt: values.measuredAt,
        weightValue: toOptionalNumber(values.weightValue),
        weightUnit: blankToUndefined(values.weightUnit),
        bodyFatPercent: toOptionalNumber(values.bodyFatPercent),
        chestCm: toOptionalNumber(values.chestCm),
        waistCm: toOptionalNumber(values.waistCm),
        hipsCm: toOptionalNumber(values.hipsCm),
        notes: blankToUndefined(values.notes),
      });
      setMeasurement(updated);
    } catch (error) {
      setEditError(applyServerErrors(error, setEditFieldError));
    }
  });

  async function handleDelete() {
    if (!bodyMeasurementId || !window.confirm("Delete this check-in? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      await deleteBodyMeasurement(bodyMeasurementId);
      navigate("/body-measurements");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this check-in.");
      setIsDeleting(false);
    }
  }

  if (error && !measurement) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!measurement) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  const linkedContact = contacts.find((contact) => contact.id === measurement.contactId);

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/body-measurements" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Body Measurements
          </Link>
          <h1 className="mt-1 text-2xl font-semibold text-slate-900">Check-in: {measurement.measuredAt}</h1>
        </div>
        <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
          Delete
        </Button>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-500">Overview</h2>
        <dl className="mt-3 flex flex-col gap-2 text-sm">
          <Row
            label="Client"
            value={
              linkedContact && (
                <Link to={`/contacts/${linkedContact.id}`} className="text-slate-900 hover:underline">
                  {linkedContact.fullName}
                </Link>
              )
            }
          />
          <Row label="Weight" value={measurement.weightValue != null ? `${measurement.weightValue} ${measurement.weightUnit ?? ""}`.trim() : undefined} />
          <Row label="Body fat %" value={measurement.bodyFatPercent != null ? `${measurement.bodyFatPercent}%` : undefined} />
          <Row
            label="Chest / Waist / Hips (cm)"
            value={
              measurement.chestCm != null || measurement.waistCm != null || measurement.hipsCm != null
                ? `${measurement.chestCm ?? "?"} / ${measurement.waistCm ?? "?"} / ${measurement.hipsCm ?? "?"}`
                : undefined
            }
          />
        </dl>
      </div>

      <form onSubmit={onSaveEdits} noValidate className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Edit check-in</h2>

        {editError && <Alert variant="error">{editError}</Alert>}

        <TextField label="Date" type="date" error={errors.measuredAt?.message} {...register("measuredAt")} />

        <div className="grid gap-4 sm:grid-cols-3">
          <TextField label="Weight" type="number" step="any" error={errors.weightValue?.message} {...register("weightValue")} />
          <TextField label="Weight unit" error={errors.weightUnit?.message} {...register("weightUnit")} />
          <TextField label="Body fat %" type="number" step="any" error={errors.bodyFatPercent?.message} {...register("bodyFatPercent")} />
        </div>

        <div className="grid gap-4 sm:grid-cols-3">
          <TextField label="Chest (cm)" type="number" step="any" error={errors.chestCm?.message} {...register("chestCm")} />
          <TextField label="Waist (cm)" type="number" step="any" error={errors.waistCm?.message} {...register("waistCm")} />
          <TextField label="Hips (cm)" type="number" step="any" error={errors.hipsCm?.message} {...register("hipsCm")} />
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
