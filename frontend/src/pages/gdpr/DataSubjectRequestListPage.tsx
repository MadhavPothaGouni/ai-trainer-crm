import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { eraseDataSubject, exportDataSubject, listDataSubjectRequests } from "../../api/gdpr";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { dataSubjectRequestSchema, type DataSubjectRequestFormValues } from "../../lib/validation";
import type { DataSubjectRequestDto, DataSubjectRequestType, PageResponse } from "../../types/api";

const PAGE_SIZE = 20;

const TYPE_STYLES: Record<DataSubjectRequestType, string> = {
  EXPORT: "bg-sky-50 text-sky-700",
  ERASURE: "bg-red-50 text-red-700",
};

/** DATA_SUBJECT_REQUEST:*:ORGANIZATION only - admin-only by default, since this isn't a core CRM resource a default MEMBER role holds. See backend/crm-platform/README.md's module layout for `gdpr`. */
export default function DataSubjectRequestListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<DataSubjectRequestDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  function reload() {
    setIsLoading(true);
    listDataSubjectRequests({ page, size: PAGE_SIZE })
      .then((res) => setResult(res))
      .catch((err: unknown) => setError(err instanceof ApiError ? err.message : "Could not load data subject requests."))
      .finally(() => setIsLoading(false));
  }

  useEffect(() => {
    reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page]);

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Data Subject Requests</h1>
        <p className="mt-1 text-sm text-slate-500">
          GDPR/CCPA-style export and erasure, by email address rather than a specific record - a request reaches every
          Contact and Lead in this organization matching the email you enter, regardless of who owns it.
        </p>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <RequestForm onDone={reload} />

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="min-w-full divide-y divide-slate-200 text-sm">
          <thead className="bg-slate-50 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3">Type</th>
              <th className="px-4 py-3">Subject</th>
              <th className="px-4 py-3">Status</th>
              <th className="px-4 py-3">Contacts</th>
              <th className="px-4 py-3">Leads</th>
              <th className="px-4 py-3">When</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {isLoading && (
              <tr>
                <td colSpan={6} className="px-4 py-6 text-center text-sm text-slate-400">
                  Loading...
                </td>
              </tr>
            )}
            {!isLoading && result?.content.length === 0 && (
              <tr>
                <td colSpan={6} className="px-4 py-6 text-center text-sm text-slate-400">
                  No requests yet.
                </td>
              </tr>
            )}
            {result?.content.map((request) => (
              <tr key={request.id}>
                <td className="px-4 py-3">
                  <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${TYPE_STYLES[request.requestType]}`}>
                    {request.requestType}
                  </span>
                </td>
                <td className="px-4 py-3 text-slate-900">{request.subjectEmail}</td>
                <td className="px-4 py-3 text-slate-500">{request.status}</td>
                <td className="px-4 py-3">{request.contactsAffected}</td>
                <td className="px-4 py-3">{request.leadsAffected}</td>
                <td className="px-4 py-3 text-slate-500">{new Date(request.createdAt).toLocaleString()}</td>
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

function RequestForm({ onDone }: { onDone: () => void }) {
  const [formError, setFormError] = useState<string | null>(null);
  const [isExporting, setIsExporting] = useState(false);
  const [isErasing, setIsErasing] = useState(false);

  const {
    getValues,
    register,
    handleSubmit,
    formState: { errors },
    setError,
  } = useForm<DataSubjectRequestFormValues>({
    resolver: zodResolver(dataSubjectRequestSchema),
    defaultValues: { subjectEmail: "" },
  });

  const onExport = handleSubmit(async (values) => {
    setFormError(null);
    setIsExporting(true);
    try {
      await exportDataSubject({ subjectEmail: values.subjectEmail });
      onDone();
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    } finally {
      setIsExporting(false);
    }
  });

  async function onErase() {
    const subjectEmail = getValues("subjectEmail");
    const parsed = dataSubjectRequestSchema.safeParse({ subjectEmail });
    if (!parsed.success) {
      setError("subjectEmail", { message: parsed.error.issues[0]?.message ?? "Enter a valid email address" });
      return;
    }
    if (
      !window.confirm(
        `Permanently erase every Contact and Lead matching ${subjectEmail}? Their name, email, phone, and other personal ` +
          "details will be scrubbed and cannot be recovered. This cannot be undone.",
      )
    ) {
      return;
    }
    setFormError(null);
    setIsErasing(true);
    try {
      await eraseDataSubject({ subjectEmail });
      onDone();
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    } finally {
      setIsErasing(false);
    }
  }

  return (
    <form onSubmit={onExport} noValidate className="flex max-w-xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
      <h2 className="text-sm font-medium text-slate-900">New request</h2>

      {formError && <Alert variant="error">{formError}</Alert>}

      <TextField label="Subject email" type="email" error={errors.subjectEmail?.message} {...register("subjectEmail")} />

      <div className="flex justify-end gap-3">
        <Button type="button" variant="danger" isLoading={isErasing} onClick={() => void onErase()}>
          Erase
        </Button>
        <Button type="submit" variant="secondary" isLoading={isExporting}>
          Export
        </Button>
      </div>
    </form>
  );
}
