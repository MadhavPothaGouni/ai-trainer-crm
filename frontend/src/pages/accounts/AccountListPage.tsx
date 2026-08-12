import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { listAccounts } from "../../api/accounts";
import { SavedViewsBar, type SortFieldOption } from "../../components/saved-views/SavedViewsBar";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { ApiError } from "../../lib/apiClient";
import type { AccountDto, PageResponse, SavedViewSortDirection } from "../../types/api";

const PAGE_SIZE = 20;

const SORT_FIELD_OPTIONS: SortFieldOption[] = [
  { value: "name", label: "Name" },
  { value: "createdAt", label: "Date created" },
  { value: "industry", label: "Industry" },
];

export default function AccountListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<AccountDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [sortField, setSortField] = useState("name");
  const [sortDirection, setSortDirection] = useState<SavedViewSortDirection>("ASC");

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    listAccounts({ page, size: PAGE_SIZE, sort: `${sortField},${sortDirection.toLowerCase()}` })
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
  }, [page, sortField, sortDirection]);

  const visibleAccounts = useMemo(() => {
    const content = result?.content ?? [];
    const query = search.trim().toLowerCase();
    if (!query) return content;
    return content.filter(
      (account) => account.name.toLowerCase().includes(query) || (account.industry ?? "").toLowerCase().includes(query),
    );
  }, [result, search]);

  function handleSortChange(field: string, direction: SavedViewSortDirection) {
    setSortField(field);
    setSortDirection(direction);
    setPage(0);
  }

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

      <SavedViewsBar
        entityType="ACCOUNT"
        search={search}
        onSearchChange={setSearch}
        sortField={sortField}
        sortDirection={sortDirection}
        sortFieldOptions={SORT_FIELD_OPTIONS}
        onSortChange={handleSortChange}
      />

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
            {!isLoading && visibleAccounts.length === 0 && (
              <tr>
                <td className="px-4 py-6 text-center text-slate-400" colSpan={4}>
                  {search ? "No accounts match this search." : "No accounts yet."}
                </td>
              </tr>
            )}
            {visibleAccounts.map((account) => (
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
