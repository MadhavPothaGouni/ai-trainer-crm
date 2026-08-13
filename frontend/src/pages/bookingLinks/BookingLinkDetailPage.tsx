import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import {
  addBookingSlot,
  bookSlot,
  cancelSlot,
  deleteBookingLink,
  getBookingLink,
  removeBookingSlot,
  updateBookingLink,
} from "../../api/bookingLinks";
import { listContacts } from "../../api/contacts";
import { listLeads } from "../../api/leads";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import {
  blankToUndefined,
  bookSlotSchema,
  createBookingLinkSchema,
  createBookingSlotSchema,
  type BookSlotFormValues,
  type CreateBookingLinkFormValues,
  type CreateBookingSlotFormValues,
} from "../../lib/validation";
import type { BookingLinkDto, BookingSlotDto, BookingSlotStatus, BookingTargetType, ContactDto, LeadDto } from "../../types/api";

const SLOT_STATUS_CLASSES: Record<BookingSlotStatus, string> = {
  OPEN: "bg-blue-100 text-blue-700",
  BOOKED: "bg-emerald-100 text-emerald-700",
  CANCELLED: "bg-slate-100 text-slate-600",
};

export default function BookingLinkDetailPage() {
  const { bookingLinkId } = useParams<{ bookingLinkId: string }>();
  const navigate = useNavigate();
  const [link, setLink] = useState<BookingLinkDto | null>(null);
  const [leads, setLeads] = useState<LeadDto[]>([]);
  const [contacts, setContacts] = useState<ContactDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<CreateBookingLinkFormValues>({ resolver: zodResolver(createBookingLinkSchema) });

  function loadLink() {
    if (!bookingLinkId) return;
    getBookingLink(bookingLinkId)
      .then((data) => {
        setLink(data);
        reset({ title: data.title, description: data.description ?? "", durationMinutes: String(data.durationMinutes), slug: data.slug });
      })
      .catch((err: unknown) => setError(err instanceof ApiError ? err.message : "Could not load this booking link."));
  }

  useEffect(() => {
    if (!bookingLinkId) return;
    let cancelled = false;
    getBookingLink(bookingLinkId)
      .then((data) => {
        if (cancelled) return;
        setLink(data);
        reset({ title: data.title, description: data.description ?? "", durationMinutes: String(data.durationMinutes), slug: data.slug });
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this booking link.");
      });
    listLeads({ size: 200, sort: "createdAt,desc" })
      .then((res) => {
        if (!cancelled) setLeads(res.content);
      })
      .catch(() => undefined);
    listContacts({ size: 200, sort: "createdAt,desc" })
      .then((res) => {
        if (!cancelled) setContacts(res.content);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [bookingLinkId, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!bookingLinkId || !link) return;
    setFormError(null);
    try {
      const updated = await updateBookingLink(bookingLinkId, {
        title: values.title,
        description: blankToUndefined(values.description),
        durationMinutes: Number(values.durationMinutes),
        slug: values.slug,
        active: link.active,
      });
      setLink(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function toggleActive() {
    if (!bookingLinkId || !link) return;
    try {
      const updated = await updateBookingLink(bookingLinkId, {
        title: link.title,
        description: link.description ?? undefined,
        durationMinutes: link.durationMinutes,
        slug: link.slug,
        active: !link.active,
      });
      setLink(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update this booking link.");
    }
  }

  async function handleDelete() {
    if (!bookingLinkId || !window.confirm("Delete this booking link?")) return;
    setIsDeleting(true);
    try {
      await deleteBookingLink(bookingLinkId);
      navigate("/booking-links");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this booking link.");
      setIsDeleting(false);
    }
  }

  function targetLabel(slot: BookingSlotDto): string {
    if (!slot.targetType || !slot.targetId) return "—";
    if (slot.targetType === "LEAD") {
      return leads.find((l) => l.id === slot.targetId)?.fullName ?? "Unknown lead";
    }
    return contacts.find((c) => c.id === slot.targetId)?.fullName ?? "Unknown contact";
  }

  if (error && !link) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!link) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/booking-links" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Booking links
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{link.title}</h1>
            {link.active ? (
              <span className="inline-block rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-medium text-emerald-700">Active</span>
            ) : (
              <span className="inline-block rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-500">Inactive</span>
            )}
          </div>
          <p className="mt-1 text-sm text-slate-500">/{link.slug} - {link.durationMinutes} min</p>
        </div>
        <div className="flex gap-3">
          <Button variant="secondary" onClick={() => void toggleActive()}>
            {link.active ? "Deactivate" : "Activate"}
          </Button>
          <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
            Delete
          </Button>
        </div>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Edit</h2>

        {formError && <Alert variant="error">{formError}</Alert>}

        <TextField label="Title" error={errors.title?.message} {...register("title")} />

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Duration (minutes)" type="number" min={1} error={errors.durationMinutes?.message} {...register("durationMinutes")} />
          <TextField label="Slug" error={errors.slug?.message} {...register("slug")} />
        </div>

        <TextArea label="Description" error={errors.description?.message} {...register("description")} />

        <div className="flex justify-end">
          <Button type="submit" isLoading={isSubmitting}>
            Save changes
          </Button>
        </div>
      </form>

      <SlotsPanel link={link} leads={leads} contacts={contacts} targetLabel={targetLabel} onChanged={loadLink} setError={setError} />
    </div>
  );
}

function SlotsPanel({
  link,
  leads,
  contacts,
  targetLabel,
  onChanged,
  setError,
}: {
  link: BookingLinkDto;
  leads: LeadDto[];
  contacts: ContactDto[];
  targetLabel: (slot: BookingSlotDto) => string;
  onChanged: () => void;
  setError: (message: string | null) => void;
}) {
  const [actioningId, setActioningId] = useState<string | null>(null);
  const [bookingSlotId, setBookingSlotId] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<CreateBookingSlotFormValues>({ resolver: zodResolver(createBookingSlotSchema) });

  const onAddSlot = handleSubmit(async (values) => {
    try {
      // datetime-local inputs report local time with no offset - same conversion CalendarEventCreatePage uses.
      await addBookingSlot(link.id, { startAt: new Date(values.startAt).toISOString() });
      reset({ startAt: "" });
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not add this slot.");
    }
  });

  async function handleRemove(slotId: string) {
    setActioningId(slotId);
    try {
      await removeBookingSlot(link.id, slotId);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not remove this slot.");
    } finally {
      setActioningId(null);
    }
  }

  async function handleCancel(slotId: string) {
    setActioningId(slotId);
    try {
      await cancelSlot(link.id, slotId);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not cancel this slot.");
    } finally {
      setActioningId(null);
    }
  }

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-5">
      <h2 className="text-sm font-medium text-slate-500">Slots</h2>
      <p className="mt-1 text-xs text-slate-400">
        Booking a slot creates a real calendar event; cancelling a booked slot removes that event too.
      </p>

      <div className="mt-3 overflow-hidden rounded-md border border-slate-100">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-100 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-3 py-2 font-medium">When</th>
              <th className="px-3 py-2 font-medium">Status</th>
              <th className="px-3 py-2 font-medium">With</th>
              <th className="px-3 py-2 font-medium" />
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {link.slots.length === 0 && (
              <tr>
                <td className="px-3 py-4 text-center text-slate-400" colSpan={4}>
                  No slots yet.
                </td>
              </tr>
            )}
            {link.slots.map((slot) => (
              <tr key={slot.id}>
                <td className="px-3 py-2 text-slate-900">{new Date(slot.startAt).toLocaleString()}</td>
                <td className="px-3 py-2">
                  <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${SLOT_STATUS_CLASSES[slot.status]}`}>
                    {slot.status}
                  </span>
                </td>
                <td className="px-3 py-2 text-slate-600">{targetLabel(slot)}</td>
                <td className="px-3 py-2 text-right">
                  {slot.status === "OPEN" && (
                    <div className="flex justify-end gap-2">
                      <Button variant="secondary" isLoading={actioningId === slot.id} onClick={() => setBookingSlotId(slot.id)}>
                        Book
                      </Button>
                      <Button variant="danger" isLoading={actioningId === slot.id} onClick={() => void handleRemove(slot.id)}>
                        Remove
                      </Button>
                    </div>
                  )}
                  {slot.status === "BOOKED" && (
                    <Button variant="danger" isLoading={actioningId === slot.id} onClick={() => void handleCancel(slot.id)}>
                      Cancel
                    </Button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {bookingSlotId && (
        <BookSlotForm
          linkId={link.id}
          slotId={bookingSlotId}
          leads={leads}
          contacts={contacts}
          onDone={() => {
            setBookingSlotId(null);
            onChanged();
          }}
          onCancel={() => setBookingSlotId(null)}
          setError={setError}
        />
      )}

      <form onSubmit={onAddSlot} noValidate className="mt-4 flex flex-col gap-3 border-t border-slate-100 pt-4 sm:flex-row sm:items-end">
        <div className="flex-1">
          <TextField label="Add a slot at" type="datetime-local" error={errors.startAt?.message} {...register("startAt")} />
        </div>
        <Button type="submit" isLoading={isSubmitting}>
          Add slot
        </Button>
      </form>
    </div>
  );
}

function BookSlotForm({
  linkId,
  slotId,
  leads,
  contacts,
  onDone,
  onCancel,
  setError,
}: {
  linkId: string;
  slotId: string;
  leads: LeadDto[];
  contacts: ContactDto[];
  onDone: () => void;
  onCancel: () => void;
  setError: (message: string | null) => void;
}) {
  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<BookSlotFormValues>({ resolver: zodResolver(bookSlotSchema), defaultValues: { targetType: "LEAD", targetId: "" } });

  const targetType = watch("targetType");
  const targetOptions =
    targetType === "CONTACT" ? contacts.map((c) => ({ value: c.id, label: c.fullName })) : leads.map((l) => ({ value: l.id, label: l.fullName }));

  const onSubmit = handleSubmit(async (values) => {
    try {
      await bookSlot(linkId, slotId, { targetType: values.targetType as BookingTargetType, targetId: values.targetId });
      onDone();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not book this slot.");
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="mt-4 flex flex-col gap-3 border-t border-slate-100 pt-4">
      <h3 className="text-sm font-medium text-slate-900">Book this slot</h3>
      <div className="grid gap-3 sm:grid-cols-3">
        <Select label="Type" options={[{ value: "LEAD", label: "Lead" }, { value: "CONTACT", label: "Contact" }]} error={errors.targetType?.message} {...register("targetType")} />
        <Select label="Target" options={targetOptions} error={errors.targetId?.message} {...register("targetId")} />
      </div>
      <div className="flex justify-end gap-3">
        <Button type="button" variant="secondary" onClick={onCancel}>
          Cancel
        </Button>
        <Button type="submit" isLoading={isSubmitting}>
          Confirm booking
        </Button>
      </div>
    </form>
  );
}
