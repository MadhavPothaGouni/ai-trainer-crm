import { downloadAttachment } from "../api/attachments";
import type { AttachmentDto } from "../types/api";

/** "12.3 KB"/"4.1 MB" rather than a raw byte count - shared by AttachmentListPage and AttachmentDetailPage. */
export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/** Triggers a browser download from an in-memory Blob - shared by AttachmentListPage and AttachmentDetailPage's own download buttons. Pulled out of both page files rather than exported alongside their components, since a plain-function export next to a component export breaks Vite's fast-refresh boundary (react/only-export-components). */
export async function triggerAttachmentDownload(attachment: AttachmentDto): Promise<void> {
  const blob = await downloadAttachment(attachment.id);
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = attachment.fileName;
  link.click();
  URL.revokeObjectURL(url);
}
