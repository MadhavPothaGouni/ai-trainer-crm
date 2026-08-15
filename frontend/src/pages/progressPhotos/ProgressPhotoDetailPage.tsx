import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { deleteProgressPhoto, getProgressPhoto, updateProgressPhoto } from "../../api/progressPhotos";
import { getContact } from "../../api/contacts";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, updateProgressPhotoSchema, type UpdateProgressPhotoFormValues } from "../../lib/validation";
import { PROGRESS_PHOTO_CATEGORIES, type ContactDto, type ProgressPhotoCategory, type ProgressPhotoDto } from "../../types/api";

export default function ProgressPhotoDetailPage() {
  const { progressPhotoId } = useParams<{ progressPhotoId: string }>();
  const navigate = useNavigate();
  const [photo, setPhoto] = useState<ProgressPhotoDto | null>(null);
  const [contact, setContact] = useState<ContactDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<UpdateProgressPhotoFormValues>({ resolver: zodResolver(updateProgressPhotoSchema) });

  useEffect(() => {
    if (!progressPhotoId) return;
    let cancelled = false;
    getProgressPhoto(progressPhotoId)
      .then((data) => {
        if (cancelled) return;
        setPhoto(data);
        reset({ photoUrl: data.photoUrl, category: data.category, notes: data.notes ?? "" });
        getContact(data.contactId).then(setContact).catch(() => undefined);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this progress photo.");
      });
    return () => {
      cancelled = true;
    };
  }, [progressPhotoId, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!progressPhotoId) return;
    setFormError(null);
    try {
      const updated = await updateProgressPhoto(progressPhotoId, {
        photoUrl: values.photoUrl,
        category: values.category as ProgressPhotoCategory,
        notes: blankToUndefined(values.notes),
      });
      setPhoto(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function handleDelete() {
    if (!progressPhotoId || !window.confirm("Delete this progress photo? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      await deleteProgressPhoto(progressPhotoId);
      navigate("/progress-photos");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this progress photo.");
      setIsDeleting(false);
    }
  }

  if (error && !photo) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!photo) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/progress-photos" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Progress Photos
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{photo.category} photo</h1>
          </div>
          {contact && (
            <p className="mt-1 text-sm text-slate-500">
              Of{" "}
              <Link to={`/contacts/${contact.id}`} className="text-slate-700 hover:underline">
                {contact.fullName}
              </Link>
            </p>
          )}
        </div>
        <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
          Delete
        </Button>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-500">Overview</h2>
        <dl className="mt-3 flex flex-col gap-2 text-sm">
          <Row label="Taken" value={new Date(photo.takenAt).toLocaleString()} />
          <Row
            label="Photo"
            value={
              <a href={photo.photoUrl} target="_blank" rel="noreferrer" className="text-slate-700 hover:underline">
                Open photo
              </a>
            }
          />
        </dl>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Edit progress photo</h2>

        {formError && <Alert variant="error">{formError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Photo URL" error={errors.photoUrl?.message} {...register("photoUrl")} />
          <Select
            label="Category"
            options={PROGRESS_PHOTO_CATEGORIES.map((category) => ({ value: category, label: category }))}
            error={errors.category?.message}
            {...register("category")}
          />
        </div>

        <TextArea label="Notes" error={errors.notes?.message} {...register("notes")} />

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
