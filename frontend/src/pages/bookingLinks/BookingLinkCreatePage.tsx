import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { createBookingLink } from "../../api/bookingLinks";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createBookingLinkSchema, type CreateBookingLinkFormValues } from "../../lib/validation";

export default function BookingLinkCreatePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateBookingLinkFormValues>({
    resolver: zodResolver(createBookingLinkSchema),
    defaultValues: { durationMinutes: "30" },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const link = await createBookingLink({
        title: values.title,
        description: blankToUndefined(values.description),
        durationMinutes: Number(values.durationMinutes),
        slug: values.slug,
      });
      navigate(`/booking-links/${link.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New booking link</h1>
        <p className="mt-1 text-sm text-slate-500">Publish a reusable link, then add open time slots to it.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <TextField label="Title" error={errors.title?.message} {...register("title")} />

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Duration (minutes)" type="number" min={1} error={errors.durationMinutes?.message} {...register("durationMinutes")} />
          <TextField label="Slug" placeholder="discovery-call" error={errors.slug?.message} {...register("slug")} />
        </div>

        <TextArea label="Description" error={errors.description?.message} {...register("description")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/booking-links")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Create booking link
          </Button>
        </div>
      </form>
    </div>
  );
}
