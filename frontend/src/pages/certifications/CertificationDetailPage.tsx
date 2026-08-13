import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import {
  awardCertification,
  deleteCertification,
  getCertification,
  listUserCertifications,
  updateCertification,
  updateUserCertificationStatus,
} from "../../api/certifications";
import { listUsers } from "../../api/users";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import {
  awardCertificationSchema,
  blankToUndefined,
  certificationSchema,
  type AwardCertificationFormValues,
  type CertificationFormValues,
} from "../../lib/validation";
import type { CertificationDto, UserCertificationDto, UserCertificationStatus, UserDto } from "../../types/api";

const AWARD_STATUS_CLASSES: Record<UserCertificationStatus, string> = {
  ACTIVE: "bg-emerald-100 text-emerald-700",
  EXPIRED: "bg-amber-100 text-amber-700",
  REVOKED: "bg-red-100 text-red-700",
};

export default function CertificationDetailPage() {
  const { certificationId } = useParams<{ certificationId: string }>();
  const navigate = useNavigate();
  const [certification, setCertification] = useState<CertificationDto | null>(null);
  const [awards, setAwards] = useState<UserCertificationDto[]>([]);
  const [users, setUsers] = useState<UserDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<CertificationFormValues>({ resolver: zodResolver(certificationSchema) });

  function loadAwards() {
    if (!certificationId) return;
    listUserCertifications({ size: 200, sort: "earnedAt,desc" })
      .then((res) => setAwards(res.content.filter((a) => a.certificationId === certificationId)))
      .catch(() => undefined);
  }

  useEffect(() => {
    if (!certificationId) return;
    let cancelled = false;
    getCertification(certificationId)
      .then((data) => {
        if (cancelled) return;
        setCertification(data);
        reset({
          name: data.name,
          issuingBody: data.issuingBody ?? "",
          description: data.description ?? "",
          validityMonths: data.validityMonths ? String(data.validityMonths) : "",
        });
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this certification.");
      });
    listUsers({ size: 200 })
      .then((res) => {
        if (!cancelled) setUsers(res.content);
      })
      .catch(() => undefined);
    loadAwards();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [certificationId, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!certificationId || !certification) return;
    setFormError(null);
    try {
      const updated = await updateCertification(certificationId, {
        name: values.name,
        issuingBody: blankToUndefined(values.issuingBody),
        description: blankToUndefined(values.description),
        validityMonths: values.validityMonths === "" || values.validityMonths === undefined ? undefined : Number(values.validityMonths),
        active: certification.active,
      });
      setCertification(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function toggleActive() {
    if (!certificationId || !certification) return;
    try {
      const updated = await updateCertification(certificationId, {
        name: certification.name,
        issuingBody: certification.issuingBody ?? undefined,
        description: certification.description ?? undefined,
        validityMonths: certification.validityMonths ?? undefined,
        active: !certification.active,
      });
      setCertification(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update this certification.");
    }
  }

  async function handleDelete() {
    if (!certificationId || !window.confirm("Delete this certification?")) return;
    setIsDeleting(true);
    try {
      await deleteCertification(certificationId);
      navigate("/certifications");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this certification.");
      setIsDeleting(false);
    }
  }

  function userLabel(userId: string): string {
    return users.find((u) => u.id === userId)?.fullName ?? "Unknown teammate";
  }

  if (error && !certification) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!certification) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/certifications" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Certifications
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{certification.name}</h1>
            {certification.active ? (
              <span className="inline-block rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-medium text-emerald-700">Active</span>
            ) : (
              <span className="inline-block rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-500">Inactive</span>
            )}
          </div>
        </div>
        <div className="flex gap-3">
          <Button variant="secondary" onClick={() => void toggleActive()}>
            {certification.active ? "Deactivate" : "Activate"}
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

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Issuing body" error={errors.issuingBody?.message} {...register("issuingBody")} />
          <TextField
            label="Validity (months)"
            type="number"
            min={1}
            placeholder="Never expires"
            error={errors.validityMonths?.message}
            {...register("validityMonths")}
          />
        </div>

        <TextArea label="Description" error={errors.description?.message} {...register("description")} />

        <div className="flex justify-end">
          <Button type="submit" isLoading={isSubmitting}>
            Save changes
          </Button>
        </div>
      </form>

      <AwardsPanel awards={awards} userLabel={userLabel} onChanged={loadAwards} setError={setError} />
      <AwardForm certificationId={certification.id} users={users} onAwarded={loadAwards} />
    </div>
  );
}

function AwardsPanel({
  awards,
  userLabel,
  onChanged,
  setError,
}: {
  awards: UserCertificationDto[];
  userLabel: (userId: string) => string;
  onChanged: () => void;
  setError: (message: string | null) => void;
}) {
  const [actioningId, setActioningId] = useState<string | null>(null);

  async function revoke(award: UserCertificationDto) {
    if (!window.confirm(`Revoke ${userLabel(award.userId)}'s credential?`)) return;
    setActioningId(award.id);
    try {
      await updateUserCertificationStatus(award.id, { status: "REVOKED" });
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update this record.");
    } finally {
      setActioningId(null);
    }
  }

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-5">
      <h2 className="text-sm font-medium text-slate-500">Who holds this credential</h2>
      <p className="mt-1 text-xs text-slate-400">
        Expiry is computed from the earned date plus this credential's validity - it isn't re-derived if the validity above
        changes later, so an already-issued award never silently shifts.
      </p>
      <div className="mt-3 overflow-hidden rounded-md border border-slate-100">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-100 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-3 py-2 font-medium">Teammate</th>
              <th className="px-3 py-2 font-medium">Earned</th>
              <th className="px-3 py-2 font-medium">Expires</th>
              <th className="px-3 py-2 font-medium">Status</th>
              <th className="px-3 py-2 font-medium" />
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {awards.length === 0 && (
              <tr>
                <td className="px-3 py-4 text-center text-slate-400" colSpan={5}>
                  No one holds this credential yet.
                </td>
              </tr>
            )}
            {awards.map((award) => (
              <tr key={award.id}>
                <td className="px-3 py-2 text-slate-900">{userLabel(award.userId)}</td>
                <td className="px-3 py-2 text-slate-600">{new Date(award.earnedAt).toLocaleDateString()}</td>
                <td className="px-3 py-2 text-slate-600">{award.expiresAt ? new Date(award.expiresAt).toLocaleDateString() : "Never"}</td>
                <td className="px-3 py-2">
                  <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${AWARD_STATUS_CLASSES[award.status]}`}>
                    {award.status}
                    {award.expired && award.status === "ACTIVE" ? " (expired)" : ""}
                  </span>
                </td>
                <td className="px-3 py-2 text-right">
                  {award.status !== "REVOKED" && (
                    <Button variant="danger" isLoading={actioningId === award.id} onClick={() => void revoke(award)}>
                      Revoke
                    </Button>
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

function AwardForm({ certificationId, users, onAwarded }: { certificationId: string; users: UserDto[]; onAwarded: () => void }) {
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<AwardCertificationFormValues>({
    resolver: zodResolver(awardCertificationSchema),
    defaultValues: { certificationId, userId: "", earnedAt: "", credentialNumber: "" },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await awardCertification({
        certificationId,
        userId: blankToUndefined(values.userId),
        earnedAt: values.earnedAt,
        credentialNumber: blankToUndefined(values.credentialNumber),
      });
      reset({ certificationId, userId: "", earnedAt: "", credentialNumber: "" });
      onAwarded();
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
      <h2 className="text-sm font-medium text-slate-900">Award this credential</h2>

      {formError && <Alert variant="error">{formError}</Alert>}

      <div className="grid gap-4 sm:grid-cols-3">
        <Select
          label="Teammate"
          placeholder="Myself"
          options={users.map((user) => ({ value: user.id, label: user.fullName }))}
          error={errors.userId?.message}
          {...register("userId")}
        />
        <TextField label="Earned date" type="date" error={errors.earnedAt?.message} {...register("earnedAt")} />
        <TextField label="Credential number" error={errors.credentialNumber?.message} {...register("credentialNumber")} />
      </div>

      <div className="flex justify-end">
        <Button type="submit" isLoading={isSubmitting}>
          Award
        </Button>
      </div>
    </form>
  );
}
