import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link } from "react-router-dom";
import { createSlaPolicy, listSlaPolicies } from "../../api/sla";
import { listUsers } from "../../api/users";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { Select } from "../../components/ui/Select";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createSlaPolicySchema, toRequiredNumber, type CreateSlaPolicyFormValues } from "../../lib/validation";
import { TICKET_PRIORITIES, type PageResponse, type SlaPolicyDto, type TicketPriority, type UserDto } from "../../types/api";

const PAGE_SIZE = 20;

export default function SlaPolicyListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<SlaPolicyDto> | null>(null);
  const [users, setUsers] = useState<UserDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  function reload() {
    setIsLoading(true);
    listSlaPolicies({ page, size: PAGE_SIZE })
      .then((res) => setResult(res))
      .catch((err: unknown) => setError(err instanceof ApiError ? err.message : "Could not load SLA policies."))
      .finally(() => setIsLoading(false));
  }

  useEffect(() => {
    reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page]);

  useEffect(() => {
    listUsers({ size: 200 })
      .then((res) => setUsers(res.content))
      .catch(() => undefined);
  }, []);

  function userLabel(userId: string | null): string {
    if (!userId) return "—";
    return users.find((u) => u.id === userId)?.fullName ?? "Unknown teammate";
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">SLA Policies</h1>
        <p className="mt-1 text-sm text-slate-500">
          Per-priority response and resolution deadlines for Tickets. At most one active policy per priority.
        </p>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Name</th>
              <th className="px-4 py-3 font-medium">Priority</th>
              <th className="px-4 py-3 font-medium">Response target</th>
              <th className="px-4 py-3 font-medium">Resolution target</th>
              <th className="px-4 py-3 font-medium">Escalates to</th>
              <th className="px-4 py-3 font-medium">Active</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {isLoading && (
              <tr>
                <td className="px-4 py-6 text-center text-slate-400" colSpan={6}>
                  Loading...
                </td>
              </tr>
            )}
            {!isLoading && result?.content.length === 0 && (
              <tr>
                <td className="px-4 py-6 text-center text-slate-400" colSpan={6}>
                  No SLA policies yet.
                </td>
              </tr>
            )}
            {result?.content.map((policy) => (
              <tr key={policy.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/sla-policies/${policy.id}`} className="font-medium text-slate-900 hover:underline">
                    {policy.name}
                  </Link>
                </td>
                <td className="px-4 py-3 text-slate-500">{policy.priority}</td>
                <td className="px-4 py-3 text-slate-500">{policy.responseTargetMinutes} min</td>
                <td className="px-4 py-3 text-slate-500">{policy.resolutionTargetMinutes} min</td>
                <td className="px-4 py-3 text-slate-500">{userLabel(policy.escalateToUserId)}</td>
                <td className="px-4 py-3">
                  {policy.active ? (
                    <span className="inline-block rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-medium text-emerald-800">
                      Active
                    </span>
                  ) : (
                    <span className="inline-block rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-600">
                      Inactive
                    </span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {result && (
        <Pagination
          pageNumber={result.pageNumber}
          totalPages={result.totalPages}
          first={result.first}
          last={result.last}
          totalElements={result.totalElements}
          onPageChange={setPage}
        />
      )}

      <CreateSlaPolicyForm users={users} onCreated={reload} />
    </div>
  );
}

function CreateSlaPolicyForm({ users, onCreated }: { users: UserDto[]; onCreated: () => void }) {
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<CreateSlaPolicyFormValues>({
    resolver: zodResolver(createSlaPolicySchema),
    defaultValues: { name: "", priority: "", responseTargetMinutes: "", resolutionTargetMinutes: "", escalateToUserId: "" },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await createSlaPolicy({
        name: values.name,
        priority: values.priority as TicketPriority,
        responseTargetMinutes: toRequiredNumber(values.responseTargetMinutes),
        resolutionTargetMinutes: toRequiredNumber(values.resolutionTargetMinutes),
        escalateToUserId: blankToUndefined(values.escalateToUserId),
      });
      reset({ name: "", priority: "", responseTargetMinutes: "", resolutionTargetMinutes: "", escalateToUserId: "" });
      onCreated();
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
      <h2 className="text-sm font-medium text-slate-900">New SLA policy</h2>

      {formError && <Alert variant="error">{formError}</Alert>}

      <TextField label="Name" error={errors.name?.message} {...register("name")} />

      <div className="grid gap-4 sm:grid-cols-3">
        <Select
          label="Priority"
          placeholder="Choose a priority"
          options={TICKET_PRIORITIES.map((priority) => ({ value: priority, label: priority }))}
          error={errors.priority?.message}
          {...register("priority")}
        />
        <TextField
          label="Response target (minutes)"
          type="number"
          min={1}
          error={errors.responseTargetMinutes?.message}
          {...register("responseTargetMinutes")}
        />
        <TextField
          label="Resolution target (minutes)"
          type="number"
          min={1}
          error={errors.resolutionTargetMinutes?.message}
          {...register("resolutionTargetMinutes")}
        />
      </div>

      <Select
        label="Escalate to (optional)"
        placeholder="No escalation"
        options={users.map((u) => ({ value: u.id, label: u.fullName }))}
        error={errors.escalateToUserId?.message}
        {...register("escalateToUserId")}
      />

      <div className="flex justify-end">
        <Button type="submit" isLoading={isSubmitting}>
          Create policy
        </Button>
      </div>
    </form>
  );
}
