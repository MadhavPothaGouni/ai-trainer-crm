import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link } from "react-router-dom";
import { createEmailTemplate, listEmailTemplates } from "../../api/emailTemplates";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { createEmailTemplateSchema, type CreateEmailTemplateFormValues } from "../../lib/validation";
import { EMAIL_TEMPLATE_CATEGORIES, type EmailTemplateCategory, type EmailTemplateDto, type PageResponse } from "../../types/api";

const PAGE_SIZE = 20;

const CATEGORY_CLASSES: Record<EmailTemplateCategory, string> = {
  GENERAL: "bg-slate-100 text-slate-700",
  SALES: "bg-emerald-100 text-emerald-700",
  SUPPORT: "bg-blue-100 text-blue-700",
  MARKETING: "bg-amber-100 text-amber-700",
};

export default function EmailTemplateListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<EmailTemplateDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  function reload() {
    setIsLoading(true);
    listEmailTemplates({ page, size: PAGE_SIZE, sort: "name,asc" })
      .then((res) => setResult(res))
      .catch((err: unknown) => setError(err instanceof ApiError ? err.message : "Could not load email templates."))
      .finally(() => setIsLoading(false));
  }

  useEffect(() => {
    reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page]);

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Email Templates</h1>
        <p className="mt-1 text-sm text-slate-500">
          Reusable subject/body pairs with {"{{"}token{"}}"} placeholders - shared across the whole organization, not owned by
          one rep. Open a template to merge it against a real Contact, Lead, Account, or Opportunity.
        </p>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Name</th>
              <th className="px-4 py-3 font-medium">Category</th>
              <th className="px-4 py-3 font-medium">Subject</th>
              <th className="px-4 py-3 font-medium">Status</th>
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
                  No email templates yet.
                </td>
              </tr>
            )}
            {result?.content.map((template) => (
              <tr key={template.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/email-templates/${template.id}`} className="font-medium text-slate-900 hover:underline">
                    {template.name}
                  </Link>
                </td>
                <td className="px-4 py-3">
                  <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${CATEGORY_CLASSES[template.category]}`}>
                    {template.category}
                  </span>
                </td>
                <td className="max-w-sm truncate px-4 py-3 text-slate-600">{template.subject}</td>
                <td className="px-4 py-3">
                  {template.active ? (
                    <span className="inline-block rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-medium text-emerald-800">
                      Active
                    </span>
                  ) : (
                    <span className="inline-block rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-600">
                      Inactive
                    </span>
                  )}
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

      <CreateEmailTemplateForm onCreated={reload} />
    </div>
  );
}

function CreateEmailTemplateForm({ onCreated }: { onCreated: () => void }) {
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<CreateEmailTemplateFormValues>({
    resolver: zodResolver(createEmailTemplateSchema),
    defaultValues: { name: "", category: "GENERAL", subject: "", body: "" },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await createEmailTemplate({
        name: values.name,
        category: values.category as EmailTemplateCategory,
        subject: values.subject,
        body: values.body,
      });
      reset({ name: "", category: "GENERAL", subject: "", body: "" });
      onCreated();
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
      <h2 className="text-sm font-medium text-slate-900">New template</h2>

      {formError && <Alert variant="error">{formError}</Alert>}

      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="Name" error={errors.name?.message} {...register("name")} />
        <Select
          label="Category"
          options={EMAIL_TEMPLATE_CATEGORIES.map((c) => ({ value: c, label: c }))}
          error={errors.category?.message}
          {...register("category")}
        />
      </div>

      <TextField
        label="Subject"
        placeholder="Hi {{contact.firstName}}, following up..."
        error={errors.subject?.message}
        {...register("subject")}
      />

      <TextArea
        label="Body"
        rows={6}
        placeholder={"Hi {{contact.firstName}},\n\nThanks for your time at {{account.name}}...\n\n{{sender.fullName}}"}
        error={errors.body?.message}
        {...register("body")}
      />

      <p className="text-xs text-slate-400">
        Available tokens: {"{{"}contact.firstName{"}}"}, {"{{"}contact.lastName{"}}"}, {"{{"}contact.fullName{"}}"}, {"{{"}
        contact.email{"}}"}, {"{{"}lead.fullName{"}}"}, {"{{"}lead.companyName{"}}"}, {"{{"}account.name{"}}"}, {"{{"}
        opportunity.name{"}}"}, {"{{"}opportunity.amount{"}}"}, {"{{"}sender.fullName{"}}"}, {"{{"}sender.email{"}}"}, {"{{"}
        today{"}}"}.
      </p>

      <div className="flex justify-end">
        <Button type="submit" isLoading={isSubmitting}>
          Create template
        </Button>
      </div>
    </form>
  );
}
