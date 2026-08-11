import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate, useSearchParams } from "react-router-dom";
import { createCustomField } from "../../api/customFields";
import { listCustomObjects } from "../../api/customObjects";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { createCustomFieldSchema, toOptionalNumber, toPicklistValues, type CreateCustomFieldFormValues } from "../../lib/validation";
import {
  CUSTOM_FIELD_TYPES,
  STANDARD_ENTITY_TYPES,
  type CustomFieldType,
  type CustomObjectDto,
  type StandardEntityType,
} from "../../types/api";

export default function CustomFieldCreatePage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [formError, setFormError] = useState<string | null>(null);
  const [customObjects, setCustomObjects] = useState<CustomObjectDto[]>([]);

  const presetStandardEntityType = searchParams.get("standardEntityType") ?? "";
  const presetCustomObjectId = searchParams.get("customObjectId") ?? "";

  const {
    register,
    handleSubmit,
    watch,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateCustomFieldFormValues>({
    resolver: zodResolver(createCustomFieldSchema),
    defaultValues: {
      standardEntityType: presetStandardEntityType,
      customObjectId: presetCustomObjectId,
      required: false,
      displayOrder: "0",
    },
  });

  const fieldType = watch("fieldType");
  const standardEntityType = watch("standardEntityType");
  const customObjectId = watch("customObjectId");

  useEffect(() => {
    listCustomObjects({ size: 100, sort: "label,asc" })
      .then((res) => setCustomObjects(res.content))
      .catch(() => undefined);
  }, []);

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const field = await createCustomField({
        standardEntityType: values.standardEntityType ? (values.standardEntityType as StandardEntityType) : undefined,
        customObjectId: values.customObjectId || undefined,
        apiName: values.apiName,
        label: values.label,
        fieldType: values.fieldType as CustomFieldType,
        required: values.required,
        displayOrder: toOptionalNumber(values.displayOrder),
        picklistValues: values.fieldType === "PICKLIST" ? toPicklistValues(values.picklistValues) : undefined,
      });
      if (field.customObjectId) {
        navigate(`/custom-objects/${field.customObjectId}`);
      } else {
        navigate(`/custom-fields?standardEntityType=${field.standardEntityType}`);
      }
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New custom field</h1>
        <p className="mt-1 text-sm text-slate-500">Attach it to a standard entity or a custom object - never both.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <Select
            label="Standard entity"
            placeholder="-- none --"
            options={STANDARD_ENTITY_TYPES.map((type) => ({ value: type, label: type }))}
            {...register("standardEntityType")}
          />
          <Select
            label="Custom object"
            placeholder="-- none --"
            options={customObjects.map((object) => ({ value: object.id, label: object.label }))}
            error={errors.customObjectId?.message}
            {...register("customObjectId")}
          />
        </div>
        {standardEntityType && customObjectId && (
          <p className="text-sm text-red-600">Choose exactly one of a standard entity or a custom object, not both.</p>
        )}

        <TextField label="API name" placeholder="priority" error={errors.apiName?.message} {...register("apiName")} />
        <p className="-mt-2 text-xs text-slate-400">Lowercase letters, numbers, and underscores only - can't be changed later.</p>

        <TextField label="Label" placeholder="Priority" error={errors.label?.message} {...register("label")} />

        <div className="grid gap-4 sm:grid-cols-2">
          <Select
            label="Field type"
            options={CUSTOM_FIELD_TYPES.map((type) => ({ value: type, label: type }))}
            error={errors.fieldType?.message}
            {...register("fieldType")}
          />
          <TextField label="Display order" type="number" error={errors.displayOrder?.message} {...register("displayOrder")} />
        </div>

        {fieldType === "PICKLIST" && (
          <>
            <TextField
              label="Picklist values"
              placeholder="LOW, MEDIUM, HIGH"
              error={errors.picklistValues?.message}
              {...register("picklistValues")}
            />
            <p className="-mt-2 text-xs text-slate-400">Comma-separated. At least one value is required for a PICKLIST field.</p>
          </>
        )}

        <label className="flex items-center gap-2 text-sm text-slate-700">
          <input type="checkbox" className="h-4 w-4 rounded border-slate-300" {...register("required")} />
          Required
        </label>

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate(-1)}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Create field
          </Button>
        </div>
      </form>
    </div>
  );
}
