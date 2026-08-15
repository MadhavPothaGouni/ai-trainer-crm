import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { createClientFeedback } from "../../api/clientFeedback";
import { listContacts } from "../../api/contacts";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createClientFeedbackSchema, type CreateClientFeedbackFormValues } from "../../lib/validation";
import { CLIENT_FEEDBACK_RELATED_TYPES, type ClientFeedbackRelatedType, type ContactDto } from "../../types/api";

export default function ClientFeedbackCreatePage() {
  const navigate = useNavigate();
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
  } = useForm<CreateClientFeedbackFormValues>({
    resolver: zodResolver(createClientFeedbackSchema),
    defaultValues: { relatedType: "GENERAL" },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const feedback = await createClientFeedback({
        contactId: values.contactId,
        npsScore: Number(values.npsScore),
        relatedType: values.relatedType as ClientFeedbackRelatedType,
        submittedAt: new Date(values.submittedAt).toISOString(),
        comments: blankToUndefined(values.comments),
      });
      navigate(`/client-feedback/${feedback.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Record feedback</h1>
        <p className="mt-1 text-sm text-slate-500">Score is 0 (not likely to recommend) through 10 (extremely likely).</p>
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
            label="About"
            options={CLIENT_FEEDBACK_RELATED_TYPES.map((type) => ({ value: type, label: type }))}
            error={errors.relatedType?.message}
            {...register("relatedType")}
          />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Score (0-10)" type="number" min={0} max={10} error={errors.npsScore?.message} {...register("npsScore")} />
          <TextField label="Submitted at" type="datetime-local" error={errors.submittedAt?.message} {...register("submittedAt")} />
        </div>

        <TextArea label="Comments" error={errors.comments?.message} {...register("comments")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/client-feedback")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Record feedback
          </Button>
        </div>
      </form>
    </div>
  );
}
