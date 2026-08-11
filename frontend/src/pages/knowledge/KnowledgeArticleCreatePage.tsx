import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { createKnowledgeArticle } from "../../api/knowledgeArticles";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createKnowledgeArticleSchema, toTagList, type CreateKnowledgeArticleFormValues } from "../../lib/validation";

export default function KnowledgeArticleCreatePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateKnowledgeArticleFormValues>({ resolver: zodResolver(createKnowledgeArticleSchema) });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const article = await createKnowledgeArticle({
        title: values.title,
        category: blankToUndefined(values.category),
        content: values.content,
        tags: toTagList(values.tags),
      });
      navigate(`/knowledge-articles/${article.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New article</h1>
        <p className="mt-1 text-sm text-slate-500">Starts as a DRAFT - publish it once it's ready.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <TextField label="Title" error={errors.title?.message} {...register("title")} />

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Category" error={errors.category?.message} {...register("category")} />
          <TextField label="Tags" placeholder="comma, separated, tags" error={errors.tags?.message} {...register("tags")} />
        </div>

        <TextArea label="Content" rows={10} error={errors.content?.message} {...register("content")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/knowledge-articles")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Create article
          </Button>
        </div>
      </form>
    </div>
  );
}
