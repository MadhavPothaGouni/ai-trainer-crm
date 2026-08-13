import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { listAccounts } from "../../api/accounts";
import { deleteContract, getContract, updateContract, updateContractStatus } from "../../api/contracts";
import { listOpportunities } from "../../api/opportunities";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createContractSchema, toOptionalNumber, type CreateContractFormValues } from "../../lib/validation";
import { CONTRACT_STATUSES, type AccountDto, type ContractDto, type ContractStatus, type OpportunityDto } from "../../types/api";
import { ContractStatusBadge } from "./ContractListPage";

export default function ContractDetailPage() {
  const { contractId } = useParams<{ contractId: string }>();
  const navigate = useNavigate();
  const [contract, setContract] = useState<ContractDto | null>(null);
  const [accounts, setAccounts] = useState<AccountDto[]>([]);
  const [opportunities, setOpportunities] = useState<OpportunityDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [isUpdatingStatus, setIsUpdatingStatus] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);

  useEffect(() => {
    if (!contractId) return;
    let cancelled = false;
    getContract(contractId)
      .then((data) => {
        if (!cancelled) setContract(data);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this contract.");
      });
    listAccounts({ size: 100, sort: "name,asc" })
      .then((res) => {
        if (!cancelled) setAccounts(res.content);
      })
      .catch(() => undefined);
    listOpportunities({ size: 100, sort: "createdAt,desc" })
      .then((res) => {
        if (!cancelled) setOpportunities(res.content);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [contractId]);

  const {
    register,
    handleSubmit,
    reset,
    watch,
    formState: { errors, isSubmitting },
    setError: setEditFieldError,
  } = useForm<CreateContractFormValues>({ resolver: zodResolver(createContractSchema) });

  const autoRenew = watch("autoRenew");

  useEffect(() => {
    if (!contract) return;
    reset({
      accountId: contract.accountId,
      opportunityId: contract.opportunityId ?? "",
      contractNumber: contract.contractNumber,
      title: contract.title,
      startDate: contract.startDate,
      endDate: contract.endDate,
      totalValue: contract.totalValue != null ? String(contract.totalValue) : "",
      autoRenew: contract.autoRenew,
      renewalTermMonths: contract.renewalTermMonths != null ? String(contract.renewalTermMonths) : "",
      terms: contract.terms ?? "",
    });
  }, [contract, reset]);

  const onSaveEdits = handleSubmit(async (values) => {
    if (!contractId) return;
    setEditError(null);
    try {
      const updated = await updateContract(contractId, {
        opportunityId: blankToUndefined(values.opportunityId),
        contractNumber: values.contractNumber,
        title: values.title,
        startDate: values.startDate,
        endDate: values.endDate,
        totalValue: toOptionalNumber(values.totalValue),
        autoRenew: values.autoRenew ?? false,
        renewalTermMonths: values.renewalTermMonths ? Number(values.renewalTermMonths) : undefined,
        terms: blankToUndefined(values.terms),
      });
      setContract(updated);
    } catch (error) {
      setEditError(applyServerErrors(error, setEditFieldError));
    }
  });

  async function handleStatusChange(status: string) {
    if (!contractId) return;
    setIsUpdatingStatus(true);
    setError(null);
    try {
      const updated = await updateContractStatus(contractId, { status: status as ContractStatus });
      setContract(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update the status.");
    } finally {
      setIsUpdatingStatus(false);
    }
  }

  async function handleDelete() {
    if (!contractId || !window.confirm("Delete this contract? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      await deleteContract(contractId);
      navigate("/contracts");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this contract.");
      setIsDeleting(false);
    }
  }

  if (error && !contract) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!contract) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  const linkedAccount = accounts.find((account) => account.id === contract.accountId);
  const linkedOpportunity = opportunities.find((opportunity) => opportunity.id === contract.opportunityId);

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/contracts" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Contracts
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{contract.contractNumber}</h1>
            <ContractStatusBadge status={contract.status} />
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
              label="Opportunity"
              value={
                linkedOpportunity && (
                  <Link to={`/opportunities/${linkedOpportunity.id}`} className="text-slate-900 hover:underline">
                    {linkedOpportunity.name}
                  </Link>
                )
              }
            />
            <Row label="Total value" value={contract.totalValue != null ? contract.totalValue.toLocaleString() : undefined} />
            <Row label="Auto-renews" value={contract.autoRenew ? `Yes (${contract.renewalTermMonths ?? "?"} months)` : "No"} />
            <Row label="Signed at" value={contract.signedAt ? new Date(contract.signedAt).toLocaleString() : undefined} />
          </dl>
        </div>

        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Status</h2>
          <p className="mt-1 text-xs text-slate-400">Contracts move freely between statuses - correcting a mistaken termination is normal.</p>
          <div className="mt-3">
            <Select
              label="Status"
              options={CONTRACT_STATUSES.map((status) => ({ value: status, label: status }))}
              value={contract.status}
              disabled={isUpdatingStatus}
              onChange={(event) => void handleStatusChange(event.target.value)}
            />
          </div>
        </div>
      </div>

      <form onSubmit={onSaveEdits} noValidate className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Edit contract</h2>

        {editError && <Alert variant="error">{editError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <Select
            label="Opportunity"
            placeholder="None"
            options={opportunities.map((opportunity) => ({ value: opportunity.id, label: opportunity.name }))}
            error={errors.opportunityId?.message}
            {...register("opportunityId")}
          />
          <TextField label="Contract number" error={errors.contractNumber?.message} {...register("contractNumber")} />
        </div>

        <TextField label="Title" error={errors.title?.message} {...register("title")} />

        <div className="grid gap-4 sm:grid-cols-3">
          <TextField label="Start date" type="date" error={errors.startDate?.message} {...register("startDate")} />
          <TextField label="End date" type="date" error={errors.endDate?.message} {...register("endDate")} />
          <TextField label="Total value" type="number" min={0} step="any" error={errors.totalValue?.message} {...register("totalValue")} />
        </div>

        <label className="flex items-center gap-2 text-sm text-slate-700">
          <input type="checkbox" className="h-4 w-4 rounded border-slate-300" {...register("autoRenew")} />
          Auto-renews
        </label>

        {autoRenew && (
          <TextField
            label="Renewal term (months)"
            type="number"
            min={1}
            step={1}
            error={errors.renewalTermMonths?.message}
            {...register("renewalTermMonths")}
          />
        )}

        <TextArea label="Terms" error={errors.terms?.message} {...register("terms")} />

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
