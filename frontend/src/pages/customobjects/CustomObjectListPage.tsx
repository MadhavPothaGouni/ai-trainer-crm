import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listCustomObjects } from "../../api/customObjects";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { ApiError } from "../../lib/apiClient";
import type { CustomObjectDto, PageResponse } from "../../types/api";

const PAGE_SIZE = 20;

export default function CustomObjectListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<CustomObjectDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    listCustomObjects({ page, size: PAGE_SIZE, sort: "label,asc" })
      .then((res) => {
        if (!cancelled) setResult(res);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load custom objects.");
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
          <h1 className="text-2xl font-semibold text-slate-900">Custom objects</h1>
          <p className="mt-1 text-sm text-slate-500">Admin-defined entities, each with its own custom fields and records.</p>
        </div>
        <div className="flex gap-3">
          <Link to="/custom-fields">
            <Button variant="secondary">Manage custom fields</Button>
          </Link>
          <Link to="/custom-objects/new">
            <Button>New custom object</Button>
          </Link>
        </div>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Label</th>
              <th className="px-4 py-3 font-medium">API name</th>
              <th className="px-4 py-3 font-medium">Status</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {isLoading && (
              <tr>
                <td className="px-4 py-6 text-center text-slate-400" colSpan={3}>
                  Loading...
                </td>
              </tr>
            )}
            {!isLoading && result?.content.length === 0 && (
              <tr>
                <td className="px-4 py-6 text-center text-slate-400" colSpan={3}>
                  No custom objects yet.
                </td>
              </tr>
            )}
            {result?.content.map((object) => (
              <tr key={object.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/custom-objects/${object.id}`} className="font-medium text-slate-900 hover:underline">
                    {object.label}
                  </Link>
                  <p className="text-xs text-slate-400">{object.pluralLabel}</p>
                </td>
                <td className="px-4 py-3 font-mono text-xs text-slate-600">{object.apiName}</td>
                <td className="px-4 py-3">
                  <span
                    className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${
                      object.active ? "bg-emerald-100 text-emerald-700" : "bg-slate-100 text-slate-500"
                    }`}
                  >
                    {object.active ? "Active" : "Inactive"}
                  </span>
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
