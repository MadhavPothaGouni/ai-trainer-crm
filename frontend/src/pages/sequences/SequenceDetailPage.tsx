import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { listContacts } from "../../api/contacts";
import { listLeads } from "../../api/leads";
import {
  addSequenceStep,
  advanceSequenceEnrollment,
  createSequenceEnrollment,
  deleteSequence,
  getSequence,
  listSequenceEnrollments,
  removeSequenceStep,
  updateSequence,
  updateSequenceEnrollmentStatus,
} from "../../api/sequences";
import { listUsers } from "../../api/users";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import {
  blankToUndefined,
  createSequenceEnrollmentSchema,
  createSequenceSchema,
  sequenceStepSchema,
  type CreateSequenceEnrollmentFormValues,
  type CreateSequenceFormValues,
  type SequenceStepFormValues,
} from "../../lib/validation";
import type {
  ContactDto,
  LeadDto,
  SequenceDto,
  SequenceEnrollmentDto,
  SequenceEnrollmentStatus,
  SequenceEnrollmentTargetType,
  SequenceStepType,
  UserDto,
} from "../../types/api";

const STEP_TYPES: SequenceStepType[] = ["EMAIL", "CALL", "TASK"];

const ENROLLMENT_STATUS_CLASSES: Record<SequenceEnrollmentStatus, string> = {
  ACTIVE: "bg-blue-100 text-blue-700",
  PAUSED: "bg-amber-100 text-amber-700",
  COMPLETED: "bg-emerald-100 text-emerald-700",
  CANCELLED: "bg-slate-100 text-slate-600",
};

export default function SequenceDetailPage() {
  const { sequenceId } = useParams<{ sequenceId: string }>();
  const navigate = useNavigate();
  const [sequence, setSequence] = useState<SequenceDto | null>(null);
  const [enrollments, setEnrollments] = useState<SequenceEnrollmentDto[]>([]);
  const [users, setUsers] = useState<UserDto[]>([]);
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
  } = useForm<CreateSequenceFormValues>({ resolver: zodResolver(createSequenceSchema) });

  function loadSequence() {
    if (!sequenceId) return;
    getSequence(sequenceId)
      .then((data) => {
        setSequence(data);
        reset({ name: data.name, description: data.description ?? "" });
      })
      .catch((err: unknown) => setError(err instanceof ApiError ? err.message : "Could not load this sequence."));
  }

  function loadEnrollments() {
    if (!sequenceId) return;
    listSequenceEnrollments({ size: 200, sort: "enrolledAt,desc" })
      .then((res) => setEnrollments(res.content.filter((e) => e.sequenceId === sequenceId)))
      .catch(() => undefined);
  }

  useEffect(() => {
    if (!sequenceId) return;
    let cancelled = false;
    getSequence(sequenceId)
      .then((data) => {
        if (cancelled) return;
        setSequence(data);
        reset({ name: data.name, description: data.description ?? "" });
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this sequence.");
      });
    listUsers({ size: 200 })
      .then((res) => {
        if (!cancelled) setUsers(res.content);
      })
      .catch(() => undefined);
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
    loadEnrollments();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sequenceId, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!sequenceId || !sequence) return;
    setFormError(null);
    try {
      const updated = await updateSequence(sequenceId, {
        name: values.name,
        description: blankToUndefined(values.description),
        active: sequence.active,
      });
      setSequence(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function toggleActive() {
    if (!sequenceId || !sequence) return;
    try {
      const updated = await updateSequence(sequenceId, {
        name: sequence.name,
        description: sequence.description ?? undefined,
        active: !sequence.active,
      });
      setSequence(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update this sequence.");
    }
  }

  async function handleDelete() {
    if (!sequenceId || !window.confirm("Delete this sequence?")) return;
    setIsDeleting(true);
    try {
      await deleteSequence(sequenceId);
      navigate("/sequences");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this sequence.");
      setIsDeleting(false);
    }
  }

  function targetLabel(enrollment: SequenceEnrollmentDto): string {
    if (enrollment.targetType === "LEAD") {
      return leads.find((l) => l.id === enrollment.targetId)?.fullName ?? "Unknown lead";
    }
    return contacts.find((c) => c.id === enrollment.targetId)?.fullName ?? "Unknown contact";
  }

  function ownerLabel(ownerId: string): string {
    return users.find((u) => u.id === ownerId)?.fullName ?? "Unknown teammate";
  }

  if (error && !sequence) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!sequence) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/sequences" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Sequences
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{sequence.name}</h1>
            {sequence.active ? (
              <span className="inline-block rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-medium text-emerald-700">Active</span>
            ) : (
              <span className="inline-block rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-500">Inactive</span>
            )}
          </div>
        </div>
        <div className="flex gap-3">
          <Button variant="secondary" onClick={() => void toggleActive()}>
            {sequence.active ? "Deactivate" : "Activate"}
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

        <TextField label="Name" error={errors.name?.message} {...register("name")} />
        <TextArea label="Description" error={errors.description?.message} {...register("description")} />

        <div className="flex justify-end">
          <Button type="submit" isLoading={isSubmitting}>
            Save changes
          </Button>
        </div>
      </form>

      <StepsPanel sequence={sequence} onChanged={loadSequence} setError={setError} />
      <EnrollmentsPanel enrollments={enrollments} targetLabel={targetLabel} ownerLabel={ownerLabel} onChanged={loadEnrollments} setError={setError} />
      <EnrollForm sequenceId={sequence.id} users={users} leads={leads} contacts={contacts} onEnrolled={loadEnrollments} />
    </div>
  );
}

function StepsPanel({
  sequence,
  onChanged,
  setError,
}: {
  sequence: SequenceDto;
  onChanged: () => void;
  setError: (message: string | null) => void;
}) {
  const [removingId, setRemovingId] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<SequenceStepFormValues>({
    resolver: zodResolver(sequenceStepSchema),
    defaultValues: { type: "EMAIL", dayOffset: "0" },
  });

  const onSubmit = handleSubmit(async (values) => {
    try {
      await addSequenceStep(sequence.id, {
        type: values.type as SequenceStepType,
        dayOffset: Number(values.dayOffset),
        subject: blankToUndefined(values.subject),
        body: blankToUndefined(values.body),
      });
      reset({ type: "EMAIL", dayOffset: "0", subject: "", body: "" });
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not add this step.");
    }
  });

  async function handleRemove(stepId: string) {
    setRemovingId(stepId);
    try {
      await removeSequenceStep(sequence.id, stepId);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not remove this step.");
    } finally {
      setRemovingId(null);
    }
  }

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-5">
      <h2 className="text-sm font-medium text-slate-500">Steps</h2>
      <p className="mt-1 text-xs text-slate-400">
        New steps are always appended at the end. Day offset is display-only - there's no scheduler enforcing it.
      </p>

      <div className="mt-3 overflow-hidden rounded-md border border-slate-100">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-100 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-3 py-2 font-medium">#</th>
              <th className="px-3 py-2 font-medium">Type</th>
              <th className="px-3 py-2 font-medium">Day</th>
              <th className="px-3 py-2 font-medium">Subject</th>
              <th className="px-3 py-2 font-medium" />
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {sequence.steps.length === 0 && (
              <tr>
                <td className="px-3 py-4 text-center text-slate-400" colSpan={5}>
                  No steps yet.
                </td>
              </tr>
            )}
            {sequence.steps.map((step) => (
              <tr key={step.id}>
                <td className="px-3 py-2 text-slate-600">{step.stepOrder + 1}</td>
                <td className="px-3 py-2 text-slate-900">{step.type}</td>
                <td className="px-3 py-2 text-slate-600">Day {step.dayOffset}</td>
                <td className="px-3 py-2 text-slate-600">{step.subject ?? "—"}</td>
                <td className="px-3 py-2 text-right">
                  <Button variant="danger" isLoading={removingId === step.id} onClick={() => void handleRemove(step.id)}>
                    Remove
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <form onSubmit={onSubmit} noValidate className="mt-4 flex flex-col gap-3 border-t border-slate-100 pt-4">
        <div className="grid gap-3 sm:grid-cols-3">
          <Select
            label="Type"
            options={STEP_TYPES.map((type) => ({ value: type, label: type }))}
            error={errors.type?.message}
            {...register("type")}
          />
          <TextField label="Day offset" type="number" min={0} error={errors.dayOffset?.message} {...register("dayOffset")} />
          <TextField label="Subject" error={errors.subject?.message} {...register("subject")} />
        </div>
        <TextArea label="Body" error={errors.body?.message} {...register("body")} />
        <div className="flex justify-end">
          <Button type="submit" isLoading={isSubmitting}>
            Add step
          </Button>
        </div>
      </form>
    </div>
  );
}

function EnrollmentsPanel({
  enrollments,
  targetLabel,
  ownerLabel,
  onChanged,
  setError,
}: {
  enrollments: SequenceEnrollmentDto[];
  targetLabel: (enrollment: SequenceEnrollmentDto) => string;
  ownerLabel: (ownerId: string) => string;
  onChanged: () => void;
  setError: (message: string | null) => void;
}) {
  const [actioningId, setActioningId] = useState<string | null>(null);

  async function advance(enrollment: SequenceEnrollmentDto) {
    setActioningId(enrollment.id);
    try {
      await advanceSequenceEnrollment(enrollment.id);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not advance this enrollment.");
    } finally {
      setActioningId(null);
    }
  }

  async function pause(enrollment: SequenceEnrollmentDto) {
    setActioningId(enrollment.id);
    try {
      await updateSequenceEnrollmentStatus(enrollment.id, { status: enrollment.status === "PAUSED" ? "ACTIVE" : "PAUSED" });
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update this enrollment.");
    } finally {
      setActioningId(null);
    }
  }

  async function cancel(enrollment: SequenceEnrollmentDto) {
    setActioningId(enrollment.id);
    try {
      await updateSequenceEnrollmentStatus(enrollment.id, { status: "CANCELLED" });
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not cancel this enrollment.");
    } finally {
      setActioningId(null);
    }
  }

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-5">
      <h2 className="text-sm font-medium text-slate-500">Enrollments</h2>
      <p className="mt-1 text-xs text-slate-400">
        Advancing past the last step automatically completes the enrollment - there's no separate "mark complete" action.
      </p>
      <div className="mt-3 overflow-hidden rounded-md border border-slate-100">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-100 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-3 py-2 font-medium">Target</th>
              <th className="px-3 py-2 font-medium">Owner</th>
              <th className="px-3 py-2 font-medium">Step</th>
              <th className="px-3 py-2 font-medium">Status</th>
              <th className="px-3 py-2 font-medium" />
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {enrollments.length === 0 && (
              <tr>
                <td className="px-3 py-4 text-center text-slate-400" colSpan={5}>
                  No one is enrolled yet.
                </td>
              </tr>
            )}
            {enrollments.map((enrollment) => (
              <tr key={enrollment.id}>
                <td className="px-3 py-2 text-slate-900">
                  {targetLabel(enrollment)} <span className="text-xs text-slate-400">({enrollment.targetType})</span>
                </td>
                <td className="px-3 py-2 text-slate-600">{ownerLabel(enrollment.ownerId)}</td>
                <td className="px-3 py-2 text-slate-600">{enrollment.currentStepIndex}</td>
                <td className="px-3 py-2">
                  <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${ENROLLMENT_STATUS_CLASSES[enrollment.status]}`}>
                    {enrollment.status}
                  </span>
                </td>
                <td className="px-3 py-2 text-right">
                  {(enrollment.status === "ACTIVE" || enrollment.status === "PAUSED") && (
                    <div className="flex justify-end gap-2">
                      {enrollment.status === "ACTIVE" && (
                        <Button variant="secondary" isLoading={actioningId === enrollment.id} onClick={() => void advance(enrollment)}>
                          Advance
                        </Button>
                      )}
                      <Button variant="secondary" isLoading={actioningId === enrollment.id} onClick={() => void pause(enrollment)}>
                        {enrollment.status === "PAUSED" ? "Resume" : "Pause"}
                      </Button>
                      <Button variant="danger" isLoading={actioningId === enrollment.id} onClick={() => void cancel(enrollment)}>
                        Cancel
                      </Button>
                    </div>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function EnrollForm({
  sequenceId,
  users,
  leads,
  contacts,
  onEnrolled,
}: {
  sequenceId: string;
  users: UserDto[];
  leads: LeadDto[];
  contacts: ContactDto[];
  onEnrolled: () => void;
}) {
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    watch,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<CreateSequenceEnrollmentFormValues>({
    resolver: zodResolver(createSequenceEnrollmentSchema),
    defaultValues: { sequenceId, targetType: "LEAD", targetId: "", ownerId: "" },
  });

  const targetType = watch("targetType");
  const targetOptions =
    targetType === "CONTACT" ? contacts.map((c) => ({ value: c.id, label: c.fullName })) : leads.map((l) => ({ value: l.id, label: l.fullName }));

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await createSequenceEnrollment({
        sequenceId,
        targetType: values.targetType as SequenceEnrollmentTargetType,
        targetId: values.targetId,
        ownerId: blankToUndefined(values.ownerId),
      });
      reset({ sequenceId, targetType: "LEAD", targetId: "", ownerId: "" });
      onEnrolled();
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
      <h2 className="text-sm font-medium text-slate-900">Enroll a Lead or Contact</h2>

      {formError && <Alert variant="error">{formError}</Alert>}

      <div className="grid gap-4 sm:grid-cols-3">
        <Select label="Type" options={[{ value: "LEAD", label: "Lead" }, { value: "CONTACT", label: "Contact" }]} error={errors.targetType?.message} {...register("targetType")} />
        <Select label="Target" options={targetOptions} error={errors.targetId?.message} {...register("targetId")} />
        <Select
          label="Owner"
          placeholder="Myself"
          options={users.map((user) => ({ value: user.id, label: user.fullName }))}
          error={errors.ownerId?.message}
          {...register("ownerId")}
        />
      </div>

      <div className="flex justify-end">
        <Button type="submit" isLoading={isSubmitting}>
          Enroll
        </Button>
      </div>
    </form>
  );
}
