import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { approveApprovalStep, cancelApprovalRequest, getApprovalRequest, rejectApprovalStep } from "../../api/approvals";
import { listUsers } from "../../api/users";
import { useAuth } from "../../auth/useAuth";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { TextArea } from "../../components/ui/TextArea";
import { ApiError } from "../../lib/apiClient";
import type { ApprovalRequestDto, ApprovalRequestStatus, ApprovalStepDto, ApprovalStepStatus, UserDto } from "../../types/api";

const REQUEST_STATUS_CLASSES: Record<ApprovalRequestStatus, string> = {
  PENDING: "bg-amber-100 text-amber-800",
  APPROVED: "bg-emerald-100 text-emerald-800",
  REJECTED: "bg-red-100 text-red-700",
  CANCELLED: "bg-slate-100 text-slate-600",
};

const STEP_STATUS_CLASSES: Record<ApprovalStepStatus, string> = {
  PENDING: "bg-slate-100 text-slate-600",
  APPROVED: "bg-emerald-100 text-emerald-800",
  REJECTED: "bg-red-100 text-red-700",
};

function relatedToLabel(relatedToType: string): string {
  return relatedToType[0] + relatedToType.slice(1).toLowerCase();
}

export default function ApprovalDetailPage() {
  const { requestId } = useParams<{ requestId: string }>();
  const { user } = useAuth();
  const [request, setRequest] = useState<ApprovalRequestDto | null>(null);
  const [users, setUsers] = useState<UserDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isCancelling, setIsCancelling] = useState(false);

  function reload() {
    if (!requestId) return;
    getApprovalRequest(requestId)
      .then((data) => setRequest(data))
      .catch((err: unknown) => setError(err instanceof ApiError ? err.message : "Could not load this approval request."));
  }

  useEffect(reload, [requestId]);

  useEffect(() => {
    listUsers({ size: 200 })
      .then((res) => setUsers(res.content))
      .catch(() => undefined);
  }, []);

  function userLabel(userId: string): string {
    if (userId === user?.id) return "You";
    return users.find((u) => u.id === userId)?.fullName ?? "Unknown teammate";
  }

  async function handleCancel() {
    if (!requestId || !window.confirm("Cancel this approval request?")) return;
    setIsCancelling(true);
    try {
      const updated = await cancelApprovalRequest(requestId);
      setRequest(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not cancel this approval request.");
    } finally {
      setIsCancelling(false);
    }
  }

  if (error && !request) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!request) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  const canCancel = request.status === "PENDING" && request.requestedByUserId === user?.id;

  return (
    <div className="flex max-w-2xl flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/approvals" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Approvals
          </Link>
          <h1 className="mt-1 text-2xl font-semibold text-slate-900">{request.title}</h1>
        </div>
        {canCancel && (
          <Button variant="danger" onClick={() => void handleCancel()} isLoading={isCancelling}>
            Cancel request
          </Button>
        )}
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-500">Overview</h2>
        <dl className="mt-3 flex flex-col gap-2 text-sm">
          <div className="flex justify-between gap-4">
            <dt className="text-slate-500">Status</dt>
            <dd>
              <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${REQUEST_STATUS_CLASSES[request.status]}`}>
                {request.status}
              </span>
            </dd>
          </div>
          <div className="flex justify-between gap-4">
            <dt className="text-slate-500">Related to</dt>
            <dd className="text-slate-900">{relatedToLabel(request.relatedToType)}</dd>
          </div>
          <div className="flex justify-between gap-4">
            <dt className="text-slate-500">Requested by</dt>
            <dd className="text-slate-900">{userLabel(request.requestedByUserId)}</dd>
          </div>
          <div className="flex justify-between gap-4">
            <dt className="text-slate-500">Submitted</dt>
            <dd className="text-slate-900">{new Date(request.createdAt).toLocaleString()}</dd>
          </div>
          {request.decidedAt && (
            <div className="flex justify-between gap-4">
              <dt className="text-slate-500">Decided</dt>
              <dd className="text-slate-900">{new Date(request.decidedAt).toLocaleString()}</dd>
            </div>
          )}
        </dl>
      </div>

      <div className="flex flex-col gap-3">
        <h2 className="text-sm font-medium text-slate-900">Approval chain</h2>
        {request.steps.map((step) => (
          <StepCard key={step.id} requestId={request.id} step={step} approverLabel={userLabel(step.approverUserId)} onDecided={reload} />
        ))}
      </div>
    </div>
  );
}

/** Each step gets its own comment textarea/submit state - approving step 1 shouldn't clear or block step 2's (not-yet-actionable) form. */
function StepCard({
  requestId,
  step,
  approverLabel,
  onDecided,
}: {
  requestId: string;
  step: ApprovalStepDto;
  approverLabel: string;
  onDecided: () => void;
}) {
  const { user } = useAuth();
  const [comment, setComment] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isDeciding, setIsDeciding] = useState(false);

  const isMyTurn = step.actionable && step.approverUserId === user?.id;

  async function decide(approve: boolean) {
    setIsDeciding(true);
    setError(null);
    try {
      const action = approve ? approveApprovalStep : rejectApprovalStep;
      await action(requestId, step.stepNumber, { comment: comment || undefined });
      onDecided();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not record your decision.");
    } finally {
      setIsDeciding(false);
    }
  }

  return (
    <div className={`rounded-lg border bg-white p-4 ${isMyTurn ? "border-amber-300 ring-1 ring-amber-200" : "border-slate-200"}`}>
      <div className="flex items-center justify-between">
        <span className="flex items-center gap-2 text-sm font-medium text-slate-900">
          <span className="inline-block w-6 rounded-full bg-slate-100 text-center text-xs font-medium text-slate-600">
            {step.stepNumber}
          </span>
          {approverLabel}
        </span>
        <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${STEP_STATUS_CLASSES[step.status]}`}>
          {step.status}
        </span>
      </div>

      {step.comment && <p className="mt-2 text-sm text-slate-600">&ldquo;{step.comment}&rdquo;</p>}
      {step.decidedAt && <p className="mt-1 text-xs text-slate-400">{new Date(step.decidedAt).toLocaleString()}</p>}

      {isMyTurn && (
        <div className="mt-3 flex flex-col gap-3 border-t border-slate-100 pt-3">
          {error && <Alert variant="error">{error}</Alert>}
          <TextArea label="Comment (optional)" value={comment} onChange={(e) => setComment(e.target.value)} rows={2} />
          <div className="flex justify-end gap-3">
            <Button variant="danger" onClick={() => void decide(false)} isLoading={isDeciding}>
              Reject
            </Button>
            <Button onClick={() => void decide(true)} isLoading={isDeciding}>
              Approve
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
