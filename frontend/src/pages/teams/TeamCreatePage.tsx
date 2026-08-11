import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { createTeam } from "../../api/teams";
import { listUsers } from "../../api/users";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createTeamSchema, type CreateTeamFormValues } from "../../lib/validation";
import type { UserDto } from "../../types/api";

export default function TeamCreatePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);
  const [users, setUsers] = useState<UserDto[]>([]);

  useEffect(() => {
    listUsers({ size: 100 })
      .then((res) => setUsers(res.content))
      .catch(() => undefined);
  }, []);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateTeamFormValues>({ resolver: zodResolver(createTeamSchema) });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const team = await createTeam({
        name: values.name,
        department: blankToUndefined(values.department),
        leadUserId: blankToUndefined(values.leadUserId),
      });
      navigate(`/teams/${team.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New team</h1>
        <p className="mt-1 text-sm text-slate-500">Department is a free-text field - teams sharing the same department string share DEPARTMENT-scope visibility.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <TextField label="Name" error={errors.name?.message} {...register("name")} />
        <TextField label="Department" placeholder="Sales, Marketing, Support..." error={errors.department?.message} {...register("department")} />
        <Select
          label="Lead"
          placeholder="None"
          options={users.map((u) => ({ value: u.id, label: u.fullName }))}
          error={errors.leadUserId?.message}
          {...register("leadUserId")}
        />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/teams")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Create team
          </Button>
        </div>
      </form>
    </div>
  );
}
