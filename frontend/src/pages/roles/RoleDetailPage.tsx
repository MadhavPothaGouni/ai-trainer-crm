import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { deleteRole, getRole, listPermissions, updateRole } from "../../api/roles";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { TextField } from "../../components/ui/TextField";
import { PermissionPicker } from "../../components/roles/PermissionPicker";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, roleFormSchema, type RoleFormValues } from "../../lib/validation";
import type { PermissionDto, RoleDto } from "../../types/api";

export default function RoleDetailPage() {
  const { roleId } = useParams<{ roleId: string }>();
  const navigate = useNavigate();
  const [role, setRole] = useState<RoleDto | null>(null);
  const [catalog, setCatalog] = useState<PermissionDto[]>([]);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<RoleFormValues>({ resolver: zodResolver(roleFormSchema) });

  useEffect(() => {
    if (!roleId) return;
    let cancelled = false;
    Promise.all([getRole(roleId), listPermissions()])
      .then(([roleData, permissionCatalog]) => {
        if (cancelled) return;
        setRole(roleData);
        setCatalog(permissionCatalog);
        setSelectedIds(new Set(roleData.permissions.map((permission) => permission.id)));
        reset({ name: roleData.name, description: roleData.description ?? "" });
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this role.");
      });
    return () => {
      cancelled = true;
    };
  }, [roleId, reset]);

  function togglePermission(permissionId: string) {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(permissionId)) next.delete(permissionId);
      else next.add(permissionId);
      return next;
    });
  }

  const onSubmit = handleSubmit(async (values) => {
    if (!roleId) return;
    setFormError(null);
    try {
      const updated = await updateRole(roleId, {
        name: values.name,
        description: blankToUndefined(values.description),
        permissionIds: [...selectedIds],
      });
      setRole(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function handleDelete() {
    if (!roleId || !window.confirm("Delete this role? Anyone holding it will lose its permissions.")) return;
    setIsDeleting(true);
    try {
      await deleteRole(roleId);
      navigate("/roles");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this role.");
      setIsDeleting(false);
    }
  }

  if (error && !role) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!role) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/roles" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Roles
          </Link>
          <h1 className="mt-1 text-2xl font-semibold text-slate-900">{role.name}</h1>
        </div>
        {!role.systemRole && (
          <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
            Delete role
          </Button>
        )}
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      {role.systemRole ? (
        <div className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
          <Alert variant="info">
            {role.name} is a built-in role and can&apos;t be edited or deleted. Its permissions are shown for reference.
          </Alert>
          <p className="text-sm text-slate-600">{role.description}</p>
          <PermissionPicker catalog={catalog} selectedIds={selectedIds} onToggle={() => undefined} disabled />
        </div>
      ) : (
        <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
          {formError && <Alert variant="error">{formError}</Alert>}

          <TextField label="Role name" error={errors.name?.message} {...register("name")} />
          <TextField label="Description" error={errors.description?.message} {...register("description")} />

          <div>
            <h2 className="mb-2 text-sm font-medium text-slate-700">Permissions</h2>
            <PermissionPicker catalog={catalog} selectedIds={selectedIds} onToggle={togglePermission} />
          </div>

          <div className="flex justify-end">
            <Button type="submit" isLoading={isSubmitting}>
              Save changes
            </Button>
          </div>
        </form>
      )}
    </div>
  );
}
