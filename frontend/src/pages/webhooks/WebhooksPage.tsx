import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { createWebhook, deleteWebhook, listWebhooks, updateWebhook } from "../../api/webhooks";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { createWebhookSchema, type CreateWebhookFormValues } from "../../lib/validation";
import type { WebhookSubscriptionDto } from "../../types/api";

/**
 * Webhook subscriptions, dispatched off the same domain events the audit
 * log listens to (see the backend WebhookDispatchListener's javadoc).
 * eventType left blank subscribes to everything; the signing secret is
 * shown persistently here (unlike an API key's raw value) because the
 * subscriber needs it forever to verify each delivery's signature.
 */
export default function WebhooksPage() {
  const [webhooks, setWebhooks] = useState<WebhookSubscriptionDto[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<CreateWebhookFormValues>({ resolver: zodResolver(createWebhookSchema) });

  function reload() {
    listWebhooks({ size: 100, sort: "createdAt,desc" })
      .then((res) => setWebhooks(res.content))
      .catch((err: unknown) => setError(err instanceof ApiError ? err.message : "Could not load webhooks."));
  }

  useEffect(reload, []);

  const onSubmit = handleSubmit(async (values) => {
    setError(null);
    try {
      await createWebhook({ url: values.url, eventType: values.eventType || null });
      reset();
      reload();
    } catch (err) {
      setError(applyServerErrors(err, setFieldError));
    }
  });

  async function toggleActive(webhook: WebhookSubscriptionDto) {
    setBusyId(webhook.id);
    try {
      await updateWebhook(webhook.id, { url: webhook.url, eventType: webhook.eventType, active: !webhook.active });
      reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update this webhook.");
    } finally {
      setBusyId(null);
    }
  }

  async function handleDelete(webhookId: string) {
    if (!window.confirm("Delete this webhook subscription?")) return;
    setBusyId(webhookId);
    try {
      await deleteWebhook(webhookId);
      reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this webhook.");
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Webhooks</h1>
        <p className="mt-1 text-sm text-slate-500">
          Get a signed HTTP POST whenever a record changes. Leave event type blank to subscribe to everything.
        </p>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <form onSubmit={onSubmit} noValidate className="flex flex-wrap items-end gap-3 rounded-lg border border-slate-200 bg-white p-5">
        <div className="w-80">
          <TextField label="Endpoint URL" placeholder="https://example.com/hooks/crm" error={errors.url?.message} {...register("url")} />
        </div>
        <div className="w-56">
          <TextField
            label="Event type (optional)"
            placeholder="e.g. Opportunity_CREATED"
            error={errors.eventType?.message}
            {...register("eventType")}
          />
        </div>
        <Button type="submit" isLoading={isSubmitting}>
          Add webhook
        </Button>
      </form>

      <div className="flex flex-col gap-3">
        {webhooks === null && <p className="text-sm text-slate-400">Loading...</p>}
        {webhooks?.length === 0 && <p className="text-sm text-slate-400">No webhooks yet.</p>}
        {webhooks?.map((webhook) => (
          <div key={webhook.id} className="rounded-lg border border-slate-200 bg-white p-5">
            <div className="flex items-start justify-between gap-4">
              <div className="min-w-0">
                <p className="truncate font-medium text-slate-900">{webhook.url}</p>
                <p className="mt-1 text-sm text-slate-500">{webhook.eventType ?? "All events"}</p>
              </div>
              <div className="flex shrink-0 items-center gap-2">
                {webhook.active ? (
                  <span className="inline-block rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-medium text-emerald-700">Active</span>
                ) : (
                  <span className="inline-block rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-500">Paused</span>
                )}
              </div>
            </div>

            <dl className="mt-3 grid gap-2 text-sm sm:grid-cols-2">
              <div>
                <dt className="text-slate-500">Signing secret</dt>
                <dd>
                  <code className="text-xs">{webhook.secret}</code>
                </dd>
              </div>
              <div>
                <dt className="text-slate-500">Last delivery</dt>
                <dd className="text-slate-900">
                  {webhook.lastTriggeredAt
                    ? `${new Date(webhook.lastTriggeredAt).toLocaleString()} (HTTP ${webhook.lastResponseStatus ?? "no response"})`
                    : "Never"}
                </dd>
              </div>
            </dl>

            <div className="mt-4 flex justify-end gap-2">
              <Button variant="secondary" isLoading={busyId === webhook.id} onClick={() => void toggleActive(webhook)}>
                {webhook.active ? "Pause" : "Resume"}
              </Button>
              <Button variant="danger" isLoading={busyId === webhook.id} onClick={() => void handleDelete(webhook.id)}>
                Delete
              </Button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
