import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { getOpportunity } from "../../api/opportunities";
import { listProducts } from "../../api/products";
import {
  addQuoteLineItem,
  deleteQuote,
  getQuote,
  removeQuoteLineItem,
  updateQuote,
  updateQuoteLineItem,
  updateQuoteStatus,
} from "../../api/quotes";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import {
  blankToUndefined,
  quoteLineItemSchema,
  toOptionalNumber,
  toRequiredNumber,
  updateQuoteSchema,
  type QuoteLineItemFormValues,
  type UpdateQuoteFormValues,
} from "../../lib/validation";
import { QUOTE_STATUSES, type ProductDto, type QuoteDto, type QuoteLineItemDto, type QuoteStatus } from "../../types/api";
import { QuoteStatusBadge } from "./QuoteListPage";

export default function QuoteDetailPage() {
  const { quoteId } = useParams<{ quoteId: string }>();
  const navigate = useNavigate();
  const [quote, setQuote] = useState<QuoteDto | null>(null);
  const [opportunityName, setOpportunityName] = useState<string | null>(null);
  const [products, setProducts] = useState<ProductDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [isUpdatingStatus, setIsUpdatingStatus] = useState(false);
  const [editingLineItemId, setEditingLineItemId] = useState<string | null>(null);
  const [pendingLineItemId, setPendingLineItemId] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<UpdateQuoteFormValues>({ resolver: zodResolver(updateQuoteSchema) });

  function reload() {
    if (!quoteId) return;
    getQuote(quoteId)
      .then((data) => {
        setQuote(data);
        reset({
          name: data.name,
          currency: data.currency ?? "",
          validUntil: data.validUntil ?? "",
          discountAmount: String(data.discountAmount),
          taxAmount: String(data.taxAmount),
        });
        getOpportunity(data.opportunityId)
          .then((opportunity) => setOpportunityName(opportunity.name))
          .catch(() => undefined);
      })
      .catch((err: unknown) => setError(err instanceof ApiError ? err.message : "Could not load this quote."));
  }

  useEffect(() => {
    reload();
    listProducts({ size: 100, sort: "name,asc" })
      .then((res) => setProducts(res.content.filter((product) => product.active)))
      .catch(() => undefined);
  }, [quoteId]);

  const onSaveHeader = handleSubmit(async (values) => {
    if (!quoteId) return;
    setFormError(null);
    try {
      const updated = await updateQuote(quoteId, {
        name: values.name,
        currency: blankToUndefined(values.currency),
        validUntil: blankToUndefined(values.validUntil),
        discountAmount: toOptionalNumber(values.discountAmount),
        taxAmount: toOptionalNumber(values.taxAmount),
      });
      setQuote(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function handleStatusChange(status: string) {
    if (!quoteId) return;
    setIsUpdatingStatus(true);
    setError(null);
    try {
      const updated = await updateQuoteStatus(quoteId, { status: status as QuoteStatus });
      setQuote(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update the status.");
    } finally {
      setIsUpdatingStatus(false);
    }
  }

  async function handleDelete() {
    if (!quoteId || !window.confirm("Delete this quote? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      await deleteQuote(quoteId);
      navigate("/quotes");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this quote.");
      setIsDeleting(false);
    }
  }

  async function handleRemoveLineItem(lineItemId: string) {
    if (!quoteId || !window.confirm("Remove this line item?")) return;
    setPendingLineItemId(lineItemId);
    try {
      await removeQuoteLineItem(quoteId, lineItemId);
      reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not remove this line item.");
    } finally {
      setPendingLineItemId(null);
    }
  }

  if (error && !quote) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!quote || !quoteId) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/quotes" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Quotes
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{quote.name}</h1>
            <QuoteStatusBadge status={quote.status} />
          </div>
          {opportunityName && (
            <Link to={`/opportunities/${quote.opportunityId}`} className="text-sm text-slate-500 hover:underline">
              {opportunityName}
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

          <TextField label="Quote name" error={errors.name?.message} {...register("name")} />
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField label="Currency" placeholder="USD" error={errors.currency?.message} {...register("currency")} />
            <TextField label="Valid until" type="date" error={errors.validUntil?.message} {...register("validUntil")} />
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
            <div className="mt-3">
              <Select
                label="Status"
                options={QUOTE_STATUSES.map((status) => ({ value: status, label: status }))}
                value={quote.status}
                disabled={isUpdatingStatus}
                onChange={(event) => void handleStatusChange(event.target.value)}
              />
            </div>
          </div>

          <div className="rounded-lg border border-slate-200 bg-white p-5">
            <h2 className="text-sm font-medium text-slate-500">Totals</h2>
            <dl className="mt-3 flex flex-col gap-2 text-sm">
              <Row label="Subtotal" value={quote.subtotal} currency={quote.currency} />
              <Row label="Discount" value={quote.discountAmount} currency={quote.currency} />
              <Row label="Tax" value={quote.taxAmount} currency={quote.currency} />
              <div className="flex justify-between gap-4 border-t border-slate-100 pt-2 font-medium">
                <dt className="text-slate-900">Total</dt>
                <dd className="text-slate-900">
                  {quote.totalAmount.toLocaleString()} {quote.currency ?? ""}
                </dd>
              </div>
            </dl>
          </div>
        </div>
      </div>

      <div className="rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-500">Line items</h2>

        <div className="mt-3 flex flex-col gap-2">
          {quote.lineItems.length === 0 && <p className="text-sm text-slate-400">No line items yet.</p>}
          {quote.lineItems.map((lineItem) =>
            editingLineItemId === lineItem.id ? (
              <LineItemForm
                key={lineItem.id}
                quoteId={quoteId}
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
          <LineItemForm quoteId={quoteId} products={products} onDone={reload} />
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

/** Shared form for adding a new line item (no `lineItem` prop) or editing an existing one. */
function LineItemForm({
  quoteId,
  products,
  lineItem,
  onDone,
  onCancel,
}: {
  quoteId: string;
  products: ProductDto[];
  lineItem?: QuoteLineItemDto;
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
  } = useForm<QuoteLineItemFormValues>({
    resolver: zodResolver(quoteLineItemSchema),
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
        await updateQuoteLineItem(quoteId, lineItem.id, request);
      } else {
        await addQuoteLineItem(quoteId, request);
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
