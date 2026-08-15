import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { listContacts } from "../../api/contacts";
import { deleteReferral, getReferral, issueReferralReward, updateReferral, updateReferralStatus } from "../../api/referrals";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, toOptionalNumber, updateReferralSchema, type UpdateReferralFormValues } from "../../lib/validation";
import { REFERRAL_STATUSES, type ContactDto, type ReferralDto, type ReferralStatus } from "../../types/api";
import { ReferralStatusBadge } from "./ReferralListPage";

export default function ReferralDetailPage() {
  const { referralId } = useParams<{ referralId: string }>();
  const navigate = useNavigate();
  const [referral, setReferral] = useState<ReferralDto | null>(null);
  const [contacts, setContacts] = useState<ContactDto[]>([]);
  const [convertedContactId, setConvertedContactId] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [isUpdatingStatus, setIsUpdatingStatus] = useState(false);
  const [isIssuingReward, setIsIssuingReward] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<UpdateReferralFormValues>({ resolver: zodResolver(updateReferralSchema) });

  useEffect(() => {
    if (!referralId) return;
    let cancelled = false;
    getReferral(referralId)
      .then((data) => {
        if (cancelled) return;
        setReferral(data);
        reset({
          referredName: data.referredName,
          referredEmail: data.referredEmail ?? "",
          referredPhone: data.referredPhone ?? "",
          rewardAmount: data.rewardAmount != null ? String(data.rewardAmount) : "",
          notes: data.notes ?? "",
        });
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this referral.");
      });
    listContacts({ size: 100, sort: "lastName,asc" })
      .then((res) => {
        if (!cancelled) setContacts(res.content);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [referralId, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!referralId) return;
    setFormError(null);
    try {
      const updated = await updateReferral(referralId, {
        referredName: values.referredName,
        referredEmail: blankToUndefined(values.referredEmail),
        referredPhone: blankToUndefined(values.referredPhone),
        rewardAmount: toOptionalNumber(values.rewardAmount),
        notes: blankToUndefined(values.notes),
      });
      setReferral(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function handleStatusChange(status: string) {
    if (!referralId) return;
    setIsUpdatingStatus(true);
    setError(null);
    try {
      const updated = await updateReferralStatus(referralId, {
        status: status as ReferralStatus,
        convertedContactId: status === "CONVERTED" ? blankToUndefined(convertedContactId) : undefined,
      });
      setReferral(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update the status.");
    } finally {
      setIsUpdatingStatus(false);
    }
  }

  async function handleIssueReward() {
    if (!referralId) return;
    setIsIssuingReward(true);
    setError(null);
    try {
      const updated = await issueReferralReward(referralId);
      setReferral(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not issue the reward.");
    } finally {
      setIsIssuingReward(false);
    }
  }

  async function handleDelete() {
    if (!referralId || !window.confirm("Delete this referral? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      await deleteReferral(referralId);
      navigate("/referrals");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this referral.");
      setIsDeleting(false);
    }
  }

  if (error && !referral) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!referral) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  const referrer = contacts.find((c) => c.id === referral.referrerContactId);

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/referrals" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Referrals
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{referral.referredName}</h1>
            <ReferralStatusBadge status={referral.status} />
          </div>
          {referrer && <p className="mt-1 text-sm text-slate-500">Referred by {referrer.fullName}</p>}
        </div>
        <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
          Delete
        </Button>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="grid gap-4 sm:grid-cols-2">
        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Reward</h2>
          <dl className="mt-3 flex flex-col gap-2 text-sm">
            <Row label="Amount" value={referral.rewardAmount != null ? `$${referral.rewardAmount.toFixed(2)}` : "—"} />
            <Row label="Issued" value={referral.rewardIssuedAt ? new Date(referral.rewardIssuedAt).toLocaleString() : "Not yet"} />
          </dl>
          <div className="mt-4">
            <Button
              variant="secondary"
              onClick={() => void handleIssueReward()}
              isLoading={isIssuingReward}
              disabled={referral.rewardAmount == null || referral.rewardIssuedAt != null}
            >
              {referral.rewardIssuedAt ? "Reward issued" : "Issue reward"}
            </Button>
          </div>
        </div>

        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Status</h2>
          <p className="mt-1 text-xs text-slate-400">Referrals move freely between statuses - reinstating a declined referral is a normal correction.</p>
          <div className="mt-3 flex flex-col gap-3">
            <Select
              label="Status"
              options={REFERRAL_STATUSES.map((status) => ({ value: status, label: status }))}
              value={referral.status}
              disabled={isUpdatingStatus}
              onChange={(event) => void handleStatusChange(event.target.value)}
            />
            {referral.status !== "CONVERTED" && (
              <Select
                label="Converted contact (used when moving to Converted)"
                placeholder="Select the new contact"
                options={contacts.map((contact) => ({ value: contact.id, label: contact.fullName }))}
                value={convertedContactId}
                onChange={(event) => setConvertedContactId(event.target.value)}
              />
            )}
            {referral.convertedContactId && (
              <p className="text-xs text-slate-500">
                Converted to{" "}
                <Link to={`/contacts/${referral.convertedContactId}`} className="text-slate-700 hover:underline">
                  {contacts.find((c) => c.id === referral.convertedContactId)?.fullName ?? "this contact"}
                </Link>
              </p>
            )}
          </div>
        </div>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Edit referral</h2>

        {formError && <Alert variant="error">{formError}</Alert>}

        <TextField label="Referred person's name" error={errors.referredName?.message} {...register("referredName")} />

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Email" type="email" error={errors.referredEmail?.message} {...register("referredEmail")} />
          <TextField label="Phone" error={errors.referredPhone?.message} {...register("referredPhone")} />
        </div>

        <TextField label="Reward amount" type="number" min={0} step="0.01" error={errors.rewardAmount?.message} {...register("rewardAmount")} />

        <TextArea label="Notes" error={errors.notes?.message} {...register("notes")} />

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
