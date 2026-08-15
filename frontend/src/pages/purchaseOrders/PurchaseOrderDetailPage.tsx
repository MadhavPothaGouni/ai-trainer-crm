import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { deletePurchaseOrder, getPurchaseOrder, updatePurchaseOrder, updatePurchaseOrderStatus } from "../../api/purchaseOrders";
import { getVendor } from "../../api/vendors";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, toOptionalNumber, updatePurchaseOrderSchema, type UpdatePurchaseOrderFormValues } from "../../lib/validation";
import { PURCHASE_ORDER_STATUSES, type PurchaseOrderDto, type PurchaseOrderStatus, type VendorDto } from "../../types/api";
import { PurchaseOrderStatusBadge } from "./PurchaseOrderListPage";

export default function PurchaseOrderDetailPage() {
  const { purchaseOrderId } = useParams<{ purchaseOrderId: string }>();
  const navigate = useNavigate();
  const [order, setOrder] = useState<PurchaseOrderDto | null>(null);
  const [vendor, setVendor] = useState<VendorDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [isUpdatingStatus, setIsUpdatingStatus] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<UpdatePurchaseOrderFormValues>({ resolver: zodResolver(updatePurchaseOrderSchema) });

  useEffect(() => {
    if (!purchaseOrderId) return;
    let cancelled = false;
    getPurchaseOrder(purchaseOrderId)
      .then((data) => {
        if (cancelled) return;
        setOrder(data);
        reset({
          orderDate: data.orderDate,
          totalAmount: data.totalAmount != null ? String(data.totalAmount) : "",
          expectedDeliveryDate: data.expectedDeliveryDate ?? "",
          notes: data.notes ?? "",
        });
        getVendor(data.vendorId).then(setVendor).catch(() => undefined);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this purchase order.");
      });
    return () => {
      cancelled = true;
    };
  }, [purchaseOrderId, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!purchaseOrderId) return;
    setFormError(null);
    try {
      const updated = await updatePurchaseOrder(purchaseOrderId, {
        orderDate: values.orderDate,
        totalAmount: toOptionalNumber(values.totalAmount),
        expectedDeliveryDate: blankToUndefined(values.expectedDeliveryDate),
        notes: blankToUndefined(values.notes),
      });
      setOrder(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function handleStatusChange(status: string) {
    if (!purchaseOrderId) return;
    setIsUpdatingStatus(true);
    setError(null);
    try {
      const updated = await updatePurchaseOrderStatus(purchaseOrderId, { status: status as PurchaseOrderStatus });
      setOrder(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update the status.");
    } finally {
      setIsUpdatingStatus(false);
    }
  }

  async function handleDelete() {
    if (!purchaseOrderId || !window.confirm("Delete this purchase order? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      await deletePurchaseOrder(purchaseOrderId);
      navigate("/purchase-orders");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this purchase order.");
      setIsDeleting(false);
    }
  }

  if (error && !order) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!order) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/purchase-orders" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Purchase Orders
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">
              {vendor ? (
                <Link to={`/vendors/${vendor.id}`} className="hover:underline">
                  {vendor.name}
                </Link>
              ) : (
                "Purchase order"
              )}
            </h1>
            <PurchaseOrderStatusBadge status={order.status} />
          </div>
        </div>
        <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
          Delete
        </Button>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="grid gap-4 sm:grid-cols-2">
        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Overview</h2>
          <dl className="mt-3 flex flex-col gap-2 text-sm">
            <Row label="Order date" value={order.orderDate} />
            <Row label="Total" value={order.totalAmount != null ? `$${order.totalAmount.toFixed(2)}` : "—"} />
            <Row label="Expected delivery" value={order.expectedDeliveryDate ?? "—"} />
            <Row label="Received" value={order.receivedAt ? new Date(order.receivedAt).toLocaleString() : "Not yet"} />
          </dl>
        </div>

        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Status</h2>
          <p className="mt-1 text-xs text-slate-400">Orders move freely between statuses - reinstating a cancelled order is a normal correction.</p>
          <div className="mt-3">
            <Select
              label="Status"
              options={PURCHASE_ORDER_STATUSES.map((status) => ({ value: status, label: status }))}
              value={order.status}
              disabled={isUpdatingStatus}
              onChange={(event) => void handleStatusChange(event.target.value)}
            />
          </div>
        </div>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Edit order</h2>

        {formError && <Alert variant="error">{formError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Order date" type="date" error={errors.orderDate?.message} {...register("orderDate")} />
          <TextField label="Expected delivery" type="date" error={errors.expectedDeliveryDate?.message} {...register("expectedDeliveryDate")} />
        </div>

        <TextField label="Total amount" type="number" min={0} step="0.01" error={errors.totalAmount?.message} {...register("totalAmount")} />

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
