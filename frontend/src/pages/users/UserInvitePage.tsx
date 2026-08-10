import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { listRoles } from "../../api/roles";
import { inviteUser } from "../../api/users";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { inviteUserSchema, type InviteUserFormValues } from "../../lib/validation";
import type { RoleDto } from "../../types/api";

export default function UserInvitePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);
  const [roles, setRoles] = useState<RoleDto[]>([]);
  const [selectedRoleIds, setSelectedRoleIds] = useState<Set<string>>(new Set());

  useEffect(() => {
    listRoles()
      .then((data) => {
        setRoles(data);
        const memberRole = data.find((role) => role.name === "MEMBER");
        if (memberRole) setSelectedRoleIds(new Set([memberRole.id]));
      })
      .catch(() => setRoles([]));
  }, []);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<InviteUserFormValues>({ resolver: zodResolver(inviteUserSchema) });

  function toggleRole(roleId: string) {
    setSelectedRoleIds((prev) => {
      const next = new Set(prev);
      if (next.has(roleId)) next.delete(roleId);
      else next.add(roleId);
      return next;
    });
  }

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const user = await inviteUser({
        email: values.email,
        firstName: values.firstName,
        lastName: values.lastName,
        roleIds: selectedRoleIds.size > 0 ? [...selectedRoleIds] : undefined,
      });
      navigate(`/users/${user.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Invite teammate</h1>
        <p className="mt-1 text-sm text-slate-500">
          They&apos;ll get an email with a link to set their password. Defaults to MEMBER if no role is chosen.
        </p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-lg flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="First name" error={errors.firstName?.message} {...register("firstName")} />
          <TextField label="Last name" error={errors.lastName?.message} {...register("lastName")} />
        </div>
        <TextField label="Email" type="email" error={errors.email?.message} {...register("email")} />

        <div>
          <h2 className="mb-2 text-sm font-medium text-slate-700">Roles</h2>
          <div className="flex flex-col gap-1.5 rounded-md border border-slate-200 p-3">
            {roles.map((role) => (
              <label key={role.id} className="flex items-center gap-2 text-sm text-slate-700">
                <input
                  type="checkbox"
                  className="h-4 w-4 rounded border-slate-300"
                  checked={selectedRoleIds.has(role.id)}
                  onChange={() => toggleRole(role.id)}
                />
                {role.name}
              </label>
            ))}
          </div>
        </div>

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/users")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Send invite
          </Button>
        </div>
      </form>
    </div>
  );
}
