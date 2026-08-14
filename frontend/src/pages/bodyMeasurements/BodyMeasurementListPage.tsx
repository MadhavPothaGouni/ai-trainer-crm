import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listBodyMeasurements } from "../../api/bodyMeasurements";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { ApiError } from "../../lib/apiClient";
import type { BodyMeasurementDto, PageResponse } from "../../types/api";

const PAGE_SIZE = 20;

function formatWeight(weightValue: number | null, weightUnit: string | null): string {
  if (weightValue == null) return "—";
  return weightUnit ? `${weightValue} ${weightUnit}` : `${weightValue}`;
}

export default function BodyMeasurementListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<BodyMeasurementDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    listBodyMeasurements({ page, size: PAGE_SIZE, sort: "measuredAt,desc" })
      .then((res) => {
        if (!cancelled) setResult(res);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load body measurements.");
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
          <h1 className="text-2xl font-semibold text-slate-900">Body Measurements</h1>
          <p className="mt-1 text-sm text-slate-500">Periodic check-ins tracking a client's weight, body fat, and circumference over time.</p>
        </div>
        <Link to="/body-measurements/new">
          <Button>New check-in</Button>
        </Link>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Date</th>
              <th className="px-4 py-3 font-medium">Weight</th>
              <th className="px-4 py-3 font-medium">Body fat %</th>
              <th className="px-4 py-3 font-medium">Notes</th>
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
                  No body measurements yet.
                </td>
              </tr>
            )}
            {result?.content.map((measurement) => (
              <tr key={measurement.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/body-measurements/${measurement.id}`} className="font-medium text-slate-900 hover:underline">
                    {measurement.measuredAt}
                  </Link>
                </td>
                <td className="px-4 py-3 text-slate-600">{formatWeight(measurement.weightValue, measurement.weightUnit)}</td>
                <td className="px-4 py-3 text-slate-600">{measurement.bodyFatPercent != null ? `${measurement.bodyFatPercent}%` : "—"}</td>
                <td className="px-4 py-3 text-slate-600">{measurement.notes ?? "—"}</td>
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
