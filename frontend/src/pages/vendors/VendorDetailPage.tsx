import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { deleteVendor, getVendor, updateVendor } from "../../api/vendors";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createVendorSchema, type CreateVendorFormValues } from "../../lib/validation";
import type { VendorDto } from "../../types/api";

export default function VendorDetailPage() {
  const { vendorId } = useParams<{ vendorId: string }>();
  const navigate = useNavigate();
  const [vendor, setVendor] = useState<VendorDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<CreateVendorFormValues>({ resolver: zodResolver(createVendorSchema) });

  useEffect(() => {
    if (!vendorId) return;
    let cancelled = false;
    getVendor(vendorId)
      .then((data) => {
        if (cancelled) return;
        setVendor(data);
        reset({
          name: data.name,
          contactName: data.contactName ?? "",
          email: data.email ?? "",
          phone: data.phone ?? "",
          category: data.category ?? "",
          notes: data.notes ?? "",
        });
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this vendor.");
      });
    return () => {
      cancelled = true;
    };
  }, [vendorId, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!vendorId || !vendor) return;
    setFormError(null);
    try {
      const updated = await updateVendor(vendorId, {
        name: values.name,
        contactName: blankToUndefined(values.contactName),
        email: blankToUndefined(values.email),
        phone: blankToUndefined(values.phone),
        category: blankToUndefined(values.category),
        notes: blankToUndefined(values.notes),
        active: vendor.active,
      });
      setVendor(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function toggleActive() {
    if (!vendorId || !vendor) return;
    try {
      const updated = await updateVendor(vendorId, {
        name: vendor.name,
        contactName: vendor.contactName ?? undefined,
        email: vendor.email ?? undefined,
        phone: vendor.phone ?? undefined,
        category: vendor.category ?? undefined,
        notes: vendor.notes ?? undefined,
        active: !vendor.active,
      });
      setVendor(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update this vendor.");
    }
  }

  async function handleDelete() {
    if (!vendorId || !window.confirm("Delete this vendor? Existing purchase orders keep their own record, so this is safe.")) return;
    setIsDeleting(true);
    try {
      await deleteVendor(vendorId);
      navigate("/vendors");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this vendor.");
      setIsDeleting(false);
    }
  }

  if (error && !vendor) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!vendor) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/vendors" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Vendors
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{vendor.name}</h1>
            {vendor.active ? (
              <span className="inline-block rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-medium text-emerald-700">Active</span>
            ) : (
              <span className="inline-block rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-500">Inactive</span>
            )}
          </div>
        </div>
        <div className="flex gap-3">
          <Link to={`/purchase-orders/new?vendorId=${vendor.id}`}>
            <Button variant="secondary">Place order</Button>
          </Link>
          <Button variant="secondary" onClick={() => void toggleActive()}>
            {vendor.active ? "Deactivate" : "Activate"}
          </Button>
          <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
            Delete
          </Button>
        </div>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <TextField label="Name" error={errors.name?.message} {...register("name")} />

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Contact name" error={errors.contactName?.message} {...register("contactName")} />
          <TextField label="Category" error={errors.category?.message} {...register("category")} />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Email" type="email" error={errors.email?.message} {...register("email")} />
          <TextField label="Phone" error={errors.phone?.message} {...register("phone")} />
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
