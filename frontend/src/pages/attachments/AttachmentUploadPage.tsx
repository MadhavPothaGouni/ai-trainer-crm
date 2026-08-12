import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { uploadAttachment } from "../../api/attachments";
import { RelatedToPicker } from "../../components/crm/RelatedToPicker";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { TextArea } from "../../components/ui/TextArea";
import { ApiError } from "../../lib/apiClient";
import { blankToUndefined, uploadAttachmentSchema, type UploadAttachmentFormValues } from "../../lib/validation";
import type { CrmRecordType } from "../../types/api";

/** No react-hook-form register() for the file input itself - a native <input type="file">'s value is a FileList the browser controls, not something RHF/zod needs to validate beyond "is one chosen," which the plain isSubmitting-blocked submit handler below already checks. */
export default function AttachmentUploadPage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [isUploading, setIsUploading] = useState(false);

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    formState: { errors },
  } = useForm<UploadAttachmentFormValues>({
    resolver: zodResolver(uploadAttachmentSchema),
    defaultValues: { relatedToType: "", relatedToId: "" },
  });

  const relatedToType = watch("relatedToType") ?? "";
  const relatedToId = watch("relatedToId") ?? "";

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    if (!file) {
      setFormError("Choose a file to upload.");
      return;
    }
    setIsUploading(true);
    try {
      const attachment = await uploadAttachment(file, values.relatedToType as CrmRecordType, values.relatedToId, blankToUndefined(values.description));
      navigate(`/attachments/${attachment.id}`);
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : "Could not upload this file.");
    } finally {
      setIsUploading(false);
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Upload file</h1>
        <p className="mt-1 text-sm text-slate-500">Up to 20 MB, attached to an Account, Contact, Opportunity, Lead, or Ticket.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <div className="flex flex-col gap-1.5">
          <label htmlFor="attachment-file" className="text-sm font-medium text-slate-700">
            File
          </label>
          <input
            id="attachment-file"
            type="file"
            onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            className="rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm outline-none file:mr-3 file:rounded-md file:border-0 file:bg-slate-100 file:px-3 file:py-1.5 file:text-sm file:font-medium file:text-slate-700 hover:file:bg-slate-200"
          />
        </div>

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

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/attachments")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isUploading}>
            Upload
          </Button>
        </div>
      </form>
    </div>
  );
}
