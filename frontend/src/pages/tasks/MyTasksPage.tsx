import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listActivities, updateActivityStatus } from "../../api/activities";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { ApiError } from "../../lib/apiClient";
import type { ActivityDto, RelatedToType } from "../../types/api";

const RELATED_TO_PATH: Record<RelatedToType, string> = {
  ACCOUNT: "/accounts",
  CONTACT: "/contacts",
  OPPORTUNITY: "/opportunities",
  LEAD: "/leads",
};

const TYPE_LABELS: Record<ActivityDto["type"], string> = {
  CALL: "Call",
  EMAIL: "Email",
  MEETING: "Meeting",
  TASK: "Task",
  NOTE: "Note",
};

/**
 * Every open call/email/meeting/task assigned to the caller, across every account/
 * contact/opportunity/lead - the one cross-record view Activity's per-record timeline
 * (ActivityTimeline) can't give you. Filtered and sorted client-side rather than via a
 * dedicated backend query param: the list endpoint already supports enough server-side
 * scoping (owner visibility, optional relatedTo) that adding a third axis (status) for
 * one page didn't seem worth a combinatorial explosion of repository methods yet.
 */
export default function MyTasksPage() {
  const [activities, setActivities] = useState<ActivityDto[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pendingId, setPendingId] = useState<string | null>(null);

  function reload() {
    listActivities({ size: 200, sort: "dueAt,asc" })
      .then((res) => setActivities(res.content))
      .catch((err: unknown) => setError(err instanceof ApiError ? err.message : "Could not load your tasks."));
  }

  useEffect(() => {
    reload();
  }, []);

  async function complete(activityId: string) {
    setPendingId(activityId);
    try {
      await updateActivityStatus(activityId, { status: "COMPLETED" });
      reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update this task.");
    } finally {
      setPendingId(null);
    }
  }

  const openActivities = activities?.filter((activity) => activity.status === "OPEN") ?? null;

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">My tasks</h1>
        <p className="mt-1 text-sm text-slate-500">Everything open and assigned to you, soonest due date first.</p>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Type</th>
              <th className="px-4 py-3 font-medium">Subject</th>
              <th className="px-4 py-3 font-medium">Related to</th>
              <th className="px-4 py-3 font-medium">Due</th>
              <th className="px-4 py-3 font-medium">Priority</th>
              <th className="px-4 py-3 font-medium" />
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {openActivities === null && (
              <tr>
                <td className="px-4 py-6 text-center text-slate-400" colSpan={6}>
                  Loading...
                </td>
              </tr>
            )}
            {openActivities !== null && openActivities.length === 0 && (
              <tr>
                <td className="px-4 py-6 text-center text-slate-400" colSpan={6}>
                  Nothing open - you're caught up.
                </td>
              </tr>
            )}
            {openActivities?.map((activity) => (
              <tr key={activity.id} className="hover:bg-slate-50">
                <td className="px-4 py-3 text-slate-600">{TYPE_LABELS[activity.type]}</td>
                <td className="px-4 py-3 font-medium text-slate-900">{activity.subject}</td>
                <td className="px-4 py-3">
                  <Link
                    to={`${RELATED_TO_PATH[activity.relatedToType]}/${activity.relatedToId}`}
                    className="text-slate-600 hover:underline"
                  >
                    View {activity.relatedToType.toLowerCase()}
                  </Link>
                </td>
                <td className="px-4 py-3 text-slate-600">{activity.dueAt ? new Date(activity.dueAt).toLocaleString() : "—"}</td>
                <td className="px-4 py-3 text-slate-600">{activity.priority ?? "—"}</td>
                <td className="px-4 py-3 text-right">
                  <Button variant="secondary" onClick={() => void complete(activity.id)} isLoading={pendingId === activity.id}>
                    Complete
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
