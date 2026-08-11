import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { deleteCustomField, listCustomFields } from "../../api/customFields";
import { listCustomObjects } from "../../api/customObjects";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { ApiError } from "../../lib/apiClient";
import { STANDARD_ENTITY_TYPES, type CustomFieldDto, type CustomObjectDto, type StandardEntityType } from "../../types/api";

type TargetKind = "standard" | "object";

export default function CustomFieldListPage() {
  const [targetKind, setTargetKind] = useState<TargetKind>("standard");
  const [standardEntityType, setStandardEntityType] = useState<StandardEntityType>("ACCOUNT");
  const [customObjects, setCustomObjects] = useState<CustomObjectDto[]>([]);
  const [customObjectId, setCustomObjectId] = useState("");
  const [fields, setFields] = useState<CustomFieldDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    listCustomObjects({ size: 100, sort: "label,asc" })
      .then((res) => {
        setCustomObjects(res.content);
        if (res.content.length > 0) setCustomObjectId((current) => current || res.content[0].id);
      })
      .catch(() => undefined);
  }, []);

  useEffect(() => {
    if (targetKind === "object" && !customObjectId) {
      setFields([]);
      return;
    }
    let cancelled = false;
    setIsLoading(true);
    setError(null);
    const target = targetKind === "standard" ? { standardEntityType } : { customObjectId };
    listCustomFields(target)
      .then((data) => {
        if (!cancelled) setFields(data);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load custom fields.");
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [targetKind, standardEntityType, customObjectId]);

  async function handleDelete(fieldId: string) {
    if (!window.confirm("Delete this custom field? Its saved values are deleted too.")) return;
    try {
      await deleteCustomField(fieldId);
      setFields((prev) => prev.filter((field) => field.id !== fieldId));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this field.");
    }
  }

  const newFieldHref =
    targetKind === "standard"
      ? `/custom-fields/new?standardEntityType=${standardEntityType}`
      : customObjectId
        ? `/custom-fields/new?customObjectId=${customObjectId}`
        : "/custom-fields/new";

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Custom fields</h1>
          <p className="mt-1 text-sm text-slate-500">Extra fields attached to a standard entity or to a custom object.</p>
        </div>
        <div className="flex gap-3">
          <Link to="/custom-objects">
            <Button variant="secondary">Custom objects</Button>
          </Link>
          <Link to={newFieldHref}>
            <Button>New field</Button>
          </Link>
        </div>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="flex flex-wrap items-end gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <Select
          label="Attached to"
          value={targetKind}
          onChange={(event) => setTargetKind(event.target.value as TargetKind)}
          options={[
            { value: "standard", label: "A standard entity" },
            { value: "object", label: "A custom object" },
          ]}
        />
        {targetKind === "standard" ? (
          <Select
            label="Entity"
            value={standardEntityType}
            onChange={(event) => setStandardEntityType(event.target.value as StandardEntityType)}
            options={STANDARD_ENTITY_TYPES.map((type) => ({ value: type, label: type }))}
          />
        ) : (
          <Select
            label="Custom object"
            value={customObjectId}
            onChange={(event) => setCustomObjectId(event.target.value)}
            options={customObjects.map((object) => ({ value: object.id, label: object.label }))}
            placeholder={customObjects.length === 0 ? "No custom objects yet" : undefined}
          />
        )}
      </div>

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Label</th>
              <th className="px-4 py-3 font-medium">API name</th>
              <th className="px-4 py-3 font-medium">Type</th>
              <th className="px-4 py-3 font-medium">Required</th>
              <th className="px-4 py-3 font-medium">Status</th>
              <th className="px-4 py-3 font-medium"></th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {isLoading && (
              <tr>
                <td className="px-4 py-6 text-center text-slate-400" colSpan={6}>
                  Loading...
                </td>
              </tr>
            )}
            {!isLoading && fields.length === 0 && (
              <tr>
                <td className="px-4 py-6 text-center text-slate-400" colSpan={6}>
                  No custom fields on this target yet.
                </td>
              </tr>
            )}
            {fields.map((field) => (
              <tr key={field.id} className="hover:bg-slate-50">
                <td className="px-4 py-3 font-medium text-slate-900">{field.label}</td>
                <td className="px-4 py-3 font-mono text-xs text-slate-600">{field.apiName}</td>
                <td className="px-4 py-3 text-slate-600">{field.fieldType}</td>
                <td className="px-4 py-3 text-slate-600">{field.required ? "Yes" : "No"}</td>
                <td className="px-4 py-3">
                  <span
                    className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${
                      field.active ? "bg-emerald-100 text-emerald-700" : "bg-slate-100 text-slate-500"
                    }`}
                  >
                    {field.active ? "Active" : "Inactive"}
                  </span>
                </td>
                <td className="px-4 py-3 text-right">
                  <button type="button" onClick={() => void handleDelete(field.id)} className="text-sm text-red-600 hover:underline">
                    Delete
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
