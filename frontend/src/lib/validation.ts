import { z } from "zod";

// Mirrors RegisterRequest/ResetPasswordRequest/ChangePasswordRequest's @Size(8-100) + complexity
// regex on the backend (auth/dto/*.java). This is purely a UX nicety - the backend re-validates
// everything and is the actual source of truth, so this schema doesn't need to match its regex
// character-for-character, just reject the same categories of weak password before a round trip.
export const passwordSchema = z
  .string()
  .min(8, "Password must be at least 8 characters")
  .max(100, "Password must be at most 100 characters")
  .regex(/[a-z]/, "Password must include a lowercase letter")
  .regex(/[A-Z]/, "Password must include an uppercase letter")
  .regex(/[0-9]/, "Password must include a number")
  .regex(/[^a-zA-Z0-9]/, "Password must include a special character");

export const emailSchema = z.string().min(1, "Email is required").email("Enter a valid email address");

export const loginSchema = z.object({
  email: emailSchema,
  password: z.string().min(1, "Password is required"),
});
export type LoginFormValues = z.infer<typeof loginSchema>;

export const registerSchema = z.object({
  firstName: z.string().min(1, "First name is required").max(100),
  lastName: z.string().min(1, "Last name is required").max(100),
  email: emailSchema,
  password: passwordSchema,
  organizationName: z.string().max(200).optional().or(z.literal("")),
});
export type RegisterFormValues = z.infer<typeof registerSchema>;

export const forgotPasswordSchema = z.object({
  email: emailSchema,
});
export type ForgotPasswordFormValues = z.infer<typeof forgotPasswordSchema>;

export const resetPasswordSchema = z
  .object({
    newPassword: passwordSchema,
    confirmPassword: z.string().min(1, "Please confirm your password"),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: "Passwords do not match",
    path: ["confirmPassword"],
  });
export type ResetPasswordFormValues = z.infer<typeof resetPasswordSchema>;

// ---- CRM ----

// Number inputs round-trip through the DOM as strings. Keeping the schema's input
// *and* output type as `string | undefined` (rather than transforming to `number`
// here) keeps the inferred FormValues type simple and avoids a zodResolver generic
// mismatch between useForm's single type parameter and a transformed output type -
// the actual string-to-number conversion happens at submit time via toOptionalNumber.
const optionalNumberString = z
  .string()
  .optional()
  .refine((value) => value === undefined || value === "" || !Number.isNaN(Number(value)), "Must be a number");

/** Converts a form's optional numeric-string field into a number for the API request, or undefined if blank. */
export function toOptionalNumber(value: string | undefined): number | undefined {
  return value === undefined || value === "" ? undefined : Number(value);
}

// Same input/output-type-must-match reasoning as optionalNumberString, for a field that's
// always required (unlike an optional amount) - Product.unitPrice, a line item's unitPrice.
const requiredNumberString = z
  .string()
  .min(1, "This field is required")
  .refine((value) => !Number.isNaN(Number(value)), "Must be a number");

const requiredPositiveIntegerString = z
  .string()
  .min(1, "Required")
  .refine((value) => Number.isInteger(Number(value)) && Number(value) >= 1, "Must be a whole number of at least 1");

/** Converts a form's required numeric-string field into a number for the API request. */
export function toRequiredNumber(value: string): number {
  return Number(value);
}

export const createAccountSchema = z.object({
  name: z.string().min(1, "Account name is required").max(200),
  industry: z.string().max(100).optional().or(z.literal("")),
  website: z.string().max(255).optional().or(z.literal("")),
  phone: z.string().max(30).optional().or(z.literal("")),
  billingStreet: z.string().max(255).optional().or(z.literal("")),
  billingCity: z.string().max(100).optional().or(z.literal("")),
  billingState: z.string().max(100).optional().or(z.literal("")),
  billingPostalCode: z.string().max(20).optional().or(z.literal("")),
  billingCountry: z.string().max(100).optional().or(z.literal("")),
  annualRevenue: optionalNumberString,
  employeeCount: optionalNumberString,
  description: z.string().max(2000).optional().or(z.literal("")),
});
export type CreateAccountFormValues = z.infer<typeof createAccountSchema>;

export const createContactSchema = z.object({
  firstName: z.string().min(1, "First name is required").max(100),
  lastName: z.string().min(1, "Last name is required").max(100),
  email: z.string().email("Enter a valid email address").max(255).optional().or(z.literal("")),
  phone: z.string().max(30).optional().or(z.literal("")),
  title: z.string().max(150).optional().or(z.literal("")),
  description: z.string().max(2000).optional().or(z.literal("")),
  accountId: z.string().optional().or(z.literal("")),
});
export type CreateContactFormValues = z.infer<typeof createContactSchema>;

export const createOpportunitySchema = z.object({
  accountId: z.string().min(1, "Account is required"),
  primaryContactId: z.string().optional().or(z.literal("")),
  name: z.string().min(1, "Opportunity name is required").max(200),
  amount: optionalNumberString,
  currency: z.string().max(3).optional().or(z.literal("")),
  expectedCloseDate: z.string().optional().or(z.literal("")),
  description: z.string().max(2000).optional().or(z.literal("")),
});
export type CreateOpportunityFormValues = z.infer<typeof createOpportunitySchema>;

export const createLeadSchema = z.object({
  firstName: z.string().min(1, "First name is required").max(100),
  lastName: z.string().min(1, "Last name is required").max(100),
  email: z.string().email("Enter a valid email address").max(255).optional().or(z.literal("")),
  phone: z.string().max(30).optional().or(z.literal("")),
  companyName: z.string().max(200).optional().or(z.literal("")),
  title: z.string().max(150).optional().or(z.literal("")),
  source: z.string().min(1, "Source is required"),
  description: z.string().max(2000).optional().or(z.literal("")),
});
export type CreateLeadFormValues = z.infer<typeof createLeadSchema>;

export const convertLeadSchema = z.object({
  existingAccountId: z.string().optional().or(z.literal("")),
  newAccountName: z.string().max(200).optional().or(z.literal("")),
  createOpportunity: z.boolean().optional(),
  opportunityName: z.string().max(200).optional().or(z.literal("")),
  opportunityAmount: optionalNumberString,
  opportunityExpectedCloseDate: z.string().optional().or(z.literal("")),
});
export type ConvertLeadFormValues = z.infer<typeof convertLeadSchema>;

export const createTicketSchema = z.object({
  subject: z.string().min(1, "Subject is required").max(200),
  description: z.string().max(2000).optional().or(z.literal("")),
  priority: z.string().min(1, "Priority is required"),
  accountId: z.string().optional().or(z.literal("")),
  contactId: z.string().optional().or(z.literal("")),
});
export type CreateTicketFormValues = z.infer<typeof createTicketSchema>;

// ---- CRM: Email & Calendar ----

// fromAddress/toAddresses are plain required strings, not z.string().email() -
// toAddresses in particular is a comma-separated list of addresses (see
// LogEmailRequest's javadoc for why that's a plain column, not an array
// column or a child table), so a single-address email() check would reject
// every valid multi-recipient value.
export const logEmailSchema = z.object({
  direction: z.string().min(1, "Direction is required"),
  subject: z.string().min(1, "Subject is required").max(500),
  body: z.string().max(10000).optional().or(z.literal("")),
  fromAddress: z.string().min(1, "From address is required").max(255),
  toAddresses: z.string().min(1, "At least one recipient is required").max(2000),
  ccAddresses: z.string().max(2000).optional().or(z.literal("")),
  relatedToType: z.string().min(1, "Related record type is required"),
  relatedToId: z.string().min(1, "Related record is required"),
  sentAt: z.string().optional().or(z.literal("")),
});
export type LogEmailFormValues = z.infer<typeof logEmailSchema>;

// relatedToType/relatedToId are optional here, unlike logEmailSchema's required
// pair - see CreateCalendarEventRequest's javadoc for why (not every event is
// about a CRM record).
export const createCalendarEventSchema = z
  .object({
    title: z.string().min(1, "Title is required").max(300),
    description: z.string().max(2000).optional().or(z.literal("")),
    location: z.string().max(255).optional().or(z.literal("")),
    startAt: z.string().min(1, "Start time is required"),
    endAt: z.string().min(1, "End time is required"),
    allDay: z.boolean().optional(),
    relatedToType: z.string().optional().or(z.literal("")),
    relatedToId: z.string().optional().or(z.literal("")),
  })
  .refine((data) => data.endAt >= data.startAt, {
    message: "End time cannot be before start time",
    path: ["endAt"],
  });
export type CreateCalendarEventFormValues = z.infer<typeof createCalendarEventSchema>;

export const addAttendeeSchema = z
  .object({
    userId: z.string().optional().or(z.literal("")),
    externalEmail: z.string().email("Enter a valid email address").optional().or(z.literal("")),
  })
  .refine((data) => Boolean(data.userId) !== Boolean(data.externalEmail), {
    message: "Choose exactly one of a teammate or an external email",
    path: ["externalEmail"],
  });
export type AddAttendeeFormValues = z.infer<typeof addAttendeeSchema>;

// ---- Organization: Team ----

export const createTeamSchema = z.object({
  name: z.string().min(1, "Team name is required").max(150),
  department: z.string().max(100).optional().or(z.literal("")),
  leadUserId: z.string().optional().or(z.literal("")),
});
export type CreateTeamFormValues = z.infer<typeof createTeamSchema>;

/** Turns "" into undefined - use when mapping an optional select/text field onto a request DTO. */
export function blankToUndefined(value: string | undefined | null): string | undefined {
  return value === "" || value === null || value === undefined ? undefined : value;
}

// ---- Notification ----

export const createNotificationSchema = z.object({
  recipientUserId: z.string().min(1, "Choose a teammate to notify"),
  type: z.string().min(1, "Type is required"),
  title: z.string().min(1, "Title is required").max(200),
  body: z.string().max(2000).optional().or(z.literal("")),
  relatedToType: z.string().optional().or(z.literal("")),
  relatedToId: z.string().optional().or(z.literal("")),
});
export type CreateNotificationFormValues = z.infer<typeof createNotificationSchema>;

// ---- Attachment ----

/** Backs the upload form's relatedToType/description fields - the file itself is a native <input type="file"> value, not a zod-validated field, so it's handled separately in the component (see AttachmentsPage's onSubmit). */
export const uploadAttachmentSchema = z.object({
  relatedToType: z.string().min(1, "Choose a record type"),
  relatedToId: z.string().min(1, "Choose a record"),
  description: z.string().max(1000).optional().or(z.literal("")),
});
export type UploadAttachmentFormValues = z.infer<typeof uploadAttachmentSchema>;

export const updateAttachmentSchema = z.object({
  fileName: z.string().min(1, "File name is required").max(255),
  description: z.string().max(1000).optional().or(z.literal("")),
  relatedToType: z.string().min(1, "Related record type is required"),
  relatedToId: z.string().min(1, "Related record is required"),
});
export type UpdateAttachmentFormValues = z.infer<typeof updateAttachmentSchema>;

// ---- Approval Workflow ----
//
// approverUserIds isn't part of this schema - it's an ordered list built up by plain component
// state (add/remove rows), not a react-hook-form field, the same "manual value/onChange" reasoning
// RelatedToPicker's own javadoc gives for relatedToType/relatedToId. ApprovalCreatePage validates
// the list itself (non-empty, no duplicates) before submitting.
export const createApprovalRequestSchema = z.object({
  relatedToType: z.string().min(1, "Choose a record type"),
  relatedToId: z.string().min(1, "Choose a record"),
  title: z.string().min(1, "Title is required").max(300),
});
export type CreateApprovalRequestFormValues = z.infer<typeof createApprovalRequestSchema>;

export const decideApprovalStepSchema = z.object({
  comment: z.string().max(1000).optional().or(z.literal("")),
});
export type DecideApprovalStepFormValues = z.infer<typeof decideApprovalStepSchema>;

// ---- SLA & Escalation ----

export const createSlaPolicySchema = z.object({
  name: z.string().min(1, "Name is required").max(150),
  priority: z.string().min(1, "Priority is required"),
  responseTargetMinutes: requiredPositiveIntegerString,
  resolutionTargetMinutes: requiredPositiveIntegerString,
  escalateToUserId: z.string().optional().or(z.literal("")),
});
export type CreateSlaPolicyFormValues = z.infer<typeof createSlaPolicySchema>;

/** Same fields as createSlaPolicySchema minus priority (not editable after creation, see UpdateSlaPolicyRequest's javadoc on the backend), plus active. */
export const updateSlaPolicySchema = createSlaPolicySchema.omit({ priority: true }).extend({
  active: z.boolean(),
});
export type UpdateSlaPolicyFormValues = z.infer<typeof updateSlaPolicySchema>;

// ---- Territory / Assignment Rules ----

const requiredWholeNumberString = z
  .string()
  .min(1, "Required")
  .refine((value) => Number.isInteger(Number(value)) && Number(value) >= 0, "Must be a whole number");

/** assignToType/assignToId aren't real request fields - the form collects "who" as one radio choice plus one picker, then the page maps it to assignToUserId or assignToTeamId (exactly one, the same "exactly one of two" rule the backend enforces) before calling the API. */
export const createTerritoryRuleSchema = z.object({
  name: z.string().min(1, "Name is required").max(150),
  targetResource: z.string().min(1, "Choose Lead or Account"),
  matchField: z.string().min(1, "Choose a field to match on"),
  matchOperator: z.string().min(1, "Choose an operator"),
  matchValue: z.string().min(1, "Match value is required").max(200),
  priority: requiredWholeNumberString,
  assignToType: z.enum(["USER", "TEAM"]),
  assignToId: z.string().min(1, "Choose who this rule assigns to"),
});
export type CreateTerritoryRuleFormValues = z.infer<typeof createTerritoryRuleSchema>;

/** Same fields as createTerritoryRuleSchema minus targetResource (not editable after creation, see UpdateTerritoryRuleRequest's javadoc on the backend), plus active. */
export const updateTerritoryRuleSchema = createTerritoryRuleSchema.omit({ targetResource: true }).extend({
  active: z.boolean(),
});
export type UpdateTerritoryRuleFormValues = z.infer<typeof updateTerritoryRuleSchema>;

// ---- Lead Scoring ----

/** Unlike requiredWholeNumberString (priority, >= 0), points can be negative - a rule can penalize a score just as easily as boost it. */
const requiredSignedWholeNumberString = z
  .string()
  .min(1, "Required")
  .refine((value) => Number.isInteger(Number(value)), "Must be a whole number");

export const createLeadScoringRuleSchema = z.object({
  name: z.string().min(1, "Name is required").max(150),
  matchField: z.string().min(1, "Choose a field to match on"),
  matchOperator: z.string().min(1, "Choose an operator"),
  matchValue: z.string().min(1, "Match value is required").max(200),
  points: requiredSignedWholeNumberString,
});
export type CreateLeadScoringRuleFormValues = z.infer<typeof createLeadScoringRuleSchema>;

export const updateLeadScoringRuleSchema = createLeadScoringRuleSchema.extend({
  active: z.boolean(),
});
export type UpdateLeadScoringRuleFormValues = z.infer<typeof updateLeadScoringRuleSchema>;

// ---- Team / role management ----

export const roleFormSchema = z.object({
  name: z.string().min(1, "Role name is required").max(100),
  description: z.string().max(500).optional().or(z.literal("")),
});
export type RoleFormValues = z.infer<typeof roleFormSchema>;

export const inviteUserSchema = z.object({
  email: emailSchema,
  firstName: z.string().min(1, "First name is required").max(100),
  lastName: z.string().min(1, "Last name is required").max(100),
});
export type InviteUserFormValues = z.infer<typeof inviteUserSchema>;

// ---- Profile settings ----

// Mirrors UpdateProfileRequest's @Size constraints (user/dto/UpdateProfileRequest.java).
export const profileFormSchema = z.object({
  firstName: z.string().min(1, "First name is required").max(100),
  lastName: z.string().min(1, "Last name is required").max(100),
  phone: z.string().max(30).optional().or(z.literal("")),
  timezone: z.string().max(60).optional().or(z.literal("")),
  locale: z.string().max(20).optional().or(z.literal("")),
});
export type ProfileFormValues = z.infer<typeof profileFormSchema>;

// ---- Activities (calls/emails/meetings/tasks/notes) ----

export const createActivitySchema = z.object({
  type: z.string().min(1, "Type is required"),
  subject: z.string().min(1, "Subject is required").max(200),
  description: z.string().max(2000).optional().or(z.literal("")),
  priority: z.string().optional().or(z.literal("")),
  dueAt: z.string().optional().or(z.literal("")),
});
export type CreateActivityFormValues = z.infer<typeof createActivitySchema>;

// ---- Sales: products & quotes ----

export const createProductSchema = z.object({
  name: z.string().min(1, "Product name is required").max(200),
  sku: z.string().max(100).optional().or(z.literal("")),
  description: z.string().max(2000).optional().or(z.literal("")),
  unitPrice: requiredNumberString,
  currency: z.string().max(3).optional().or(z.literal("")),
});
export type CreateProductFormValues = z.infer<typeof createProductSchema>;

export const createQuoteSchema = z.object({
  opportunityId: z.string().min(1, "Opportunity is required"),
  name: z.string().min(1, "Quote name is required").max(200),
  currency: z.string().max(3).optional().or(z.literal("")),
  validUntil: z.string().optional().or(z.literal("")),
  discountAmount: optionalNumberString,
  taxAmount: optionalNumberString,
});
export type CreateQuoteFormValues = z.infer<typeof createQuoteSchema>;

/** Same fields as createQuoteSchema minus opportunityId - see Quote's javadoc for why it's immutable after creation. */
export const updateQuoteSchema = createQuoteSchema.omit({ opportunityId: true });
export type UpdateQuoteFormValues = z.infer<typeof updateQuoteSchema>;

export const quoteLineItemSchema = z.object({
  productId: z.string().optional().or(z.literal("")),
  description: z.string().min(1, "Description is required").max(500),
  quantity: requiredPositiveIntegerString,
  unitPrice: requiredNumberString,
});
export type QuoteLineItemFormValues = z.infer<typeof quoteLineItemSchema>;

// ---- Sales/finance: orders, invoices, payments ----

export const createOrderSchema = z.object({
  orderNumber: z.string().min(1, "Order number is required").max(50),
  currency: z.string().max(3).optional().or(z.literal("")),
  discountAmount: optionalNumberString,
  taxAmount: optionalNumberString,
});
export type CreateOrderFormValues = z.infer<typeof createOrderSchema>;

export const createOrderFromQuoteSchema = z.object({
  orderNumber: z.string().min(1, "Order number is required").max(50),
});
export type CreateOrderFromQuoteFormValues = z.infer<typeof createOrderFromQuoteSchema>;

export const orderLineItemSchema = z.object({
  productId: z.string().optional().or(z.literal("")),
  description: z.string().min(1, "Description is required").max(500),
  quantity: requiredPositiveIntegerString,
  unitPrice: requiredNumberString,
});
export type OrderLineItemFormValues = z.infer<typeof orderLineItemSchema>;

export const generateInvoiceSchema = z.object({
  invoiceNumber: z.string().min(1, "Invoice number is required").max(50),
  issueDate: z.string().optional().or(z.literal("")),
  dueDate: z.string().optional().or(z.literal("")),
});
export type GenerateInvoiceFormValues = z.infer<typeof generateInvoiceSchema>;

export const updateInvoiceSchema = z.object({
  invoiceNumber: z.string().min(1, "Invoice number is required").max(50),
  currency: z.string().max(3).optional().or(z.literal("")),
  issueDate: z.string().min(1, "Issue date is required"),
  dueDate: z.string().min(1, "Due date is required"),
  discountAmount: optionalNumberString,
  taxAmount: optionalNumberString,
});
export type UpdateInvoiceFormValues = z.infer<typeof updateInvoiceSchema>;

export const invoiceLineItemSchema = orderLineItemSchema;
export type InvoiceLineItemFormValues = z.infer<typeof invoiceLineItemSchema>;

export const recordPaymentSchema = z.object({
  amount: requiredNumberString,
  method: z.string().min(1, "Method is required"),
  reference: z.string().max(200).optional().or(z.literal("")),
  notes: z.string().max(1000).optional().or(z.literal("")),
});
export type RecordPaymentFormValues = z.infer<typeof recordPaymentSchema>;

// ---- Marketing/support: campaigns, campaign members, knowledge articles ----

export const createCampaignSchema = z.object({
  name: z.string().min(1, "Campaign name is required").max(200),
  type: z.string().min(1, "Type is required"),
  startDate: z.string().optional().or(z.literal("")),
  endDate: z.string().optional().or(z.literal("")),
  budget: optionalNumberString,
  actualCost: optionalNumberString,
  description: z.string().max(2000).optional().or(z.literal("")),
});
export type CreateCampaignFormValues = z.infer<typeof createCampaignSchema>;

export const addCampaignMemberSchema = z
  .object({
    leadId: z.string().optional().or(z.literal("")),
    contactId: z.string().optional().or(z.literal("")),
  })
  .refine((data) => Boolean(data.leadId) !== Boolean(data.contactId), {
    message: "Choose exactly one of a lead or a contact",
    path: ["contactId"],
  });
export type AddCampaignMemberFormValues = z.infer<typeof addCampaignMemberSchema>;

export const createKnowledgeArticleSchema = z.object({
  title: z.string().min(1, "Title is required").max(300),
  category: z.string().max(100).optional().or(z.literal("")),
  content: z.string().min(1, "Content is required"),
  tags: z.string().optional().or(z.literal("")), // comma-separated in the form; split into string[] at submit time
});
export type CreateKnowledgeArticleFormValues = z.infer<typeof createKnowledgeArticleSchema>;

/** Splits a comma-separated tags field into a trimmed, de-duplicated, non-empty string array. */
export function toTagList(value: string | undefined): string[] {
  if (!value) return [];
  return [...new Set(value.split(",").map((tag) => tag.trim()).filter((tag) => tag.length > 0))];
}

// ---- Platform extensibility: custom objects/fields ----

const apiNamePattern = /^[a-z][a-z0-9_]*$/;

export const createCustomObjectSchema = z.object({
  apiName: z
    .string()
    .min(1, "API name is required")
    .max(80)
    .regex(apiNamePattern, "Lowercase letters, numbers, and underscores only, starting with a letter"),
  label: z.string().min(1, "Label is required").max(150),
  pluralLabel: z.string().min(1, "Plural label is required").max(150),
  description: z.string().max(500).optional().or(z.literal("")),
});
export type CreateCustomObjectFormValues = z.infer<typeof createCustomObjectSchema>;

export const updateCustomObjectSchema = createCustomObjectSchema.omit({ apiName: true }).extend({
  active: z.boolean(),
});
export type UpdateCustomObjectFormValues = z.infer<typeof updateCustomObjectSchema>;

export const createCustomFieldSchema = z
  .object({
    standardEntityType: z.string().optional().or(z.literal("")),
    customObjectId: z.string().optional().or(z.literal("")),
    apiName: z
      .string()
      .min(1, "API name is required")
      .max(80)
      .regex(apiNamePattern, "Lowercase letters, numbers, and underscores only, starting with a letter"),
    label: z.string().min(1, "Label is required").max(150),
    fieldType: z.string().min(1, "Field type is required"),
    required: z.boolean(),
    // Same string-in/string-out reasoning as optionalNumberString above - converted via
    // toRequiredNumber at submit time, not coerced here.
    displayOrder: z
      .string()
      .optional()
      .refine((value) => value === undefined || value === "" || Number.isInteger(Number(value)), "Must be a whole number"),
    // Comma-separated in the form; only meaningful (and required) when fieldType is PICKLIST - split/validated at submit time.
    picklistValues: z.string().optional().or(z.literal("")),
  })
  .refine((data) => Boolean(data.standardEntityType) !== Boolean(data.customObjectId), {
    message: "Choose exactly one of a standard entity or a custom object",
    path: ["customObjectId"],
  });
export type CreateCustomFieldFormValues = z.infer<typeof createCustomFieldSchema>;

/** Splits a comma-separated picklist-options field into a trimmed, de-duplicated, non-empty string array - same shape as toTagList. */
export function toPicklistValues(value: string | undefined): string[] {
  if (!value) return [];
  return [...new Set(value.split(",").map((option) => option.trim()).filter((option) => option.length > 0))];
}

// ---- Automation: workflows ----

export const createWorkflowSchema = z.object({
  name: z.string().min(1, "Name is required").max(200),
  description: z.string().max(2000).optional().or(z.literal("")),
  triggerResource: z.string().min(1, "Trigger resource is required"),
  triggerEvent: z.string().min(1, "Trigger event is required"),
  taskSubject: z.string().min(1, "Task subject is required").max(200),
  taskAssigneeUserId: z.string().optional().or(z.literal("")),
});
export type CreateWorkflowFormValues = z.infer<typeof createWorkflowSchema>;

// triggerResource/triggerEvent are immutable after creation (see UpdateWorkflowRequest) - the edit form omits them.
export const updateWorkflowSchema = createWorkflowSchema.omit({ triggerResource: true, triggerEvent: true });
export type UpdateWorkflowFormValues = z.infer<typeof updateWorkflowSchema>;

// ---- Dashboards ----

export const createDashboardSchema = z.object({
  name: z.string().min(1, "Name is required").max(200),
  description: z.string().max(2000).optional().or(z.literal("")),
});
export type CreateDashboardFormValues = z.infer<typeof createDashboardSchema>;

export const addDashboardWidgetSchema = z.object({
  reportType: z.string().min(1, "Report type is required"),
  title: z.string().max(200).optional().or(z.literal("")),
  width: z
    .string()
    .optional()
    .refine((value) => value === undefined || value === "" || Number.isInteger(Number(value)), "Must be a whole number"),
  height: z
    .string()
    .optional()
    .refine((value) => value === undefined || value === "" || Number.isInteger(Number(value)), "Must be a whole number"),
});
export type AddDashboardWidgetFormValues = z.infer<typeof addDashboardWidgetSchema>;

export const runWorkflowSchema = z.object({
  resourceId: z.string().min(1, "A record id is required"),
});
export type RunWorkflowFormValues = z.infer<typeof runWorkflowSchema>;

export const changePasswordFormSchema = z
  .object({
    currentPassword: z.string().min(1, "Current password is required"),
    newPassword: passwordSchema,
    confirmPassword: z.string().min(1, "Please confirm your new password"),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: "Passwords do not match",
    path: ["confirmPassword"],
  });
export type ChangePasswordFormValues = z.infer<typeof changePasswordFormSchema>;

// ---- Platform: API keys & webhooks ----

export const createApiKeySchema = z.object({
  name: z.string().min(1, "Name is required").max(200),
});
export type CreateApiKeyFormValues = z.infer<typeof createApiKeySchema>;

export const createWebhookSchema = z.object({
  url: z.string().min(1, "URL is required").max(500).url("Enter a valid URL"),
  eventType: z.string().max(100).optional().or(z.literal("")),
});
export type CreateWebhookFormValues = z.infer<typeof createWebhookSchema>;
