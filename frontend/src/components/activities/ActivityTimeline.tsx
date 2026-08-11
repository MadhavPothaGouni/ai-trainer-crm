import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { createActivity, deleteActivity, listActivities, updateActivityStatus } from "../../api/activities";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createActivitySchema, type CreateActivityFormValues } from "../../lib/validation";
import { ACTIVITY_PRIORITIES, ACTIVITY_TYPES, type ActivityDto, type RelatedToType } from "../../types/api";
import { Alert } from "../ui/Alert";
import { Button } from "../ui/Button";
import { Select } from "../ui/Select";
import { TextArea } from "../ui/TextArea";
import { TextField } from "../ui/TextField";

const TYPE_LABELS: Record<ActivityDto["type"], string> = {
  CALL: "Call",
  EMAIL: "Email",
  MEETING: "Meeting",
  TASK: "Task",
  NOTE: "Note",
};

/** Calls/emails/meetings/tasks/notes logged against one CRM record - embedded at the bottom of the Account/Contact/Opportunity/Lead detail pages. */
export function ActivityTimeline({ relatedToType, relatedToId }: { relatedToType: RelatedToType; relatedToId: string }) {
  const [activities, setActivities] = useState<ActivityDto[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [pendingId, setPendingId] = useState<string | null>(null);

  function reload() {
    listActivities({ relatedToType, relatedToId, size: 50, sort: "createdAt,desc" })
      .then((res) => setActivities(res.content))
      .catch((err: unknown) => setError(err instanceof ApiError ? err.message : "Could not load activity."));
  }

  useEffect(() => {
    reload();
  }, [relatedToType, relatedToId]);

  const {
    register,
    handleSubmit,
    reset,
    setError: setFormFieldError,
    formState: { errors, isSubmitting },
  } = useForm<CreateActivityFormValues>({
    resolver: zodResolver(createActivitySchema),
    defaultValues: { type: "TASK" },
  });

  const onSubmit = handleSubmit(async (values) => {
    setError(null);
    try {
      await createActivity({
        type: values.type as ActivityDto["type"],
        subject: values.subject,
        description: blankToUndefined(values.description),
        priority: blankToUndefined(values.priority) as ActivityDto["priority"],
        dueAt: values.dueAt ? new Date(values.dueAt).toISOString() : undefined,
        relatedToType,
        relatedToId,
      });
      reset({ type: "TASK", subject: "", description: "", priority: "", dueAt: "" });
      setIsFormOpen(false);
      reload();
    } catch (err) {
      setError(applyServerErrors(err, setFormFieldError));
    }
  });

  async function toggleStatus(activity: ActivityDto) {
    setPendingId(activity.id);
    try {
      await updateActivityStatus(activity.id, { status: activity.status === "OPEN" ? "COMPLETED" : "OPEN" });
      reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update this activity.");
    } finally {
      setPendingId(null);
    }
  }

  async function remove(activityId: string) {
    if (!window.confirm("Delete this activity?")) return;
    setPendingId(activityId);
    try {
      await deleteActivity(activityId);
      reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this activity.");
      setPendingId(null);
    }
  }

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-5">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-medium text-slate-500">Activity</h2>
        <Button variant="secondary" onClick={() => setIsFormOpen((open) => !open)}>
          {isFormOpen ? "Cancel" : "Log activity"}
        </Button>
      </div>

      {error && (
        <div className="mt-3">
          <Alert variant="error">{error}</Alert>
        </div>
      )}

      {isFormOpen && (
        <form onSubmit={onSubmit} noValidate className="mt-4 flex flex-col gap-3 rounded-md border border-slate-200 p-4">
          <div className="grid gap-3 sm:grid-cols-3">
            <Select
              label="Type"
              options={ACTIVITY_TYPES.map((type) => ({ value: type, label: TYPE_LABELS[type] }))}
              error={errors.type?.message}
              {...register("type")}
            />
            <Select
              label="Priority"
              placeholder="None"
              options={ACTIVITY_PRIORITIES.map((priority) => ({ value: priority, label: priority }))}
              error={errors.priority?.message}
              {...register("priority")}
            />
            <TextField label="Due" type="datetime-local" error={errors.dueAt?.message} {...register("dueAt")} />
          </div>
          <TextField label="Subject" error={errors.subject?.message} {...register("subject")} />
          <TextArea label="Notes" error={errors.description?.message} {...register("description")} />
          <div className="flex justify-end">
            <Button type="submit" isLoading={isSubmitting}>
              Save
            </Button>
          </div>
        </form>
      )}

      <ul className="mt-4 flex flex-col gap-3">
        {activities === null && <li className="text-sm text-slate-400">Loading...</li>}
        {activities !== null && activities.length === 0 && (
          <li className="text-sm text-slate-400">Nothing logged yet.</li>
        )}
        {activities?.map((activity) => (
          <li key={activity.id} className="flex items-start justify-between gap-4 border-t border-slate-100 pt-3 first:border-t-0 first:pt-0">
            <div>
              <div className="flex items-center gap-2 text-sm">
                <span className="rounded bg-slate-100 px-1.5 py-0.5 text-xs font-medium text-slate-600">
                  {TYPE_LABELS[activity.type]}
                </span>
                <span className={`font-medium ${activity.status === "COMPLETED" ? "text-slate-400 line-through" : "text-slate-900"}`}>
                  {activity.subject}
                </span>
                {activity.priority && <span className="text-xs text-slate-400">{activity.priority}</span>}
              </div>
              {activity.description && <p className="mt-1 whitespace-pre-wrap text-sm text-slate-600">{activity.description}</p>}
              {activity.dueAt && <p className="mt-1 text-xs text-slate-400">Due {new Date(activity.dueAt).toLocaleString()}</p>}
            </div>
            <div className="flex shrink-0 gap-2">
              {activity.type !== "NOTE" && (
                <Button variant="secondary" onClick={() => void toggleStatus(activity)} isLoading={pendingId === activity.id}>
                  {activity.status === "OPEN" ? "Complete" : "Reopen"}
                </Button>
              )}
              <Button variant="danger" onClick={() => void remove(activity.id)} isLoading={pendingId === activity.id}>
                Delete
              </Button>
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}
