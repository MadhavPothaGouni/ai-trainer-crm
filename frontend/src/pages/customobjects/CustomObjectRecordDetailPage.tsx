import { useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { getCustomFieldValues, listCustomFields, setCustomFieldValues } from "../../api/customFields";
import { deleteCustomObjectRecord, getCustomObject, getCustomObjectRecord, updateCustomObjectRecord } from "../../api/customObjects";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import type { CustomFieldDto, CustomObjectDto, CustomObjectRecordDto } from "../../types/api";

/** Renders one form control per {@link CustomFieldDto}, matched to its {@code fieldType} - the dynamic-form counterpart to a normal react-hook-form field, except the set of fields isn't known until this object's field definitions load. */
function FieldInput({
  field,
  value,
  onChange,
}: {
  field: CustomFieldDto;
  value: string;
  onChange: (value: string) => void;
}) {
  switch (field.fieldType) {
    case "TEXT_AREA":
      return (
        <textarea
          rows={3}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm shadow-sm outline-none focus:border-slate-500 focus:ring-2 focus:ring-slate-200"
        />
      );
    case "NUMBER":
      return (
        <input
          type="number"
          step="any"
          value={value}
          onChange={(event) => onChange(event.target.value)}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm shadow-sm outline-none focus:border-slate-500 focus:ring-2 focus:ring-slate-200"
        />
      );
    case "DATE":
      return (
        <input
          type="date"
          value={value}
          onChange={(event) => onChange(event.target.value)}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm shadow-sm outline-none focus:border-slate-500 focus:ring-2 focus:ring-slate-200"
        />
      );
    case "BOOLEAN":
      return (
        <select
          value={value}
          onChange={(event) => onChange(event.target.value)}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm shadow-sm outline-none focus:border-slate-500 focus:ring-2 focus:ring-slate-200"
        >
          <option value="">Unset</option>
          <option value="true">Yes</option>
          <option value="false">No</option>
        </select>
      );
    case "PICKLIST":
      return (
        <select
          value={value}
          onChange={(event) => onChange(event.target.value)}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm shadow-sm outline-none focus:border-slate-500 focus:ring-2 focus:ring-slate-200"
        >
          <option value="">{field.required ? "-- choose --" : "Unset"}</option>
          {field.picklistValues.map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>
      );
    default:
      return (
        <input
          type="text"
          value={value}
          onChange={(event) => onChange(event.target.value)}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm shadow-sm outline-none focus:border-slate-500 focus:ring-2 focus:ring-slate-200"
        />
      );
  }
}

export default function CustomObjectRecordDetailPage() {
  const { customObjectId, recordId } = useParams<{ customObjectId: string; recordId: string }>();
  const navigate = useNavigate();
  const [object, setObject] = useState<CustomObjectDto | null>(null);
  const [record, setRecord] = useState<CustomObjectRecordDto | null>(null);
  const [fields, setFields] = useState<CustomFieldDto[]>([]);
  const [values, setValues] = useState<Record<string, string>>({});
  const [name, setName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isSavingName, setIsSavingName] = useState(false);
  const [isSavingValues, setIsSavingValues] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);

  function reload() {
    if (!customObjectId || !recordId) return;
    getCustomObject(customObjectId).then(setObject).catch(() => undefined);
    getCustomObjectRecord(customObjectId, recordId)
      .then((data) => {
        setRecord(data);
        setName(data.name);
      })
      .catch((err: unknown) => setError(err instanceof ApiError ? err.message : "Could not load this record."));
    listCustomFields({ customObjectId })
      .then(setFields)
      .catch(() => undefined);
    getCustomFieldValues({ customObjectId }, recordId)
      .then((valueDtos) => {
        const next: Record<string, string> = {};
        for (const valueDto of valueDtos) {
          next[valueDto.customFieldId] = valueDto.value ?? "";
        }
        setValues(next);
      })
      .catch(() => undefined);
  }

  useEffect(() => {
    reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [customObjectId, recordId]);

  async function handleSaveName(event: FormEvent) {
    event.preventDefault();
    if (!customObjectId || !recordId || !name.trim()) return;
    setIsSavingName(true);
    setError(null);
    try {
      const updated = await updateCustomObjectRecord(customObjectId, recordId, { name: name.trim() });
      setRecord(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not save the name.");
    } finally {
      setIsSavingName(false);
    }
  }

  async function handleSaveValues() {
    if (!customObjectId || !recordId) return;
    setIsSavingValues(true);
    setError(null);
    try {
      await setCustomFieldValues({ customObjectId }, recordId, { values });
      reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not save field values.");
    } finally {
      setIsSavingValues(false);
    }
  }

  async function handleDelete() {
    if (!customObjectId || !recordId || !window.confirm("Delete this record?")) return;
    setIsDeleting(true);
    try {
      await deleteCustomObjectRecord(customObjectId, recordId);
      navigate(`/custom-objects/${customObjectId}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this record.");
      setIsDeleting(false);
    }
  }

  if (error && !record) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!record || !customObjectId || !recordId) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to={`/custom-objects/${customObjectId}`} className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; {object?.label ?? "Custom object"}
          </Link>
          <h1 className="mt-1 text-2xl font-semibold text-slate-900">{record.name}</h1>
        </div>
        <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
          Delete
        </Button>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <form onSubmit={(event) => void handleSaveName(event)} className="flex items-end gap-3 rounded-lg border border-slate-200 bg-white p-6">
        <div className="flex-1">
          <TextField label="Name" value={name} onChange={(event) => setName(event.target.value)} />
        </div>
        <Button type="submit" isLoading={isSavingName} disabled={!name.trim()}>
          Save name
        </Button>
      </form>

      <div className="rounded-lg border border-slate-200 bg-white p-6">
        <h2 className="text-sm font-medium text-slate-500">Custom fields</h2>

        {fields.length === 0 && (
          <p className="mt-3 text-sm text-slate-400">
            No custom fields defined on {object?.label ?? "this object"} yet.{" "}
            <Link to={`/custom-fields/new?customObjectId=${customObjectId}`} className="text-slate-700 hover:underline">
              Add one
            </Link>
            .
          </p>
        )}

        <div className="mt-3 flex flex-col gap-4">
          {fields.map((field) => (
            <div key={field.id}>
              <label className="text-sm font-medium text-slate-700">
                {field.label}
                {field.required && <span className="text-red-500"> *</span>}
              </label>
              <div className="mt-1.5">
                <FieldInput
                  field={field}
                  value={values[field.id] ?? ""}
                  onChange={(value) => setValues((prev) => ({ ...prev, [field.id]: value }))}
                />
              </div>
            </div>
          ))}
        </div>

        {fields.length > 0 && (
          <div className="mt-4 flex justify-end">
            <Button onClick={() => void handleSaveValues()} isLoading={isSavingValues}>
              Save field values
            </Button>
          </div>
        )}
      </div>
    </div>
  );
}
