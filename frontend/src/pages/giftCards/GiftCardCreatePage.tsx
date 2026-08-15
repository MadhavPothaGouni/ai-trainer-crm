import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { createGiftCard } from "../../api/giftCards";
import { listContacts } from "../../api/contacts";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createGiftCardSchema, type CreateGiftCardFormValues } from "../../lib/validation";
import type { ContactDto } from "../../types/api";

export default function GiftCardCreatePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);
  const [contacts, setContacts] = useState<ContactDto[]>([]);

  useEffect(() => {
    listContacts({ size: 100, sort: "lastName,asc" })
      .then((res) => setContacts(res.content))
      .catch(() => setContacts([]));
  }, []);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateGiftCardFormValues>({ resolver: zodResolver(createGiftCardSchema) });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const giftCard = await createGiftCard({
        contactId: values.contactId,
        code: values.code,
        initialBalance: Number(values.initialBalance),
        expiresAt: blankToUndefined(values.expiresAt),
        notes: blankToUndefined(values.notes),
      });
      navigate(`/gift-cards/${giftCard.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Issue a gift card</h1>
        <p className="mt-1 text-sm text-slate-500">Assigned to you by default - redeem it later for part or all of its balance.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <Select
            label="Client"
            placeholder="Select a contact"
            options={contacts.map((contact) => ({ value: contact.id, label: contact.fullName }))}
            error={errors.contactId?.message}
            {...register("contactId")}
          />
          <TextField label="Code" placeholder="GC-BIRTHDAY" error={errors.code?.message} {...register("code")} />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Initial balance" type="number" min={0.01} step="0.01" error={errors.initialBalance?.message} {...register("initialBalance")} />
          <TextField label="Expires on" type="date" error={errors.expiresAt?.message} {...register("expiresAt")} />
        </div>

        <TextArea label="Notes" error={errors.notes?.message} {...register("notes")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/gift-cards")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Issue gift card
          </Button>
        </div>
      </form>
    </div>
  );
}
