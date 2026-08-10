import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listAccounts } from "../../api/accounts";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { ApiError } from "../../lib/apiClient";
import type { AccountDto, PageResponse } from "../../types/api";

const PAGE_SIZE = 20;

export default function AccountListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<AccountDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    listAccounts({ page, size: PAGE_SIZE, sort: "name,asc" })
      .then((res) => {
        if (!cancelled) setResult(res);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load accounts.");
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
          <h1 className="text-2xl font-semibold text-slate-900">Accounts</h1>
          <p className="mt-1 text-sm text-slate-500">Companies your team is working with.</p>
        </div>
        <Link to="/accounts/new">
          <Button>New account</Button>
        </Link>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Name</th>
              <th className="px-4 py-3 font-medium">Industry</th>
              <th className="px-4 py-3 font-medium">Phone</th>
              <th className="px-4 py-3 font-medium">Employees</th>
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
                  No accounts yet.
                </td>
              </tr>
            )}
            {result?.content.map((account) => (
              <tr key={account.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/accounts/${account.id}`} className="font-medium text-slate-900 hover:underline">
                    {account.name}
                  </Link>
                </td>
                <td className="px-4 py-3 text-slate-600">{account.industry ?? "—"}</td>
                <td className="px-4 py-3 text-slate-600">{account.phone ?? "—"}</td>
                <td className="px-4 py-3 text-slate-600">{account.employeeCount ?? "—"}</td>
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
