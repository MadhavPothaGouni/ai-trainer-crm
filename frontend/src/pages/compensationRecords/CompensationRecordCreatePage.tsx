import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { createCompensationRecord } from "../../api/compensationRecords";
import { listUsers } from "../../api/users";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createCompensationRecordSchema, toOptionalNumber, type CreateCompensationRecordFormValues } from "../../lib/validation";
import type { UserDto } from "../../types/api";

export default function CompensationRecordCreatePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);
  const [users, setUsers] = useState<UserDto[]>([]);

  useEffect(() => {
    listUsers({ size: 100, sort: "lastName,asc" })
      .then((res) => setUsers(res.content))
      .catch(() => setUsers([]));
  }, []);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateCompensationRecordFormValues>({ resolver: zodResolver(createCompensationRecordSchema) });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const record = await createCompensationRecord({
        staffUserId: values.staffUserId,
        payPeriodStart: values.payPeriodStart,
        payPeriodEnd: values.payPeriodEnd,
        hoursWorked: Number(values.hoursWorked),
        hourlyRate: Number(values.hourlyRate),
        commissionAmount: toOptionalNumber(values.commissionAmount),
        bonusAmount: toOptionalNumber(values.bonusAmount),
        notes: blankToUndefined(values.notes),
      });
      navigate(`/compensation-records/${record.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New compensation record</h1>
        <p className="mt-1 text-sm text-slate-500">Total is computed automatically from hours, rate, commission, and bonus.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <Select
          label="Staff member"
          placeholder="Select a staff member"
          options={users.map((user) => ({ value: user.id, label: user.fullName }))}
          error={errors.staffUserId?.message}
          {...register("staffUserId")}
        />

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Pay period start" type="date" error={errors.payPeriodStart?.message} {...register("payPeriodStart")} />
          <TextField label="Pay period end" type="date" error={errors.payPeriodEnd?.message} {...register("payPeriodEnd")} />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Hours worked" type="number" min={0} step="0.01" error={errors.hoursWorked?.message} {...register("hoursWorked")} />
          <TextField label="Hourly rate" type="number" min={0} step="0.01" error={errors.hourlyRate?.message} {...register("hourlyRate")} />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Commission" type="number" min={0} step="0.01" error={errors.commissionAmount?.message} {...register("commissionAmount")} />
          <TextField label="Bonus" type="number" min={0} step="0.01" error={errors.bonusAmount?.message} {...register("bonusAmount")} />
        </div>

        <TextArea label="Notes" error={errors.notes?.message} {...register("notes")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/compensation-records")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Create record
          </Button>
        </div>
      </form>
    </div>
  );
}
