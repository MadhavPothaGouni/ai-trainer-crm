import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { listAccounts } from "../../api/accounts";
import { listContacts } from "../../api/contacts";
import { deleteEmailTemplate, getEmailTemplate, renderEmailTemplate, updateEmailTemplate } from "../../api/emailTemplates";
import { listLeads } from "../../api/leads";
import { listOpportunities } from "../../api/opportunities";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { updateEmailTemplateSchema, type UpdateEmailTemplateFormValues } from "../../lib/validation";
import {
  EMAIL_TEMPLATE_CATEGORIES,
  type AccountDto,
  type ContactDto,
  type EmailTemplateCategory,
  type EmailTemplateDto,
  type LeadDto,
  type OpportunityDto,
  type RenderedEmailDto,
} from "../../types/api";

export default function EmailTemplateDetailPage() {
  const { templateId } = useParams<{ templateId: string }>();
  const navigate = useNavigate();
  const [template, setTemplate] = useState<EmailTemplateDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);

  useEffect(() => {
    if (!templateId) return;
    let cancelled = false;
    getEmailTemplate(templateId)
      .then((data) => {
        if (!cancelled) setTemplate(data);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this template.");
      });
    return () => {
      cancelled = true;
    };
  }, [templateId]);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
    setError: setEditFieldError,
  } = useForm<UpdateEmailTemplateFormValues>({ resolver: zodResolver(updateEmailTemplateSchema) });

  useEffect(() => {
    if (!template) return;
    reset({ name: template.name, category: template.category, subject: template.subject, body: template.body, active: template.active });
  }, [template, reset]);

  const onSaveEdits = handleSubmit(async (values) => {
    if (!templateId) return;
    setEditError(null);
    try {
      const updated = await updateEmailTemplate(templateId, {
        name: values.name,
        category: values.category as EmailTemplateCategory,
        subject: values.subject,
        body: values.body,
        active: values.active,
      });
      setTemplate(updated);
    } catch (error) {
      setEditError(applyServerErrors(error, setEditFieldError));
    }
  });

  async function handleDelete() {
    if (!templateId || !window.confirm("Delete this email template?")) return;
    setIsDeleting(true);
    try {
      await deleteEmailTemplate(templateId);
      navigate("/email-templates");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this template.");
      setIsDeleting(false);
    }
  }

  if (error && !template) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!template) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex max-w-4xl flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/email-templates" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Email Templates
          </Link>
          <h1 className="mt-1 text-2xl font-semibold text-slate-900">{template.name}</h1>
        </div>
        <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
          Delete
        </Button>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <form onSubmit={onSaveEdits} noValidate className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Edit</h2>

        {editError && <Alert variant="error">{editError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Name" error={errors.name?.message} {...register("name")} />
          <Select
            label="Category"
            options={EMAIL_TEMPLATE_CATEGORIES.map((c) => ({ value: c, label: c }))}
            error={errors.category?.message}
            {...register("category")}
          />
        </div>

        <TextField label="Subject" error={errors.subject?.message} {...register("subject")} />
        <TextArea label="Body" rows={8} error={errors.body?.message} {...register("body")} />

        <label className="flex w-fit items-center gap-2 text-sm text-slate-700">
          <input type="checkbox" className="h-4 w-4 rounded border-slate-300" {...register("active")} />
          Active
        </label>

        <div className="flex justify-end">
          <Button type="submit" isLoading={isSubmitting}>
            Save changes
          </Button>
        </div>
      </form>

      <RenderPreviewPanel templateId={template.id} />
    </div>
  );
}

/** Merges this template's placeholders against a real, caller-chosen Contact/Lead/Account/
 * Opportunity - render() is read-only, so this calls the API fresh every time "Preview" is
 * clicked rather than trying to keep a live-typed preview in sync. */
function RenderPreviewPanel({ templateId }: { templateId: string }) {
  const [contacts, setContacts] = useState<ContactDto[]>([]);
  const [leads, setLeads] = useState<LeadDto[]>([]);
  const [accounts, setAccounts] = useState<AccountDto[]>([]);
  const [opportunities, setOpportunities] = useState<OpportunityDto[]>([]);
  const [contactId, setContactId] = useState("");
  const [leadId, setLeadId] = useState("");
  const [accountId, setAccountId] = useState("");
  const [opportunityId, setOpportunityId] = useState("");
  const [result, setResult] = useState<RenderedEmailDto | null>(null);
  const [isRendering, setIsRendering] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listContacts({ size: 100, sort: "lastName,asc" }).then((res) => setContacts(res.content)).catch(() => undefined);
    listLeads({ size: 100, sort: "createdAt,desc" }).then((res) => setLeads(res.content)).catch(() => undefined);
    listAccounts({ size: 100, sort: "name,asc" }).then((res) => setAccounts(res.content)).catch(() => undefined);
    listOpportunities({ size: 100, sort: "createdAt,desc" }).then((res) => setOpportunities(res.content)).catch(() => undefined);
  }, []);

  async function handlePreview() {
    setIsRendering(true);
    setError(null);
    try {
      const rendered = await renderEmailTemplate(templateId, {
        contactId: contactId || null,
        leadId: leadId || null,
        accountId: accountId || null,
        opportunityId: opportunityId || null,
      });
      setResult(rendered);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not render this template.");
    } finally {
      setIsRendering(false);
    }
  }

  return (
    <div className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
      <div>
        <h2 className="text-sm font-medium text-slate-900">Preview / mail merge</h2>
        <p className="mt-1 text-xs text-slate-500">
          Pick any combination of records to merge this template's placeholders against. Nothing is sent or saved - this is a
          read-only preview.
        </p>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Select
          label="Contact"
          placeholder="None"
          value={contactId}
          onChange={(event) => setContactId(event.target.value)}
          options={contacts.map((c) => ({ value: c.id, label: c.fullName }))}
        />
        <Select
          label="Lead"
          placeholder="None"
          value={leadId}
          onChange={(event) => setLeadId(event.target.value)}
          options={leads.map((l) => ({ value: l.id, label: l.fullName }))}
        />
        <Select
          label="Account"
          placeholder="None"
          value={accountId}
          onChange={(event) => setAccountId(event.target.value)}
          options={accounts.map((a) => ({ value: a.id, label: a.name }))}
        />
        <Select
          label="Opportunity"
          placeholder="None"
          value={opportunityId}
          onChange={(event) => setOpportunityId(event.target.value)}
          options={opportunities.map((o) => ({ value: o.id, label: o.name }))}
        />
      </div>

      <div className="flex justify-end">
        <Button type="button" onClick={() => void handlePreview()} isLoading={isRendering}>
          Preview
        </Button>
      </div>

      {result && (
        <div className="flex flex-col gap-3 rounded-md border border-slate-200 bg-slate-50 p-4">
          {result.unresolvedTokens.length > 0 && (
            <Alert variant="error">
              Unresolved: {result.unresolvedTokens.join(", ")} - pick a matching record above, or these will be sent literally.
            </Alert>
          )}
          <div>
            <p className="text-xs font-medium uppercase tracking-wide text-slate-400">Subject</p>
            <p className="text-sm text-slate-900">{result.subject}</p>
          </div>
          <div>
            <p className="text-xs font-medium uppercase tracking-wide text-slate-400">Body</p>
            <p className="whitespace-pre-wrap text-sm text-slate-900">{result.body}</p>
          </div>
        </div>
      )}
    </div>
  );
}
