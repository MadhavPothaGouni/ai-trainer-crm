import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listEmailMessages } from "../../api/emailMessages";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { ApiError } from "../../lib/apiClient";
import type { EmailDirection, EmailMessageDto, PageResponse } from "../../types/api";

const PAGE_SIZE = 20;

const DIRECTION_CLASSES: Record<EmailDirection, string> = {
  INBOUND: "bg-blue-100 text-blue-700",
  OUTBOUND: "bg-emerald-100 text-emerald-700",
};

export function EmailDirectionBadge({ direction }: { direction: EmailDirection }) {
  return (
    <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${DIRECTION_CLASSES[direction]}`}>
      {direction === "INBOUND" ? "Received" : "Sent"}
    </span>
  );
}

export default function EmailListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<EmailMessageDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    listEmailMessages({ page, size: PAGE_SIZE, sort: "sentAt,desc" })
      .then((res) => {
        if (!cancelled) setResult(res);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load emails.");
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
          <h1 className="text-2xl font-semibold text-slate-900">Emails</h1>
          <p className="mt-1 text-sm text-slate-500">Logged emails against Accounts, Contacts, Opportunities, Leads, and Tickets.</p>
        </div>
        <Link to="/emails/new">
          <Button>Log email</Button>
        </Link>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Subject</th>
              <th className="px-4 py-3 font-medium">Direction</th>
              <th className="px-4 py-3 font-medium">From</th>
              <th className="px-4 py-3 font-medium">To</th>
              <th className="px-4 py-3 font-medium">Sent</th>
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
                  No emails logged yet.
                </td>
              </tr>
            )}
            {result?.content.map((email) => (
              <tr key={email.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/emails/${email.id}`} className="font-medium text-slate-900 hover:underline">
                    {email.subject}
                  </Link>
                </td>
                <td className="px-4 py-3">
                  <EmailDirectionBadge direction={email.direction} />
                </td>
                <td className="px-4 py-3 text-slate-600">{email.fromAddress}</td>
                <td className="px-4 py-3 text-slate-600">{email.toAddresses}</td>
                <td className="px-4 py-3 text-slate-600">{new Date(email.sentAt).toLocaleString()}</td>
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
