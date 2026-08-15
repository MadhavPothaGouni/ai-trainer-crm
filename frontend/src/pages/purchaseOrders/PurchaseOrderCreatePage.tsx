import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate, useSearchParams } from "react-router-dom";
import { createPurchaseOrder } from "../../api/purchaseOrders";
import { listVendors } from "../../api/vendors";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createPurchaseOrderSchema, toOptionalNumber, type CreatePurchaseOrderFormValues } from "../../lib/validation";
import type { VendorDto } from "../../types/api";

export default function PurchaseOrderCreatePage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const preselectedVendorId = searchParams.get("vendorId") ?? "";
  const [formError, setFormError] = useState<string | null>(null);
  const [vendors, setVendors] = useState<VendorDto[]>([]);

  useEffect(() => {
    listVendors({ size: 100, sort: "name,asc" })
      .then((res) => setVendors(res.content.filter((vendor) => vendor.active)))
      .catch(() => setVendors([]));
  }, []);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreatePurchaseOrderFormValues>({
    resolver: zodResolver(createPurchaseOrderSchema),
    defaultValues: { vendorId: preselectedVendorId },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const order = await createPurchaseOrder({
        vendorId: values.vendorId,
        orderDate: values.orderDate,
        totalAmount: toOptionalNumber(values.totalAmount),
        expectedDeliveryDate: blankToUndefined(values.expectedDeliveryDate),
        notes: blankToUndefined(values.notes),
      });
      navigate(`/purchase-orders/${order.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New purchase order</h1>
        <p className="mt-1 text-sm text-slate-500">An order placed with a vendor.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <Select
          label="Vendor"
          placeholder="Select a vendor"
          options={vendors.map((vendor) => ({ value: vendor.id, label: vendor.name }))}
          error={errors.vendorId?.message}
          {...register("vendorId")}
        />

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Order date" type="date" error={errors.orderDate?.message} {...register("orderDate")} />
          <TextField label="Expected delivery" type="date" error={errors.expectedDeliveryDate?.message} {...register("expectedDeliveryDate")} />
        </div>

        <TextField label="Total amount" type="number" min={0} step="0.01" error={errors.totalAmount?.message} {...register("totalAmount")} />

        <TextArea label="Notes" error={errors.notes?.message} {...register("notes")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/purchase-orders")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Create order
          </Button>
        </div>
      </form>
    </div>
  );
}
