// Mirrors the backend's response envelopes exactly (see
// backend/crm-platform/.../common/dto/{ApiResponse,ErrorResponse,PageResponse}.java).
// Keeping these in one file makes it obvious when the frontend's assumptions
// about the wire format drift from what the backend actually sends.

export interface ApiResponse<T> {
  success: boolean;
  data: T | null;
  message: string | null;
  timestamp: string;
}

export interface FieldError {
  field: string;
  message: string;
  rejectedValue: unknown;
}

export interface ApiErrorBody {
  success: false;
  errorCode: string;
  message: string;
  status: number;
  path: string;
  timestamp: string;
  fieldErrors: FieldError[] | null;
  traceId: string;
}

export interface PageResponse<T> {
  content: T[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

// ---- Auth ----

export interface RegisterRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  organizationName?: string | null;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
}

export interface VerifyEmailRequest {
  token: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
  userId: string;
  email: string;
  fullName: string;
}

// ---- Organization ----

export interface OrganizationDto {
  id: string;
  name: string;
  slug: string;
  defaultCurrency: string;
  timezone: string;
  fiscalYearStartMonth: number;
}

export interface UpdateOrganizationRequest {
  name: string;
  defaultCurrency?: string;
  timezone?: string;
  fiscalYearStartMonth: number;
}

// ---- User ----

export type UserStatus = "PENDING_VERIFICATION" | "ACTIVE" | "SUSPENDED" | "DEACTIVATED";

export interface UserDto {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  fullName: string;
  phone: string | null;
  avatarUrl: string | null;
  timezone: string | null;
  locale: string | null;
  status: UserStatus;
  emailVerified: boolean;
  mfaEnabled: boolean;
  teamId: string | null;
  managerId: string | null;
  roles: string[];
  lastLoginAt: string | null;
  createdAt: string;
}

export interface UpdateProfileRequest {
  firstName: string;
  lastName: string;
  phone?: string;
  timezone?: string;
  locale?: string;
}

export interface CreateUserRequest {
  email: string;
  firstName: string;
  lastName: string;
  roleIds?: string[] | null;
}

export interface UpdateUserRolesRequest {
  roleIds: string[];
}

export interface UpdateUserStatusRequest {
  status: UserStatus;
}

/** teamId is deliberately optional/nullable, not required - null unassigns rather than being rejected as missing input. See UpdateUserTeamRequest's backend javadoc. */
export interface UpdateUserTeamRequest {
  teamId?: string | null;
}

// ---- Organization: Team ----
//
// Team/teams.team_id existed since the platform's very first migration
// purely so ScopeAuthorizationService had something to resolve TEAM/
// DEPARTMENT-scope visibility against - there was no management API for a
// long time, so in practice no user ever had a team. TeamController (CRUD)
// and PATCH /users/{id}/team (assignment, above) closed that gap - see
// backend/crm-platform/README.md's module layout for `organization`/`user`.

export interface TeamDto {
  id: string;
  name: string;
  department: string | null;
  leadUserId: string | null;
  /** Optional link into region/'s org-chart tree - see backend/crm-platform/README.md's module layout for `region`. */
  regionId: string | null;
}

export interface CreateTeamRequest {
  name: string;
  department?: string | null;
  leadUserId?: string | null;
  regionId?: string | null;
}

export type UpdateTeamRequest = CreateTeamRequest;

// ---- Role / Permission ----

export interface PermissionDto {
  id: string;
  resource: string;
  action: string;
  scope: string;
  description: string;
  authorityName: string;
}

export interface RoleDto {
  id: string;
  name: string;
  description: string | null;
  systemRole: boolean;
  permissions: PermissionDto[];
}

export interface CreateRoleRequest {
  name: string;
  description?: string;
  permissionIds?: string[] | null;
}

export interface UpdateRoleRequest {
  name: string;
  description?: string;
  permissionIds?: string[] | null;
}

// ---- CRM: Account ----

export interface AccountDto {
  id: string;
  name: string;
  industry: string | null;
  website: string | null;
  phone: string | null;
  billingStreet: string | null;
  billingCity: string | null;
  billingState: string | null;
  billingPostalCode: string | null;
  billingCountry: string | null;
  annualRevenue: number | null;
  employeeCount: number | null;
  description: string | null;
  ownerId: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateAccountRequest {
  name: string;
  industry?: string | null;
  website?: string | null;
  phone?: string | null;
  billingStreet?: string | null;
  billingCity?: string | null;
  billingState?: string | null;
  billingPostalCode?: string | null;
  billingCountry?: string | null;
  annualRevenue?: number | null;
  employeeCount?: number | null;
  description?: string | null;
  ownerId?: string | null;
}

export type UpdateAccountRequest = CreateAccountRequest;

// ---- CRM: Contact ----

export interface ContactDto {
  id: string;
  firstName: string;
  lastName: string;
  fullName: string;
  email: string | null;
  phone: string | null;
  title: string | null;
  description: string | null;
  accountId: string | null;
  ownerId: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateContactRequest {
  firstName: string;
  lastName: string;
  email?: string | null;
  phone?: string | null;
  title?: string | null;
  description?: string | null;
  accountId?: string | null;
  ownerId?: string | null;
}

export type UpdateContactRequest = CreateContactRequest;

// ---- CRM: Opportunity ----

export type OpportunityStage =
  | "PROSPECTING"
  | "QUALIFICATION"
  | "PROPOSAL"
  | "NEGOTIATION"
  | "CLOSED_WON"
  | "CLOSED_LOST";

export const OPPORTUNITY_STAGES: OpportunityStage[] = [
  "PROSPECTING",
  "QUALIFICATION",
  "PROPOSAL",
  "NEGOTIATION",
  "CLOSED_WON",
  "CLOSED_LOST",
];

export interface OpportunityDto {
  id: string;
  accountId: string;
  primaryContactId: string | null;
  name: string;
  stage: OpportunityStage;
  amount: number | null;
  currency: string | null;
  expectedCloseDate: string | null;
  actualCloseDate: string | null;
  description: string | null;
  ownerId: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateOpportunityRequest {
  accountId: string;
  primaryContactId?: string | null;
  name: string;
  amount?: number | null;
  currency?: string | null;
  expectedCloseDate?: string | null;
  description?: string | null;
  ownerId?: string | null;
}

export type UpdateOpportunityRequest = CreateOpportunityRequest;

export interface UpdateOpportunityStageRequest {
  stage: OpportunityStage;
}

// ---- CRM: Lead ----

export type LeadStatus = "NEW" | "CONTACTED" | "QUALIFIED" | "UNQUALIFIED" | "CONVERTED";

export const LEAD_STATUSES: LeadStatus[] = ["NEW", "CONTACTED", "QUALIFIED", "UNQUALIFIED", "CONVERTED"];

export type LeadSource = "WEBSITE" | "REFERRAL" | "COLD_CALL" | "EVENT" | "ADVERTISEMENT" | "OTHER";

export const LEAD_SOURCES: LeadSource[] = ["WEBSITE", "REFERRAL", "COLD_CALL", "EVENT", "ADVERTISEMENT", "OTHER"];

export interface LeadDto {
  id: string;
  firstName: string;
  lastName: string;
  fullName: string;
  email: string | null;
  phone: string | null;
  companyName: string | null;
  title: string | null;
  status: LeadStatus;
  source: LeadSource;
  description: string | null;
  ownerId: string;
  convertedAccountId: string | null;
  convertedContactId: string | null;
  convertedOpportunityId: string | null;
  convertedAt: string | null;
  /** Materialized by the backend's LeadScoringEngine - see the Lead Scoring section below. Never edited directly. */
  score: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateLeadRequest {
  firstName: string;
  lastName: string;
  email?: string | null;
  phone?: string | null;
  companyName?: string | null;
  title?: string | null;
  source: LeadSource;
  description?: string | null;
  ownerId?: string | null;
}

export type UpdateLeadRequest = CreateLeadRequest;

export interface UpdateLeadStatusRequest {
  status: LeadStatus;
}

export interface ConvertLeadRequest {
  existingAccountId?: string | null;
  newAccountName?: string | null;
  createOpportunity?: boolean | null;
  opportunityName?: string | null;
  opportunityAmount?: number | null;
  opportunityExpectedCloseDate?: string | null;
}

export interface LeadConversionResult {
  leadId: string;
  accountId: string;
  contactId: string;
  opportunityId: string | null;
}

// ---- CRM: Ticket ----
// The resource that had a full permission set seeded (V2__seed_permission_catalog.sql) but no
// module anywhere until this session found the gap while building bulk import/export - see
// backend/crm-platform/README.md's module layout for `ticket`. Status is a free (non-linear)
// transition, unlike Lead's one-way CONVERTED - reopening a resolved ticket is normal, so there's
// no REASSIGNABLE_STATUSES-style filtered subset the way LeadDetailPage needs for CONVERTED.

export type TicketStatus = "OPEN" | "IN_PROGRESS" | "RESOLVED" | "CLOSED";

export const TICKET_STATUSES: TicketStatus[] = ["OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED"];

export type TicketPriority = "LOW" | "MEDIUM" | "HIGH" | "URGENT";

export const TICKET_PRIORITIES: TicketPriority[] = ["LOW", "MEDIUM", "HIGH", "URGENT"];

export interface TicketDto {
  id: string;
  accountId: string | null;
  contactId: string | null;
  subject: string;
  description: string | null;
  status: TicketStatus;
  priority: TicketPriority;
  ownerId: string;
  resolvedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateTicketRequest {
  subject: string;
  description?: string | null;
  priority: TicketPriority;
  accountId?: string | null;
  contactId?: string | null;
  ownerId?: string | null;
}

export interface UpdateTicketRequest {
  subject: string;
  description?: string | null;
  priority: TicketPriority;
  accountId?: string | null;
  contactId?: string | null;
}

export interface UpdateTicketStatusRequest {
  status: TicketStatus;
}

// ---- CRM: Activity ----

export type ActivityType = "CALL" | "EMAIL" | "MEETING" | "TASK" | "NOTE";

export const ACTIVITY_TYPES: ActivityType[] = ["CALL", "EMAIL", "MEETING", "TASK", "NOTE"];

export type ActivityStatus = "OPEN" | "COMPLETED";

export type ActivityPriority = "LOW" | "MEDIUM" | "HIGH";

export const ACTIVITY_PRIORITIES: ActivityPriority[] = ["LOW", "MEDIUM", "HIGH"];

export type RelatedToType = "ACCOUNT" | "CONTACT" | "OPPORTUNITY" | "LEAD";

export interface ActivityDto {
  id: string;
  type: ActivityType;
  subject: string;
  description: string | null;
  status: ActivityStatus;
  priority: ActivityPriority | null;
  dueAt: string | null;
  completedAt: string | null;
  relatedToType: RelatedToType;
  relatedToId: string;
  ownerId: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateActivityRequest {
  type: ActivityType;
  subject: string;
  description?: string | null;
  priority?: ActivityPriority | null;
  dueAt?: string | null;
  relatedToType: RelatedToType;
  relatedToId: string;
  ownerId?: string | null;
}

export type UpdateActivityRequest = CreateActivityRequest;

export interface UpdateActivityStatusRequest {
  status: ActivityStatus;
}

// ---- CRM: Email & Calendar ----
//
// EMAIL_MESSAGE/CALENDAR_EVENT are a genuinely new permission-catalog
// resource pair (seeded in V15, not a pre-existing gap like Ticket) that
// coexist with Activity's own EMAIL/MEETING types rather than replace them -
// see backend/crm-platform/README.md's module layout for `email`/`calendar`.
// CrmRecordType is the same idea as RelatedToType above but with TICKET
// added, since both new modules can be logged against a Ticket and
// Activity's RelatedToType predates the Ticket module entirely.

export type CrmRecordType = "ACCOUNT" | "CONTACT" | "OPPORTUNITY" | "LEAD" | "TICKET";

export const CRM_RECORD_TYPES: CrmRecordType[] = ["ACCOUNT", "CONTACT", "OPPORTUNITY", "LEAD", "TICKET"];

export type EmailDirection = "INBOUND" | "OUTBOUND";

export const EMAIL_DIRECTIONS: EmailDirection[] = ["INBOUND", "OUTBOUND"];

export interface EmailMessageDto {
  id: string;
  direction: EmailDirection;
  subject: string;
  body: string | null;
  fromAddress: string;
  toAddresses: string;
  ccAddresses: string | null;
  relatedToType: CrmRecordType;
  relatedToId: string;
  sentAt: string;
  ownerId: string;
  createdAt: string;
  updatedAt: string;
}

/** Backs both create (POST) and update (PUT) on the backend - see LogEmailRequest's javadoc. */
export interface LogEmailRequest {
  direction: EmailDirection;
  subject: string;
  body?: string | null;
  fromAddress: string;
  toAddresses: string;
  ccAddresses?: string | null;
  relatedToType: CrmRecordType;
  relatedToId: string;
  sentAt?: string | null;
  ownerId?: string | null;
}

export type CalendarAttendeeResponseStatus = "NEEDS_ACTION" | "ACCEPTED" | "DECLINED" | "TENTATIVE";

export const CALENDAR_ATTENDEE_RESPONSE_STATUSES: CalendarAttendeeResponseStatus[] = [
  "NEEDS_ACTION",
  "ACCEPTED",
  "DECLINED",
  "TENTATIVE",
];

export interface CalendarEventAttendeeDto {
  id: string;
  userId: string | null;
  externalEmail: string | null;
  responseStatus: CalendarAttendeeResponseStatus;
}

export interface CalendarEventDto {
  id: string;
  title: string;
  description: string | null;
  location: string | null;
  startAt: string;
  endAt: string;
  allDay: boolean;
  relatedToType: CrmRecordType | null;
  relatedToId: string | null;
  ownerId: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCalendarEventRequest {
  title: string;
  description?: string | null;
  location?: string | null;
  startAt: string;
  endAt: string;
  allDay: boolean;
  relatedToType?: CrmRecordType | null;
  relatedToId?: string | null;
  ownerId?: string | null;
}

export type UpdateCalendarEventRequest = Omit<CreateCalendarEventRequest, "ownerId">;

/** Exactly one of userId/externalEmail - see AddAttendeeRequest's javadoc. */
export interface AddAttendeeRequest {
  userId?: string | null;
  externalEmail?: string | null;
}

export interface UpdateAttendeeResponseRequest {
  responseStatus: CalendarAttendeeResponseStatus;
}

// ---- Sales: Product ----

export interface ProductDto {
  id: string;
  name: string;
  sku: string | null;
  description: string | null;
  unitPrice: number;
  currency: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateProductRequest {
  name: string;
  sku?: string | null;
  description?: string | null;
  unitPrice: number;
  currency?: string | null;
}

export interface UpdateProductRequest extends CreateProductRequest {
  active: boolean;
}

// ---- Sales: Quote ----

export type QuoteStatus = "DRAFT" | "SENT" | "ACCEPTED" | "REJECTED";

export const QUOTE_STATUSES: QuoteStatus[] = ["DRAFT", "SENT", "ACCEPTED", "REJECTED"];

export interface QuoteLineItemDto {
  id: string;
  productId: string | null;
  description: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

export interface QuoteDto {
  id: string;
  opportunityId: string;
  name: string;
  status: QuoteStatus;
  currency: string | null;
  validUntil: string | null;
  subtotal: number;
  discountAmount: number;
  taxAmount: number;
  totalAmount: number;
  ownerId: string;
  createdAt: string;
  updatedAt: string;
  lineItems: QuoteLineItemDto[];
}

export interface CreateQuoteRequest {
  opportunityId: string;
  name: string;
  currency?: string | null;
  validUntil?: string | null;
  discountAmount?: number | null;
  taxAmount?: number | null;
  ownerId?: string | null;
}

export interface UpdateQuoteRequest {
  name: string;
  currency?: string | null;
  validUntil?: string | null;
  discountAmount?: number | null;
  taxAmount?: number | null;
}

export interface UpdateQuoteStatusRequest {
  status: QuoteStatus;
}

export interface CreateQuoteLineItemRequest {
  productId?: string | null;
  description: string;
  quantity: number;
  unitPrice: number;
}

export type UpdateQuoteLineItemRequest = CreateQuoteLineItemRequest;

// ---- Sales: Order ----

export type OrderStatus = "DRAFT" | "CONFIRMED" | "FULFILLED" | "CANCELLED";

export const ORDER_STATUSES: OrderStatus[] = ["DRAFT", "CONFIRMED", "FULFILLED", "CANCELLED"];

export interface OrderLineItemDto {
  id: string;
  productId: string | null;
  description: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

export interface OrderDto {
  id: string;
  quoteId: string | null;
  orderNumber: string;
  status: OrderStatus;
  currency: string | null;
  subtotal: number;
  discountAmount: number;
  taxAmount: number;
  totalAmount: number;
  createdAt: string;
  updatedAt: string;
  lineItems: OrderLineItemDto[];
}

export interface CreateOrderRequest {
  orderNumber: string;
  currency?: string | null;
  discountAmount?: number | null;
  taxAmount?: number | null;
}

export type UpdateOrderRequest = CreateOrderRequest;

export interface CreateOrderFromQuoteRequest {
  orderNumber: string;
}

export interface UpdateOrderStatusRequest {
  status: OrderStatus;
}

export interface CreateOrderLineItemRequest {
  productId?: string | null;
  description: string;
  quantity: number;
  unitPrice: number;
}

export type UpdateOrderLineItemRequest = CreateOrderLineItemRequest;

// ---- Finance: Invoice ----

export type InvoiceStatus = "DRAFT" | "SENT" | "PAID" | "OVERDUE" | "VOID";

export const INVOICE_STATUSES: InvoiceStatus[] = ["DRAFT", "SENT", "PAID", "OVERDUE", "VOID"];

export interface InvoiceLineItemDto {
  id: string;
  productId: string | null;
  description: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

export interface InvoiceDto {
  id: string;
  orderId: string;
  invoiceNumber: string;
  status: InvoiceStatus;
  currency: string | null;
  issueDate: string;
  dueDate: string;
  subtotal: number;
  discountAmount: number;
  taxAmount: number;
  totalAmount: number;
  amountPaid: number;
  balanceDue: number;
  createdAt: string;
  updatedAt: string;
  lineItems: InvoiceLineItemDto[];
}

export interface GenerateInvoiceRequest {
  invoiceNumber: string;
  issueDate?: string | null;
  dueDate?: string | null;
}

export interface UpdateInvoiceRequest {
  invoiceNumber: string;
  currency?: string | null;
  issueDate: string;
  dueDate: string;
  discountAmount?: number | null;
  taxAmount?: number | null;
}

export interface CreateInvoiceLineItemRequest {
  productId?: string | null;
  description: string;
  quantity: number;
  unitPrice: number;
}

export type UpdateInvoiceLineItemRequest = CreateInvoiceLineItemRequest;

// ---- Finance: Payment ----

export type PaymentMethod = "CREDIT_CARD" | "BANK_TRANSFER" | "CASH" | "CHECK" | "OTHER";

export const PAYMENT_METHODS: PaymentMethod[] = ["CREDIT_CARD", "BANK_TRANSFER", "CASH", "CHECK", "OTHER"];

export interface PaymentDto {
  id: string;
  invoiceId: string;
  amount: number;
  method: PaymentMethod;
  reference: string | null;
  paidAt: string;
  notes: string | null;
  createdAt: string;
}

export interface CreatePaymentRequest {
  amount: number;
  method: PaymentMethod;
  reference?: string | null;
  paidAt?: string | null;
  notes?: string | null;
}

// ---- Marketing: Campaign ----

export type CampaignType = "EMAIL" | "WEBINAR" | "EVENT" | "SOCIAL_MEDIA" | "DIRECT_MAIL" | "OTHER";

export const CAMPAIGN_TYPES: CampaignType[] = ["EMAIL", "WEBINAR", "EVENT", "SOCIAL_MEDIA", "DIRECT_MAIL", "OTHER"];

export type CampaignStatus = "PLANNED" | "ACTIVE" | "COMPLETED" | "CANCELLED";

export const CAMPAIGN_STATUSES: CampaignStatus[] = ["PLANNED", "ACTIVE", "COMPLETED", "CANCELLED"];

export type CampaignMemberStatus = "ADDED" | "SENT" | "OPENED" | "CLICKED" | "RESPONDED" | "CONVERTED";

export const CAMPAIGN_MEMBER_STATUSES: CampaignMemberStatus[] = ["ADDED", "SENT", "OPENED", "CLICKED", "RESPONDED", "CONVERTED"];

export interface CampaignDto {
  id: string;
  name: string;
  type: CampaignType;
  status: CampaignStatus;
  startDate: string | null;
  endDate: string | null;
  budget: number | null;
  actualCost: number | null;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCampaignRequest {
  name: string;
  type: CampaignType;
  startDate?: string | null;
  endDate?: string | null;
  budget?: number | null;
  actualCost?: number | null;
  description?: string | null;
}

export type UpdateCampaignRequest = CreateCampaignRequest;

export interface UpdateCampaignStatusRequest {
  status: CampaignStatus;
}

export interface CampaignMemberDto {
  id: string;
  leadId: string | null;
  contactId: string | null;
  status: CampaignMemberStatus;
  respondedAt: string | null;
  createdAt: string;
}

export interface AddCampaignMemberRequest {
  leadId?: string | null;
  contactId?: string | null;
}

export interface UpdateCampaignMemberStatusRequest {
  status: CampaignMemberStatus;
}

export interface CampaignStatsDto {
  campaignId: string;
  totalMembers: number;
  countsByStatus: Record<CampaignMemberStatus, number>;
}

// ---- Support: Knowledge Article ----

export type KnowledgeArticleStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";

export const KNOWLEDGE_ARTICLE_STATUSES: KnowledgeArticleStatus[] = ["DRAFT", "PUBLISHED", "ARCHIVED"];

export interface KnowledgeArticleDto {
  id: string;
  title: string;
  slug: string;
  category: string | null;
  /** Only populated on the single-article GET - list rows use the summary shape and omit it. */
  content?: string;
  status: KnowledgeArticleStatus;
  viewCount: number;
  publishedAt: string | null;
  tags: string[];
  createdAt: string;
  updatedAt: string;
}

export interface CreateKnowledgeArticleRequest {
  title: string;
  category?: string | null;
  content: string;
  tags?: string[];
}

export type UpdateKnowledgeArticleRequest = CreateKnowledgeArticleRequest;

// ---- Platform extensibility: Custom Objects/Fields ----
// CUSTOM_FIELD/CUSTOM_OBJECT are ORGANIZATION-scope-only permissions (see
// V10's migration comment on the backend) - there's no OWN/TEAM/DEPARTMENT
// variant, so unlike Campaign/KnowledgeArticle these pages don't need to
// account for a caller lacking access at some scopes but not others.

export interface CustomObjectDto {
  id: string;
  apiName: string;
  label: string;
  pluralLabel: string;
  description: string | null;
  active: boolean;
  createdAt: string;
}

export interface CreateCustomObjectRequest {
  apiName: string;
  label: string;
  pluralLabel: string;
  description?: string | null;
}

export interface UpdateCustomObjectRequest {
  label: string;
  pluralLabel: string;
  description?: string | null;
  active: boolean;
}

export interface CustomObjectRecordDto {
  id: string;
  customObjectId: string;
  name: string;
  createdAt: string;
  updatedAt: string;
  values: CustomFieldValueDto[];
}

export interface CreateCustomObjectRecordRequest {
  name: string;
  values?: Record<string, string>;
}

export interface UpdateCustomObjectRecordRequest {
  name: string;
}

/** A fixed allow-list on the backend (CustomField.StandardEntityType) - a custom field can attach to one of these, or to a CustomObject, never both. */
export type StandardEntityType = "ACCOUNT" | "CONTACT" | "LEAD" | "OPPORTUNITY" | "CAMPAIGN";

export const STANDARD_ENTITY_TYPES: StandardEntityType[] = ["ACCOUNT", "CONTACT", "LEAD", "OPPORTUNITY", "CAMPAIGN"];

export type CustomFieldType = "TEXT" | "TEXT_AREA" | "NUMBER" | "DATE" | "BOOLEAN" | "PICKLIST";

export const CUSTOM_FIELD_TYPES: CustomFieldType[] = ["TEXT", "TEXT_AREA", "NUMBER", "DATE", "BOOLEAN", "PICKLIST"];

export interface CustomFieldDto {
  id: string;
  standardEntityType: StandardEntityType | null;
  customObjectId: string | null;
  apiName: string;
  label: string;
  fieldType: CustomFieldType;
  required: boolean;
  displayOrder: number;
  active: boolean;
  picklistValues: string[];
}

export interface CreateCustomFieldRequest {
  standardEntityType?: StandardEntityType | null;
  customObjectId?: string | null;
  apiName: string;
  label: string;
  fieldType: CustomFieldType;
  required?: boolean;
  displayOrder?: number;
  picklistValues?: string[];
}

export interface UpdateCustomFieldRequest {
  label: string;
  required: boolean;
  displayOrder: number;
  active: boolean;
  picklistValues?: string[];
}

/** One field definition joined with its (possibly absent) value on the record just fetched - what CustomFieldValueForm renders one row from. */
export interface CustomFieldValueDto {
  customFieldId: string;
  apiName: string;
  label: string;
  fieldType: CustomFieldType;
  required: boolean;
  value: string | null;
}

export interface SetCustomFieldValuesRequest {
  values: Record<string, string | null>;
}

// ---- Automation: workflows ----
// WORKFLOW is owner-scoped (OWN/TEAM/ORGANIZATION, no DEPARTMENT) - unlike
// Campaign/KnowledgeArticle/CustomField/CustomObject above, a workflow
// belongs to whoever created it, same shape as Account/Contact/Lead/
// Opportunity (see the backend's Workflow entity javadoc).

export type WorkflowTriggerResource = "LEAD" | "CONTACT" | "ACCOUNT" | "OPPORTUNITY";

export const WORKFLOW_TRIGGER_RESOURCES: WorkflowTriggerResource[] = ["LEAD", "CONTACT", "ACCOUNT", "OPPORTUNITY"];

export type WorkflowTriggerEvent = "CREATED" | "UPDATED" | "DELETED";

export const WORKFLOW_TRIGGER_EVENTS: WorkflowTriggerEvent[] = ["CREATED", "UPDATED", "DELETED"];

export type WorkflowActionType = "CREATE_TASK";

export interface WorkflowDto {
  id: string;
  ownerId: string;
  name: string;
  description: string | null;
  triggerResource: WorkflowTriggerResource;
  triggerEvent: WorkflowTriggerEvent;
  actionType: WorkflowActionType;
  taskSubject: string;
  taskAssigneeUserId: string | null;
  active: boolean;
  runCount: number;
  lastRunAt: string | null;
  createdAt: string;
}

export interface CreateWorkflowRequest {
  name: string;
  description?: string | null;
  triggerResource: WorkflowTriggerResource;
  triggerEvent: WorkflowTriggerEvent;
  taskSubject: string;
  taskAssigneeUserId?: string | null;
  ownerId?: string | null;
}

export interface UpdateWorkflowRequest {
  name: string;
  description?: string | null;
  taskSubject: string;
  taskAssigneeUserId?: string | null;
}

export interface SetWorkflowActiveRequest {
  active: boolean;
}

export interface RunWorkflowRequest {
  resourceId: string;
}

export type WorkflowRunStatus = "SUCCEEDED" | "FAILED";

export interface WorkflowRunDto {
  id: string;
  resourceId: string;
  createdActivityId: string | null;
  status: WorkflowRunStatus;
  errorMessage: string | null;
  ranAt: string;
}

// ---- Reporting ----

export interface PipelineStageSummaryDto {
  stage: OpportunityStage;
  opportunityCount: number;
  totalAmount: number;
}

export interface LeadFunnelStageDto {
  status: LeadStatus;
  leadCount: number;
}

export interface RepLeaderboardEntryDto {
  ownerId: string;
  ownerName: string;
  openCount: number;
  openAmount: number;
  wonCount: number;
  wonAmount: number;
  lostCount: number;
}

// ---- Forecasting: pipeline snapshots ----
//
// Daily (org, date, owner, stage) history of the pipeline - see backend/crm-platform/README.md's
// module layout for `forecast`. Entirely read-only from this API: there is no create/update/
// delete request type here, only the two GET response shapes, since the only writer is a
// @Scheduled backend job.

export interface PipelineSnapshotDto {
  id: string;
  snapshotDate: string;
  ownerId: string;
  stage: OpportunityStage;
  dealCount: number;
  totalValue: number;
}

/** One day's totals, already folded across every visible owner/stage by the backend - dealCount/totalValue are the day's grand totals, valueByStage is the same total broken down per OpportunityStage for a stacked view. */
export interface PipelineTrendPointDto {
  date: string;
  dealCount: number;
  totalValue: number;
  valueByStage: Partial<Record<OpportunityStage, number>>;
}

// ---- Dashboards ----
// DASHBOARD is owner-scoped (OWN/TEAM/ORGANIZATION, no DEPARTMENT) like
// Workflow above. A widget's `data` shape depends on its reportType - see
// DashboardWidgetType below - the same discriminated-union-by-tag pattern
// the backend's DashboardWidgetDataDto javadoc describes.

export type DashboardWidgetReportType = "PIPELINE_BY_STAGE" | "LEAD_FUNNEL" | "LEADERBOARD";

export const DASHBOARD_WIDGET_REPORT_TYPES: DashboardWidgetReportType[] = ["PIPELINE_BY_STAGE", "LEAD_FUNNEL", "LEADERBOARD"];

export interface DashboardWidgetDto {
  id: string;
  reportType: DashboardWidgetReportType;
  title: string;
  displayOrder: number;
  width: number;
  height: number;
}

export interface DashboardDto {
  id: string;
  ownerId: string;
  name: string;
  description: string | null;
  isDefault: boolean;
  createdAt: string;
  widgets: DashboardWidgetDto[];
}

export interface CreateDashboardRequest {
  name: string;
  description?: string | null;
  ownerId?: string | null;
}

export type UpdateDashboardRequest = Omit<CreateDashboardRequest, "ownerId">;

export interface CreateDashboardWidgetRequest {
  reportType: DashboardWidgetReportType;
  title?: string;
  displayOrder?: number;
  width?: number;
  height?: number;
}

export interface UpdateDashboardWidgetRequest {
  title?: string;
  displayOrder: number;
  width: number;
  height: number;
}

/** `data`'s shape depends on `reportType` - PipelineStageSummaryDto[] for PIPELINE_BY_STAGE, LeadFunnelStageDto[] for LEAD_FUNNEL, RepLeaderboardEntryDto[] for LEADERBOARD. Narrow with a switch on reportType before reading it, same as the pages do. */
export interface DashboardWidgetDataDto {
  id: string;
  reportType: DashboardWidgetReportType;
  title: string;
  displayOrder: number;
  width: number;
  height: number;
  data: PipelineStageSummaryDto[] | LeadFunnelStageDto[] | RepLeaderboardEntryDto[];
}

export interface DashboardDataDto {
  dashboardId: string;
  name: string;
  widgets: DashboardWidgetDataDto[];
}

// ---- Bulk CSV import/export (Account/Contact/Lead) ----
// LEAD/CONTACT/ACCOUNT/OPPORTUNITY/ACTIVITY/QUOTE/TICKET all got IMPORT and
// EXPORT permissions seeded alongside their other CRUD actions, but only
// Account/Contact/Lead have a real implementation so far - see
// ImportExportService's javadoc on the backend. IMPORT/EXPORT aren't in
// MEMBER's default permission set (RoleService#isCoreCrmResource only
// grants CREATE/READ/UPDATE), so this lives in AppLayout's admin-only nav
// group, same reasoning as Reports/Workflows/Dashboards.

export type ImportEntityType = "ACCOUNT" | "CONTACT" | "LEAD" | "TICKET";

export const IMPORT_ENTITY_TYPES: ImportEntityType[] = ["ACCOUNT", "CONTACT", "LEAD", "TICKET"];

export type ImportJobStatus = "COMPLETED" | "FAILED";

export interface ImportRowErrorDto {
  rowNumber: number;
  message: string;
}

export interface ImportJobDto {
  id: string;
  entityType: ImportEntityType;
  status: ImportJobStatus;
  totalRows: number;
  successCount: number;
  errorCount: number;
  createdAt: string;
  errors: ImportRowErrorDto[];
}

// ---- Platform: API keys ----

export interface ApiKeyDto {
  id: string;
  name: string;
  keyPrefix: string;
  createdByUserId: string;
  lastUsedAt: string | null;
  expiresAt: string | null;
  revokedAt: string | null;
  createdAt: string;
  /** Only ever populated on the response to POST /api/v1/api-keys - see the backend ApiKeyDto's javadoc. */
  rawKey?: string | null;
}

export interface CreateApiKeyRequest {
  name: string;
  expiresAt?: string | null;
}

// ---- Platform: webhooks ----

export interface WebhookSubscriptionDto {
  id: string;
  url: string;
  eventType: string | null;
  secret: string;
  active: boolean;
  createdByUserId: string;
  lastTriggeredAt: string | null;
  lastResponseStatus: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateWebhookSubscriptionRequest {
  url: string;
  eventType?: string | null;
}

export interface UpdateWebhookSubscriptionRequest {
  url: string;
  eventType?: string | null;
  active: boolean;
}

// ---- CRM: shared ----

export interface AssignOwnerRequest {
  ownerId: string;
}

// ---- Notification ----
//
// A fourth, simpler access pattern than everything above - see
// backend/crm-platform/README.md's module layout for `notification` and
// Notification's javadoc. There is no owner/scope concept here at all: the
// backend hard-scopes every read/write to "recipientUserId == the caller,"
// so NotificationDto never even includes a recipientUserId field - it's
// always implicitly "mine," the same way a fetched UserDto from /users/me
// doesn't need to say whose profile it is.

export type NotificationType = "ASSIGNMENT" | "MENTION" | "REMINDER" | "GENERAL";

export const NOTIFICATION_TYPES: NotificationType[] = ["ASSIGNMENT", "MENTION", "REMINDER", "GENERAL"];

export interface NotificationDto {
  id: string;
  senderUserId: string | null;
  type: NotificationType;
  title: string;
  body: string | null;
  relatedToType: CrmRecordType | null;
  relatedToId: string | null;
  readAt: string | null;
  createdAt: string;
}

/** Sends a notification to a teammate - relatedToType/relatedToId are optional, both-null-or-both-set, same shape as CreateCalendarEventRequest's. */
export interface CreateNotificationRequest {
  recipientUserId: string;
  type: NotificationType;
  title: string;
  body?: string | null;
  relatedToType?: CrmRecordType | null;
  relatedToId?: string | null;
}

export interface UnreadCountResponse {
  unreadCount: number;
}

// ---- Attachment ----
//
// Back to owner-scoped (see backend/crm-platform/README.md's module layout for `attachment`) -
// unlike Notification, an uploaded file is a normal team-visible CRM record. storageKey is
// deliberately never part of this type - the backend's AttachmentDto never serializes it either;
// the only way back to the bytes is GET /attachments/{id}/download.

export interface AttachmentDto {
  id: string;
  fileName: string;
  contentType: string | null;
  fileSizeBytes: number;
  description: string | null;
  relatedToType: CrmRecordType;
  relatedToId: string;
  ownerId: string;
  createdAt: string;
  updatedAt: string;
}

/** Metadata-only - matches UpdateAttachmentRequest on the backend. There's no CreateAttachmentRequest type; upload goes through FormData directly (see api/attachments.ts's uploadAttachment), not a JSON body. */
export interface UpdateAttachmentRequest {
  fileName: string;
  description?: string | null;
  relatedToType: CrmRecordType;
  relatedToId: string;
}

// ---- Approval Workflow ----
//
// Named, ordered multi-step sign-off chains against a Quote/Order/Opportunity - see
// backend/crm-platform/README.md's module layout for `approval` and ApprovalRequestService's
// javadoc for the "fifth resource-access shape" (a named approver can always read/act on a
// request they're on, regardless of scope). ApprovalRelatedToType is a separate type from
// CrmRecordType above, not a subset reused by name - only Quote/Order/Opportunity carry an
// approval-worthy dollar figure, unlike Account/Contact/Lead/Ticket.

export type ApprovalRelatedToType = "QUOTE" | "ORDER" | "OPPORTUNITY";

export const APPROVAL_RELATED_TO_TYPES: ApprovalRelatedToType[] = ["QUOTE", "ORDER", "OPPORTUNITY"];

export type ApprovalRequestStatus = "PENDING" | "APPROVED" | "REJECTED" | "CANCELLED";

export type ApprovalStepStatus = "PENDING" | "APPROVED" | "REJECTED";

export interface ApprovalStepDto {
  id: string;
  stepNumber: number;
  approverUserId: string;
  status: ApprovalStepStatus;
  comment: string | null;
  decidedAt: string | null;
  /** True only when this step is both PENDING and the chain's current step - see ApprovalStepDto's javadoc on the backend. */
  actionable: boolean;
}

export interface ApprovalRequestDto {
  id: string;
  relatedToType: ApprovalRelatedToType;
  relatedToId: string;
  requestedByUserId: string;
  title: string;
  status: ApprovalRequestStatus;
  currentStepNumber: number;
  decidedAt: string | null;
  createdAt: string;
  /** Always empty on list rows (GET /approval-requests) - only GET /approval-requests/{id} populates this, same "don't eagerly load the collection on every list row" reasoning QuoteDto's lineItems doesn't apply to its own list view either. */
  steps: ApprovalStepDto[];
}

/** approverUserIds is ordered - index 0 is step 1, and so on. See CreateApprovalRequestRequest's javadoc on the backend for why a duplicated user id is rejected. */
export interface CreateApprovalRequestRequest {
  relatedToType: ApprovalRelatedToType;
  relatedToId: string;
  title: string;
  approverUserIds: string[];
}

/** Backs both POST .../approve and POST .../reject - comment is optional either way. */
export interface DecideStepRequest {
  comment?: string | null;
}

/** One row in the "my approvals" inbox (GET /approval-requests/my-approvals) - a step assigned to the caller, flattened with just enough of its parent request's context to render without a second round-trip per row. */
export interface ApprovalTaskDto {
  stepId: string;
  approvalRequestId: string;
  requestTitle: string;
  relatedToType: ApprovalRelatedToType;
  relatedToId: string;
  requestedByUserId: string;
  stepNumber: number;
  actionable: boolean;
  createdAt: string;
}

// ---- SLA & Escalation ----
//
// Per-Ticket-priority response/resolution deadlines, plus automatic escalation - see
// backend/crm-platform/README.md's module layout for `sla`. SlaPolicyDto reuses TicketPriority
// directly (same choice SlaPolicy makes on the backend against Ticket.Priority) rather than a
// parallel type, since these really are the same four values, not a different set the way
// ApprovalRelatedToType deliberately is.

export interface SlaPolicyDto {
  id: string;
  name: string;
  priority: TicketPriority;
  responseTargetMinutes: number;
  resolutionTargetMinutes: number;
  escalateToUserId: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateSlaPolicyRequest {
  name: string;
  priority: TicketPriority;
  responseTargetMinutes: number;
  resolutionTargetMinutes: number;
  escalateToUserId?: string | null;
}

/** priority is deliberately absent here - not editable after creation, see UpdateSlaPolicyRequest's javadoc on the backend. */
export interface UpdateSlaPolicyRequest {
  name: string;
  responseTargetMinutes: number;
  resolutionTargetMinutes: number;
  escalateToUserId?: string | null;
  active: boolean;
}

/** The response shape of GET /ticket-sla/{ticketId} when a policy IS tracking this ticket - the endpoint returns `data: null` instead when no active policy covers the ticket's priority, so callers should treat this as `TicketSlaStatusDto | null`, not assume it's always present. */
export interface TicketSlaStatusDto {
  ticketId: string;
  slaPolicyId: string;
  responseDueAt: string;
  resolutionDueAt: string;
  responseBreached: boolean;
  resolutionBreached: boolean;
  responseBreachedAt: string | null;
  resolutionBreachedAt: string | null;
  escalated: boolean;
  escalatedAt: string | null;
}

// ---- Territory / Assignment Rules ----
//
// Auto-routes a newly created Lead or Account to an owner - see backend/crm-platform/README.md's
// module layout for `territory`. The actual matching/round-robin runs asynchronously in
// TerritoryAssignmentListener on the backend; there is no endpoint here that triggers it, only
// CRUD for the rules that drive it.

export type TerritoryTargetResource = "LEAD" | "ACCOUNT";
export const TERRITORY_TARGET_RESOURCES: TerritoryTargetResource[] = ["LEAD", "ACCOUNT"];

export type TerritoryMatchField = "SOURCE" | "COMPANY_NAME" | "INDUSTRY" | "BILLING_COUNTRY" | "BILLING_STATE";

/** Which matchField values are valid depends on targetResource - TerritoryRuleService enforces this pairing server-side; these two lists exist so the form can only ever offer a valid combination. */
export const TERRITORY_MATCH_FIELDS_BY_RESOURCE: Record<TerritoryTargetResource, TerritoryMatchField[]> = {
  LEAD: ["SOURCE", "COMPANY_NAME"],
  ACCOUNT: ["INDUSTRY", "BILLING_COUNTRY", "BILLING_STATE"],
};

export type TerritoryMatchOperator = "EQUALS" | "CONTAINS";
export const TERRITORY_MATCH_OPERATORS: TerritoryMatchOperator[] = ["EQUALS", "CONTAINS"];

export interface TerritoryRuleDto {
  id: string;
  name: string;
  targetResource: TerritoryTargetResource;
  matchField: TerritoryMatchField;
  matchOperator: TerritoryMatchOperator;
  matchValue: string;
  priority: number;
  assignToUserId: string | null;
  assignToTeamId: string | null;
  active: boolean;
  matchCount: number;
  lastMatchedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateTerritoryRuleRequest {
  name: string;
  targetResource: TerritoryTargetResource;
  matchField: TerritoryMatchField;
  matchOperator: TerritoryMatchOperator;
  matchValue: string;
  priority: number;
  assignToUserId?: string | null;
  assignToTeamId?: string | null;
}

/** targetResource is deliberately absent - not editable after creation, see UpdateTerritoryRuleRequest's javadoc on the backend. */
export interface UpdateTerritoryRuleRequest {
  name: string;
  matchField: TerritoryMatchField;
  matchOperator: TerritoryMatchOperator;
  matchValue: string;
  priority: number;
  assignToUserId?: string | null;
  assignToTeamId?: string | null;
  active: boolean;
}

// ---- Duplicate Detection / Merge ----
//
// Flags likely-duplicate Lead/Contact/Account pairs on creation - see backend/crm-platform/
// README.md's module layout for `dedupe`. No DUPLICATE_MATCH permission exists: the list/merge/
// dismiss endpoints reuse whichever of LEAD/CONTACT/ACCOUNT's own READ/UPDATE the pair's
// entityType maps to, checked against BOTH records - so this page lives in the main CRM nav
// (not the admin-only group Forecast/Reports sit in), since it needs no permission a default
// MEMBER doesn't already hold on Leads/Contacts/Accounts.

export type DuplicateEntityType = "LEAD" | "CONTACT" | "ACCOUNT";
export const DUPLICATE_ENTITY_TYPES: DuplicateEntityType[] = ["LEAD", "CONTACT", "ACCOUNT"];

export type DuplicateMatchReason = "EMAIL" | "NAME";

export type DuplicateMatchStatus = "PENDING" | "MERGED" | "DISMISSED";
export const DUPLICATE_MATCH_STATUSES: DuplicateMatchStatus[] = ["PENDING", "MERGED", "DISMISSED"];

export interface DuplicateMatchDto {
  id: string;
  entityType: DuplicateEntityType;
  recordAId: string;
  recordBId: string;
  matchReason: DuplicateMatchReason;
  status: DuplicateMatchStatus;
  survivorId: string | null;
  absorbedId: string | null;
  resolvedByUserId: string | null;
  resolvedAt: string | null;
  createdAt: string;
}

/** survivorId must be either the match's recordAId or recordBId - the backend rejects anything else with a 400. */
export interface MergeDuplicateRequest {
  survivorId: string;
}

// ---- Lead Scoring ----
//
// Admin-defined rules (field/operator/value/points) that sum onto Lead.score, recomputed by the
// backend's LeadScoringEngine on every Lead create/update - see backend/crm-platform/README.md's
// module layout for `leadscoring`. Unlike Territory Rule, every ACTIVE matching rule contributes
// (no "first match wins"), so there's no priority field here.

export type LeadScoringMatchField = "SOURCE" | "COMPANY_NAME" | "TITLE" | "EMAIL_DOMAIN";
export const LEAD_SCORING_MATCH_FIELDS: LeadScoringMatchField[] = ["SOURCE", "COMPANY_NAME", "TITLE", "EMAIL_DOMAIN"];

export type LeadScoringMatchOperator = "EQUALS" | "CONTAINS";
export const LEAD_SCORING_MATCH_OPERATORS: LeadScoringMatchOperator[] = ["EQUALS", "CONTAINS"];

export interface LeadScoringRuleDto {
  id: string;
  name: string;
  matchField: LeadScoringMatchField;
  matchOperator: LeadScoringMatchOperator;
  matchValue: string;
  points: number;
  active: boolean;
  matchCount: number;
  lastMatchedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateLeadScoringRuleRequest {
  name: string;
  matchField: LeadScoringMatchField;
  matchOperator: LeadScoringMatchOperator;
  matchValue: string;
  points: number;
}

export interface UpdateLeadScoringRuleRequest {
  name: string;
  matchField: LeadScoringMatchField;
  matchOperator: LeadScoringMatchOperator;
  matchValue: string;
  points: number;
  active: boolean;
}

// ---- Sales Goals / Quota Tracking ----
//
// Admin-set revenue/deal-count quotas per period, assigned to exactly one of a user or a team -
// see backend/crm-platform/README.md's module layout for `salesgoals`. actualValue/percentComplete
// are computed live by the backend on every read, never stored. Two access patterns: full CRUD is
// admin-gated (SALES_GOAL:*:ORGANIZATION), but GET /sales-goals/mine needs no permission at all -
// the same self-scoped shape the notification inbox uses - so "My Goals" lives in the main nav
// while defining goals lives in the admin group.

export type SalesGoalMetric = "REVENUE" | "DEAL_COUNT";
export const SALES_GOAL_METRICS: SalesGoalMetric[] = ["REVENUE", "DEAL_COUNT"];

export interface SalesGoalDto {
  id: string;
  name: string;
  ownerUserId: string | null;
  teamId: string | null;
  metric: SalesGoalMetric;
  targetValue: number;
  periodStart: string;
  periodEnd: string;
  actualValue: number;
  percentComplete: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateSalesGoalRequest {
  name: string;
  ownerUserId?: string | null;
  teamId?: string | null;
  metric: SalesGoalMetric;
  targetValue: number;
  periodStart: string;
  periodEnd: string;
}

export interface UpdateSalesGoalRequest {
  name: string;
  ownerUserId?: string | null;
  teamId?: string | null;
  metric: SalesGoalMetric;
  targetValue: number;
  periodStart: string;
  periodEnd: string;
}

// ---- Saved List Views ----
//
// A teammate's own named filter+sort preset for one of the standard CRM list pages - see
// backend/crm-platform/README.md's module layout for `savedviews`. The purest self-scoped module
// yet: no permission is required for any of these endpoints, every action is implicitly scoped to
// the caller. `filters` is an opaque JSON string the backend never parses - SavedViewsBar owns its
// shape (`SavedViewFilters` below) and is responsible for JSON.stringify/JSON.parse on both ends.

export type SavedViewEntityType = "LEAD" | "CONTACT" | "ACCOUNT" | "OPPORTUNITY" | "TICKET";
export type SavedViewSortDirection = "ASC" | "DESC";

/** The shape SavedViewsBar reads/writes into SavedViewDto#filters via JSON.stringify/JSON.parse. */
export interface SavedViewFilters {
  search?: string;
}

export interface SavedViewDto {
  id: string;
  entityType: SavedViewEntityType;
  name: string;
  filters: string;
  sortField: string | null;
  sortDirection: SavedViewSortDirection | null;
  isDefault: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateSavedViewRequest {
  entityType: SavedViewEntityType;
  name: string;
  filters: string;
  sortField?: string | null;
  sortDirection?: SavedViewSortDirection | null;
}

export interface UpdateSavedViewRequest {
  name: string;
  filters: string;
  sortField?: string | null;
  sortDirection?: SavedViewSortDirection | null;
}

// ---- Email Templates ----
//
// Reusable, organization-wide email templates with {{token}} placeholders - see
// backend/crm-platform/README.md's module layout for `emailtemplate`. Like Products, there's no
// ownerId: any teammate holding EMAIL_TEMPLATE:*:{TEAM,DEPARTMENT,ORGANIZATION} (no OWN scope
// exists) can manage every template in the org. render() is a read-only preview - nothing is
// persisted server-side - so the frontend calls it live as the user picks a target record.

export type EmailTemplateCategory = "GENERAL" | "SALES" | "SUPPORT" | "MARKETING";
export const EMAIL_TEMPLATE_CATEGORIES: EmailTemplateCategory[] = ["GENERAL", "SALES", "SUPPORT", "MARKETING"];

export interface EmailTemplateDto {
  id: string;
  name: string;
  category: EmailTemplateCategory;
  subject: string;
  body: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateEmailTemplateRequest {
  name: string;
  category: EmailTemplateCategory;
  subject: string;
  body: string;
}

export interface UpdateEmailTemplateRequest {
  name: string;
  category: EmailTemplateCategory;
  subject: string;
  body: string;
  active: boolean;
}

export interface RenderEmailTemplateRequest {
  contactId?: string | null;
  leadId?: string | null;
  accountId?: string | null;
  opportunityId?: string | null;
}

export interface RenderedEmailDto {
  subject: string;
  body: string;
  unresolvedTokens: string[];
}

// ---- Territory Hierarchy (Regions) ----
//
// A nested org-chart Region tree sitting ABOVE territory/'s existing Territory Rules, not on top of
// them - see backend/crm-platform/README.md's module layout for `region`. Region answers "how does
// our sales org roll up for reporting" (a static grouping of Teams), a different question from
// Territory Rules' "who should own this brand-new Lead/Account." A Team optionally points at a
// Region (TeamDto#regionId above) - that's the only link between this tree and any real CRM data.

export interface RegionDto {
  id: string;
  name: string;
  parentRegionId: string | null;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateRegionRequest {
  name: string;
  parentRegionId?: string | null;
  description?: string | null;
}

export type UpdateRegionRequest = CreateRegionRequest;

/** Computed live by RegionService#rollup, never stored - see that method's javadoc. */
export interface RegionRollupDto {
  regionId: string;
  regionName: string;
  descendantRegionCount: number;
  teamCount: number;
  userCount: number;
  openOpportunityCount: number;
  openPipelineValue: number;
  wonOpportunityCount: number;
  wonValue: number;
  lostOpportunityCount: number;
  lostValue: number;
}

// ---- Commission Tracking ----
//
// See backend/crm-platform/README.md's module layout for `commission`. CommissionPlan is admin
// config (COMMISSION_PLAN:*:ORGANIZATION only) with exactly one of ownerUserId/teamId set.
// CommissionRecord is never created or edited through the API - CommissionEngine is the only
// writer of everything except status/paidAt, so the only mutation exposed here is the one-way
// PENDING -> APPROVED -> PAID status walk (updateCommissionRecordStatus). GET .../mine needs no
// permission at all, the same self-scoped shape My Goals already uses.

export type CommissionRateType = "PERCENTAGE" | "FLAT_PER_DEAL";
export const COMMISSION_RATE_TYPES: CommissionRateType[] = ["PERCENTAGE", "FLAT_PER_DEAL"];

export type CommissionRecordStatus = "PENDING" | "APPROVED" | "PAID";

export interface CommissionPlanDto {
  id: string;
  name: string;
  ownerUserId: string | null;
  teamId: string | null;
  rateType: CommissionRateType;
  rate: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCommissionPlanRequest {
  name: string;
  ownerUserId?: string | null;
  teamId?: string | null;
  rateType: CommissionRateType;
  rate: number;
}

export interface UpdateCommissionPlanRequest {
  name: string;
  ownerUserId?: string | null;
  teamId?: string | null;
  rateType: CommissionRateType;
  rate: number;
  active: boolean;
}

export interface CommissionRecordDto {
  id: string;
  opportunityId: string;
  ownerUserId: string;
  planId: string | null;
  dealAmount: number;
  rateType: CommissionRateType;
  rate: number;
  commissionAmount: number;
  status: CommissionRecordStatus;
  earnedAt: string;
  paidAt: string | null;
}

export interface UpdateCommissionRecordStatusRequest {
  status: CommissionRecordStatus;
}

// ---- Data Subject Requests (GDPR/CCPA) ----
//
// See backend/crm-platform/README.md's module layout for `gdpr`. A subject is identified by email
// address, not a specific Contact/Lead id - export/erase both reach every Contact/Lead in the org
// matching that email. DATA_SUBJECT_REQUEST:*:ORGANIZATION only, admin-only by default (it isn't a
// core CRM resource, so a default MEMBER holds none of it). Export triggers a raw JSON file
// download (see api/gdpr.ts); erase returns affected-row counts through the normal envelope.

export type DataSubjectRequestType = "EXPORT" | "ERASURE";
export type DataSubjectRequestStatus = "COMPLETED" | "FAILED";

export interface DataSubjectRequestDto {
  id: string;
  requestType: DataSubjectRequestType;
  subjectEmail: string;
  status: DataSubjectRequestStatus;
  initiatedByUserId: string;
  contactsAffected: number;
  leadsAffected: number;
  resultNote: string | null;
  completedAt: string | null;
  createdAt: string;
}

export interface CreateDataSubjectRequest {
  subjectEmail: string;
}

// ---- Course / Certification (training) ----
//
// See backend/crm-platform/README.md's module layout for `course`/`certification` (V31) - the first
// module this session specific to "ai-trainer-crm" rather than generic CRM surface area. Course and
// Certification are admin-maintained catalogs (no ownerId, mirrors ProductDto's shape - no OWN
// scope). CourseEnrollment and UserCertification are owner-scoped records where userId (not
// ownerId) is the enrolled learner / credential holder.

export type CourseCategory = "SALES" | "PRODUCT" | "COMPLIANCE" | "ONBOARDING" | "LEADERSHIP" | "TECHNICAL";

export interface CourseDto {
  id: string;
  title: string;
  description: string | null;
  category: CourseCategory;
  durationMinutes: number;
  passingScorePercent: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCourseRequest {
  title: string;
  description?: string | null;
  category: CourseCategory;
  durationMinutes: number;
  passingScorePercent: number;
}

export interface UpdateCourseRequest extends CreateCourseRequest {
  active: boolean;
}

export type CourseEnrollmentStatus = "NOT_STARTED" | "IN_PROGRESS" | "COMPLETED" | "FAILED";

export interface CourseEnrollmentDto {
  id: string;
  courseId: string;
  userId: string;
  assignedByUserId: string | null;
  status: CourseEnrollmentStatus;
  scorePercent: number | null;
  dueDate: string | null;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCourseEnrollmentRequest {
  courseId: string;
  /** Null/omitted defaults to the caller (self-enrollment) - see CourseEnrollmentService#resolveLearner's javadoc. */
  userId?: string | null;
  dueDate?: string | null;
}

export interface UpdateCourseEnrollmentProgressRequest {
  status: CourseEnrollmentStatus;
  scorePercent?: number | null;
}

export interface CertificationDto {
  id: string;
  name: string;
  issuingBody: string | null;
  description: string | null;
  /** Null means this credential never expires - see Certification#validityMonths' javadoc. */
  validityMonths: number | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCertificationRequest {
  name: string;
  issuingBody?: string | null;
  description?: string | null;
  validityMonths?: number | null;
}

export interface UpdateCertificationRequest extends CreateCertificationRequest {
  active: boolean;
}

export type UserCertificationStatus = "ACTIVE" | "EXPIRED" | "REVOKED";

export interface UserCertificationDto {
  id: string;
  certificationId: string;
  userId: string;
  credentialNumber: string | null;
  earnedAt: string;
  expiresAt: string | null;
  status: UserCertificationStatus;
  /** Computed by the backend from expiresAt vs. today - see UserCertification#isExpired's javadoc. */
  expired: boolean;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AwardCertificationRequest {
  certificationId: string;
  /** Null/omitted defaults to the caller - see UserCertificationService#resolveHolder's javadoc. */
  userId?: string | null;
  earnedAt: string;
  credentialNumber?: string | null;
}

export interface UpdateUserCertificationStatusRequest {
  status: UserCertificationStatus;
  notes?: string | null;
}

// ---- Sequence (sales engagement cadences) ----
//
// See backend/crm-platform/README.md's module layout for `sequence` (V32) - the second module this
// session that's real new functional surface area. Sequence + SequenceStep mirror the
// Product/QuoteLineItem shape (no-OWN catalog + FK child row, steps embedded in SequenceDto).
// SequenceEnrollment mirrors Ticket's owner-scoped shape, except ownerId (the rep) and targetId
// (the Lead/Contact being worked) are two different people - see SequenceEnrollment's javadoc.

export type SequenceStepType = "EMAIL" | "CALL" | "TASK";

export interface SequenceStepDto {
  id: string;
  stepOrder: number;
  type: SequenceStepType;
  dayOffset: number;
  subject: string | null;
  body: string | null;
}

export interface CreateSequenceStepRequest {
  type: SequenceStepType;
  dayOffset: number;
  subject?: string | null;
  body?: string | null;
}

export interface UpdateSequenceStepRequest extends CreateSequenceStepRequest {}

export interface SequenceDto {
  id: string;
  name: string;
  description: string | null;
  active: boolean;
  steps: SequenceStepDto[];
  createdAt: string;
  updatedAt: string;
}

export interface CreateSequenceRequest {
  name: string;
  description?: string | null;
}

export interface UpdateSequenceRequest extends CreateSequenceRequest {
  active: boolean;
}

export type SequenceEnrollmentTargetType = "LEAD" | "CONTACT";
export type SequenceEnrollmentStatus = "ACTIVE" | "PAUSED" | "COMPLETED" | "CANCELLED";

export interface SequenceEnrollmentDto {
  id: string;
  sequenceId: string;
  targetType: SequenceEnrollmentTargetType;
  targetId: string;
  ownerId: string;
  currentStepIndex: number;
  status: SequenceEnrollmentStatus;
  enrolledAt: string;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateSequenceEnrollmentRequest {
  sequenceId: string;
  targetType: SequenceEnrollmentTargetType;
  targetId: string;
  /** Null/omitted defaults to the caller - see SequenceEnrollmentService#resolveOwner's javadoc. */
  ownerId?: string | null;
}

/** COMPLETED is rejected here - it's only ever reached by the /advance endpoint walking off the end of the step list. */
export interface UpdateSequenceEnrollmentStatusRequest {
  status: SequenceEnrollmentStatus;
}

// ---- Booking (meeting scheduler) ----
//
// See backend/crm-platform/README.md's module layout for `booking` (V33) - the third module this
// session, and the first one that actively drives another module's service: booking a slot creates
// a real CalendarEvent via CalendarEventService, cancelling one soft-deletes that same event.

export type BookingSlotStatus = "OPEN" | "BOOKED" | "CANCELLED";
export type BookingTargetType = "LEAD" | "CONTACT";

export interface BookingSlotDto {
  id: string;
  startAt: string;
  endAt: string;
  status: BookingSlotStatus;
  targetType: BookingTargetType | null;
  targetId: string | null;
  bookedAt: string | null;
  calendarEventId: string | null;
}

/** endAt is computed server-side from the link's current durationMinutes - see BookingSlot's javadoc. */
export interface CreateBookingSlotRequest {
  startAt: string;
}

export interface BookSlotRequest {
  targetType: BookingTargetType;
  targetId: string;
}

export interface BookingLinkDto {
  id: string;
  ownerId: string;
  title: string;
  description: string | null;
  durationMinutes: number;
  slug: string;
  active: boolean;
  slots: BookingSlotDto[];
  createdAt: string;
  updatedAt: string;
}

export interface CreateBookingLinkRequest {
  title: string;
  description?: string | null;
  durationMinutes: number;
  slug: string;
  /** Null/omitted defaults to the creator - see BookingLinkService#resolveOwner's javadoc. */
  ownerId?: string | null;
}

export interface UpdateBookingLinkRequest {
  title: string;
  description?: string | null;
  durationMinutes: number;
  slug: string;
  active: boolean;
}

// ---- Macro (ticket canned responses) ----
//
// See backend/crm-platform/README.md's module layout for `macro` (V34) - the fourth module this
// session. Applying a macro mutates a real Ticket through the ticket module's own endpoints
// (TicketService#update/#updateStatus), so applyMacro below returns a TicketDto, not a MacroDto.

export interface MacroDto {
  id: string;
  name: string;
  body: string;
  /** Null means applying this macro never changes the ticket's status. */
  newStatus: TicketStatus | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateMacroRequest {
  name: string;
  body: string;
  newStatus?: TicketStatus | null;
}

export interface UpdateMacroRequest {
  name: string;
  body: string;
  newStatus?: TicketStatus | null;
  active: boolean;
}

export interface ApplyMacroRequest {
  ticketId: string;
}

// ---- Contract ----
//
// See backend/crm-platform/README.md's module layout for `contract` (V35) - the fifth module
// this session and the first added after the LOC checkpoint. Fills a real gap Quote/Order/
// Invoice each fall short of: the ongoing agreement itself, tracked after a deal closes. Status
// is a free (non-linear) transition, same shape TicketStatus already uses - reopening a
// terminated contract is a legitimate correction, not an error.

export type ContractStatus = "DRAFT" | "ACTIVE" | "EXPIRED" | "TERMINATED" | "RENEWED";

export const CONTRACT_STATUSES: ContractStatus[] = ["DRAFT", "ACTIVE", "EXPIRED", "TERMINATED", "RENEWED"];

export interface ContractDto {
  id: string;
  accountId: string;
  opportunityId: string | null;
  ownerId: string;
  contractNumber: string;
  title: string;
  status: ContractStatus;
  startDate: string;
  endDate: string;
  totalValue: number;
  autoRenew: boolean;
  renewalTermMonths: number | null;
  /** Stamped the first time status moves to ACTIVE, never overwritten afterward - see Contract's javadoc on the backend. */
  signedAt: string | null;
  terms: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateContractRequest {
  accountId: string;
  opportunityId?: string | null;
  contractNumber: string;
  title: string;
  startDate: string;
  endDate: string;
  totalValue?: number | null;
  autoRenew: boolean;
  renewalTermMonths?: number | null;
  terms?: string | null;
  ownerId?: string | null;
}

export interface UpdateContractRequest {
  opportunityId?: string | null;
  contractNumber: string;
  title: string;
  startDate: string;
  endDate: string;
  totalValue?: number | null;
  autoRenew: boolean;
  renewalTermMonths?: number | null;
  terms?: string | null;
}

export interface UpdateContractStatusRequest {
  status: ContractStatus;
}

// ---- Client Goal ----
//
// See backend/crm-platform/README.md's module layout for `clientgoal` (V36) - the sixth module
// this session and the first to lean into what this platform's own name ("ai-trainer-crm")
// actually implies rather than staying purely generic-CRM. Distinct from CourseEnrollment
// (progress through a course's content), SalesGoal (an internal rep's own quota), and Contract
// (legal/subscription terms) - this tracks a client's own measurable objective.

export type ClientGoalType = "WEIGHT_LOSS" | "STRENGTH" | "ENDURANCE" | "CUSTOM";

export const CLIENT_GOAL_TYPES: ClientGoalType[] = ["WEIGHT_LOSS", "STRENGTH", "ENDURANCE", "CUSTOM"];

export type ClientGoalStatus = "ACTIVE" | "ON_HOLD" | "ACHIEVED" | "ABANDONED";

export const CLIENT_GOAL_STATUSES: ClientGoalStatus[] = ["ACTIVE", "ON_HOLD", "ACHIEVED", "ABANDONED"];

export interface ClientGoalDto {
  id: string;
  contactId: string;
  ownerId: string;
  title: string;
  goalType: ClientGoalType;
  metricUnit: string | null;
  startValue: number | null;
  targetValue: number | null;
  currentValue: number | null;
  targetDate: string | null;
  status: ClientGoalStatus;
  /** Stamped the first time status moves to ACHIEVED, never overwritten afterward - see ClientGoal's javadoc on the backend. */
  achievedAt: string | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateClientGoalRequest {
  contactId: string;
  title: string;
  goalType: ClientGoalType;
  metricUnit?: string | null;
  startValue?: number | null;
  targetValue?: number | null;
  currentValue?: number | null;
  targetDate?: string | null;
  notes?: string | null;
  ownerId?: string | null;
}

export interface UpdateClientGoalRequest {
  title: string;
  goalType: ClientGoalType;
  metricUnit?: string | null;
  startValue?: number | null;
  targetValue?: number | null;
  currentValue?: number | null;
  targetDate?: string | null;
  notes?: string | null;
}

export interface UpdateClientGoalStatusRequest {
  status: ClientGoalStatus;
}

// ---- Training Session ----
//
// See backend/crm-platform/README.md's module layout for `trainingsession` (V37) - the seventh
// module this session. Deliberately distinct from BookingLink/BookingSlot (the PRE-session
// scheduling mechanism) and ClientGoal (the long-term target this session is one unit of work
// toward) - no relationship between ClientGoalDto and this, purely a reporting-time join on
// contactId if a caller wants one.

export type TrainingSessionType = "IN_PERSON" | "VIRTUAL" | "GROUP";

export const TRAINING_SESSION_TYPES: TrainingSessionType[] = ["IN_PERSON", "VIRTUAL", "GROUP"];

export type TrainingSessionStatus = "SCHEDULED" | "COMPLETED" | "CANCELLED" | "NO_SHOW";

export const TRAINING_SESSION_STATUSES: TrainingSessionStatus[] = ["SCHEDULED", "COMPLETED", "CANCELLED", "NO_SHOW"];

export interface TrainingSessionDto {
  id: string;
  contactId: string;
  ownerId: string;
  /** Optional cross-reference to the BookingSlot this session originated from, if any. */
  bookingSlotId: string | null;
  startedAt: string;
  durationMinutes: number;
  sessionType: TrainingSessionType;
  status: TrainingSessionStatus;
  focusArea: string | null;
  /** The client's own perceived-effort rating, 1-10 (Rate of Perceived Exertion). */
  clientRpe: number | null;
  coachNotes: string | null;
  createdAt: string;
  updatedAt: string;
  /** Empty on list responses (fetching every session's exercises would be wasteful) - populated on get/create/update, the same header-only-on-list split QuoteDto.lineItems uses. */
  exercises: TrainingSessionExerciseDto[];
}

export interface CreateTrainingSessionRequest {
  contactId: string;
  bookingSlotId?: string | null;
  startedAt: string;
  durationMinutes: number;
  sessionType: TrainingSessionType;
  focusArea?: string | null;
  clientRpe?: number | null;
  coachNotes?: string | null;
  ownerId?: string | null;
}

export interface UpdateTrainingSessionRequest {
  bookingSlotId?: string | null;
  startedAt: string;
  durationMinutes: number;
  sessionType: TrainingSessionType;
  focusArea?: string | null;
  clientRpe?: number | null;
  coachNotes?: string | null;
}

export interface UpdateTrainingSessionStatusRequest {
  status: TrainingSessionStatus;
}

// ---- Training Session Exercise (V39) ----
//
// The connective tissue between TrainingSession and Exercise that both of those modules'
// backend migration comments flagged as deliberately unbuilt - which specific exercises, with
// which sets/reps/weight, were actually performed in a given session. Child-entity-of-parent
// pattern like QuoteLineItem/SequenceStep - embedded in TrainingSessionDto.exercises, managed
// through /training-sessions/{id}/exercises sub-resource endpoints, no permission of its own.

export interface TrainingSessionExerciseDto {
  id: string;
  exerciseId: string | null;
  /** Snapshot stamped once at creation - copied from the catalog Exercise's name if exerciseId is set, or typed freehand otherwise. Never resynced if the catalog entry is later renamed. */
  exerciseName: string;
  sequenceOrder: number;
  setsCompleted: number;
  /** Freeform per-set string like "12,10,8" - reps routinely vary set to set. */
  repsCompleted: string;
  weightValue: number | null;
  weightUnit: string | null;
  notes: string | null;
}

export interface CreateTrainingSessionExerciseRequest {
  exerciseId?: string | null;
  exerciseName: string;
  setsCompleted: number;
  repsCompleted: string;
  weightValue?: number | null;
  weightUnit?: string | null;
  notes?: string | null;
}

export type UpdateTrainingSessionExerciseRequest = CreateTrainingSessionExerciseRequest;

// ---- Exercise (movement library) ----
//
// See backend/crm-platform/README.md's module layout for `exercise` (V38) - the eighth module
// this session. Catalog-resource pattern (no ownerId, no OWN scope), mirrors CourseDto/ProductDto's
// shape exactly - the atomic movement-library building block a coach references when planning,
// distinct from Course's structured curriculum content and TrainingSession's post-session log.
// V39 (TrainingSessionExercise, above) is what actually connects the two, in a per-session log.

export type ExerciseCategory = "STRENGTH" | "CARDIO" | "FLEXIBILITY" | "MOBILITY" | "BALANCE" | "PLYOMETRIC";

export const EXERCISE_CATEGORIES: ExerciseCategory[] = ["STRENGTH", "CARDIO", "FLEXIBILITY", "MOBILITY", "BALANCE", "PLYOMETRIC"];

export type ExerciseMuscleGroup = "CHEST" | "BACK" | "SHOULDERS" | "ARMS" | "LEGS" | "GLUTES" | "CORE" | "FULL_BODY";

export const EXERCISE_MUSCLE_GROUPS: ExerciseMuscleGroup[] = ["CHEST", "BACK", "SHOULDERS", "ARMS", "LEGS", "GLUTES", "CORE", "FULL_BODY"];

export type ExerciseEquipment = "BARBELL" | "DUMBBELL" | "KETTLEBELL" | "MACHINE" | "CABLE" | "RESISTANCE_BAND" | "BODYWEIGHT" | "NONE";

export const EXERCISE_EQUIPMENT: ExerciseEquipment[] = [
  "BARBELL",
  "DUMBBELL",
  "KETTLEBELL",
  "MACHINE",
  "CABLE",
  "RESISTANCE_BAND",
  "BODYWEIGHT",
  "NONE",
];

export type ExerciseDifficultyLevel = "BEGINNER" | "INTERMEDIATE" | "ADVANCED";

export const EXERCISE_DIFFICULTY_LEVELS: ExerciseDifficultyLevel[] = ["BEGINNER", "INTERMEDIATE", "ADVANCED"];

export interface ExerciseDto {
  id: string;
  name: string;
  description: string | null;
  category: ExerciseCategory;
  primaryMuscleGroup: ExerciseMuscleGroup;
  equipment: ExerciseEquipment;
  difficultyLevel: ExerciseDifficultyLevel;
  videoUrl: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateExerciseRequest {
  name: string;
  description?: string | null;
  category: ExerciseCategory;
  primaryMuscleGroup: ExerciseMuscleGroup;
  equipment: ExerciseEquipment;
  difficultyLevel: ExerciseDifficultyLevel;
  videoUrl?: string | null;
}

export interface UpdateExerciseRequest extends CreateExerciseRequest {
  active: boolean;
}

// ---- Nutrition Plan (V40) ----
//
// See backend/crm-platform/README.md's module layout for `nutritionplan` (V40) - the tenth
// module this session, and the fourth fitness-specific one in a row. Owner-scoped CRM-resource
// pattern like ClientGoal/Contract - fills the dietary/macro-guidance gap ClientGoal (long-term
// outcome), TrainingSession/TrainingSessionExercise (workout log), and Exercise (movement
// catalog) don't cover.

export type NutritionPlanStatus = "DRAFT" | "ACTIVE" | "COMPLETED" | "ARCHIVED";

export const NUTRITION_PLAN_STATUSES: NutritionPlanStatus[] = ["DRAFT", "ACTIVE", "COMPLETED", "ARCHIVED"];

export interface NutritionPlanDto {
  id: string;
  contactId: string;
  ownerId: string;
  title: string;
  dailyCalorieTarget: number | null;
  proteinTargetGrams: number | null;
  carbTargetGrams: number | null;
  fatTargetGrams: number | null;
  startDate: string | null;
  endDate: string | null;
  status: NutritionPlanStatus;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateNutritionPlanRequest {
  contactId: string;
  title: string;
  dailyCalorieTarget?: number | null;
  proteinTargetGrams?: number | null;
  carbTargetGrams?: number | null;
  fatTargetGrams?: number | null;
  startDate?: string | null;
  endDate?: string | null;
  notes?: string | null;
  ownerId?: string | null;
}

export interface UpdateNutritionPlanRequest {
  title: string;
  dailyCalorieTarget?: number | null;
  proteinTargetGrams?: number | null;
  carbTargetGrams?: number | null;
  fatTargetGrams?: number | null;
  startDate?: string | null;
  endDate?: string | null;
  notes?: string | null;
}

export interface UpdateNutritionPlanStatusRequest {
  status: NutritionPlanStatus;
}
