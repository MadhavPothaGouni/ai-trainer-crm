import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { createProduct } from "../../api/products";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createProductSchema, toRequiredNumber, type CreateProductFormValues } from "../../lib/validation";

export default function ProductCreatePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateProductFormValues>({ resolver: zodResolver(createProductSchema) });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const product = await createProduct({
        name: values.name,
        sku: blankToUndefined(values.sku),
        description: blankToUndefined(values.description),
        unitPrice: toRequiredNumber(values.unitPrice),
        currency: blankToUndefined(values.currency),
      });
      navigate(`/products/${product.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New product</h1>
        <p className="mt-1 text-sm text-slate-500">Add a catalog item quotes can be built from.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <TextField label="Name" error={errors.name?.message} {...register("name")} />

        <div className="grid gap-4 sm:grid-cols-3">
          <TextField label="SKU" error={errors.sku?.message} {...register("sku")} />
          <TextField label="Unit price" type="number" min={0} step="any" error={errors.unitPrice?.message} {...register("unitPrice")} />
          <TextField label="Currency" placeholder="USD" error={errors.currency?.message} {...register("currency")} />
        </div>

        <TextArea label="Description" error={errors.description?.message} {...register("description")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/products")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Create product
          </Button>
        </div>
      </form>
    </div>
  );
}
