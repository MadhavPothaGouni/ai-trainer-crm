import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { listAccounts } from "../../api/accounts";
import { listContacts } from "../../api/contacts";
import { createTicket } from "../../api/tickets";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createTicketSchema, type CreateTicketFormValues } from "../../lib/validation";
import { TICKET_PRIORITIES, type AccountDto, type ContactDto, type TicketPriority } from "../../types/api";

export default function TicketCreatePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);
  const [accounts, setAccounts] = useState<AccountDto[]>([]);
  const [contacts, setContacts] = useState<ContactDto[]>([]);

  useEffect(() => {
    listAccounts({ size: 100, sort: "name,asc" })
      .then((res) => setAccounts(res.content))
      .catch(() => undefined);
    listContacts({ size: 100, sort: "createdAt,desc" })
      .then((res) => setContacts(res.content))
      .catch(() => undefined);
  }, []);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateTicketFormValues>({
    resolver: zodResolver(createTicketSchema),
    defaultValues: { priority: "MEDIUM" },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const ticket = await createTicket({
        subject: values.subject,
        description: blankToUndefined(values.description),
        priority: values.priority as TicketPriority,
        accountId: blankToUndefined(values.accountId),
        contactId: blankToUndefined(values.contactId),
      });
      navigate(`/tickets/${ticket.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New ticket</h1>
        <p className="mt-1 text-sm text-slate-500">Raise a support request, optionally tied to an account or contact.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <TextField label="Subject" error={errors.subject?.message} {...register("subject")} />

        <div className="grid gap-4 sm:grid-cols-3">
          <Select
            label="Priority"
            options={TICKET_PRIORITIES.map((priority) => ({ value: priority, label: priority }))}
            error={errors.priority?.message}
            {...register("priority")}
          />
          <Select
            label="Account"
            placeholder="None"
            options={accounts.map((account) => ({ value: account.id, label: account.name }))}
            error={errors.accountId?.message}
            {...register("accountId")}
          />
          <Select
            label="Contact"
            placeholder="None"
            options={contacts.map((contact) => ({ value: contact.id, label: contact.fullName }))}
            error={errors.contactId?.message}
            {...register("contactId")}
          />
        </div>

        <TextArea label="Description" error={errors.description?.message} {...register("description")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/tickets")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Create ticket
          </Button>
        </div>
      </form>
    </div>
  );
}
