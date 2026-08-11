import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState, type FormEvent } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import {
  createCustomObjectRecord,
  deleteCustomObject,
  deleteCustomObjectRecord,
  getCustomObject,
  listCustomObjectRecords,
  updateCustomObject,
} from "../../api/customObjects";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, updateCustomObjectSchema, type UpdateCustomObjectFormValues } from "../../lib/validation";
import type { CustomObjectDto, CustomObjectRecordDto } from "../../types/api";

export default function CustomObjectDetailPage() {
  const { customObjectId } = useParams<{ customObjectId: string }>();
  const navigate = useNavigate();
  const [object, setObject] = useState<CustomObjectDto | null>(null);
  const [records, setRecords] = useState<CustomObjectRecordDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [newRecordName, setNewRecordName] = useState("");
  const [isCreatingRecord, setIsCreatingRecord] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<UpdateCustomObjectFormValues>({ resolver: zodResolver(updateCustomObjectSchema) });

  function reload() {
    if (!customObjectId) return;
    getCustomObject(customObjectId)
      .then((data) => {
        setObject(data);
        reset({ label: data.label, pluralLabel: data.pluralLabel, description: data.description ?? "", active: data.active });
      })
      .catch((err: unknown) => setError(err instanceof ApiError ? err.message : "Could not load this custom object."));
    listCustomObjectRecords(customObjectId, { size: 100, sort: "createdAt,desc" })
      .then((res) => setRecords(res.content))
      .catch(() => undefined);
  }

  useEffect(() => {
    reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [customObjectId]);

  const onSave = handleSubmit(async (values) => {
    if (!customObjectId) return;
    setFormError(null);
    try {
      const updated = await updateCustomObject(customObjectId, {
        label: values.label,
        pluralLabel: values.pluralLabel,
        description: blankToUndefined(values.description),
        active: values.active,
      });
      setObject(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function handleDelete() {
    if (!customObjectId || !window.confirm("Delete this custom object? Its fields and records are deleted too.")) return;
    setIsDeleting(true);
    try {
      await deleteCustomObject(customObjectId);
      navigate("/custom-objects");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this custom object.");
      setIsDeleting(false);
    }
  }

  async function handleCreateRecord(event: FormEvent) {
    event.preventDefault();
    if (!customObjectId || !newRecordName.trim()) return;
    setIsCreatingRecord(true);
    setError(null);
    try {
      const record = await createCustomObjectRecord(customObjectId, { name: newRecordName.trim() });
      setRecords((prev) => [record, ...prev]);
      setNewRecordName("");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not create the record.");
    } finally {
      setIsCreatingRecord(false);
    }
  }

  async function handleDeleteRecord(recordId: string) {
    if (!customObjectId || !window.confirm("Delete this record?")) return;
    try {
      await deleteCustomObjectRecord(customObjectId, recordId);
      setRecords((prev) => prev.filter((record) => record.id !== recordId));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete the record.");
    }
  }

  if (error && !object) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!object || !customObjectId) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/custom-objects" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Custom objects
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{object.label}</h1>
            <span className="rounded-full bg-slate-100 px-2.5 py-0.5 font-mono text-xs text-slate-600">{object.apiName}</span>
          </div>
        </div>
        <div className="flex gap-3">
          <Link to={`/custom-fields/new?customObjectId=${object.id}`}>
            <Button variant="secondary">Add field</Button>
          </Link>
          <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
            Delete
          </Button>
        </div>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <form onSubmit={onSave} noValidate className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Label" error={errors.label?.message} {...register("label")} />
          <TextField label="Plural label" error={errors.pluralLabel?.message} {...register("pluralLabel")} />
        </div>

        <TextArea label="Description" rows={2} error={errors.description?.message} {...register("description")} />

        <label className="flex items-center gap-2 text-sm text-slate-700">
          <input type="checkbox" className="h-4 w-4 rounded border-slate-300" {...register("active")} />
          Active
        </label>

        <div className="flex justify-end">
          <Button type="submit" isLoading={isSubmitting}>
            Save changes
          </Button>
        </div>
      </form>

      <div className="rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-500">Records ({records.length})</h2>

        <form onSubmit={(event) => void handleCreateRecord(event)} className="mt-3 flex gap-2">
          <input
            type="text"
            value={newRecordName}
            onChange={(event) => setNewRecordName(event.target.value)}
            placeholder={`New ${object.label.toLowerCase()} name`}
            className="flex-1 rounded-md border border-slate-300 px-3 py-2 text-sm shadow-sm outline-none focus:border-slate-500 focus:ring-2 focus:ring-slate-200"
          />
          <Button type="submit" isLoading={isCreatingRecord} disabled={!newRecordName.trim()}>
            Add record
          </Button>
        </form>

        <div className="mt-4 divide-y divide-slate-100">
          {records.length === 0 && <p className="py-4 text-sm text-slate-400">No records yet.</p>}
          {records.map((record) => (
            <div key={record.id} className="flex items-center justify-between py-2.5">
              <Link
                to={`/custom-objects/${customObjectId}/records/${record.id}`}
                className="text-sm font-medium text-slate-900 hover:underline"
              >
                {record.name}
              </Link>
              <button
                type="button"
                onClick={() => void handleDeleteRecord(record.id)}
                className="text-sm text-red-600 hover:underline"
              >
                Remove
              </button>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
