import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { listAccounts } from "../../api/accounts";
import { listContacts } from "../../api/contacts";
import { deleteTicket, getTicket, updateTicket, updateTicketStatus } from "../../api/tickets";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createTicketSchema, type CreateTicketFormValues } from "../../lib/validation";
import { TICKET_PRIORITIES, TICKET_STATUSES, type AccountDto, type ContactDto, type TicketDto, type TicketPriority, type TicketStatus } from "../../types/api";
import { TicketPriorityBadge, TicketStatusBadge } from "./TicketListPage";

export default function TicketDetailPage() {
  const { ticketId } = useParams<{ ticketId: string }>();
  const navigate = useNavigate();
  const [ticket, setTicket] = useState<TicketDto | null>(null);
  const [accounts, setAccounts] = useState<AccountDto[]>([]);
  const [contacts, setContacts] = useState<ContactDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [isUpdatingStatus, setIsUpdatingStatus] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);

  useEffect(() => {
    if (!ticketId) return;
    let cancelled = false;
    getTicket(ticketId)
      .then((data) => {
        if (!cancelled) setTicket(data);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this ticket.");
      });
    listAccounts({ size: 100, sort: "name,asc" })
      .then((res) => {
        if (!cancelled) setAccounts(res.content);
      })
      .catch(() => undefined);
    listContacts({ size: 100, sort: "createdAt,desc" })
      .then((res) => {
        if (!cancelled) setContacts(res.content);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [ticketId]);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
    setError: setEditFieldError,
  } = useForm<CreateTicketFormValues>({ resolver: zodResolver(createTicketSchema) });

  useEffect(() => {
    if (!ticket) return;
    reset({
      subject: ticket.subject,
      description: ticket.description ?? "",
      priority: ticket.priority,
      accountId: ticket.accountId ?? "",
      contactId: ticket.contactId ?? "",
    });
  }, [ticket, reset]);

  const onSaveEdits = handleSubmit(async (values) => {
    if (!ticketId) return;
    setEditError(null);
    try {
      const updated = await updateTicket(ticketId, {
        subject: values.subject,
        description: blankToUndefined(values.description),
        priority: values.priority as TicketPriority,
        accountId: blankToUndefined(values.accountId),
        contactId: blankToUndefined(values.contactId),
      });
      setTicket(updated);
    } catch (error) {
      setEditError(applyServerErrors(error, setEditFieldError));
    }
  });

  async function handleStatusChange(status: string) {
    if (!ticketId) return;
    setIsUpdatingStatus(true);
    setError(null);
    try {
      const updated = await updateTicketStatus(ticketId, { status: status as TicketStatus });
      setTicket(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update the status.");
    } finally {
      setIsUpdatingStatus(false);
    }
  }

  async function handleDelete() {
    if (!ticketId || !window.confirm("Delete this ticket? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      await deleteTicket(ticketId);
      navigate("/tickets");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this ticket.");
      setIsDeleting(false);
    }
  }

  if (error && !ticket) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!ticket) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  const linkedAccount = accounts.find((account) => account.id === ticket.accountId);
  const linkedContact = contacts.find((contact) => contact.id === ticket.contactId);

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/tickets" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Tickets
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{ticket.subject}</h1>
            <TicketPriorityBadge priority={ticket.priority} />
            <TicketStatusBadge status={ticket.status} />
          </div>
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
            <Row
              label="Account"
              value={
                linkedAccount && (
                  <Link to={`/accounts/${linkedAccount.id}`} className="text-slate-900 hover:underline">
                    {linkedAccount.name}
                  </Link>
                )
              }
            />
            <Row
              label="Contact"
              value={
                linkedContact && (
                  <Link to={`/contacts/${linkedContact.id}`} className="text-slate-900 hover:underline">
                    {linkedContact.fullName}
                  </Link>
                )
              }
            />
            <Row label="Resolved at" value={ticket.resolvedAt ? new Date(ticket.resolvedAt).toLocaleString() : undefined} />
          </dl>
        </div>

        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Status</h2>
          <p className="mt-1 text-xs text-slate-400">Tickets can move freely between statuses - reopening a resolved ticket is normal.</p>
          <div className="mt-3">
            <Select
              label="Status"
              options={TICKET_STATUSES.map((status) => ({ value: status, label: status.replace("_", " ") }))}
              value={ticket.status}
              disabled={isUpdatingStatus}
              onChange={(event) => void handleStatusChange(event.target.value)}
            />
          </div>
        </div>
      </div>

      <form onSubmit={onSaveEdits} noValidate className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Edit ticket</h2>

        {editError && <Alert variant="error">{editError}</Alert>}

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
