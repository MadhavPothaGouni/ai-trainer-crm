import { apiClient, unwrap } from "../lib/apiClient";
import type { ApplyMacroRequest, CreateMacroRequest, MacroDto, PageResponse, TicketDto, UpdateMacroRequest } from "../types/api";

export interface ListMacrosParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listMacros(params: ListMacrosParams = {}): Promise<PageResponse<MacroDto>> {
  return unwrap(apiClient.get("/api/v1/macros", { params }));
}

/** The unpaginated active catalog - used by the "apply a macro" picker on a ticket. See MacroService#listActive's javadoc. */
export function listActiveMacros(): Promise<MacroDto[]> {
  return unwrap(apiClient.get("/api/v1/macros/active"));
}

export function getMacro(macroId: string): Promise<MacroDto> {
  return unwrap(apiClient.get(`/api/v1/macros/${macroId}`));
}

export function createMacro(request: CreateMacroRequest): Promise<MacroDto> {
  return unwrap(apiClient.post("/api/v1/macros", request));
}

export function updateMacro(macroId: string, request: UpdateMacroRequest): Promise<MacroDto> {
  return unwrap(apiClient.put(`/api/v1/macros/${macroId}`, request));
}

export function deleteMacro(macroId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/macros/${macroId}`));
}

/** Mutates a real Ticket via TicketService#update/#updateStatus under the hood - see MacroService#apply's javadoc. Returns the updated ticket, not the macro. */
export function applyMacro(macroId: string, request: ApplyMacroRequest): Promise<TicketDto> {
  return unwrap(apiClient.patch(`/api/v1/macros/${macroId}/apply`, request));
}
