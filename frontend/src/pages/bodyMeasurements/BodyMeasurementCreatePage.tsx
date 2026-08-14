import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { createBodyMeasurement } from "../../api/bodyMeasurements";
import { listContacts } from "../../api/contacts";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createBodyMeasurementSchema, toOptionalNumber, type CreateBodyMeasurementFormValues } from "../../lib/validation";
import type { ContactDto } from "../../types/api";

export default function BodyMeasurementCreatePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);
  const [contacts, setContacts] = useState<ContactDto[]>([]);

  useEffect(() => {
    listContacts({ size: 100, sort: "lastName,asc" })
      .then((res) => setContacts(res.content))
      .catch(() => setContacts([]));
  }, []);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateBodyMeasurementFormValues>({ resolver: zodResolver(createBodyMeasurementSchema) });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const measurement = await createBodyMeasurement({
        contactId: values.contactId,
        measuredAt: values.measuredAt,
        weightValue: toOptionalNumber(values.weightValue),
        weightUnit: blankToUndefined(values.weightUnit),
        bodyFatPercent: toOptionalNumber(values.bodyFatPercent),
        chestCm: toOptionalNumber(values.chestCm),
        waistCm: toOptionalNumber(values.waistCm),
        hipsCm: toOptionalNumber(values.hipsCm),
        notes: blankToUndefined(values.notes),
      });
      navigate(`/body-measurements/${measurement.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New check-in</h1>
        <p className="mt-1 text-sm text-slate-500">Record a client's weight, body fat, and circumference for a given date.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <Select
            label="Client"
            placeholder="Select a contact"
            options={contacts.map((contact) => ({ value: contact.id, label: contact.fullName }))}
            error={errors.contactId?.message}
            {...register("contactId")}
          />
          <TextField label="Date" type="date" error={errors.measuredAt?.message} {...register("measuredAt")} />
        </div>

        <div className="grid gap-4 sm:grid-cols-3">
          <TextField label="Weight" type="number" step="any" error={errors.weightValue?.message} {...register("weightValue")} />
          <TextField label="Weight unit" placeholder="lbs, kg..." error={errors.weightUnit?.message} {...register("weightUnit")} />
          <TextField label="Body fat %" type="number" step="any" error={errors.bodyFatPercent?.message} {...register("bodyFatPercent")} />
        </div>

        <div className="grid gap-4 sm:grid-cols-3">
          <TextField label="Chest (cm)" type="number" step="any" error={errors.chestCm?.message} {...register("chestCm")} />
          <TextField label="Waist (cm)" type="number" step="any" error={errors.waistCm?.message} {...register("waistCm")} />
          <TextField label="Hips (cm)" type="number" step="any" error={errors.hipsCm?.message} {...register("hipsCm")} />
        </div>

        <TextArea label="Notes" error={errors.notes?.message} {...register("notes")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/body-measurements")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Save check-in
          </Button>
        </div>
      </form>
    </div>
  );
}
