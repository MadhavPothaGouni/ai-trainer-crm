import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { getAccount } from "../../api/accounts";
import { deleteContact, getContact } from "../../api/contacts";
import { ActivityTimeline } from "../../components/activities/ActivityTimeline";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { ApiError } from "../../lib/apiClient";
import type { ContactDto } from "../../types/api";

export default function ContactDetailPage() {
  const { contactId } = useParams<{ contactId: string }>();
  const navigate = useNavigate();
  const [contact, setContact] = useState<ContactDto | null>(null);
  const [accountName, setAccountName] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  useEffect(() => {
    if (!contactId) return;
    let cancelled = false;
    getContact(contactId)
      .then((data) => {
        if (cancelled) return;
        setContact(data);
        if (data.accountId) {
          getAccount(data.accountId)
            .then((account) => {
              if (!cancelled) setAccountName(account.name);
            })
            .catch(() => undefined);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this contact.");
      });
    return () => {
      cancelled = true;
    };
  }, [contactId]);

  async function handleDelete() {
    if (!contactId || !window.confirm("Delete this contact? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      await deleteContact(contactId);
      navigate("/contacts");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this contact.");
      setIsDeleting(false);
    }
  }

  if (error && !contact) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!contact) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/contacts" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Contacts
          </Link>
          <h1 className="mt-1 text-2xl font-semibold text-slate-900">{contact.fullName}</h1>
        </div>
        <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
          Delete
        </Button>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-500">Overview</h2>
        <dl className="mt-3 flex flex-col gap-2 text-sm">
          <Row label="Title" value={contact.title} />
          <Row label="Email" value={contact.email} />
          <Row label="Phone" value={contact.phone} />
          <Row
            label="Account"
            value={
              contact.accountId ? (
                <Link to={`/accounts/${contact.accountId}`} className="text-slate-900 hover:underline">
                  {accountName ?? "View account"}
                </Link>
              ) : null
            }
          />
        </dl>
      </div>

      {contact.description && (
        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Description</h2>
          <p className="mt-3 whitespace-pre-wrap text-sm text-slate-900">{contact.description}</p>
        </div>
      )}

      <ActivityTimeline relatedToType="CONTACT" relatedToId={contact.id} />
    </div>
  );
}

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex justify-between gap-4">
      <dt className="text-slate-500">{label}</dt>
      <dd className="text-right text-slate-900">{value ?? "—"}</dd>
    </div>
  );
}
