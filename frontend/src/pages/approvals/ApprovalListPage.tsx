import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listApprovalRequests, listMyApprovalTasks } from "../../api/approvals";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { ApiError } from "../../lib/apiClient";
import type { ApprovalRequestDto, ApprovalRequestStatus, ApprovalTaskDto, PageResponse } from "../../types/api";

const PAGE_SIZE = 20;

const STATUS_CLASSES: Record<ApprovalRequestStatus, string> = {
  PENDING: "bg-amber-100 text-amber-800",
  APPROVED: "bg-emerald-100 text-emerald-800",
  REJECTED: "bg-red-100 text-red-700",
  CANCELLED: "bg-slate-100 text-slate-600",
};

function StatusBadge({ status }: { status: ApprovalRequestStatus }) {
  return <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_CLASSES[status]}`}>{status}</span>;
}

function relatedToLabel(relatedToType: string): string {
  return relatedToType[0] + relatedToType.slice(1).toLowerCase();
}

/**
 * Two separately-fetched views sharing one page, not one merged query - see
 * ApprovalRequestService's javadoc on the backend (list() vs. myApprovalTasks()) for why
 * "requests I submitted" and "requests I need to act on" are structurally different reads.
 */
export default function ApprovalListPage() {
  const [tab, setTab] = useState<"requests" | "my-approvals">("requests");
  const [page, setPage] = useState(0);

  useEffect(() => setPage(0), [tab]);

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Approvals</h1>
          <p className="mt-1 text-sm text-slate-500">Multi-step sign-off chains on Quotes, Orders, and Opportunities.</p>
        </div>
        <Link to="/approvals/new">
          <Button>New approval request</Button>
        </Link>
      </div>

      <div className="flex w-fit gap-1 rounded-lg border border-slate-200 bg-white p-1">
        <button
          type="button"
          onClick={() => setTab("requests")}
          className={`rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
            tab === "requests" ? "bg-slate-900 text-white" : "text-slate-600 hover:text-slate-900"
          }`}
        >
          All requests
        </button>
        <button
          type="button"
          onClick={() => setTab("my-approvals")}
          className={`rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
            tab === "my-approvals" ? "bg-slate-900 text-white" : "text-slate-600 hover:text-slate-900"
          }`}
        >
          My approvals
        </button>
      </div>

      {tab === "requests" ? <AllRequestsTab page={page} onPageChange={setPage} /> : <MyApprovalsTab page={page} onPageChange={setPage} />}
    </div>
  );
}

function AllRequestsTab({ page, onPageChange }: { page: number; onPageChange: (page: number) => void }) {
  const [result, setResult] = useState<PageResponse<ApprovalRequestDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    listApprovalRequests({ page, size: PAGE_SIZE, sort: "createdAt,desc" })
      .then((res) => {
        if (!cancelled) setResult(res);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load approval requests.");
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [page]);

  return (
    <div className="flex flex-col gap-4">
      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Title</th>
              <th className="px-4 py-3 font-medium">Related to</th>
              <th className="px-4 py-3 font-medium">Status</th>
              <th className="px-4 py-3 font-medium">Step</th>
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
                  No approval requests yet.
                </td>
              </tr>
            )}
            {result?.content.map((request) => (
              <tr key={request.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/approvals/${request.id}`} className="font-medium text-slate-900 hover:underline">
                    {request.title}
                  </Link>
                </td>
                <td className="px-4 py-3 text-slate-500">{relatedToLabel(request.relatedToType)}</td>
                <td className="px-4 py-3">
                  <StatusBadge status={request.status} />
                </td>
                <td className="px-4 py-3 text-slate-500">{request.status === "PENDING" ? `Step ${request.currentStepNumber}` : "—"}</td>
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
          onPageChange={onPageChange}
        />
      )}
    </div>
  );
}

function MyApprovalsTab({ page, onPageChange }: { page: number; onPageChange: (page: number) => void }) {
  const [result, setResult] = useState<PageResponse<ApprovalTaskDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    listMyApprovalTasks({ page, size: PAGE_SIZE, sort: "createdAt,asc" })
      .then((res) => {
        if (!cancelled) setResult(res);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load your pending approvals.");
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [page]);

  return (
    <div className="flex flex-col gap-4">
      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Request</th>
              <th className="px-4 py-3 font-medium">Related to</th>
              <th className="px-4 py-3 font-medium">Your step</th>
              <th className="px-4 py-3 font-medium" />
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
                  Nothing waiting on you.
                </td>
              </tr>
            )}
            {result?.content.map((task) => (
              <tr key={task.stepId} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/approvals/${task.approvalRequestId}`} className="font-medium text-slate-900 hover:underline">
                    {task.requestTitle}
                  </Link>
                </td>
                <td className="px-4 py-3 text-slate-500">{relatedToLabel(task.relatedToType)}</td>
                <td className="px-4 py-3 text-slate-500">Step {task.stepNumber}</td>
                <td className="px-4 py-3 text-right">
                  {task.actionable ? (
                    <span className="inline-block rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-medium text-amber-800">
                      Your turn
                    </span>
                  ) : (
                    <span className="text-xs text-slate-400">Waiting on an earlier step</span>
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
          onPageChange={onPageChange}
        />
      )}
    </div>
  );
}
