import { useEffect, useState } from "react";
import { getMyOrganization } from "../api/organizations";
import { useAuth } from "../auth/useAuth";
import { Alert } from "../components/ui/Alert";
import { ApiError } from "../lib/apiClient";
import type { OrganizationDto } from "../types/api";

export default function DashboardPage() {
  const { user } = useAuth();
  const [organization, setOrganization] = useState<OrganizationDto | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    getMyOrganization()
      .then((org) => {
        if (!cancelled) setOrganization(org);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load organization.");
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Welcome, {user?.firstName}</h1>
        <p className="mt-1 text-sm text-slate-500">
          {organization ? organization.name : "Loading your organization..."}
        </p>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="grid gap-4 sm:grid-cols-2">
        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Your account</h2>
          <dl className="mt-3 flex flex-col gap-2 text-sm">
            <div className="flex justify-between">
              <dt className="text-slate-500">Email</dt>
              <dd className="text-slate-900">{user?.email}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-slate-500">Status</dt>
              <dd className="text-slate-900">{user?.status}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-slate-500">Roles</dt>
              <dd className="text-slate-900">{user?.roles.join(", ")}</dd>
            </div>
          </dl>
        </div>

        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Organization</h2>
          <dl className="mt-3 flex flex-col gap-2 text-sm">
            <div className="flex justify-between">
              <dt className="text-slate-500">Name</dt>
              <dd className="text-slate-900">{organization?.name ?? "—"}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-slate-500">Currency</dt>
              <dd className="text-slate-900">{organization?.defaultCurrency ?? "—"}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-slate-500">Timezone</dt>
              <dd className="text-slate-900">{organization?.timezone ?? "—"}</dd>
            </div>
          </dl>
        </div>
      </div>

      <p className="text-sm text-slate-400">
        This is a scaffold - team management, roles, and the rest of the CRM workspace land in a
        later pass.
      </p>
    </div>
  );
}
