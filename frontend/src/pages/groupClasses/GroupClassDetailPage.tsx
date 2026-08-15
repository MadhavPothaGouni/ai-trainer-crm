import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { deleteGroupClass, getGroupClass, updateGroupClass } from "../../api/groupClasses";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createGroupClassSchema, toOptionalNumber, type CreateGroupClassFormValues } from "../../lib/validation";
import type { GroupClassDto } from "../../types/api";

export default function GroupClassDetailPage() {
  const { groupClassId } = useParams<{ groupClassId: string }>();
  const navigate = useNavigate();
  const [groupClass, setGroupClass] = useState<GroupClassDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<CreateGroupClassFormValues>({ resolver: zodResolver(createGroupClassSchema) });

  useEffect(() => {
    if (!groupClassId) return;
    let cancelled = false;
    getGroupClass(groupClassId)
      .then((data) => {
        if (cancelled) return;
        setGroupClass(data);
        reset({
          name: data.name,
          description: data.description ?? "",
          durationMinutes: String(data.durationMinutes),
          capacity: data.capacity != null ? String(data.capacity) : "",
          location: data.location ?? "",
        });
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this class.");
      });
    return () => {
      cancelled = true;
    };
  }, [groupClassId, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!groupClassId || !groupClass) return;
    setFormError(null);
    try {
      const updated = await updateGroupClass(groupClassId, {
        name: values.name,
        description: blankToUndefined(values.description),
        durationMinutes: Number(values.durationMinutes),
        capacity: toOptionalNumber(values.capacity),
        location: blankToUndefined(values.location),
        active: groupClass.active,
      });
      setGroupClass(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function toggleActive() {
    if (!groupClassId || !groupClass) return;
    try {
      const updated = await updateGroupClass(groupClassId, {
        name: groupClass.name,
        description: groupClass.description ?? undefined,
        durationMinutes: groupClass.durationMinutes,
        capacity: groupClass.capacity ?? undefined,
        location: groupClass.location ?? undefined,
        active: !groupClass.active,
      });
      setGroupClass(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update this class.");
    }
  }

  async function handleDelete() {
    if (!groupClassId || !window.confirm("Delete this class? Existing sessions keep their own record, so this is safe.")) return;
    setIsDeleting(true);
    try {
      await deleteGroupClass(groupClassId);
      navigate("/group-classes");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this class.");
      setIsDeleting(false);
    }
  }

  if (error && !groupClass) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!groupClass) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/group-classes" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Group Classes
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{groupClass.name}</h1>
            {groupClass.active ? (
              <span className="inline-block rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-medium text-emerald-700">Active</span>
            ) : (
              <span className="inline-block rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-500">Inactive</span>
            )}
          </div>
        </div>
        <div className="flex gap-3">
          <Link to={`/class-sessions/new?groupClassId=${groupClass.id}`}>
            <Button variant="secondary">Schedule session</Button>
          </Link>
          <Button variant="secondary" onClick={() => void toggleActive()}>
            {groupClass.active ? "Deactivate" : "Activate"}
          </Button>
          <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
            Delete
          </Button>
        </div>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <TextField label="Name" error={errors.name?.message} {...register("name")} />

        <div className="grid gap-4 sm:grid-cols-3">
          <TextField label="Duration (minutes)" type="number" min={5} step={1} error={errors.durationMinutes?.message} {...register("durationMinutes")} />
          <TextField label="Capacity" type="number" min={1} step={1} placeholder="Leave blank for unlimited" error={errors.capacity?.message} {...register("capacity")} />
          <TextField label="Location" error={errors.location?.message} {...register("location")} />
        </div>

        <TextArea label="Description" error={errors.description?.message} {...register("description")} />

        <div className="flex justify-end">
          <Button type="submit" isLoading={isSubmitting}>
            Save changes
          </Button>
        </div>
      </form>
    </div>
  );
}
