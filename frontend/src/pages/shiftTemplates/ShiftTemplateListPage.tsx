import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listShiftTemplates } from "../../api/shiftTemplates";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { ApiError } from "../../lib/apiClient";
import type { PageResponse, ShiftTemplateDto } from "../../types/api";

const PAGE_SIZE = 20;

function formatTime(value: string): string {
  const [hours, minutes] = value.split(":");
  const date = new Date();
  date.setHours(Number(hours), Number(minutes), 0, 0);
  return date.toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });
}

export default function ShiftTemplateListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<ShiftTemplateDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    listShiftTemplates({ page, size: PAGE_SIZE, sort: "name,asc" })
      .then((res) => {
        if (!cancelled) setResult(res);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load shift templates.");
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
          <h1 className="text-2xl font-semibold text-slate-900">Shift Templates</h1>
          <p className="mt-1 text-sm text-slate-500">Recurring weekly patterns that actual shifts get scheduled from.</p>
        </div>
        <Link to="/shift-templates/new">
          <Button>New template</Button>
        </Link>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Name</th>
              <th className="px-4 py-3 font-medium">Day</th>
              <th className="px-4 py-3 font-medium">Time</th>
              <th className="px-4 py-3 font-medium">Role</th>
              <th className="px-4 py-3 font-medium">Status</th>
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
                  No shift templates yet.
                </td>
              </tr>
            )}
            {result?.content.map((template) => (
              <tr key={template.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/shift-templates/${template.id}`} className="font-medium text-slate-900 hover:underline">
                    {template.name}
                  </Link>
                </td>
                <td className="px-4 py-3 text-slate-600">{template.dayOfWeek.charAt(0) + template.dayOfWeek.slice(1).toLowerCase()}</td>
                <td className="px-4 py-3 text-slate-600">
                  {formatTime(template.startTime)} – {formatTime(template.endTime)}
                </td>
                <td className="px-4 py-3 text-slate-600">{template.role ?? "—"}</td>
                <td className="px-4 py-3">
                  {template.active ? (
                    <span className="inline-block rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-medium text-emerald-700">Active</span>
                  ) : (
                    <span className="inline-block rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-500">Inactive</span>
                  )}
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
