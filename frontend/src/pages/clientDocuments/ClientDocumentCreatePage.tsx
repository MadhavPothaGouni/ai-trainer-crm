import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate, useSearchParams } from "react-router-dom";
import { listContacts } from "../../api/contacts";
import { createClientDocument } from "../../api/clientDocuments";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createClientDocumentSchema, type CreateClientDocumentFormValues } from "../../lib/validation";
import { CLIENT_DOCUMENT_TYPES, type ClientDocumentType, type ContactDto } from "../../types/api";

export default function ClientDocumentCreatePage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const preselectedContactId = searchParams.get("contactId") ?? "";
  const [formError, setFormError] = useState<string | null>(null);
  const [contacts, setContacts] = useState<ContactDto[]>([]);

  useEffect(() => {
    listContacts({ size: 100, sort: "lastName,asc" })
      .then((res) => setContacts(res.content))
      .catch(() => setContacts([]));
  }, []);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateClientDocumentFormValues>({
    resolver: zodResolver(createClientDocumentSchema),
    defaultValues: { contactId: preselectedContactId, documentType: "WAIVER" },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const document = await createClientDocument({
        contactId: values.contactId,
        documentType: values.documentType as ClientDocumentType,
        title: values.title,
        expiresAt: blankToUndefined(values.expiresAt),
        fileUrl: blankToUndefined(values.fileUrl),
        notes: blankToUndefined(values.notes),
      });
      navigate(`/client-documents/${document.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New client document</h1>
        <p className="mt-1 text-sm text-slate-500">A waiver, medical clearance, photo release, or other paperwork for one client.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <Select
            label="Client"
            placeholder="Select a contact"
            options={contacts.map((contact) => ({ value: contact.id, label: contact.fullName }))}
            error={errors.contactId?.message}
            {...register("contactId")}
          />
          <Select
            label="Document type"
            options={CLIENT_DOCUMENT_TYPES.map((type) => ({ value: type, label: type.replace("_", " ") }))}
            error={errors.documentType?.message}
            {...register("documentType")}
          />
        </div>

        <TextField label="Title" placeholder="Liability Waiver" error={errors.title?.message} {...register("title")} />

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Expires on" type="date" error={errors.expiresAt?.message} {...register("expiresAt")} />
          <TextField label="File URL" placeholder="Link to the signed file, if stored externally" error={errors.fileUrl?.message} {...register("fileUrl")} />
        </div>

        <TextArea label="Notes" error={errors.notes?.message} {...register("notes")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/client-documents")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Create document
          </Button>
        </div>
      </form>
    </div>
  );
}
