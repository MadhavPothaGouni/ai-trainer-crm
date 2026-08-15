import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listEquipment } from "../../api/equipment";
import { listMaintenanceLogs } from "../../api/maintenanceLogs";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { ApiError } from "../../lib/apiClient";
import type { EquipmentDto, MaintenanceLogDto, PageResponse } from "../../types/api";

const PAGE_SIZE = 20;

export default function MaintenanceLogListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<MaintenanceLogDto> | null>(null);
  const [equipment, setEquipment] = useState<EquipmentDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    listMaintenanceLogs({ page, size: PAGE_SIZE, sort: "performedAt,desc" })
      .then((res) => {
        if (!cancelled) setResult(res);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load maintenance logs.");
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [page]);

  useEffect(() => {
    listEquipment({ size: 100, sort: "name,asc" })
      .then((res) => setEquipment(res.content))
      .catch(() => undefined);
  }, []);

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Maintenance Logs</h1>
          <p className="mt-1 text-sm text-slate-500">Service history for the organization's equipment.</p>
        </div>
        <Link to="/maintenance-logs/new">
          <Button>Log maintenance</Button>
        </Link>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Equipment</th>
              <th className="px-4 py-3 font-medium">Type</th>
              <th className="px-4 py-3 font-medium">Performed</th>
              <th className="px-4 py-3 font-medium">Cost</th>
              <th className="px-4 py-3 font-medium">Next due</th>
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
            {!isLoading && result?.content.length === 0 && (
              <tr>
                <td className="px-4 py-6 text-center text-slate-400" colSpan={5}>
                  No maintenance logs yet.
                </td>
              </tr>
            )}
            {result?.content.map((log) => (
              <tr key={log.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/maintenance-logs/${log.id}`} className="font-medium text-slate-900 hover:underline">
                    {equipment.find((e) => e.id === log.equipmentId)?.name ?? "Equipment"}
                  </Link>
                </td>
                <td className="px-4 py-3 text-slate-600">{log.type}</td>
                <td className="px-4 py-3 text-slate-600">{new Date(log.performedAt).toLocaleDateString()}</td>
                <td className="px-4 py-3 text-slate-600">{log.cost != null ? log.cost.toLocaleString() : "—"}</td>
                <td className="px-4 py-3 text-slate-600">{log.nextDueDate ?? "—"}</td>
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
