import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { createRole, listPermissions } from "../../api/roles";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { TextField } from "../../components/ui/TextField";
import { PermissionPicker } from "../../components/roles/PermissionPicker";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, roleFormSchema, type RoleFormValues } from "../../lib/validation";
import type { PermissionDto } from "../../types/api";

export default function RoleCreatePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);
  const [catalog, setCatalog] = useState<PermissionDto[]>([]);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());

  useEffect(() => {
    listPermissions()
      .then(setCatalog)
      .catch((err: unknown) => setFormError(err instanceof ApiError ? err.message : "Could not load the permission catalog."));
  }, []);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RoleFormValues>({ resolver: zodResolver(roleFormSchema) });

  function togglePermission(permissionId: string) {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(permissionId)) next.delete(permissionId);
      else next.add(permissionId);
      return next;
    });
  }

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const role = await createRole({
        name: values.name,
        description: blankToUndefined(values.description),
        permissionIds: [...selectedIds],
      });
      navigate(`/roles/${role.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New role</h1>
        <p className="mt-1 text-sm text-slate-500">Custom roles can be assigned to teammates from their user page.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <TextField label="Role name" error={errors.name?.message} {...register("name")} />
        <TextField label="Description" error={errors.description?.message} {...register("description")} />

        <div>
          <h2 className="mb-2 text-sm font-medium text-slate-700">Permissions</h2>
          <PermissionPicker catalog={catalog} selectedIds={selectedIds} onToggle={togglePermission} />
        </div>

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/roles")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Create role
          </Button>
        </div>
      </form>
    </div>
  );
}
