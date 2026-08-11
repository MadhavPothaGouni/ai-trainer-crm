import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import {
  archiveKnowledgeArticle,
  deleteKnowledgeArticle,
  getKnowledgeArticle,
  publishKnowledgeArticle,
  updateKnowledgeArticle,
} from "../../api/knowledgeArticles";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import {
  blankToUndefined,
  createKnowledgeArticleSchema,
  toTagList,
  type CreateKnowledgeArticleFormValues,
} from "../../lib/validation";
import type { KnowledgeArticleDto } from "../../types/api";
import { KnowledgeArticleStatusBadge } from "./KnowledgeArticleListPage";

export default function KnowledgeArticleDetailPage() {
  const { articleId } = useParams<{ articleId: string }>();
  const navigate = useNavigate();
  const [article, setArticle] = useState<KnowledgeArticleDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [isTransitioning, setIsTransitioning] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<CreateKnowledgeArticleFormValues>({ resolver: zodResolver(createKnowledgeArticleSchema) });

  function reload() {
    if (!articleId) return;
    getKnowledgeArticle(articleId)
      .then((data) => {
        setArticle(data);
        reset({
          title: data.title,
          category: data.category ?? "",
          content: data.content ?? "",
          tags: data.tags.join(", "),
        });
      })
      .catch((err: unknown) => setError(err instanceof ApiError ? err.message : "Could not load this article."));
  }

  useEffect(() => {
    reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [articleId]);

  const onSave = handleSubmit(async (values) => {
    if (!articleId) return;
    setFormError(null);
    try {
      const updated = await updateKnowledgeArticle(articleId, {
        title: values.title,
        category: blankToUndefined(values.category),
        content: values.content,
        tags: toTagList(values.tags),
      });
      setArticle(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function handlePublish() {
    if (!articleId) return;
    setIsTransitioning(true);
    setError(null);
    try {
      setArticle(await publishKnowledgeArticle(articleId));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not publish this article.");
    } finally {
      setIsTransitioning(false);
    }
  }

  async function handleArchive() {
    if (!articleId || !window.confirm("Archive this article? This cannot be undone.")) return;
    setIsTransitioning(true);
    setError(null);
    try {
      setArticle(await archiveKnowledgeArticle(articleId));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not archive this article.");
    } finally {
      setIsTransitioning(false);
    }
  }

  async function handleDelete() {
    if (!articleId || !window.confirm("Delete this article? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      await deleteKnowledgeArticle(articleId);
      navigate("/knowledge-articles");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this article.");
      setIsDeleting(false);
    }
  }

  if (error && !article) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!article || !articleId) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/knowledge-articles" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Knowledge base
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{article.title}</h1>
            <KnowledgeArticleStatusBadge status={article.status} />
          </div>
          <p className="text-sm text-slate-500">
            /{article.slug} &middot; {article.viewCount.toLocaleString()} views
          </p>
        </div>
        <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
          Delete
        </Button>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-500">Status</h2>
        <div className="mt-3 flex flex-wrap gap-2">
          {article.status === "DRAFT" && (
            <Button onClick={() => void handlePublish()} isLoading={isTransitioning}>
              Publish
            </Button>
          )}
          {article.status !== "ARCHIVED" && (
            <Button variant="secondary" onClick={() => void handleArchive()} isLoading={isTransitioning}>
              Archive
            </Button>
          )}
          {article.status === "ARCHIVED" && <p className="text-sm text-slate-400">Archived articles can't be changed further.</p>}
        </div>
      </div>

      <form onSubmit={onSave} noValidate className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <TextField label="Title" error={errors.title?.message} {...register("title")} />

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Category" error={errors.category?.message} {...register("category")} />
          <TextField label="Tags" placeholder="comma, separated, tags" error={errors.tags?.message} {...register("tags")} />
        </div>

        <TextArea label="Content" rows={12} error={errors.content?.message} {...register("content")} />

        <div className="flex justify-end">
          <Button type="submit" isLoading={isSubmitting}>
            Save changes
          </Button>
        </div>
      </form>
    </div>
  );
}
