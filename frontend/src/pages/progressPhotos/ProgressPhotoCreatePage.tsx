import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { createProgressPhoto } from "../../api/progressPhotos";
import { listContacts } from "../../api/contacts";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createProgressPhotoSchema, type CreateProgressPhotoFormValues } from "../../lib/validation";
import { PROGRESS_PHOTO_CATEGORIES, type ContactDto, type ProgressPhotoCategory } from "../../types/api";

export default function ProgressPhotoCreatePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);
  const [contacts, setContacts] = useState<ContactDto[]>([]);

  useEffect(() => {
    listContacts({ size: 100, sort: "lastName,asc" })
      .then((res) => setContacts(res.content))
      .catch(() => setContacts([]));
  }, []);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateProgressPhotoFormValues>({
    resolver: zodResolver(createProgressPhotoSchema),
    defaultValues: { category: "FRONT" },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const photo = await createProgressPhoto({
        contactId: values.contactId,
        photoUrl: values.photoUrl,
        category: values.category as ProgressPhotoCategory,
        notes: blankToUndefined(values.notes),
      });
      navigate(`/progress-photos/${photo.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Log a progress photo</h1>
        <p className="mt-1 text-sm text-slate-500">A point-in-time record - taken-at is stamped now and never changes.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <Select
            label="Client"
            placeholder="Select a contact"
            options={contacts.map((contact) => ({ value: contact.id, label: contact.fullName }))}
            error={errors.contactId?.message}
            {...register("contactId")}
          />
          <Select
            label="Category"
            options={PROGRESS_PHOTO_CATEGORIES.map((category) => ({ value: category, label: category }))}
            error={errors.category?.message}
            {...register("category")}
          />
        </div>

        <TextField label="Photo URL" placeholder="https://..." error={errors.photoUrl?.message} {...register("photoUrl")} />

        <TextArea label="Notes" error={errors.notes?.message} {...register("notes")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/progress-photos")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Log photo
          </Button>
        </div>
      </form>
    </div>
  );
}
