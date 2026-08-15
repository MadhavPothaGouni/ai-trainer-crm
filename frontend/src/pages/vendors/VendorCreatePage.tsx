import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { createVendor } from "../../api/vendors";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createVendorSchema, type CreateVendorFormValues } from "../../lib/validation";

export default function VendorCreatePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateVendorFormValues>({ resolver: zodResolver(createVendorSchema) });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const vendor = await createVendor({
        name: values.name,
        contactName: blankToUndefined(values.contactName),
        email: blankToUndefined(values.email),
        phone: blankToUndefined(values.phone),
        category: blankToUndefined(values.category),
        notes: blankToUndefined(values.notes),
      });
      navigate(`/vendors/${vendor.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New vendor</h1>
        <p className="mt-1 text-sm text-slate-500">Add a supplier to the catalog - purchase orders get placed against it.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <TextField label="Name" placeholder="Acme Supply Co" error={errors.name?.message} {...register("name")} />

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Contact name" error={errors.contactName?.message} {...register("contactName")} />
          <TextField label="Category" placeholder="Equipment, Supplies..." error={errors.category?.message} {...register("category")} />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Email" type="email" error={errors.email?.message} {...register("email")} />
          <TextField label="Phone" error={errors.phone?.message} {...register("phone")} />
        </div>

        <TextArea label="Notes" error={errors.notes?.message} {...register("notes")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/vendors")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Add vendor
          </Button>
        </div>
      </form>
    </div>
  );
}
