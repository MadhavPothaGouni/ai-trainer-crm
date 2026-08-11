import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { createApiKey, listApiKeys, revokeApiKey } from "../../api/apiKeys";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { createApiKeySchema, type CreateApiKeyFormValues } from "../../lib/validation";
import type { ApiKeyDto } from "../../types/api";

/**
 * Programmatic auth: create/list/revoke keys. A freshly created key's raw
 * value is shown exactly once, right here, in a dismissible banner -
 * ApiKeyDto.rawKey is never populated by any other response (see the
 * backend DTO's javadoc), so there's no "view key" affordance anywhere
 * else in this UI, on purpose.
 */
export default function ApiKeysPage() {
  const [keys, setKeys] = useState<ApiKeyDto[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [justCreatedKey, setJustCreatedKey] = useState<string | null>(null);
  const [revokingId, setRevokingId] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<CreateApiKeyFormValues>({ resolver: zodResolver(createApiKeySchema) });

  function reload() {
    listApiKeys({ size: 100, sort: "createdAt,desc" })
      .then((res) => setKeys(res.content))
      .catch((err: unknown) => setError(err instanceof ApiError ? err.message : "Could not load API keys."));
  }

  useEffect(reload, []);

  const onSubmit = handleSubmit(async (values) => {
    setError(null);
    try {
      const created = await createApiKey({ name: values.name });
      setJustCreatedKey(created.rawKey ?? null);
      reset();
      reload();
    } catch (err) {
      setError(applyServerErrors(err, setFieldError));
    }
  });

  async function handleRevoke(apiKeyId: string) {
    if (!window.confirm("Revoke this API key? Anything using it will stop working immediately.")) return;
    setRevokingId(apiKeyId);
    try {
      await revokeApiKey(apiKeyId);
      reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not revoke this key.");
    } finally {
      setRevokingId(null);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">API keys</h1>
        <p className="mt-1 text-sm text-slate-500">
          Programmatic access to this API. A key authenticates as whoever created it - see its permissions there.
        </p>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      {justCreatedKey && (
        <Alert variant="success">
          <div className="flex flex-col gap-2">
            <p className="font-medium">Copy this key now - it won&apos;t be shown again.</p>
            <code className="break-all rounded bg-white/60 px-2 py-1 text-xs">{justCreatedKey}</code>
            <Button variant="secondary" className="w-fit" onClick={() => setJustCreatedKey(null)}>
              Dismiss
            </Button>
          </div>
        </Alert>
      )}

      <form onSubmit={onSubmit} noValidate className="flex flex-wrap items-end gap-3 rounded-lg border border-slate-200 bg-white p-5">
        <div className="w-64">
          <TextField label="Key name" placeholder="e.g. CI pipeline" error={errors.name?.message} {...register("name")} />
        </div>
        <Button type="submit" isLoading={isSubmitting}>
          Create key
        </Button>
      </form>

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Name</th>
              <th className="px-4 py-3 font-medium">Prefix</th>
              <th className="px-4 py-3 font-medium">Last used</th>
              <th className="px-4 py-3 font-medium">Status</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {keys === null && (
              <tr>
                <td className="px-4 py-6 text-center text-slate-400" colSpan={5}>
                  Loading...
                </td>
              </tr>
            )}
            {keys?.length === 0 && (
              <tr>
                <td className="px-4 py-6 text-center text-slate-400" colSpan={5}>
                  No API keys yet.
                </td>
              </tr>
            )}
            {keys?.map((key) => (
              <tr key={key.id} className="hover:bg-slate-50">
                <td className="px-4 py-3 font-medium text-slate-900">{key.name}</td>
                <td className="px-4 py-3 text-slate-600">
                  <code className="text-xs">{key.keyPrefix}</code>
                </td>
                <td className="px-4 py-3 text-slate-600">{key.lastUsedAt ? new Date(key.lastUsedAt).toLocaleString() : "Never"}</td>
                <td className="px-4 py-3">
                  {key.revokedAt ? (
                    <span className="inline-block rounded-full bg-red-100 px-2.5 py-0.5 text-xs font-medium text-red-700">Revoked</span>
                  ) : (
                    <span className="inline-block rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-medium text-emerald-700">Active</span>
                  )}
                </td>
                <td className="px-4 py-3 text-right">
                  {!key.revokedAt && (
                    <Button variant="danger" isLoading={revokingId === key.id} onClick={() => void handleRevoke(key.id)}>
                      Revoke
                    </Button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
