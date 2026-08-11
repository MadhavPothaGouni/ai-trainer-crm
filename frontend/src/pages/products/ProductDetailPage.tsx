import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { deleteProduct, getProduct, updateProduct } from "../../api/products";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createProductSchema, toRequiredNumber, type CreateProductFormValues } from "../../lib/validation";
import type { ProductDto } from "../../types/api";

export default function ProductDetailPage() {
  const { productId } = useParams<{ productId: string }>();
  const navigate = useNavigate();
  const [product, setProduct] = useState<ProductDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<CreateProductFormValues>({ resolver: zodResolver(createProductSchema) });

  useEffect(() => {
    if (!productId) return;
    let cancelled = false;
    getProduct(productId)
      .then((data) => {
        if (cancelled) return;
        setProduct(data);
        reset({
          name: data.name,
          sku: data.sku ?? "",
          description: data.description ?? "",
          unitPrice: String(data.unitPrice),
          currency: data.currency ?? "",
        });
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this product.");
      });
    return () => {
      cancelled = true;
    };
  }, [productId, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!productId || !product) return;
    setFormError(null);
    try {
      const updated = await updateProduct(productId, {
        name: values.name,
        sku: blankToUndefined(values.sku),
        description: blankToUndefined(values.description),
        unitPrice: toRequiredNumber(values.unitPrice),
        currency: blankToUndefined(values.currency),
        active: product.active,
      });
      setProduct(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function toggleActive() {
    if (!productId || !product) return;
    try {
      const updated = await updateProduct(productId, {
        name: product.name,
        sku: product.sku ?? undefined,
        description: product.description ?? undefined,
        unitPrice: product.unitPrice,
        currency: product.currency ?? undefined,
        active: !product.active,
      });
      setProduct(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update this product.");
    }
  }

  async function handleDelete() {
    if (!productId || !window.confirm("Delete this product? Existing quote line items keep their own copy of the price, so this is safe.")) return;
    setIsDeleting(true);
    try {
      await deleteProduct(productId);
      navigate("/products");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this product.");
      setIsDeleting(false);
    }
  }

  if (error && !product) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!product) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/products" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Products
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{product.name}</h1>
            {product.active ? (
              <span className="inline-block rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-medium text-emerald-700">Active</span>
            ) : (
              <span className="inline-block rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-500">Inactive</span>
            )}
          </div>
        </div>
        <div className="flex gap-3">
          <Button variant="secondary" onClick={() => void toggleActive()}>
            {product.active ? "Deactivate" : "Activate"}
          </Button>
          <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
            Delete
          </Button>
        </div>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <TextField label="Name" error={errors.name?.message} {...register("name")} />

        <div className="grid gap-4 sm:grid-cols-3">
          <TextField label="SKU" error={errors.sku?.message} {...register("sku")} />
          <TextField label="Unit price" type="number" min={0} step="any" error={errors.unitPrice?.message} {...register("unitPrice")} />
          <TextField label="Currency" placeholder="USD" error={errors.currency?.message} {...register("currency")} />
        </div>

        <TextArea label="Description" error={errors.description?.message} {...register("description")} />

        <div className="flex justify-end">
          <Button type="submit" isLoading={isSubmitting}>
            Save changes
          </Button>
        </div>
      </form>
    </div>
  );
}
