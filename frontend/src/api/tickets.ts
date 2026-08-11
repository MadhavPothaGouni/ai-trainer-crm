import { apiClient, unwrap } from "../lib/apiClient";
import type {
  AssignOwnerRequest,
  CreateTicketRequest,
  PageResponse,
  TicketDto,
  UpdateTicketRequest,
  UpdateTicketStatusRequest,
} from "../types/api";

export interface ListTicketsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listTickets(params: ListTicketsParams = {}): Promise<PageResponse<TicketDto>> {
  return unwrap(apiClient.get("/api/v1/tickets", { params }));
}

export function getTicket(ticketId: string): Promise<TicketDto> {
  return unwrap(apiClient.get(`/api/v1/tickets/${ticketId}`));
}

export function createTicket(request: CreateTicketRequest): Promise<TicketDto> {
  return unwrap(apiClient.post("/api/v1/tickets", request));
}

export function updateTicket(ticketId: string, request: UpdateTicketRequest): Promise<TicketDto> {
  return unwrap(apiClient.put(`/api/v1/tickets/${ticketId}`, request));
}

export function updateTicketStatus(ticketId: string, request: UpdateTicketStatusRequest): Promise<TicketDto> {
  return unwrap(apiClient.patch(`/api/v1/tickets/${ticketId}/status`, request));
}

export function deleteTicket(ticketId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/tickets/${ticketId}`));
}

export function assignTicketOwner(ticketId: string, request: AssignOwnerRequest): Promise<TicketDto> {
  return unwrap(apiClient.patch(`/api/v1/tickets/${ticketId}/owner`, request));
}
