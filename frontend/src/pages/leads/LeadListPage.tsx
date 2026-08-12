import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { listLeads } from "../../api/leads";
import { SavedViewsBar, type SortFieldOption } from "../../components/saved-views/SavedViewsBar";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { ApiError } from "../../lib/apiClient";
import type { LeadDto, LeadStatus, PageResponse, SavedViewSortDirection } from "../../types/api";

const PAGE_SIZE = 20;

const SORT_FIELD_OPTIONS: SortFieldOption[] = [
  { value: "createdAt", label: "Date created" },
  { value: "fullName", label: "Name" },
  { value: "companyName", label: "Company" },
  { value: "score", label: "Score" },
];

const STATUS_CLASSES: Record<LeadStatus, string> = {
  NEW: "bg-slate-100 text-slate-700",
  CONTACTED: "bg-blue-100 text-blue-700",
  QUALIFIED: "bg-amber-100 text-amber-700",
  UNQUALIFIED: "bg-red-100 text-red-700",
  CONVERTED: "bg-emerald-100 text-emerald-700",
};

export function StatusBadge({ status }: { status: LeadStatus }) {
  return <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_CLASSES[status]}`}>{status}</span>;
}

export default function LeadListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<LeadDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [sortField, setSortField] = useState("createdAt");
  const [sortDirection, setSortDirection] = useState<SavedViewSortDirection>("DESC");

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    listLeads({ page, size: PAGE_SIZE, sort: `${sortField},${sortDirection.toLowerCase()}` })
      .then((res) => {
        if (!cancelled) setResult(res);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load leads.");
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [page, sortField, sortDirection]);

  const visibleLeads = useMemo(() => {
    const content = result?.content ?? [];
    const query = search.trim().toLowerCase();
    if (!query) return content;
    return content.filter(
      (lead) => lead.fullName.toLowerCase().includes(query) || (lead.companyName ?? "").toLowerCase().includes(query),
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
          <h1 className="text-2xl font-semibold text-slate-900">Leads</h1>
          <p className="mt-1 text-sm text-slate-500">People and companies not yet qualified as accounts.</p>
        </div>
        <Link to="/leads/new">
          <Button>New lead</Button>
        </Link>
      </div>

      <SavedViewsBar
        entityType="LEAD"
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
              <th className="px-4 py-3 font-medium">Company</th>
              <th className="px-4 py-3 font-medium">Source</th>
              <th className="px-4 py-3 font-medium">Status</th>
              <th className="px-4 py-3 font-medium">Score</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {isLoading && (
              <tr>
                <td className="px-4 py-6 text-center text-slate-400" colSpan={5}>
                  Loading...
                </td>
              </tr>
            )}
            {!isLoading && visibleLeads.length === 0 && (
              <tr>
                <td className="px-4 py-6 text-center text-slate-400" colSpan={5}>
                  {search ? "No leads match this search." : "No leads yet."}
                </td>
              </tr>
            )}
            {visibleLeads.map((lead) => (
              <tr key={lead.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/leads/${lead.id}`} className="font-medium text-slate-900 hover:underline">
                    {lead.fullName}
                  </Link>
                </td>
                <td className="px-4 py-3 text-slate-600">{lead.companyName ?? "—"}</td>
                <td className="px-4 py-3 text-slate-600">{lead.source}</td>
                <td className="px-4 py-3">
                  <StatusBadge status={lead.status} />
                </td>
                <td className={`px-4 py-3 font-medium ${lead.score < 0 ? "text-red-600" : lead.score > 0 ? "text-emerald-700" : "text-slate-400"}`}>
                  {lead.score}
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
