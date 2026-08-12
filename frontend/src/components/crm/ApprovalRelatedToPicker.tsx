import { useEffect, useState } from "react";
import { listOpportunities } from "../../api/opportunities";
import { listOrders } from "../../api/orders";
import { listQuotes } from "../../api/quotes";
import { APPROVAL_RELATED_TO_TYPES, type ApprovalRelatedToType } from "../../types/api";
import { Select } from "../ui/Select";

const TYPE_LABELS: Record<ApprovalRelatedToType, string> = {
  QUOTE: "Quote",
  ORDER: "Order",
  OPPORTUNITY: "Opportunity",
};

interface Option {
  value: string;
  label: string;
}

/**
 * Same shape as RelatedToPicker (components/crm/RelatedToPicker.tsx), scoped to the three record
 * types an approval request can be raised against instead of CrmRecordType's five - see
 * ApprovalRelatedToType's javadoc in types/api.ts for why it's a separate type rather than a
 * narrowed CrmRecordType.
 */
async function loadOptions(type: ApprovalRelatedToType): Promise<Option[]> {
  switch (type) {
    case "QUOTE": {
      const page = await listQuotes({ size: 100, sort: "createdAt,desc" });
      return page.content.map((q) => ({ value: q.id, label: q.name }));
    }
    case "ORDER": {
      const page = await listOrders({ size: 100, sort: "createdAt,desc" });
      return page.content.map((o) => ({ value: o.id, label: o.orderNumber }));
    }
    case "OPPORTUNITY": {
      const page = await listOpportunities({ size: 100, sort: "createdAt,desc" });
      return page.content.map((o) => ({ value: o.id, label: o.name }));
    }
  }
}

export function ApprovalRelatedToPicker({
  relatedToType,
  relatedToId,
  onChange,
  typeError,
  idError,
}: {
  relatedToType: string;
  relatedToId: string;
  onChange: (relatedToType: string, relatedToId: string) => void;
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
    loadOptions(relatedToType as ApprovalRelatedToType)
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
        placeholder="Select a type"
        options={APPROVAL_RELATED_TO_TYPES.map((type) => ({ value: type, label: TYPE_LABELS[type] }))}
        value={relatedToType}
        error={typeError}
        onChange={(e) => onChange(e.target.value, "")}
      />
      <Select
        label="Record"
        placeholder={isLoading ? "Loading..." : "Select a record"}
        options={options}
        value={relatedToId}
        error={idError}
        disabled={!relatedToType || isLoading}
        onChange={(e) => onChange(relatedToType, e.target.value)}
      />
    </div>
  );
}
