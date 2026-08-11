import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import {
  createNotification,
  deleteNotification,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from "../../api/notifications";
import { listUsers } from "../../api/users";
import { RelatedToPicker } from "../../components/crm/RelatedToPicker";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createNotificationSchema, type CreateNotificationFormValues } from "../../lib/validation";
import type { CrmRecordType, NotificationDto, NotificationType, PageResponse, UserDto } from "../../types/api";
import { NOTIFICATION_TYPES } from "../../types/api";

const PAGE_SIZE = 20;

const TYPE_LABELS: Record<NotificationType, string> = {
  ASSIGNMENT: "Assignment",
  MENTION: "Mention",
  REMINDER: "Reminder",
  GENERAL: "General",
};

function NotificationTypeBadge({ type }: { type: NotificationType }) {
  return <span className="inline-block rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-700">{TYPE_LABELS[type]}</span>;
}

export default function NotificationsPage() {
  const [page, setPage] = useState(0);
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [result, setResult] = useState<PageResponse<NotificationDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [users, setUsers] = useState<UserDto[]>([]);

  function reload() {
    setIsLoading(true);
    listNotifications({ page, size: PAGE_SIZE, sort: "createdAt,desc", unreadOnly })
      .then((res) => setResult(res))
      .catch((err: unknown) => setError(err instanceof ApiError ? err.message : "Could not load notifications."))
      .finally(() => setIsLoading(false));
  }

  useEffect(() => {
    reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, unreadOnly]);

  useEffect(() => {
    listUsers({ size: 100 })
      .then((res) => setUsers(res.content))
      .catch(() => undefined);
  }, []);

  async function handleMarkRead(notificationId: string) {
    try {
      await markNotificationRead(notificationId);
      reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not mark this notification read.");
    }
  }

  async function handleDelete(notificationId: string) {
    try {
      await deleteNotification(notificationId);
      reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this notification.");
    }
  }

  async function handleMarkAllRead() {
    try {
      await markAllNotificationsRead();
      reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not mark every notification read.");
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Notifications</h1>
          <p className="mt-1 text-sm text-slate-500">Your own inbox - nobody else, at any role, can see these.</p>
        </div>
        <Button variant="secondary" onClick={() => void handleMarkAllRead()}>
          Mark all read
        </Button>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <label className="flex w-fit items-center gap-2 text-sm text-slate-700">
        <input
          type="checkbox"
          className="h-4 w-4 rounded border-slate-300"
          checked={unreadOnly}
          onChange={(e) => {
            setPage(0);
            setUnreadOnly(e.target.checked);
          }}
        />
        Unread only
      </label>

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Type</th>
              <th className="px-4 py-3 font-medium">Title</th>
              <th className="px-4 py-3 font-medium">Received</th>
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
                  Nothing here.
                </td>
              </tr>
            )}
            {result?.content.map((notification) => (
              <tr key={notification.id} className={notification.readAt ? "" : "bg-blue-50/40"}>
                <td className="px-4 py-3">
                  <NotificationTypeBadge type={notification.type} />
                </td>
                <td className="px-4 py-3">
                  <p className="font-medium text-slate-900">{notification.title}</p>
                  {notification.body && <p className="mt-0.5 text-xs text-slate-500">{notification.body}</p>}
                </td>
                <td className="px-4 py-3 text-slate-500">{new Date(notification.createdAt).toLocaleString()}</td>
                <td className="px-4 py-3 text-right">
                  <div className="flex justify-end gap-3">
                    {!notification.readAt && (
                      <button
                        type="button"
                        onClick={() => void handleMarkRead(notification.id)}
                        className="text-xs font-medium text-slate-500 hover:text-slate-900 hover:underline"
                      >
                        Mark read
                      </button>
                    )}
                    <button
                      type="button"
                      onClick={() => void handleDelete(notification.id)}
                      className="text-xs font-medium text-red-600 hover:underline"
                    >
                      Delete
                    </button>
                  </div>
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

      <SendNotificationForm users={users} onSent={reload} />
    </div>
  );
}

/** Any org member can notify any other org member - see NotificationService's javadoc for why there's no permission gate on this. */
function SendNotificationForm({ users, onSent }: { users: UserDto[]; onSent: () => void }) {
  const [formError, setFormError] = useState<string | null>(null);
  const [sentMessage, setSentMessage] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateNotificationFormValues>({
    resolver: zodResolver(createNotificationSchema),
    defaultValues: { type: "GENERAL", relatedToType: "", relatedToId: "" },
  });

  const relatedToType = watch("relatedToType") ?? "";
  const relatedToId = watch("relatedToId") ?? "";

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    setSentMessage(null);
    try {
      await createNotification({
        recipientUserId: values.recipientUserId,
        type: values.type as NotificationType,
        title: values.title,
        body: blankToUndefined(values.body),
        relatedToType: blankToUndefined(values.relatedToType) as CrmRecordType | undefined,
        relatedToId: blankToUndefined(values.relatedToId),
      });
      reset({ recipientUserId: "", type: "GENERAL", title: "", body: "", relatedToType: "", relatedToId: "" });
      setSentMessage("Notification sent.");
      onSent();
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
      <h2 className="text-sm font-medium text-slate-900">Notify a teammate</h2>

      {formError && <Alert variant="error">{formError}</Alert>}
      {sentMessage && <Alert variant="success">{sentMessage}</Alert>}

      <div className="grid gap-4 sm:grid-cols-2">
        <Select
          label="Teammate"
          placeholder="Choose a teammate"
          options={users.map((u) => ({ value: u.id, label: u.fullName }))}
          error={errors.recipientUserId?.message}
          {...register("recipientUserId")}
        />
        <Select
          label="Type"
          options={NOTIFICATION_TYPES.map((type) => ({ value: type, label: TYPE_LABELS[type] }))}
          error={errors.type?.message}
          {...register("type")}
        />
      </div>

      <TextField label="Title" error={errors.title?.message} {...register("title")} />
      <TextArea label="Message" error={errors.body?.message} {...register("body")} />

      <RelatedToPicker
        allowEmpty
        relatedToType={relatedToType}
        relatedToId={relatedToId}
        onChange={(type, id) => {
          setValue("relatedToType", type, { shouldValidate: true });
          setValue("relatedToId", id, { shouldValidate: true });
        }}
        typeError={errors.relatedToType?.message}
        idError={errors.relatedToId?.message}
      />

      <div className="flex justify-end">
        <Button type="submit" isLoading={isSubmitting}>
          Send
        </Button>
      </div>
    </form>
  );
}
