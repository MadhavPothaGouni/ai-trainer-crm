import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate, useSearchParams } from "react-router-dom";
import { listGroupClasses } from "../../api/groupClasses";
import { createClassSession } from "../../api/classSessions";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createClassSessionSchema, toOptionalNumber, type CreateClassSessionFormValues } from "../../lib/validation";
import type { GroupClassDto } from "../../types/api";

export default function ClassSessionCreatePage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const preselectedGroupClassId = searchParams.get("groupClassId") ?? "";
  const [formError, setFormError] = useState<string | null>(null);
  const [groupClasses, setGroupClasses] = useState<GroupClassDto[]>([]);

  useEffect(() => {
    listGroupClasses({ size: 100, sort: "name,asc" })
      .then((res) => setGroupClasses(res.content.filter((groupClass) => groupClass.active)))
      .catch(() => setGroupClasses([]));
  }, []);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateClassSessionFormValues>({
    resolver: zodResolver(createClassSessionSchema),
    defaultValues: { groupClassId: preselectedGroupClassId },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const session = await createClassSession({
        groupClassId: values.groupClassId,
        startsAt: new Date(values.startsAt).toISOString(),
        endsAt: new Date(values.endsAt).toISOString(),
        capacityOverride: toOptionalNumber(values.capacityOverride),
        notes: blankToUndefined(values.notes),
      });
      navigate(`/class-sessions/${session.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Schedule a class session</h1>
        <p className="mt-1 text-sm text-slate-500">A specific occurrence of a class type - clients get added to its roster afterward.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <Select
          label="Class"
          placeholder="Select a class type"
          options={groupClasses.map((groupClass) => ({ value: groupClass.id, label: groupClass.name }))}
          error={errors.groupClassId?.message}
          {...register("groupClassId")}
        />

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Starts at" type="datetime-local" error={errors.startsAt?.message} {...register("startsAt")} />
          <TextField label="Ends at" type="datetime-local" error={errors.endsAt?.message} {...register("endsAt")} />
        </div>

        <TextField
          label="Capacity override"
          type="number"
          min={0}
          step={1}
          placeholder="Leave blank to use the class type's capacity"
          error={errors.capacityOverride?.message}
          {...register("capacityOverride")}
        />

        <TextArea label="Notes" error={errors.notes?.message} {...register("notes")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/class-sessions")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Schedule session
          </Button>
        </div>
      </form>
    </div>
  );
}
