import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useParams } from "react-router-dom";
import {
  addInvoiceLineItem,
  getInvoice,
  issueInvoice,
  removeInvoiceLineItem,
  updateInvoice,
  updateInvoiceLineItem,
  voidInvoice,
} from "../../api/invoices";
import { deletePayment, listPayments, recordPayment } from "../../api/payments";
import { listProducts } from "../../api/products";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import {
  blankToUndefined,
  invoiceLineItemSchema,
  recordPaymentSchema,
  toOptionalNumber,
  toRequiredNumber,
  updateInvoiceSchema,
  type InvoiceLineItemFormValues,
  type RecordPaymentFormValues,
  type UpdateInvoiceFormValues,
} from "../../lib/validation";
import {
  PAYMENT_METHODS,
  type InvoiceDto,
  type InvoiceLineItemDto,
  type PaymentDto,
  type PaymentMethod,
  type ProductDto,
} from "../../types/api";
import { InvoiceStatusBadge } from "./InvoiceListPage";

export default function InvoiceDetailPage() {
  const { invoiceId } = useParams<{ invoiceId: string }>();
  const [invoice, setInvoice] = useState<InvoiceDto | null>(null);
  const [payments, setPayments] = useState<PaymentDto[]>([]);
  const [products, setProducts] = useState<ProductDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isTransitioning, setIsTransitioning] = useState(false);
  const [editingLineItemId, setEditingLineItemId] = useState<string | null>(null);
  const [pendingLineItemId, setPendingLineItemId] = useState<string | null>(null);
  const [pendingPaymentId, setPendingPaymentId] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<UpdateInvoiceFormValues>({ resolver: zodResolver(updateInvoiceSchema) });

  function reload() {
    if (!invoiceId) return;
    getInvoice(invoiceId)
      .then((data) => {
        setInvoice(data);
        reset({
          invoiceNumber: data.invoiceNumber,
          currency: data.currency ?? "",
          issueDate: data.issueDate,
          dueDate: data.dueDate,
          discountAmount: String(data.discountAmount),
          taxAmount: String(data.taxAmount),
        });
      })
      .catch((err: unknown) => setError(err instanceof ApiError ? err.message : "Could not load this invoice."));
    listPayments(invoiceId, { sort: "paidAt,desc" })
      .then((res) => setPayments(res.content))
      .catch(() => undefined);
  }

  useEffect(() => {
    reload();
    listProducts({ size: 100, sort: "name,asc" })
      .then((res) => setProducts(res.content.filter((product) => product.active)))
      .catch(() => undefined);
  }, [invoiceId]);

  const onSaveHeader = handleSubmit(async (values) => {
    if (!invoiceId) return;
    setFormError(null);
    try {
      const updated = await updateInvoice(invoiceId, {
        invoiceNumber: values.invoiceNumber,
        currency: blankToUndefined(values.currency),
        issueDate: values.issueDate,
        dueDate: values.dueDate,
        discountAmount: toOptionalNumber(values.discountAmount),
        taxAmount: toOptionalNumber(values.taxAmount),
      });
      setInvoice(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function handleIssue() {
    if (!invoiceId) return;
    setIsTransitioning(true);
    setError(null);
    try {
      setInvoice(await issueInvoice(invoiceId));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not issue this invoice.");
    } finally {
      setIsTransitioning(false);
    }
  }

  async function handleVoid() {
    if (!invoiceId || !window.confirm("Void this invoice? This cannot be undone.")) return;
    setIsTransitioning(true);
    setError(null);
    try {
      setInvoice(await voidInvoice(invoiceId));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not void this invoice.");
    } finally {
      setIsTransitioning(false);
    }
  }

  async function handleRemoveLineItem(lineItemId: string) {
    if (!invoiceId || !window.confirm("Remove this line item?")) return;
    setPendingLineItemId(lineItemId);
    try {
      await removeInvoiceLineItem(invoiceId, lineItemId);
      reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not remove this line item.");
    } finally {
      setPendingLineItemId(null);
    }
  }

  async function handleRemovePayment(paymentId: string) {
    if (!window.confirm("Remove this payment? The invoice's balance due will be recalculated.")) return;
    setPendingPaymentId(paymentId);
    try {
      await deletePayment(paymentId);
      reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not remove this payment.");
    } finally {
      setPendingPaymentId(null);
    }
  }

  if (error && !invoice) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!invoice || !invoiceId) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  const isDraft = invoice.status === "DRAFT";
  const canRecordPayment = invoice.status === "SENT" || invoice.status === "OVERDUE";
  const canVoid = invoice.status === "DRAFT" || invoice.status === "SENT" || invoice.status === "OVERDUE";

  return (
    <div className="flex flex-col gap-6">
      <div>
        <Link to="/invoices" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
          &larr; Invoices
        </Link>
        <div className="mt-1 flex items-center gap-3">
          <h1 className="text-2xl font-semibold text-slate-900">{invoice.invoiceNumber}</h1>
          <InvoiceStatusBadge status={invoice.status} />
        </div>
        <Link to={`/orders/${invoice.orderId}`} className="text-sm text-slate-500 hover:underline">
          Generated from order
        </Link>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="grid gap-4 sm:grid-cols-2">
        <form onSubmit={onSaveHeader} noValidate className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Details {!isDraft && <span className="font-normal text-slate-400">(locked once issued)</span>}</h2>
          {formError && <Alert variant="error">{formError}</Alert>}

          <TextField label="Invoice number" disabled={!isDraft} error={errors.invoiceNumber?.message} {...register("invoiceNumber")} />
          <div className="grid gap-4 sm:grid-cols-3">
            <TextField label="Currency" placeholder="USD" disabled={!isDraft} error={errors.currency?.message} {...register("currency")} />
            <TextField label="Issue date" type="date" disabled={!isDraft} error={errors.issueDate?.message} {...register("issueDate")} />
            <TextField label="Due date" type="date" disabled={!isDraft} error={errors.dueDate?.message} {...register("dueDate")} />
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField label="Discount" type="number" min={0} step="any" disabled={!isDraft} error={errors.discountAmount?.message} {...register("discountAmount")} />
            <TextField label="Tax" type="number" min={0} step="any" disabled={!isDraft} error={errors.taxAmount?.message} {...register("taxAmount")} />
          </div>
          {isDraft && (
            <div className="flex justify-end">
              <Button type="submit" isLoading={isSubmitting}>
                Save changes
              </Button>
            </div>
          )}
        </form>

        <div className="flex flex-col gap-4">
          <div className="rounded-lg border border-slate-200 bg-white p-5">
            <h2 className="text-sm font-medium text-slate-500">Status</h2>
            <div className="mt-3 flex flex-wrap gap-2">
              {isDraft && (
                <Button onClick={() => void handleIssue()} isLoading={isTransitioning}>
                  Issue invoice
                </Button>
              )}
              {canVoid && (
                <Button variant="secondary" onClick={() => void handleVoid()} isLoading={isTransitioning}>
                  Void invoice
                </Button>
              )}
              {!isDraft && !canVoid && <p className="text-sm text-slate-400">No further transitions from {invoice.status}.</p>}
            </div>
          </div>

          <div className="rounded-lg border border-slate-200 bg-white p-5">
            <h2 className="text-sm font-medium text-slate-500">Totals</h2>
            <dl className="mt-3 flex flex-col gap-2 text-sm">
              <Row label="Subtotal" value={invoice.subtotal} currency={invoice.currency} />
              <Row label="Discount" value={invoice.discountAmount} currency={invoice.currency} />
              <Row label="Tax" value={invoice.taxAmount} currency={invoice.currency} />
              <div className="flex justify-between gap-4 border-t border-slate-100 pt-2 font-medium">
                <dt className="text-slate-900">Total</dt>
                <dd className="text-slate-900">
                  {invoice.totalAmount.toLocaleString()} {invoice.currency ?? ""}
                </dd>
              </div>
              <Row label="Amount paid" value={invoice.amountPaid} currency={invoice.currency} />
              <div className="flex justify-between gap-4 border-t border-slate-100 pt-2 font-medium">
                <dt className="text-slate-900">Balance due</dt>
                <dd className="text-slate-900">
                  {invoice.balanceDue.toLocaleString()} {invoice.currency ?? ""}
                </dd>
              </div>
            </dl>
          </div>
        </div>
      </div>

      <div className="rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-500">Line items {!isDraft && <span className="font-normal text-slate-400">(locked once issued)</span>}</h2>

        <div className="mt-3 flex flex-col gap-2">
          {invoice.lineItems.length === 0 && <p className="text-sm text-slate-400">No line items yet.</p>}
          {invoice.lineItems.map((lineItem) =>
            editingLineItemId === lineItem.id ? (
              <LineItemForm
                key={lineItem.id}
                invoiceId={invoiceId}
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
                {isDraft && (
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
                )}
              </div>
            ),
          )}
        </div>

        {isDraft && (
          <div className="mt-4 border-t border-slate-100 pt-4">
            <LineItemForm invoiceId={invoiceId} products={products} onDone={reload} />
          </div>
        )}
      </div>

      <div className="rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-500">Payments</h2>

        <div className="mt-3 flex flex-col gap-2">
          {payments.length === 0 && <p className="text-sm text-slate-400">No payments recorded yet.</p>}
          {payments.map((payment) => (
            <div key={payment.id} className="flex items-center justify-between gap-4 border-t border-slate-100 pt-2 first:border-t-0 first:pt-0">
              <div className="text-sm">
                <p className="font-medium text-slate-900">
                  {payment.amount.toLocaleString()} {invoice.currency ?? ""} &middot; {payment.method.replace("_", " ")}
                </p>
                <p className="text-slate-500">
                  {new Date(payment.paidAt).toLocaleDateString()}
                  {payment.reference ? ` · ${payment.reference}` : ""}
                </p>
              </div>
              <Button variant="danger" onClick={() => void handleRemovePayment(payment.id)} isLoading={pendingPaymentId === payment.id}>
                Remove
              </Button>
            </div>
          ))}
        </div>

        {canRecordPayment && (
          <div className="mt-4 border-t border-slate-100 pt-4">
            <RecordPaymentForm invoiceId={invoiceId} onDone={reload} />
          </div>
        )}
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

/** Shared form for adding a new line item (no `lineItem` prop) or editing an existing one. Only ever rendered while the invoice is DRAFT. */
function LineItemForm({
  invoiceId,
  products,
  lineItem,
  onDone,
  onCancel,
}: {
  invoiceId: string;
  products: ProductDto[];
  lineItem?: InvoiceLineItemDto;
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
  } = useForm<InvoiceLineItemFormValues>({
    resolver: zodResolver(invoiceLineItemSchema),
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
        await updateInvoiceLineItem(invoiceId, lineItem.id, request);
      } else {
        await addInvoiceLineItem(invoiceId, request);
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

/** Only ever rendered while the invoice is SENT or OVERDUE - see InvoiceDetailPage's canRecordPayment. */
function RecordPaymentForm({ invoiceId, onDone }: { invoiceId: string; onDone: () => void }) {
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RecordPaymentFormValues>({ resolver: zodResolver(recordPaymentSchema) });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await recordPayment(invoiceId, {
        amount: toRequiredNumber(values.amount),
        method: values.method as PaymentMethod,
        reference: blankToUndefined(values.reference),
        notes: blankToUndefined(values.notes),
      });
      reset({ amount: "", method: "", reference: "", notes: "" });
      onDone();
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-3">
      <h3 className="text-sm font-medium text-slate-500">Record a payment</h3>
      {formError && <Alert variant="error">{formError}</Alert>}
      <div className="grid gap-3 sm:grid-cols-4">
        <TextField label="Amount" type="number" min={0} step="any" error={errors.amount?.message} {...register("amount")} />
        <Select
          label="Method"
          placeholder="Select a method"
          options={PAYMENT_METHODS.map((method) => ({ value: method, label: method.replace("_", " ") }))}
          error={errors.method?.message}
          {...register("method")}
        />
        <TextField label="Reference" error={errors.reference?.message} {...register("reference")} />
        <div className="flex items-end">
          <Button type="submit" isLoading={isSubmitting}>
            Record payment
          </Button>
        </div>
      </div>
      <TextField label="Notes" error={errors.notes?.message} {...register("notes")} />
    </form>
  );
}
