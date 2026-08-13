import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { listRegions } from "../../api/regions";
import { deleteTeam, getTeam, updateTeam } from "../../api/teams";
import { listUsers } from "../../api/users";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createTeamSchema, type CreateTeamFormValues } from "../../lib/validation";
import type { RegionDto, TeamDto, UserDto } from "../../types/api";

export default function TeamDetailPage() {
  const { teamId } = useParams<{ teamId: string }>();
  const navigate = useNavigate();
  const [team, setTeam] = useState<TeamDto | null>(null);
  const [users, setUsers] = useState<UserDto[]>([]);
  const [regions, setRegions] = useState<RegionDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);

  useEffect(() => {
    if (!teamId) return;
    let cancelled = false;
    getTeam(teamId)
      .then((data) => {
        if (!cancelled) setTeam(data);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this team.");
      });
    listUsers({ size: 200 })
      .then((res) => {
        if (!cancelled) setUsers(res.content);
      })
      .catch(() => undefined);
    listRegions()
      .then((res) => {
        if (!cancelled) setRegions(res);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [teamId]);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
    setError: setEditFieldError,
  } = useForm<CreateTeamFormValues>({ resolver: zodResolver(createTeamSchema) });

  useEffect(() => {
    if (!team) return;
    reset({ name: team.name, department: team.department ?? "", leadUserId: team.leadUserId ?? "", regionId: team.regionId ?? "" });
  }, [team, reset]);

  const onSaveEdits = handleSubmit(async (values) => {
    if (!teamId) return;
    setEditError(null);
    try {
      const updated = await updateTeam(teamId, {
        name: values.name,
        department: blankToUndefined(values.department),
        leadUserId: blankToUndefined(values.leadUserId),
        regionId: blankToUndefined(values.regionId),
      });
      setTeam(updated);
    } catch (error) {
      setEditError(applyServerErrors(error, setEditFieldError));
    }
  });

  async function handleDelete() {
    if (!teamId || !window.confirm("Delete this team? Members keep their team assignment but the team stops appearing in lists.")) return;
    setIsDeleting(true);
    try {
      await deleteTeam(teamId);
      navigate("/teams");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this team.");
      setIsDeleting(false);
    }
  }

  if (error && !team) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!team) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  const lead = users.find((u) => u.id === team.leadUserId);
  const members = users.filter((u) => u.teamId === team.id);
  const region = regions.find((r) => r.id === team.regionId);

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/teams" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Teams
          </Link>
          <h1 className="mt-1 text-2xl font-semibold text-slate-900">{team.name}</h1>
        </div>
        <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
          Delete
        </Button>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="grid gap-4 sm:grid-cols-2">
        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Overview</h2>
          <dl className="mt-3 flex flex-col gap-2 text-sm">
            <div className="flex justify-between gap-4">
              <dt className="text-slate-500">Department</dt>
              <dd className="text-slate-900">{team.department ?? "—"}</dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt className="text-slate-500">Lead</dt>
              <dd className="text-slate-900">
                {lead ? (
                  <Link to={`/users/${lead.id}`} className="hover:underline">
                    {lead.fullName}
                  </Link>
                ) : (
                  "—"
                )}
              </dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt className="text-slate-500">Region</dt>
              <dd className="text-slate-900">
                {region ? (
                  <Link to={`/regions/${region.id}`} className="hover:underline">
                    {region.name}
                  </Link>
                ) : (
                  "—"
                )}
              </dd>
            </div>
          </dl>
        </div>

        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Members ({members.length})</h2>
          <p className="mt-1 text-xs text-slate-400">Assign a teammate to this team from their own profile page.</p>
          <ul className="mt-3 flex flex-col gap-1.5 text-sm">
            {members.length === 0 && <li className="text-slate-400">No members yet.</li>}
            {members.map((member) => (
              <li key={member.id}>
                <Link to={`/users/${member.id}`} className="text-slate-900 hover:underline">
                  {member.fullName}
                </Link>
              </li>
            ))}
          </ul>
        </div>
      </div>

      <form onSubmit={onSaveEdits} noValidate className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Edit team</h2>

        {editError && <Alert variant="error">{editError}</Alert>}

        <TextField label="Name" error={errors.name?.message} {...register("name")} />
        <TextField label="Department" error={errors.department?.message} {...register("department")} />
        <Select
          label="Lead"
          placeholder="None"
          options={users.map((u) => ({ value: u.id, label: u.fullName }))}
          error={errors.leadUserId?.message}
          {...register("leadUserId")}
        />
        <Select
          label="Region"
          placeholder="None"
          options={regions.map((r) => ({ value: r.id, label: r.name }))}
          error={errors.regionId?.message}
          {...register("regionId")}
        />

        <div className="flex justify-end">
          <Button type="submit" isLoading={isSubmitting}>
            Save changes
          </Button>
        </div>
      </form>
    </div>
  );
}
