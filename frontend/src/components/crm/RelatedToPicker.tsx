import { useEffect, useState } from "react";
import { listAccounts } from "../../api/accounts";
import { listContacts } from "../../api/contacts";
import { listLeads } from "../../api/leads";
import { listOpportunities } from "../../api/opportunities";
import { listTickets } from "../../api/tickets";
import { CRM_RECORD_TYPES, type CrmRecordType } from "../../types/api";
import { Select } from "../ui/Select";

const TYPE_LABELS: Record<CrmRecordType, string> = {
  ACCOUNT: "Account",
  CONTACT: "Contact",
  OPPORTUNITY: "Opportunity",
  LEAD: "Lead",
  TICKET: "Ticket",
};

interface Option {
  value: string;
  label: string;
}

/** Fetches and labels the records for one relatedToType - the one place this "which list endpoint, which field is the label" mapping lives, so EmailCreatePage/EmailDetailPage/CalendarEventCreatePage/CalendarEventDetailPage don't each reimplement it. */
async function loadOptions(type: CrmRecordType): Promise<Option[]> {
  switch (type) {
    case "ACCOUNT": {
      const page = await listAccounts({ size: 100, sort: "name,asc" });
      return page.content.map((a) => ({ value: a.id, label: a.name }));
    }
    case "CONTACT": {
      const page = await listContacts({ size: 100, sort: "createdAt,desc" });
      return page.content.map((c) => ({ value: c.id, label: c.fullName }));
    }
    case "OPPORTUNITY": {
      const page = await listOpportunities({ size: 100, sort: "createdAt,desc" });
      return page.content.map((o) => ({ value: o.id, label: o.name }));
    }
    case "LEAD": {
      const page = await listLeads({ size: 100, sort: "createdAt,desc" });
      return page.content.map((l) => ({ value: l.id, label: l.fullName }));
    }
    case "TICKET": {
      const page = await listTickets({ size: 100, sort: "createdAt,desc" });
      return page.content.map((t) => ({ value: t.id, label: t.subject }));
    }
  }
}

/**
 * A relatedToType + relatedToId pair, shared by Email and Calendar's create/edit forms - both
 * entities can be logged/scheduled against any of the five CrmRecordTypes. Not wired into
 * react-hook-form's register() since the second Select's options depend on the first Select's
 * current value (fetched fresh on every type change), so this is a plain controlled pair, same
 * "manual value/onChange" style LeadDetailPage's inline status Select already uses.
 */
export function RelatedToPicker({
  relatedToType,
  relatedToId,
  onChange,
  allowEmpty = false,
  typeError,
  idError,
}: {
  relatedToType: string;
  relatedToId: string;
  onChange: (relatedToType: string, relatedToId: string) => void;
  allowEmpty?: boolean;
  typeError?: string;
  idError?: string;
}) {
  const [options, setOptions] = useState<Option[]>([]);
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    if (!relatedToType) {
      setOptions([]);
      return;
    }
    let cancelled = false;
    setIsLoading(true);
    loadOptions(relatedToType as CrmRecordType)
      .then((loaded) => {
        if (!cancelled) setOptions(loaded);
      })
      .catch(() => {
        if (!cancelled) setOptions([]);
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [relatedToType]);

  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <Select
        label="Related to"
        placeholder={allowEmpty ? "None" : undefined}
        options={CRM_RECORD_TYPES.map((type) => ({ value: type, label: TYPE_LABELS[type] }))}
        value={relatedToType}
        error={typeError}
        onChange={(e) => onChange(e.target.value, "")}
      />
      <Select
        label="Record"
        placeholder={isLoading ? "Loading..." : allowEmpty ? "None" : "Select a record"}
        options={options}
        value={relatedToId}
        error={idError}
        disabled={!relatedToType || isLoading}
        onChange={(e) => onChange(relatedToType, e.target.value)}
      />
    </div>
  );
}
