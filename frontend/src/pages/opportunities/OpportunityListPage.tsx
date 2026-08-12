import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { listOpportunities } from "../../api/opportunities";
import { SavedViewsBar, type SortFieldOption } from "../../components/saved-views/SavedViewsBar";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { ApiError } from "../../lib/apiClient";
import type { OpportunityDto, OpportunityStage, PageResponse, SavedViewSortDirection } from "../../types/api";

const PAGE_SIZE = 20;

const SORT_FIELD_OPTIONS: SortFieldOption[] = [
  { value: "createdAt", label: "Date created" },
  { value: "name", label: "Name" },
  { value: "amount", label: "Amount" },
  { value: "expectedCloseDate", label: "Expected close" },
];

const STAGE_CLASSES: Record<OpportunityStage, string> = {
  PROSPECTING: "bg-slate-100 text-slate-700",
  QUALIFICATION: "bg-blue-100 text-blue-700",
  PROPOSAL: "bg-amber-100 text-amber-700",
  NEGOTIATION: "bg-orange-100 text-orange-700",
  CLOSED_WON: "bg-emerald-100 text-emerald-700",
  CLOSED_LOST: "bg-red-100 text-red-700",
};

export function StageBadge({ stage }: { stage: OpportunityStage }) {
  return (
    <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${STAGE_CLASSES[stage]}`}>
      {stage.replace("_", " ")}
    </span>
  );
}

export default function OpportunityListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<OpportunityDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [sortField, setSortField] = useState("createdAt");
  const [sortDirection, setSortDirection] = useState<SavedViewSortDirection>("DESC");

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    listOpportunities({ page, size: PAGE_SIZE, sort: `${sortField},${sortDirection.toLowerCase()}` })
      .then((res) => {
        if (!cancelled) setResult(res);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load opportunities.");
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [page, sortField, sortDirection]);

  const visibleOpportunities = useMemo(() => {
    const content = result?.content ?? [];
    const query = search.trim().toLowerCase();
    if (!query) return content;
    return content.filter((opportunity) => opportunity.name.toLowerCase().includes(query));
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
          <h1 className="text-2xl font-semibold text-slate-900">Opportunities</h1>
          <p className="mt-1 text-sm text-slate-500">Deals in progress across your accounts.</p>
        </div>
        <Link to="/opportunities/new">
          <Button>New opportunity</Button>
        </Link>
      </div>

      <SavedViewsBar
        entityType="OPPORTUNITY"
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
              <th className="px-4 py-3 font-medium">Stage</th>
              <th className="px-4 py-3 font-medium">Amount</th>
              <th className="px-4 py-3 font-medium">Expected close</th>
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
            {!isLoading && visibleOpportunities.length === 0 && (
              <tr>
                <td className="px-4 py-6 text-center text-slate-400" colSpan={4}>
                  {search ? "No opportunities match this search." : "No opportunities yet."}
                </td>
              </tr>
            )}
            {visibleOpportunities.map((opportunity) => (
              <tr key={opportunity.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/opportunities/${opportunity.id}`} className="font-medium text-slate-900 hover:underline">
                    {opportunity.name}
                  </Link>
                </td>
                <td className="px-4 py-3">
                  <StageBadge stage={opportunity.stage} />
                </td>
                <td className="px-4 py-3 text-slate-600">
                  {opportunity.amount != null
                    ? `${opportunity.amount.toLocaleString()} ${opportunity.currency ?? ""}`.trim()
                    : "—"}
                </td>
                <td className="px-4 py-3 text-slate-600">{opportunity.expectedCloseDate ?? "—"}</td>
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
