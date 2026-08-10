import { apiClient, unwrap } from "../lib/apiClient";
import type { AssignOwnerRequest, ContactDto, CreateContactRequest, PageResponse, UpdateContactRequest } from "../types/api";

export interface ListContactsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listContacts(params: ListContactsParams = {}): Promise<PageResponse<ContactDto>> {
  return unwrap(apiClient.get("/api/v1/contacts", { params }));
}

export function getContact(contactId: string): Promise<ContactDto> {
  return unwrap(apiClient.get(`/api/v1/contacts/${contactId}`));
}

export function createContact(request: CreateContactRequest): Promise<ContactDto> {
  return unwrap(apiClient.post("/api/v1/contacts", request));
}

export function updateContact(contactId: string, request: UpdateContactRequest): Promise<ContactDto> {
  return unwrap(apiClient.put(`/api/v1/contacts/${contactId}`, request));
}

export function deleteContact(contactId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/contacts/${contactId}`));
}

export function assignContactOwner(contactId: string, request: AssignOwnerRequest): Promise<ContactDto> {
  return unwrap(apiClient.patch(`/api/v1/contacts/${contactId}/owner`, request));
}
