import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { listAccounts } from "../../api/accounts";
import { convertLead, deleteLead, getLead, updateLeadStatus } from "../../api/leads";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, convertLeadSchema, toOptionalNumber, type ConvertLeadFormValues } from "../../lib/validation";
import { LEAD_STATUSES, type AccountDto, type LeadDto, type LeadStatus } from "../../types/api";
import { StatusBadge } from "./LeadListPage";

const REASSIGNABLE_STATUSES: LeadStatus[] = LEAD_STATUSES.filter((status) => status !== "CONVERTED");

export default function LeadDetailPage() {
  const { leadId } = useParams<{ leadId: string }>();
  const navigate = useNavigate();
  const [lead, setLead] = useState<LeadDto | null>(null);
  const [accounts, setAccounts] = useState<AccountDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [convertError, setConvertError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [isUpdatingStatus, setIsUpdatingStatus] = useState(false);

  useEffect(() => {
    if (!leadId) return;
    let cancelled = false;
    getLead(leadId)
      .then((data) => {
        if (!cancelled) setLead(data);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this lead.");
      });
    listAccounts({ size: 100, sort: "name,asc" })
      .then((res) => {
        if (!cancelled) setAccounts(res.content);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [leadId]);

  const {
    register,
    handleSubmit,
    setError: setConvertFieldError,
    formState: { errors, isSubmitting },
  } = useForm<ConvertLeadFormValues>({
    resolver: zodResolver(convertLeadSchema),
    defaultValues: { createOpportunity: true },
  });

  const onConvert = handleSubmit(async (values) => {
    if (!leadId) return;
    setConvertError(null);
    try {
      await convertLead(leadId, {
        existingAccountId: blankToUndefined(values.existingAccountId),
        newAccountName: blankToUndefined(values.newAccountName),
        createOpportunity: values.createOpportunity,
        opportunityName: blankToUndefined(values.opportunityName),
        opportunityAmount: toOptionalNumber(values.opportunityAmount),
        opportunityExpectedCloseDate: blankToUndefined(values.opportunityExpectedCloseDate),
      });
      const refreshed = await getLead(leadId);
      setLead(refreshed);
    } catch (error) {
      setConvertError(applyServerErrors(error, setConvertFieldError));
    }
  });

  async function handleStatusChange(status: string) {
    if (!leadId) return;
    setIsUpdatingStatus(true);
    setError(null);
    try {
      const updated = await updateLeadStatus(leadId, { status: status as LeadStatus });
      setLead(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update the status.");
    } finally {
      setIsUpdatingStatus(false);
    }
  }

  async function handleDelete() {
    if (!leadId || !window.confirm("Delete this lead? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      await deleteLead(leadId);
      navigate("/leads");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this lead.");
      setIsDeleting(false);
    }
  }

  if (error && !lead) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!lead) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  const isConverted = lead.status === "CONVERTED";

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/leads" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Leads
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{lead.fullName}</h1>
            <StatusBadge status={lead.status} />
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
            <Row label="Company" value={lead.companyName} />
            <Row label="Title" value={lead.title} />
            <Row label="Email" value={lead.email} />
            <Row label="Phone" value={lead.phone} />
            <Row label="Source" value={lead.source} />
          </dl>
        </div>

        {!isConverted && (
          <div className="rounded-lg border border-slate-200 bg-white p-5">
            <h2 className="text-sm font-medium text-slate-500">Status</h2>
            <div className="mt-3">
              <Select
                label="Status"
                options={REASSIGNABLE_STATUSES.map((status) => ({ value: status, label: status }))}
                value={lead.status}
                disabled={isUpdatingStatus}
                onChange={(event) => void handleStatusChange(event.target.value)}
              />
            </div>
          </div>
        )}

        {isConverted && (
          <div className="rounded-lg border border-slate-200 bg-white p-5">
            <h2 className="text-sm font-medium text-slate-500">Converted to</h2>
            <dl className="mt-3 flex flex-col gap-2 text-sm">
              <Row
                label="Account"
                value={
                  lead.convertedAccountId && (
                    <Link to={`/accounts/${lead.convertedAccountId}`} className="text-slate-900 hover:underline">
                      View account
                    </Link>
                  )
                }
              />
              <Row
                label="Contact"
                value={
                  lead.convertedContactId && (
                    <Link to={`/contacts/${lead.convertedContactId}`} className="text-slate-900 hover:underline">
                      View contact
                    </Link>
                  )
                }
              />
              <Row
                label="Opportunity"
                value={
                  lead.convertedOpportunityId ? (
                    <Link to={`/opportunities/${lead.convertedOpportunityId}`} className="text-slate-900 hover:underline">
                      View opportunity
                    </Link>
                  ) : (
                    "Not created"
                  )
                }
              />
            </dl>
          </div>
        )}
      </div>

      {lead.description && (
        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Description</h2>
          <p className="mt-3 whitespace-pre-wrap text-sm text-slate-900">{lead.description}</p>
        </div>
      )}

      {!isConverted && (
        <form onSubmit={onConvert} noValidate className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
          <div>
            <h2 className="text-sm font-medium text-slate-900">Convert lead</h2>
            <p className="mt-1 text-sm text-slate-500">
              Creates an Account and Contact (and, by default, an Opportunity) from this lead.
            </p>
          </div>

          {convertError && <Alert variant="error">{convertError}</Alert>}

          <div className="grid gap-4 sm:grid-cols-2">
            <Select
              label="Existing account"
              placeholder="Create a new account"
              options={accounts.map((account) => ({ value: account.id, label: account.name }))}
              error={errors.existingAccountId?.message}
              {...register("existingAccountId")}
            />
            <TextField
              label="New account name"
              placeholder={lead.companyName ?? `${lead.fullName}'s Account`}
              error={errors.newAccountName?.message}
              {...register("newAccountName")}
            />
          </div>

          <label className="flex items-center gap-2 text-sm text-slate-700">
            <input type="checkbox" className="h-4 w-4 rounded border-slate-300" {...register("createOpportunity")} />
            Also create an opportunity
          </label>

          <div className="grid gap-4 sm:grid-cols-3">
            <TextField label="Opportunity name" error={errors.opportunityName?.message} {...register("opportunityName")} />
            <TextField
              label="Amount"
              type="number"
              min={0}
              step="any"
              error={errors.opportunityAmount?.message}
              {...register("opportunityAmount")}
            />
            <TextField
              label="Expected close date"
              type="date"
              error={errors.opportunityExpectedCloseDate?.message}
              {...register("opportunityExpectedCloseDate")}
            />
          </div>

          <div className="flex justify-end">
            <Button type="submit" isLoading={isSubmitting}>
              Convert lead
            </Button>
          </div>
        </form>
      )}
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
