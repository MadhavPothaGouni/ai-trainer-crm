import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listClassSessions } from "../../api/classSessions";
import { listGroupClasses } from "../../api/groupClasses";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { ApiError } from "../../lib/apiClient";
import type { ClassSessionDto, ClassSessionStatus, GroupClassDto, PageResponse } from "../../types/api";

const PAGE_SIZE = 20;

const STATUS_CLASSES: Record<ClassSessionStatus, string> = {
  SCHEDULED: "bg-emerald-100 text-emerald-700",
  CANCELLED: "bg-slate-100 text-slate-500",
  COMPLETED: "bg-blue-100 text-blue-700",
};

export function ClassSessionStatusBadge({ status }: { status: ClassSessionStatus }) {
  return <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_CLASSES[status]}`}>{status}</span>;
}

export default function ClassSessionListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<ClassSessionDto> | null>(null);
  const [groupClasses, setGroupClasses] = useState<GroupClassDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    listClassSessions({ page, size: PAGE_SIZE, sort: "startsAt,desc" })
      .then((res) => {
        if (!cancelled) setResult(res);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load class sessions.");
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [page]);

  useEffect(() => {
    listGroupClasses({ size: 100, sort: "name,asc" })
      .then((res) => setGroupClasses(res.content))
      .catch(() => undefined);
  }, []);

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Class Sessions</h1>
          <p className="mt-1 text-sm text-slate-500">Scheduled occurrences of your group classes.</p>
        </div>
        <Link to="/class-sessions/new">
          <Button>Schedule session</Button>
        </Link>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Class</th>
              <th className="px-4 py-3 font-medium">Starts</th>
              <th className="px-4 py-3 font-medium">Ends</th>
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
                  No class sessions yet.
                </td>
              </tr>
            )}
            {result?.content.map((session) => (
              <tr key={session.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/class-sessions/${session.id}`} className="font-medium text-slate-900 hover:underline">
                    {groupClasses.find((groupClass) => groupClass.id === session.groupClassId)?.name ?? "Class session"}
                  </Link>
                </td>
                <td className="px-4 py-3 text-slate-600">{new Date(session.startsAt).toLocaleString()}</td>
                <td className="px-4 py-3 text-slate-600">{new Date(session.endsAt).toLocaleString()}</td>
                <td className="px-4 py-3">
                  <ClassSessionStatusBadge status={session.status} />
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
