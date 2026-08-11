import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listCalendarEvents } from "../../api/calendarEvents";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { ApiError } from "../../lib/apiClient";
import type { CalendarEventDto, PageResponse } from "../../types/api";

const PAGE_SIZE = 20;

export default function CalendarEventListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<CalendarEventDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    listCalendarEvents({ page, size: PAGE_SIZE, sort: "startAt,asc" })
      .then((res) => {
        if (!cancelled) setResult(res);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load calendar events.");
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
          <h1 className="text-2xl font-semibold text-slate-900">Calendar</h1>
          <p className="mt-1 text-sm text-slate-500">Scheduled events, optionally tied to an Account, Contact, Opportunity, Lead, or Ticket.</p>
        </div>
        <Link to="/calendar/new">
          <Button>New event</Button>
        </Link>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Title</th>
              <th className="px-4 py-3 font-medium">Starts</th>
              <th className="px-4 py-3 font-medium">Ends</th>
              <th className="px-4 py-3 font-medium">Location</th>
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
                  No events scheduled yet.
                </td>
              </tr>
            )}
            {result?.content.map((event) => (
              <tr key={event.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/calendar/${event.id}`} className="font-medium text-slate-900 hover:underline">
                    {event.title}
                  </Link>
                </td>
                <td className="px-4 py-3 text-slate-600">{event.allDay ? new Date(event.startAt).toLocaleDateString() : new Date(event.startAt).toLocaleString()}</td>
                <td className="px-4 py-3 text-slate-600">{event.allDay ? new Date(event.endAt).toLocaleDateString() : new Date(event.endAt).toLocaleString()}</td>
                <td className="px-4 py-3 text-slate-600">{event.location ?? "—"}</td>
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
