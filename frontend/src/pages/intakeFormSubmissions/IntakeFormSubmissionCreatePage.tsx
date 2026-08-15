import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate, useSearchParams } from "react-router-dom";
import { listContacts } from "../../api/contacts";
import { listIntakeForms } from "../../api/intakeForms";
import { createIntakeFormSubmission } from "../../api/intakeFormSubmissions";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createIntakeFormSubmissionSchema, type CreateIntakeFormSubmissionFormValues } from "../../lib/validation";
import type { ContactDto, IntakeFormDto } from "../../types/api";

export default function IntakeFormSubmissionCreatePage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const preselectedFormId = searchParams.get("formId") ?? "";
  const [formError, setFormError] = useState<string | null>(null);
  const [forms, setForms] = useState<IntakeFormDto[]>([]);
  const [contacts, setContacts] = useState<ContactDto[]>([]);

  useEffect(() => {
    listIntakeForms({ size: 100, sort: "title,asc" })
      .then((res) => setForms(res.content.filter((form) => form.active)))
      .catch(() => setForms([]));
    listContacts({ size: 100, sort: "lastName,asc" })
      .then((res) => setContacts(res.content))
      .catch(() => setContacts([]));
  }, []);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateIntakeFormSubmissionFormValues>({
    resolver: zodResolver(createIntakeFormSubmissionSchema),
    defaultValues: { formId: preselectedFormId },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const submission = await createIntakeFormSubmission({
        formId: values.formId,
        contactId: values.contactId,
        responses: blankToUndefined(values.responses),
        notes: blankToUndefined(values.notes),
      });
      navigate(`/intake-form-submissions/${submission.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Record an intake form submission</h1>
        <p className="mt-1 text-sm text-slate-500">A client has completed an intake questionnaire.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <Select
            label="Form"
            placeholder="Select an intake form"
            options={forms.map((form) => ({ value: form.id, label: form.title }))}
            error={errors.formId?.message}
            {...register("formId")}
          />
          <Select
            label="Client"
            placeholder="Select a contact"
            options={contacts.map((contact) => ({ value: contact.id, label: contact.fullName }))}
            error={errors.contactId?.message}
            {...register("contactId")}
          />
        </div>

        <TextArea label="Responses" placeholder="Free-text answers, or JSON" error={errors.responses?.message} {...register("responses")} />

        <TextArea label="Notes" error={errors.notes?.message} {...register("notes")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/intake-form-submissions")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Record submission
          </Button>
        </div>
      </form>
    </div>
  );
}
