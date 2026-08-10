import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listRoles } from "../../api/roles";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { ApiError } from "../../lib/apiClient";
import type { RoleDto } from "../../types/api";

export default function RoleListPage() {
  const [roles, setRoles] = useState<RoleDto[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    listRoles()
      .then((data) => {
        if (!cancelled) setRoles(data);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load roles.");
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Roles</h1>
          <p className="mt-1 text-sm text-slate-500">
            OWNER, ADMIN, and MEMBER are built in and read-only. Custom roles can be tailored to your team.
          </p>
        </div>
        <Link to="/roles/new">
          <Button>New role</Button>
        </Link>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Name</th>
              <th className="px-4 py-3 font-medium">Description</th>
              <th className="px-4 py-3 font-medium">Permissions</th>
              <th className="px-4 py-3 font-medium">Type</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {roles === null && (
              <tr>
                <td className="px-4 py-6 text-center text-slate-400" colSpan={4}>
                  Loading...
                </td>
              </tr>
            )}
            {roles?.map((role) => (
              <tr key={role.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/roles/${role.id}`} className="font-medium text-slate-900 hover:underline">
                    {role.name}
                  </Link>
                </td>
                <td className="px-4 py-3 text-slate-600">{role.description ?? "—"}</td>
                <td className="px-4 py-3 text-slate-600">{role.permissions.length}</td>
                <td className="px-4 py-3">
                  <span
                    className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${
                      role.systemRole ? "bg-slate-100 text-slate-600" : "bg-blue-100 text-blue-700"
                    }`}
                  >
                    {role.systemRole ? "Built in" : "Custom"}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
