import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate, useSearchParams } from "react-router-dom";
import { createRefundRecord } from "../../api/refundRecords";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createRefundRecordSchema, type CreateRefundRecordFormValues } from "../../lib/validation";
import { REFUND_RECORD_REASONS, type RefundRecordReason } from "../../types/api";

export default function RefundRecordCreatePage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const preselectedPaymentId = searchParams.get("paymentId") ?? undefined;
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateRefundRecordFormValues>({
    resolver: zodResolver(createRefundRecordSchema),
    defaultValues: { paymentId: preselectedPaymentId ?? "", reason: "OTHER" },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const refund = await createRefundRecord({
        paymentId: values.paymentId,
        amount: Number(values.amount),
        reason: values.reason as RefundRecordReason,
        notes: blankToUndefined(values.notes),
      });
      navigate(`/refund-records/${refund.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New refund</h1>
        <p className="mt-1 text-sm text-slate-500">
          A payment's refunds can never total more than the payment itself - open the invoice to find its payment id.
        </p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <TextField
          label="Payment ID"
          placeholder="00000000-0000-0000-0000-000000000000"
          error={errors.paymentId?.message}
          {...register("paymentId")}
        />

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Amount" type="number" min={0} step="0.01" error={errors.amount?.message} {...register("amount")} />
          <Select
            label="Reason"
            options={REFUND_RECORD_REASONS.map((reason) => ({ value: reason, label: reason.replace("_", " ") }))}
            error={errors.reason?.message}
            {...register("reason")}
          />
        </div>

        <TextArea label="Notes" error={errors.notes?.message} {...register("notes")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/refund-records")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Request refund
          </Button>
        </div>
      </form>
    </div>
  );
}
