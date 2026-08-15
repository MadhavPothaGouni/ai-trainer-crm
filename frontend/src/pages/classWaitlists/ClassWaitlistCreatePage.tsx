import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate, useSearchParams } from "react-router-dom";
import { createClassWaitlist } from "../../api/classWaitlists";
import { listClassSessions } from "../../api/classSessions";
import { listContacts } from "../../api/contacts";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createClassWaitlistSchema, type CreateClassWaitlistFormValues } from "../../lib/validation";
import type { ClassSessionDto, ContactDto } from "../../types/api";

export default function ClassWaitlistCreatePage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const preselectedSessionId = searchParams.get("classSessionId") ?? undefined;
  const [formError, setFormError] = useState<string | null>(null);
  const [sessions, setSessions] = useState<ClassSessionDto[]>([]);
  const [contacts, setContacts] = useState<ContactDto[]>([]);

  useEffect(() => {
    listClassSessions({ size: 100, sort: "startsAt,desc" })
      .then((res) => setSessions(res.content))
      .catch(() => setSessions([]));
    listContacts({ size: 100, sort: "lastName,asc" })
      .then((res) => setContacts(res.content))
      .catch(() => setContacts([]));
  }, []);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateClassWaitlistFormValues>({
    resolver: zodResolver(createClassWaitlistSchema),
    defaultValues: { classSessionId: preselectedSessionId ?? "" },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const entry = await createClassWaitlist({
        classSessionId: values.classSessionId,
        contactId: values.contactId,
        notes: blankToUndefined(values.notes),
      });
      navigate(`/class-waitlists/${entry.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Add to waitlist</h1>
        <p className="mt-1 text-sm text-slate-500">Position is assigned automatically - it's the next open spot for this session.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <Select
            label="Class session"
            placeholder="Select a session"
            options={sessions.map((session) => ({
              value: session.id,
              label: `${new Date(session.startsAt).toLocaleString()} (${session.status})`,
            }))}
            error={errors.classSessionId?.message}
            {...register("classSessionId")}
          />
          <Select
            label="Client"
            placeholder="Select a contact"
            options={contacts.map((contact) => ({ value: contact.id, label: contact.fullName }))}
            error={errors.contactId?.message}
            {...register("contactId")}
          />
        </div>

        <TextArea label="Notes" error={errors.notes?.message} {...register("notes")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/class-waitlists")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Add to waitlist
          </Button>
        </div>
      </form>
    </div>
  );
}
