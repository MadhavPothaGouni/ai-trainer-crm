import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate, useSearchParams } from "react-router-dom";
import { createLockerAssignment } from "../../api/lockerAssignments";
import { listLockers } from "../../api/lockers";
import { listContacts } from "../../api/contacts";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createLockerAssignmentSchema, type CreateLockerAssignmentFormValues } from "../../lib/validation";
import type { ContactDto, LockerDto } from "../../types/api";

export default function LockerAssignmentCreatePage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const preselectedLockerId = searchParams.get("lockerId") ?? "";
  const [formError, setFormError] = useState<string | null>(null);
  const [lockers, setLockers] = useState<LockerDto[]>([]);
  const [contacts, setContacts] = useState<ContactDto[]>([]);

  useEffect(() => {
    listLockers({ size: 100, sort: "label,asc" })
      .then((res) => setLockers(res.content.filter((locker) => locker.status === "ACTIVE")))
      .catch(() => setLockers([]));
    listContacts({ size: 100, sort: "lastName,asc" })
      .then((res) => setContacts(res.content))
      .catch(() => setContacts([]));
  }, []);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateLockerAssignmentFormValues>({
    resolver: zodResolver(createLockerAssignmentSchema),
    defaultValues: { lockerId: preselectedLockerId },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const assignment = await createLockerAssignment({
        lockerId: values.lockerId,
        contactId: values.contactId,
        expiresAt: blankToUndefined(values.expiresAt),
        notes: blankToUndefined(values.notes),
      });
      navigate(`/locker-assignments/${assignment.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New locker assignment</h1>
        <p className="mt-1 text-sm text-slate-500">Assign a locker to a client.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <Select
            label="Locker"
            placeholder="Select a locker"
            options={lockers.map((locker) => ({ value: locker.id, label: locker.label }))}
            error={errors.lockerId?.message}
            {...register("lockerId")}
          />
          <Select
            label="Client"
            placeholder="Select a contact"
            options={contacts.map((contact) => ({ value: contact.id, label: contact.fullName }))}
            error={errors.contactId?.message}
            {...register("contactId")}
          />
        </div>

        <TextField label="Expires on" type="date" error={errors.expiresAt?.message} {...register("expiresAt")} />

        <TextArea label="Notes" error={errors.notes?.message} {...register("notes")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/locker-assignments")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Create assignment
          </Button>
        </div>
      </form>
    </div>
  );
}
