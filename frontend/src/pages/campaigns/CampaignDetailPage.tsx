import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import {
  addCampaignMember,
  deleteCampaign,
  getCampaign,
  getCampaignStats,
  listCampaignMembers,
  removeCampaignMember,
  updateCampaign,
  updateCampaignMemberStatus,
  updateCampaignStatus,
} from "../../api/campaigns";
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
  addCampaignMemberSchema,
  blankToUndefined,
  createCampaignSchema,
  toOptionalNumber,
  type AddCampaignMemberFormValues,
  type CreateCampaignFormValues,
} from "../../lib/validation";
import {
  CAMPAIGN_MEMBER_STATUSES,
  CAMPAIGN_TYPES,
  type CampaignDto,
  type CampaignMemberDto,
  type CampaignMemberStatus,
  type CampaignStatsDto,
  type CampaignType,
  type ContactDto,
  type LeadDto,
} from "../../types/api";
import { CampaignStatusBadge } from "./CampaignListPage";

const NEXT_STATUS: Record<CampaignDto["status"], CampaignDto["status"][]> = {
  PLANNED: ["ACTIVE", "CANCELLED"],
  ACTIVE: ["COMPLETED", "CANCELLED"],
  COMPLETED: [],
  CANCELLED: [],
};

export default function CampaignDetailPage() {
  const { campaignId } = useParams<{ campaignId: string }>();
  const navigate = useNavigate();
  const [campaign, setCampaign] = useState<CampaignDto | null>(null);
  const [members, setMembers] = useState<CampaignMemberDto[]>([]);
  const [stats, setStats] = useState<CampaignStatsDto | null>(null);
  const [leads, setLeads] = useState<LeadDto[]>([]);
  const [contacts, setContacts] = useState<ContactDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [isTransitioning, setIsTransitioning] = useState(false);
  const [pendingMemberId, setPendingMemberId] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<CreateCampaignFormValues>({ resolver: zodResolver(createCampaignSchema) });

  function reload() {
    if (!campaignId) return;
    getCampaign(campaignId)
      .then((data) => {
        setCampaign(data);
        reset({
          name: data.name,
          type: data.type,
          startDate: data.startDate ?? "",
          endDate: data.endDate ?? "",
          budget: data.budget === null ? "" : String(data.budget),
          actualCost: data.actualCost === null ? "" : String(data.actualCost),
          description: data.description ?? "",
        });
      })
      .catch((err: unknown) => setError(err instanceof ApiError ? err.message : "Could not load this campaign."));
    listCampaignMembers(campaignId).then(setMembers).catch(() => undefined);
    getCampaignStats(campaignId).then(setStats).catch(() => undefined);
  }

  useEffect(() => {
    reload();
    listLeads({ size: 100, sort: "createdAt,desc" }).then((res) => setLeads(res.content)).catch(() => undefined);
    listContacts({ size: 100, sort: "createdAt,desc" }).then((res) => setContacts(res.content)).catch(() => undefined);
  }, [campaignId]);

  const onSaveHeader = handleSubmit(async (values) => {
    if (!campaignId) return;
    setFormError(null);
    try {
      const updated = await updateCampaign(campaignId, {
        name: values.name,
        type: values.type as CampaignType,
        startDate: blankToUndefined(values.startDate),
        endDate: blankToUndefined(values.endDate),
        budget: toOptionalNumber(values.budget),
        actualCost: toOptionalNumber(values.actualCost),
        description: blankToUndefined(values.description),
      });
      setCampaign(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function handleStatusChange(status: CampaignDto["status"]) {
    if (!campaignId) return;
    setIsTransitioning(true);
    setError(null);
    try {
      setCampaign(await updateCampaignStatus(campaignId, { status }));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update this campaign's status.");
    } finally {
      setIsTransitioning(false);
    }
  }

  async function handleDelete() {
    if (!campaignId || !window.confirm("Delete this campaign? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      await deleteCampaign(campaignId);
      navigate("/campaigns");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this campaign.");
      setIsDeleting(false);
    }
  }

  async function handleMemberStatusChange(memberId: string, status: CampaignMemberStatus) {
    if (!campaignId) return;
    setPendingMemberId(memberId);
    try {
      await updateCampaignMemberStatus(campaignId, memberId, { status });
      reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update this member's status.");
    } finally {
      setPendingMemberId(null);
    }
  }

  async function handleRemoveMember(memberId: string) {
    if (!campaignId || !window.confirm("Remove this member from the campaign?")) return;
    setPendingMemberId(memberId);
    try {
      await removeCampaignMember(campaignId, memberId);
      reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not remove this member.");
    } finally {
      setPendingMemberId(null);
    }
  }

  function leadOrContactLabel(member: CampaignMemberDto): string {
    if (member.leadId) {
      const lead = leads.find((candidate) => candidate.id === member.leadId);
      return lead ? `${lead.fullName} (Lead)` : "Lead";
    }
    const contact = contacts.find((candidate) => candidate.id === member.contactId);
    return contact ? `${contact.fullName} (Contact)` : "Contact";
  }

  if (error && !campaign) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!campaign || !campaignId) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  const nextStatuses = NEXT_STATUS[campaign.status];

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/campaigns" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Campaigns
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{campaign.name}</h1>
            <CampaignStatusBadge status={campaign.status} />
          </div>
        </div>
        <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
          Delete
        </Button>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="grid gap-4 sm:grid-cols-2">
        <form onSubmit={onSaveHeader} noValidate className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Details</h2>
          {formError && <Alert variant="error">{formError}</Alert>}

          <TextField label="Campaign name" error={errors.name?.message} {...register("name")} />
          <Select
            label="Type"
            options={CAMPAIGN_TYPES.map((type) => ({ value: type, label: type.replace("_", " ") }))}
            error={errors.type?.message}
            {...register("type")}
          />
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField label="Start date" type="date" error={errors.startDate?.message} {...register("startDate")} />
            <TextField label="End date" type="date" error={errors.endDate?.message} {...register("endDate")} />
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField label="Budget" type="number" min={0} step="any" error={errors.budget?.message} {...register("budget")} />
            <TextField label="Actual cost" type="number" min={0} step="any" error={errors.actualCost?.message} {...register("actualCost")} />
          </div>
          <TextArea label="Description" error={errors.description?.message} {...register("description")} />
          <div className="flex justify-end">
            <Button type="submit" isLoading={isSubmitting}>
              Save changes
            </Button>
          </div>
        </form>

        <div className="flex flex-col gap-4">
          <div className="rounded-lg border border-slate-200 bg-white p-5">
            <h2 className="text-sm font-medium text-slate-500">Status</h2>
            <div className="mt-3 flex flex-wrap gap-2">
              {nextStatuses.length === 0 && <p className="text-sm text-slate-400">No further transitions from {campaign.status}.</p>}
              {nextStatuses.map((status) => (
                <Button
                  key={status}
                  variant={status === "CANCELLED" ? "secondary" : "primary"}
                  onClick={() => void handleStatusChange(status)}
                  isLoading={isTransitioning}
                >
                  {status === "ACTIVE" ? "Activate" : status === "COMPLETED" ? "Mark completed" : "Cancel campaign"}
                </Button>
              ))}
            </div>
          </div>

          {stats && (
            <div className="rounded-lg border border-slate-200 bg-white p-5">
              <h2 className="text-sm font-medium text-slate-500">Member stats</h2>
              <dl className="mt-3 flex flex-col gap-2 text-sm">
                {CAMPAIGN_MEMBER_STATUSES.map((status) => (
                  <div key={status} className="flex justify-between gap-4">
                    <dt className="text-slate-500">{status}</dt>
                    <dd className="text-slate-900">{stats.countsByStatus[status] ?? 0}</dd>
                  </div>
                ))}
                <div className="flex justify-between gap-4 border-t border-slate-100 pt-2 font-medium">
                  <dt className="text-slate-900">Total members</dt>
                  <dd className="text-slate-900">{stats.totalMembers}</dd>
                </div>
              </dl>
            </div>
          )}
        </div>
      </div>

      <div className="rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-500">Members</h2>

        <div className="mt-3 flex flex-col gap-2">
          {members.length === 0 && <p className="text-sm text-slate-400">No members yet.</p>}
          {members.map((member) => (
            <div key={member.id} className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-2 first:border-t-0 first:pt-0">
              <p className="text-sm font-medium text-slate-900">{leadOrContactLabel(member)}</p>
              <div className="flex items-center gap-2">
                <select
                  className="rounded-md border border-slate-300 bg-white px-2 py-1.5 text-sm text-slate-900"
                  value={member.status}
                  disabled={pendingMemberId === member.id}
                  onChange={(event) => void handleMemberStatusChange(member.id, event.target.value as CampaignMemberStatus)}
                >
                  {CAMPAIGN_MEMBER_STATUSES.map((status) => (
                    <option key={status} value={status}>
                      {status}
                    </option>
                  ))}
                </select>
                <Button variant="danger" onClick={() => void handleRemoveMember(member.id)} isLoading={pendingMemberId === member.id}>
                  Remove
                </Button>
              </div>
            </div>
          ))}
        </div>

        <div className="mt-4 border-t border-slate-100 pt-4">
          <AddMemberForm campaignId={campaignId} leads={leads} contacts={contacts} onDone={reload} />
        </div>
      </div>
    </div>
  );
}

function AddMemberForm({
  campaignId,
  leads,
  contacts,
  onDone,
}: {
  campaignId: string;
  leads: LeadDto[];
  contacts: ContactDto[];
  onDone: () => void;
}) {
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<AddCampaignMemberFormValues>({ resolver: zodResolver(addCampaignMemberSchema) });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await addCampaignMember(campaignId, {
        leadId: blankToUndefined(values.leadId),
        contactId: blankToUndefined(values.contactId),
      });
      reset({ leadId: "", contactId: "" });
      onDone();
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-3">
      {formError && <Alert variant="error">{formError}</Alert>}
      <div className="grid gap-3 sm:grid-cols-3">
        <Select
          label="Add a lead"
          placeholder="None"
          options={leads.map((lead) => ({ value: lead.id, label: lead.fullName }))}
          error={errors.leadId?.message}
          {...register("leadId")}
        />
        <Select
          label="Or a contact"
          placeholder="None"
          options={contacts.map((contact) => ({ value: contact.id, label: contact.fullName }))}
          error={errors.contactId?.message}
          {...register("contactId")}
        />
        <div className="flex items-end">
          <Button type="submit" isLoading={isSubmitting}>
            Add member
          </Button>
        </div>
      </div>
    </form>
  );
}
