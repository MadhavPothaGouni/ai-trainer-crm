import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { generateInvoiceFromOrder } from "../../api/invoices";
import {
  addOrderLineItem,
  confirmOrder,
  deleteOrder,
  getOrder,
  removeOrderLineItem,
  updateOrder,
  updateOrderLineItem,
  updateOrderStatus,
} from "../../api/orders";
import { listProducts } from "../../api/products";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import {
  blankToUndefined,
  createOrderSchema,
  generateInvoiceSchema,
  orderLineItemSchema,
  toOptionalNumber,
  toRequiredNumber,
  type CreateOrderFormValues,
  type GenerateInvoiceFormValues,
  type OrderLineItemFormValues,
} from "../../lib/validation";
import type { OrderDto, OrderLineItemDto, ProductDto } from "../../types/api";
import { OrderStatusBadge } from "./OrderListPage";

export default function OrderDetailPage() {
  const { orderId } = useParams<{ orderId: string }>();
  const navigate = useNavigate();
  const [order, setOrder] = useState<OrderDto | null>(null);
  const [products, setProducts] = useState<ProductDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [isTransitioning, setIsTransitioning] = useState(false);
  const [editingLineItemId, setEditingLineItemId] = useState<string | null>(null);
  const [pendingLineItemId, setPendingLineItemId] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<CreateOrderFormValues>({ resolver: zodResolver(createOrderSchema) });

  function reload() {
    if (!orderId) return;
    getOrder(orderId)
      .then((data) => {
        setOrder(data);
        reset({
          orderNumber: data.orderNumber,
          currency: data.currency ?? "",
          discountAmount: String(data.discountAmount),
          taxAmount: String(data.taxAmount),
        });
      })
      .catch((err: unknown) => setError(err instanceof ApiError ? err.message : "Could not load this order."));
  }

  useEffect(() => {
    reload();
    listProducts({ size: 100, sort: "name,asc" })
      .then((res) => setProducts(res.content.filter((product) => product.active)))
      .catch(() => undefined);
  }, [orderId]);

  const onSaveHeader = handleSubmit(async (values) => {
    if (!orderId) return;
    setFormError(null);
    try {
      const updated = await updateOrder(orderId, {
        orderNumber: values.orderNumber,
        currency: blankToUndefined(values.currency),
        discountAmount: toOptionalNumber(values.discountAmount),
        taxAmount: toOptionalNumber(values.taxAmount),
      });
      setOrder(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function handleConfirm() {
    if (!orderId) return;
    setIsTransitioning(true);
    setError(null);
    try {
      setOrder(await confirmOrder(orderId));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not confirm this order.");
    } finally {
      setIsTransitioning(false);
    }
  }

  async function handleStatusChange(status: "FULFILLED" | "CANCELLED") {
    if (!orderId) return;
    setIsTransitioning(true);
    setError(null);
    try {
      setOrder(await updateOrderStatus(orderId, { status }));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update this order's status.");
    } finally {
      setIsTransitioning(false);
    }
  }

  async function handleDelete() {
    if (!orderId || !window.confirm("Delete this order? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      await deleteOrder(orderId);
      navigate("/orders");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this order.");
      setIsDeleting(false);
    }
  }

  async function handleRemoveLineItem(lineItemId: string) {
    if (!orderId || !window.confirm("Remove this line item?")) return;
    setPendingLineItemId(lineItemId);
    try {
      await removeOrderLineItem(orderId, lineItemId);
      reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not remove this line item.");
    } finally {
      setPendingLineItemId(null);
    }
  }

  if (error && !order) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!order || !orderId) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/orders" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Orders
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{order.orderNumber}</h1>
            <OrderStatusBadge status={order.status} />
          </div>
          {order.quoteId && (
            <Link to={`/quotes/${order.quoteId}`} className="text-sm text-slate-500 hover:underline">
              Converted from quote
            </Link>
          )}
        </div>
        <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
          Delete
        </Button>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="grid gap-4 sm:grid-cols-2">
        <form onSubmit={onSaveHeader} noValidate className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Details</h2>
          {formError && <Alert variant="error">{formError}</Alert>}

          <TextField label="Order number" error={errors.orderNumber?.message} {...register("orderNumber")} />
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField label="Currency" placeholder="USD" error={errors.currency?.message} {...register("currency")} />
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField label="Discount" type="number" min={0} step="any" error={errors.discountAmount?.message} {...register("discountAmount")} />
            <TextField label="Tax" type="number" min={0} step="any" error={errors.taxAmount?.message} {...register("taxAmount")} />
          </div>
          <div className="flex justify-end">
            <Button type="submit" isLoading={isSubmitting}>
              Save changes
            </Button>
          </div>
        </form>

        <div className="flex flex-col gap-4">
          <div className="rounded-lg border border-slate-200 bg-white p-5">
            <h2 className="text-sm font-medium text-slate-500">Status</h2>
            <div className="mt-3 flex flex-wrap gap-2">
              {order.status === "DRAFT" && (
                <>
                  <Button onClick={() => void handleConfirm()} isLoading={isTransitioning}>
                    Confirm order
                  </Button>
                  <Button variant="secondary" onClick={() => void handleStatusChange("CANCELLED")} isLoading={isTransitioning}>
                    Cancel order
                  </Button>
                </>
              )}
              {order.status === "CONFIRMED" && (
                <>
                  <Button onClick={() => void handleStatusChange("FULFILLED")} isLoading={isTransitioning}>
                    Mark fulfilled
                  </Button>
                  <Button variant="secondary" onClick={() => void handleStatusChange("CANCELLED")} isLoading={isTransitioning}>
                    Cancel order
                  </Button>
                </>
              )}
              {(order.status === "FULFILLED" || order.status === "CANCELLED") && (
                <p className="text-sm text-slate-400">No further transitions from {order.status}.</p>
              )}
            </div>
          </div>

          <div className="rounded-lg border border-slate-200 bg-white p-5">
            <h2 className="text-sm font-medium text-slate-500">Totals</h2>
            <dl className="mt-3 flex flex-col gap-2 text-sm">
              <Row label="Subtotal" value={order.subtotal} currency={order.currency} />
              <Row label="Discount" value={order.discountAmount} currency={order.currency} />
              <Row label="Tax" value={order.taxAmount} currency={order.currency} />
              <div className="flex justify-between gap-4 border-t border-slate-100 pt-2 font-medium">
                <dt className="text-slate-900">Total</dt>
                <dd className="text-slate-900">
                  {order.totalAmount.toLocaleString()} {order.currency ?? ""}
                </dd>
              </div>
            </dl>
          </div>

          {(order.status === "CONFIRMED" || order.status === "FULFILLED") && <GenerateInvoiceCard orderId={orderId} />}
        </div>
      </div>

      <div className="rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-500">Line items</h2>

        <div className="mt-3 flex flex-col gap-2">
          {order.lineItems.length === 0 && <p className="text-sm text-slate-400">No line items yet.</p>}
          {order.lineItems.map((lineItem) =>
            editingLineItemId === lineItem.id ? (
              <LineItemForm
                key={lineItem.id}
                orderId={orderId}
                products={products}
                lineItem={lineItem}
                onDone={() => {
                  setEditingLineItemId(null);
                  reload();
                }}
                onCancel={() => setEditingLineItemId(null)}
              />
            ) : (
              <div key={lineItem.id} className="flex items-center justify-between gap-4 border-t border-slate-100 pt-2 first:border-t-0 first:pt-0">
                <div className="text-sm">
                  <p className="font-medium text-slate-900">{lineItem.description}</p>
                  <p className="text-slate-500">
                    {lineItem.quantity} &times; {lineItem.unitPrice.toLocaleString()} = {lineItem.lineTotal.toLocaleString()}
                  </p>
                </div>
                <div className="flex shrink-0 gap-2">
                  <Button variant="secondary" onClick={() => setEditingLineItemId(lineItem.id)}>
                    Edit
                  </Button>
                  <Button
                    variant="danger"
                    onClick={() => void handleRemoveLineItem(lineItem.id)}
                    isLoading={pendingLineItemId === lineItem.id}
                  >
                    Remove
                  </Button>
                </div>
              </div>
            ),
          )}
        </div>

        <div className="mt-4 border-t border-slate-100 pt-4">
          <LineItemForm orderId={orderId} products={products} onDone={reload} />
        </div>
      </div>
    </div>
  );
}

function Row({ label, value, currency }: { label: string; value: number; currency: string | null }) {
  return (
    <div className="flex justify-between gap-4">
      <dt className="text-slate-500">{label}</dt>
      <dd className="text-slate-900">
        {value.toLocaleString()} {currency ?? ""}
      </dd>
    </div>
  );
}

/** Small inline form to generate a DRAFT invoice from this order - only shown once the order is CONFIRMED or FULFILLED. */
function GenerateInvoiceCard({ orderId }: { orderId: string }) {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<GenerateInvoiceFormValues>({ resolver: zodResolver(generateInvoiceSchema) });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const invoice = await generateInvoiceFromOrder(orderId, {
        invoiceNumber: values.invoiceNumber,
        issueDate: blankToUndefined(values.issueDate),
        dueDate: blankToUndefined(values.dueDate),
      });
      navigate(`/invoices/${invoice.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-3 rounded-lg border border-slate-200 bg-white p-5">
      <h2 className="text-sm font-medium text-slate-500">Generate an invoice</h2>
      {formError && <Alert variant="error">{formError}</Alert>}
      <TextField label="Invoice number" error={errors.invoiceNumber?.message} {...register("invoiceNumber")} />
      <div className="grid gap-3 sm:grid-cols-2">
        <TextField label="Issue date" type="date" placeholder="Today" error={errors.issueDate?.message} {...register("issueDate")} />
        <TextField label="Due date" type="date" placeholder="+30 days" error={errors.dueDate?.message} {...register("dueDate")} />
      </div>
      <div className="flex justify-end">
        <Button type="submit" isLoading={isSubmitting}>
          Generate invoice
        </Button>
      </div>
    </form>
  );
}

/** Shared form for adding a new line item (no `lineItem` prop) or editing an existing one. */
function LineItemForm({
  orderId,
  products,
  lineItem,
  onDone,
  onCancel,
}: {
  orderId: string;
  products: ProductDto[];
  lineItem?: OrderLineItemDto;
  onDone: () => void;
  onCancel?: () => void;
}) {
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setValue,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<OrderLineItemFormValues>({
    resolver: zodResolver(orderLineItemSchema),
    defaultValues: lineItem
      ? {
          productId: lineItem.productId ?? "",
          description: lineItem.description,
          quantity: String(lineItem.quantity),
          unitPrice: String(lineItem.unitPrice),
        }
      : { quantity: "1" },
  });

  function onProductChange(productId: string) {
    const product = products.find((candidate) => candidate.id === productId);
    if (product) {
      setValue("description", product.name);
      setValue("unitPrice", String(product.unitPrice));
    }
  }

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const request = {
        productId: blankToUndefined(values.productId),
        description: values.description,
        quantity: toRequiredNumber(values.quantity),
        unitPrice: toRequiredNumber(values.unitPrice),
      };
      if (lineItem) {
        await updateOrderLineItem(orderId, lineItem.id, request);
      } else {
        await addOrderLineItem(orderId, request);
        reset({ quantity: "1", productId: "", description: "", unitPrice: "" });
      }
      onDone();
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-3">
      {formError && <Alert variant="error">{formError}</Alert>}
      <div className="grid gap-3 sm:grid-cols-5">
        <div className="sm:col-span-2">
          <Select
            label="Product"
            placeholder="Custom item"
            options={products.map((product) => ({ value: product.id, label: product.name }))}
            error={errors.productId?.message}
            {...register("productId", { onChange: (event) => onProductChange(event.target.value) })}
          />
        </div>
        <TextField label="Quantity" type="number" min={1} step={1} error={errors.quantity?.message} {...register("quantity")} />
        <TextField label="Unit price" type="number" min={0} step="any" error={errors.unitPrice?.message} {...register("unitPrice")} />
        <div className="flex items-end gap-2">
          <Button type="submit" isLoading={isSubmitting}>
            {lineItem ? "Save" : "Add"}
          </Button>
          {onCancel && (
            <Button type="button" variant="secondary" onClick={onCancel}>
              Cancel
            </Button>
          )}
        </div>
      </div>
      <TextField label="Description" error={errors.description?.message} {...register("description")} />
    </form>
  );
}
