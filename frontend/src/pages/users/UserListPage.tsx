import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listUsers } from "../../api/users";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { ApiError } from "../../lib/apiClient";
import type { PageResponse, UserDto, UserStatus } from "../../types/api";

const PAGE_SIZE = 20;

const STATUS_CLASSES: Record<UserStatus, string> = {
  PENDING_VERIFICATION: "bg-amber-100 text-amber-700",
  ACTIVE: "bg-emerald-100 text-emerald-700",
  SUSPENDED: "bg-orange-100 text-orange-700",
  DEACTIVATED: "bg-red-100 text-red-700",
};

export function UserStatusBadge({ status }: { status: UserStatus }) {
  return (
    <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_CLASSES[status]}`}>
      {status.replace("_", " ")}
    </span>
  );
}

export default function UserListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<UserDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    listUsers({ page, size: PAGE_SIZE, sort: "firstName,asc" })
      .then((res) => {
        if (!cancelled) setResult(res);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load your team.");
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [page]);

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Team</h1>
          <p className="mt-1 text-sm text-slate-500">Everyone with access to this organization.</p>
        </div>
        <Link to="/users/invite">
          <Button>Invite teammate</Button>
        </Link>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Name</th>
              <th className="px-4 py-3 font-medium">Email</th>
              <th className="px-4 py-3 font-medium">Roles</th>
              <th className="px-4 py-3 font-medium">Status</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {isLoading && (
              <tr>
                <td className="px-4 py-6 text-center text-slate-400" colSpan={4}>
                  Loading...
                </td>
              </tr>
            )}
            {!isLoading && result?.content.length === 0 && (
              <tr>
                <td className="px-4 py-6 text-center text-slate-400" colSpan={4}>
                  No teammates yet.
                </td>
              </tr>
            )}
            {result?.content.map((user) => (
              <tr key={user.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/users/${user.id}`} className="font-medium text-slate-900 hover:underline">
                    {user.fullName}
                  </Link>
                </td>
                <td className="px-4 py-3 text-slate-600">{user.email}</td>
                <td className="px-4 py-3 text-slate-600">{user.roles.join(", ")}</td>
                <td className="px-4 py-3">
                  <UserStatusBadge status={user.status} />
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
    </div>
  );
}
