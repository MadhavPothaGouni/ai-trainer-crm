import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { createOrder } from "../../api/orders";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createOrderSchema, toOptionalNumber, type CreateOrderFormValues } from "../../lib/validation";

/** Creates a standalone DRAFT order with no line items yet. To convert an existing quote instead (line items included), use the "Convert to order" button on that quote's detail page. */
export default function OrderCreatePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateOrderFormValues>({ resolver: zodResolver(createOrderSchema) });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const order = await createOrder({
        orderNumber: values.orderNumber,
        currency: blankToUndefined(values.currency),
        discountAmount: toOptionalNumber(values.discountAmount),
        taxAmount: toOptionalNumber(values.taxAmount),
      });
      navigate(`/orders/${order.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New order</h1>
        <p className="mt-1 text-sm text-slate-500">A standalone order. Line items are added after creation.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <TextField label="Order number" error={errors.orderNumber?.message} {...register("orderNumber")} />

        <div className="grid gap-4 sm:grid-cols-3">
          <TextField label="Currency" placeholder="USD" error={errors.currency?.message} {...register("currency")} />
          <TextField
            label="Discount"
            type="number"
            min={0}
            step="any"
            error={errors.discountAmount?.message}
            {...register("discountAmount")}
          />
          <TextField label="Tax" type="number" min={0} step="any" error={errors.taxAmount?.message} {...register("taxAmount")} />
        </div>

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/orders")}>
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
