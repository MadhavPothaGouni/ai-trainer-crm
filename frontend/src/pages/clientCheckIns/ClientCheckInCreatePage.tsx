import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { createClientCheckIn } from "../../api/clientCheckIns";
import { listContacts } from "../../api/contacts";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createClientCheckInSchema, type CreateClientCheckInFormValues } from "../../lib/validation";
import { CLIENT_CHECK_IN_METHODS, type ClientCheckInMethod, type ContactDto } from "../../types/api";

export default function ClientCheckInCreatePage() {
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
  } = useForm<CreateClientCheckInFormValues>({
    resolver: zodResolver(createClientCheckInSchema),
    defaultValues: { method: "MANUAL" },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const checkIn = await createClientCheckIn({
        contactId: values.contactId,
        method: values.method as ClientCheckInMethod,
        notes: blankToUndefined(values.notes),
      });
      navigate(`/client-check-ins/${checkIn.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Check in a client</h1>
        <p className="mt-1 text-sm text-slate-500">Log a client's arrival at the facility.</p>
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
            label="Method"
            options={CLIENT_CHECK_IN_METHODS.map((method) => ({ value: method, label: method.replace("_", " ") }))}
            error={errors.method?.message}
            {...register("method")}
          />
        </div>

        <TextArea label="Notes" error={errors.notes?.message} {...register("notes")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/client-check-ins")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Check in
          </Button>
        </div>
      </form>
    </div>
  );
}
