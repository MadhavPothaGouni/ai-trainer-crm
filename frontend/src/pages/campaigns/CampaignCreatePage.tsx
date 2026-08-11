import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { createCampaign } from "../../api/campaigns";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createCampaignSchema, toOptionalNumber, type CreateCampaignFormValues } from "../../lib/validation";
import { CAMPAIGN_TYPES, type CampaignType } from "../../types/api";

export default function CampaignCreatePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateCampaignFormValues>({ resolver: zodResolver(createCampaignSchema) });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const campaign = await createCampaign({
        name: values.name,
        type: values.type as CampaignType,
        startDate: blankToUndefined(values.startDate),
        endDate: blankToUndefined(values.endDate),
        budget: toOptionalNumber(values.budget),
        actualCost: toOptionalNumber(values.actualCost),
        description: blankToUndefined(values.description),
      });
      navigate(`/campaigns/${campaign.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New campaign</h1>
        <p className="mt-1 text-sm text-slate-500">Members are added after creation.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <TextField label="Campaign name" error={errors.name?.message} {...register("name")} />
        <Select
          label="Type"
          placeholder="Select a type"
          options={CAMPAIGN_TYPES.map((type) => ({ value: type, label: type.replace("_", " ") }))}
          error={errors.type?.message}
          {...register("type")}
        />

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Start date" type="date" error={errors.startDate?.message} {...register("startDate")} />
          <TextField label="End date" type="date" error={errors.endDate?.message} {...register("endDate")} />
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Budget" type="number" min={0} step="any" error={errors.budget?.message} {...register("budget")} />
          <TextField label="Actual cost" type="number" min={0} step="any" error={errors.actualCost?.message} {...register("actualCost")} />
        </div>

        <TextArea label="Description" error={errors.description?.message} {...register("description")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/campaigns")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Create campaign
          </Button>
        </div>
      </form>
    </div>
  );
}
