import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { deleteAttachment, getAttachment, updateAttachment } from "../../api/attachments";
import { RelatedToPicker } from "../../components/crm/RelatedToPicker";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { formatFileSize, triggerAttachmentDownload } from "../../lib/attachments";
import { blankToUndefined, updateAttachmentSchema, type UpdateAttachmentFormValues } from "../../lib/validation";
import type { AttachmentDto, CrmRecordType } from "../../types/api";

export default function AttachmentDetailPage() {
  const { attachmentId } = useParams<{ attachmentId: string }>();
  const navigate = useNavigate();
  const [attachment, setAttachment] = useState<AttachmentDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isDownloading, setIsDownloading] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);

  useEffect(() => {
    if (!attachmentId) return;
    let cancelled = false;
    getAttachment(attachmentId)
      .then((data) => {
        if (!cancelled) setAttachment(data);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this attachment.");
      });
    return () => {
      cancelled = true;
    };
  }, [attachmentId]);

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    watch,
    formState: { errors, isSubmitting },
    setError: setEditFieldError,
  } = useForm<UpdateAttachmentFormValues>({ resolver: zodResolver(updateAttachmentSchema) });

  useEffect(() => {
    if (!attachment) return;
    reset({
      fileName: attachment.fileName,
      description: attachment.description ?? "",
      relatedToType: attachment.relatedToType,
      relatedToId: attachment.relatedToId,
    });
  }, [attachment, reset]);

  const relatedToType = watch("relatedToType") ?? "";
  const relatedToId = watch("relatedToId") ?? "";

  const onSaveEdits = handleSubmit(async (values) => {
    if (!attachmentId) return;
    setEditError(null);
    try {
      const updated = await updateAttachment(attachmentId, {
        fileName: values.fileName,
        description: blankToUndefined(values.description),
        relatedToType: values.relatedToType as CrmRecordType,
        relatedToId: values.relatedToId,
      });
      setAttachment(updated);
    } catch (error) {
      setEditError(applyServerErrors(error, setEditFieldError));
    }
  });

  async function handleDownload() {
    if (!attachment) return;
    setIsDownloading(true);
    setError(null);
    try {
      await triggerAttachmentDownload(attachment);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not download this file.");
    } finally {
      setIsDownloading(false);
    }
  }

  async function handleDelete() {
    if (!attachmentId || !window.confirm("Delete this attachment?")) return;
    setIsDeleting(true);
    try {
      await deleteAttachment(attachmentId);
      navigate("/attachments");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this attachment.");
      setIsDeleting(false);
    }
  }

  if (error && !attachment) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!attachment) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/attachments" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Attachments
          </Link>
          <h1 className="mt-1 text-2xl font-semibold text-slate-900">{attachment.fileName}</h1>
        </div>
        <div className="flex gap-3">
          <Button variant="secondary" onClick={() => void handleDownload()} isLoading={isDownloading}>
            Download
          </Button>
          <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
            Delete
          </Button>
        </div>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-500">Overview</h2>
        <dl className="mt-3 flex flex-col gap-2 text-sm">
          <div className="flex justify-between gap-4">
            <dt className="text-slate-500">Type</dt>
            <dd className="text-slate-900">{attachment.contentType ?? "—"}</dd>
          </div>
          <div className="flex justify-between gap-4">
            <dt className="text-slate-500">Size</dt>
            <dd className="text-slate-900">{formatFileSize(attachment.fileSizeBytes)}</dd>
          </div>
          <div className="flex justify-between gap-4">
            <dt className="text-slate-500">Uploaded</dt>
            <dd className="text-slate-900">{new Date(attachment.createdAt).toLocaleString()}</dd>
          </div>
        </dl>
      </div>

      <form onSubmit={onSaveEdits} noValidate className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Edit</h2>

        {editError && <Alert variant="error">{editError}</Alert>}

        <TextField label="File name" error={errors.fileName?.message} {...register("fileName")} />

        <RelatedToPicker
          relatedToType={relatedToType}
          relatedToId={relatedToId}
          onChange={(type, id) => {
            setValue("relatedToType", type, { shouldValidate: true });
            setValue("relatedToId", id, { shouldValidate: true });
          }}
          typeError={errors.relatedToType?.message}
          idError={errors.relatedToId?.message}
        />

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
