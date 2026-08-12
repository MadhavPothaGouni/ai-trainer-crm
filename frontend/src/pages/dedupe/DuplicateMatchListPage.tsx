import { useEffect, useState } from "react";
import { getAccount } from "../../api/accounts";
import { getContact } from "../../api/contacts";
import { dismissDuplicateMatch, listDuplicateMatches, mergeDuplicateMatch } from "../../api/duplicates";
import { getLead } from "../../api/leads";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { ApiError } from "../../lib/apiClient";
import { DUPLICATE_ENTITY_TYPES, type AccountDto, type ContactDto, type DuplicateEntityType, type DuplicateMatchDto, type LeadDto } from "../../types/api";

const ENTITY_TYPE_LABELS: Record<DuplicateEntityType, string> = { LEAD: "Leads", CONTACT: "Contacts", ACCOUNT: "Accounts" };

/** A record's display label depends on its entityType - Lead/Contact have a fullName, Account has a name. Fetched lazily, one GET per record actually shown in the current PENDING list (see loadRecordLabels), not eagerly for the whole org. */
type RecordLookup = { lead: Map<string, LeadDto>; contact: Map<string, ContactDto>; account: Map<string, AccountDto> };

export default function DuplicateMatchListPage() {
  const [entityType, setEntityType] = useState<DuplicateEntityType>("LEAD");
  const [matches, setMatches] = useState<DuplicateMatchDto[]>([]);
  const [records, setRecords] = useState<RecordLookup>({ lead: new Map(), contact: new Map(), account: new Map() });
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [actioningId, setActioningId] = useState<string | null>(null);

  function reload() {
    setIsLoading(true);
    setError(null);
    listDuplicateMatches({ entityType, status: "PENDING" })
      .then(async (result) => {
        setMatches(result);
        await loadRecordLabels(entityType, result);
      })
      .catch((err: unknown) => setError(err instanceof ApiError ? err.message : "Could not load duplicate matches."))
      .finally(() => setIsLoading(false));
  }

  async function loadRecordLabels(type: DuplicateEntityType, pairs: DuplicateMatchDto[]) {
    const ids = new Set<string>();
    pairs.forEach((match) => {
      ids.add(match.recordAId);
      ids.add(match.recordBId);
    });
    if (ids.size === 0) return;

    if (type === "LEAD") {
      const entries = await Promise.all([...ids].map((id) => getLead(id).then((lead) => [id, lead] as const).catch(() => null)));
      setRecords((prev) => ({ ...prev, lead: new Map(entries.filter((e): e is [string, LeadDto] => e !== null)) }));
    } else if (type === "CONTACT") {
      const entries = await Promise.all([...ids].map((id) => getContact(id).then((c) => [id, c] as const).catch(() => null)));
      setRecords((prev) => ({ ...prev, contact: new Map(entries.filter((e): e is [string, ContactDto] => e !== null)) }));
    } else {
      const entries = await Promise.all([...ids].map((id) => getAccount(id).then((a) => [id, a] as const).catch(() => null)));
      setRecords((prev) => ({ ...prev, account: new Map(entries.filter((e): e is [string, AccountDto] => e !== null)) }));
    }
  }

  useEffect(() => {
    reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [entityType]);

  function recordLabel(recordId: string): string {
    if (entityType === "LEAD") {
      const lead = records.lead.get(recordId);
      return lead ? `${lead.fullName}${lead.email ? ` (${lead.email})` : ""}` : recordId;
    }
    if (entityType === "CONTACT") {
      const contact = records.contact.get(recordId);
      return contact ? `${contact.fullName}${contact.email ? ` (${contact.email})` : ""}` : recordId;
    }
    const account = records.account.get(recordId);
    return account ? account.name : recordId;
  }

  async function handleMerge(match: DuplicateMatchDto, survivorId: string) {
    setActioningId(match.id);
    setError(null);
    try {
      await mergeDuplicateMatch(match.id, { survivorId });
      setMatches((prev) => prev.filter((m) => m.id !== match.id));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not merge these records.");
    } finally {
      setActioningId(null);
    }
  }

  async function handleDismiss(match: DuplicateMatchDto) {
    setActioningId(match.id);
    setError(null);
    try {
      await dismissDuplicateMatch(match.id);
      setMatches((prev) => prev.filter((m) => m.id !== match.id));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not dismiss this match.");
    } finally {
      setActioningId(null);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Duplicates</h1>
        <p className="mt-1 text-sm text-slate-500">
          Pairs flagged automatically when a new Lead, Contact, or Account matches an existing one by email or name. Merge
          keeps one record and reassigns the other's activities, files, emails, and calendar events onto it; dismiss just
          clears the flag and leaves both records untouched.
        </p>
      </div>

      <div className="flex gap-2 border-b border-slate-200">
        {DUPLICATE_ENTITY_TYPES.map((type) => (
          <button
            key={type}
            type="button"
            onClick={() => setEntityType(type)}
            className={`px-4 py-2 text-sm font-medium ${
              entityType === type ? "border-b-2 border-slate-900 text-slate-900" : "text-slate-500 hover:text-slate-700"
            }`}
          >
            {ENTITY_TYPE_LABELS[type]}
          </button>
        ))}
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="flex flex-col gap-4">
        {isLoading && <p className="text-sm text-slate-400">Loading...</p>}
        {!isLoading && matches.length === 0 && (
          <p className="rounded-lg border border-dashed border-slate-200 px-4 py-6 text-center text-sm text-slate-400">
            No pending {ENTITY_TYPE_LABELS[entityType].toLowerCase()} duplicates.
          </p>
        )}
        {matches.map((match) => (
          <div key={match.id} className="flex flex-col gap-3 rounded-lg border border-slate-200 bg-white p-4 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex flex-col gap-1 text-sm">
              <div className="flex items-center gap-2">
                <span className="inline-block rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-medium text-amber-800">
                  Matched by {match.matchReason === "EMAIL" ? "email" : "name"}
                </span>
              </div>
              <p className="text-slate-900">{recordLabel(match.recordAId)}</p>
              <p className="text-slate-400">vs.</p>
              <p className="text-slate-900">{recordLabel(match.recordBId)}</p>
            </div>
            <div className="flex flex-wrap gap-2">
              <Button
                variant="secondary"
                isLoading={actioningId === match.id}
                onClick={() => handleMerge(match, match.recordAId)}
                title={`Keep "${recordLabel(match.recordAId)}"`}
              >
                Keep first
              </Button>
              <Button
                variant="secondary"
                isLoading={actioningId === match.id}
                onClick={() => handleMerge(match, match.recordBId)}
                title={`Keep "${recordLabel(match.recordBId)}"`}
              >
                Keep second
              </Button>
              <Button variant="danger" isLoading={actioningId === match.id} onClick={() => handleDismiss(match)}>
                Not a duplicate
              </Button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
