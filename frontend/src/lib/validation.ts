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

/** Turns "" into undefined - use when mapping an optional select/text field onto a request DTO. */
export function blankToUndefined(value: string | undefined | null): string | undefined {
  return value === "" || value === null || value === undefined ? undefined : value;
}

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
