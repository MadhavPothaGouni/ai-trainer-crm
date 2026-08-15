import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listEquipment } from "../../api/equipment";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { ApiError } from "../../lib/apiClient";
import type { EquipmentDto, EquipmentStatus, PageResponse } from "../../types/api";

const PAGE_SIZE = 20;

const STATUS_CLASSES: Record<EquipmentStatus, string> = {
  ACTIVE: "bg-emerald-100 text-emerald-700",
  OUT_OF_SERVICE: "bg-amber-100 text-amber-700",
  RETIRED: "bg-slate-100 text-slate-500",
};

export function EquipmentStatusBadge({ status }: { status: EquipmentStatus }) {
  return <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_CLASSES[status]}`}>{status.replace("_", " ")}</span>;
}

export default function EquipmentListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<EquipmentDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    listEquipment({ page, size: PAGE_SIZE, sort: "name,asc" })
      .then((res) => {
        if (!cancelled) setResult(res);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load equipment.");
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
          <h1 className="text-2xl font-semibold text-slate-900">Equipment</h1>
          <p className="mt-1 text-sm text-slate-500">The organization's physical asset inventory and its service status.</p>
        </div>
        <Link to="/equipment/new">
          <Button>Add equipment</Button>
        </Link>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Name</th>
              <th className="px-4 py-3 font-medium">Category</th>
              <th className="px-4 py-3 font-medium">Location</th>
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
                  No equipment yet.
                </td>
              </tr>
            )}
            {result?.content.map((item) => (
              <tr key={item.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/equipment/${item.id}`} className="font-medium text-slate-900 hover:underline">
                    {item.name}
                  </Link>
                </td>
                <td className="px-4 py-3 text-slate-600">{item.category ?? "—"}</td>
                <td className="px-4 py-3 text-slate-600">{item.location ?? "—"}</td>
                <td className="px-4 py-3">
                  <EquipmentStatusBadge status={item.status} />
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
