import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { getAccount } from "../../api/accounts";
import { deleteOpportunity, getOpportunity, updateOpportunityStage } from "../../api/opportunities";
import { listQuotes } from "../../api/quotes";
import { ActivityTimeline } from "../../components/activities/ActivityTimeline";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { ApiError } from "../../lib/apiClient";
import { OPPORTUNITY_STAGES, type OpportunityDto, type QuoteDto } from "../../types/api";
import { StageBadge } from "./OpportunityListPage";
import { QuoteStatusBadge } from "../quotes/QuoteListPage";

export default function OpportunityDetailPage() {
  const { opportunityId } = useParams<{ opportunityId: string }>();
  const navigate = useNavigate();
  const [opportunity, setOpportunity] = useState<OpportunityDto | null>(null);
  const [accountName, setAccountName] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [isUpdatingStage, setIsUpdatingStage] = useState(false);

  useEffect(() => {
    if (!opportunityId) return;
    let cancelled = false;
    getOpportunity(opportunityId)
      .then((data) => {
        if (cancelled) return;
        setOpportunity(data);
        getAccount(data.accountId)
          .then((account) => {
            if (!cancelled) setAccountName(account.name);
          })
          .catch(() => undefined);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this opportunity.");
      });
    return () => {
      cancelled = true;
    };
  }, [opportunityId]);

  async function handleStageChange(stage: string) {
    if (!opportunityId) return;
    setIsUpdatingStage(true);
    setError(null);
    try {
      const updated = await updateOpportunityStage(opportunityId, { stage: stage as OpportunityDto["stage"] });
      setOpportunity(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update the stage.");
    } finally {
      setIsUpdatingStage(false);
    }
  }

  async function handleDelete() {
    if (!opportunityId || !window.confirm("Delete this opportunity? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      await deleteOpportunity(opportunityId);
      navigate("/opportunities");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this opportunity.");
      setIsDeleting(false);
    }
  }

  if (error && !opportunity) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!opportunity) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/opportunities" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Opportunities
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{opportunity.name}</h1>
            <StageBadge stage={opportunity.stage} />
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
                <Link to={`/accounts/${opportunity.accountId}`} className="text-slate-900 hover:underline">
                  {accountName ?? "View account"}
                </Link>
              }
            />
            <Row label="Amount" value={opportunity.amount != null ? `${opportunity.amount.toLocaleString()} ${opportunity.currency ?? ""}` : null} />
            <Row label="Expected close" value={opportunity.expectedCloseDate} />
            <Row label="Actual close" value={opportunity.actualCloseDate} />
          </dl>
        </div>

        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Update stage</h2>
          <div className="mt-3">
            <Select
              label="Stage"
              options={OPPORTUNITY_STAGES.map((stage) => ({ value: stage, label: stage.replace("_", " ") }))}
              value={opportunity.stage}
              disabled={isUpdatingStage}
              onChange={(event) => void handleStageChange(event.target.value)}
            />
          </div>
        </div>
      </div>

      {opportunity.description && (
        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Description</h2>
          <p className="mt-3 whitespace-pre-wrap text-sm text-slate-900">{opportunity.description}</p>
        </div>
      )}

      <OpportunityQuotes opportunityId={opportunity.id} />

      <ActivityTimeline relatedToType="OPPORTUNITY" relatedToId={opportunity.id} />
    </div>
  );
}

function OpportunityQuotes({ opportunityId }: { opportunityId: string }) {
  const [quotes, setQuotes] = useState<QuoteDto[] | null>(null);

  useEffect(() => {
    listQuotes({ opportunityId, size: 50, sort: "createdAt,desc" })
      .then((res) => setQuotes(res.content))
      .catch(() => setQuotes([]));
  }, [opportunityId]);

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-5">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-medium text-slate-500">Quotes</h2>
        <Link to={`/quotes/new?opportunityId=${opportunityId}`}>
          <Button variant="secondary">New quote</Button>
        </Link>
      </div>
      <ul className="mt-3 flex flex-col gap-2">
        {quotes === null && <li className="text-sm text-slate-400">Loading...</li>}
        {quotes !== null && quotes.length === 0 && <li className="text-sm text-slate-400">No quotes yet.</li>}
        {quotes?.map((quote) => (
          <li key={quote.id} className="flex items-center justify-between gap-4 border-t border-slate-100 pt-2 text-sm first:border-t-0 first:pt-0">
            <Link to={`/quotes/${quote.id}`} className="font-medium text-slate-900 hover:underline">
              {quote.name}
            </Link>
            <div className="flex items-center gap-3">
              <span className="text-slate-600">
                {quote.totalAmount.toLocaleString()} {quote.currency ?? ""}
              </span>
              <QuoteStatusBadge status={quote.status} />
            </div>
          </li>
        ))}
      </ul>
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
