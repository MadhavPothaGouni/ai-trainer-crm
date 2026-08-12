package com.aitrainercrm.platform.attachment.dto;

/** What AttachmentController#download needs to build the HTTP response - fileName/contentType for headers, content for the body. Not a JSON DTO; never serialized. */
public record DownloadedFile(String fileName, String contentType, byte[] content) {
}
