import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { listContacts } from "../../api/contacts";
import { deleteClientDocument, getClientDocument, updateClientDocument, updateClientDocumentStatus } from "../../api/clientDocuments";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, updateClientDocumentSchema, type UpdateClientDocumentFormValues } from "../../lib/validation";
import {
  CLIENT_DOCUMENT_STATUSES,
  CLIENT_DOCUMENT_TYPES,
  type ClientDocumentDto,
  type ClientDocumentStatus,
  type ClientDocumentType,
  type ContactDto,
} from "../../types/api";
import { ClientDocumentStatusBadge } from "./ClientDocumentListPage";

export default function ClientDocumentDetailPage() {
  const { clientDocumentId } = useParams<{ clientDocumentId: string }>();
  const navigate = useNavigate();
  const [clientDocument, setClientDocument] = useState<ClientDocumentDto | null>(null);
  const [contacts, setContacts] = useState<ContactDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [isUpdatingStatus, setIsUpdatingStatus] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<UpdateClientDocumentFormValues>({ resolver: zodResolver(updateClientDocumentSchema) });

  useEffect(() => {
    if (!clientDocumentId) return;
    let cancelled = false;
    getClientDocument(clientDocumentId)
      .then((data) => {
        if (cancelled) return;
        setClientDocument(data);
        reset({
          documentType: data.documentType,
          title: data.title,
          expiresAt: data.expiresAt ?? "",
          fileUrl: data.fileUrl ?? "",
          notes: data.notes ?? "",
        });
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this document.");
      });
    listContacts({ size: 100, sort: "lastName,asc" })
      .then((res) => {
        if (!cancelled) setContacts(res.content);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [clientDocumentId, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!clientDocumentId) return;
    setFormError(null);
    try {
      const updated = await updateClientDocument(clientDocumentId, {
        documentType: values.documentType as ClientDocumentType,
        title: values.title,
        expiresAt: blankToUndefined(values.expiresAt),
        fileUrl: blankToUndefined(values.fileUrl),
        notes: blankToUndefined(values.notes),
      });
      setClientDocument(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function handleStatusChange(status: string) {
    if (!clientDocumentId) return;
    setIsUpdatingStatus(true);
    setError(null);
    try {
      const updated = await updateClientDocumentStatus(clientDocumentId, { status: status as ClientDocumentStatus });
      setClientDocument(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update the status.");
    } finally {
      setIsUpdatingStatus(false);
    }
  }

  async function handleDelete() {
    if (!clientDocumentId || !window.confirm("Delete this document? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      await deleteClientDocument(clientDocumentId);
      navigate("/client-documents");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this document.");
      setIsDeleting(false);
    }
  }

  if (error && !clientDocument) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!clientDocument) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  const contact = contacts.find((c) => c.id === clientDocument.contactId);

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/client-documents" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Client Documents
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{clientDocument.title}</h1>
            <ClientDocumentStatusBadge status={clientDocument.status} />
          </div>
          {contact && (
            <p className="mt-1 text-sm text-slate-500">
              For{" "}
              <Link to={`/contacts/${contact.id}`} className="hover:underline">
                {contact.fullName}
              </Link>
            </p>
          )}
        </div>
        <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
          Delete
        </Button>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="grid gap-4 sm:grid-cols-2">
        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Overview</h2>
          <dl className="mt-3 flex flex-col gap-2 text-sm">
            <Row label="Type" value={clientDocument.documentType.replace("_", " ")} />
            <Row label="Expires" value={clientDocument.expiresAt ?? "—"} />
            <Row label="Signed" value={clientDocument.signedAt ? new Date(clientDocument.signedAt).toLocaleString() : "Not yet"} />
          </dl>
        </div>

        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Status</h2>
          <p className="mt-1 text-xs text-slate-400">Documents move freely between statuses - reinstating a revoked document is a normal correction.</p>
          <div className="mt-3">
            <Select
              label="Status"
              options={CLIENT_DOCUMENT_STATUSES.map((status) => ({ value: status, label: status }))}
              value={clientDocument.status}
              disabled={isUpdatingStatus}
              onChange={(event) => void handleStatusChange(event.target.value)}
            />
          </div>
        </div>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Edit document</h2>

        {formError && <Alert variant="error">{formError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Title" error={errors.title?.message} {...register("title")} />
          <Select
            label="Document type"
            options={CLIENT_DOCUMENT_TYPES.map((type) => ({ value: type, label: type.replace("_", " ") }))}
            error={errors.documentType?.message}
            {...register("documentType")}
          />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Expires on" type="date" error={errors.expiresAt?.message} {...register("expiresAt")} />
          <TextField label="File URL" error={errors.fileUrl?.message} {...register("fileUrl")} />
        </div>

        <TextArea label="Notes" error={errors.notes?.message} {...register("notes")} />

        <div className="flex justify-end">
          <Button type="submit" isLoading={isSubmitting}>
            Save changes
          </Button>
        </div>
      </form>
    </div>
  );
}

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex justify-between gap-4">
      <dt className="text-slate-500">{label}</dt>
      <dd className="text-right text-slate-900">{value ?? "—"}</dd>
    </div>
  );
}
