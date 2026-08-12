import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { createApprovalRequest } from "../../api/approvals";
import { listUsers } from "../../api/users";
import { ApprovalRelatedToPicker } from "../../components/crm/ApprovalRelatedToPicker";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { createApprovalRequestSchema, type CreateApprovalRequestFormValues } from "../../lib/validation";
import type { ApprovalRelatedToType, UserDto } from "../../types/api";

/**
 * approverUserIds is built up as plain component state, not a react-hook-form field array - see
 * createApprovalRequestSchema's comment in lib/validation.ts. Each entry's position IS its step
 * number (index 0 -> step 1), so "Move up"/"Move down" reorder the array directly rather than
 * editing a stepNumber field on each row.
 */
export default function ApprovalCreatePage() {
  const navigate = useNavigate();
  const [users, setUsers] = useState<UserDto[]>([]);
  const [approverIds, setApproverIds] = useState<string[]>([]);
  const [nextApproverId, setNextApproverId] = useState("");
  const [approverListError, setApproverListError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);

  useEffect(() => {
    listUsers({ size: 200 })
      .then((res) => setUsers(res.content))
      .catch(() => undefined);
  }, []);

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateApprovalRequestFormValues>({
    resolver: zodResolver(createApprovalRequestSchema),
    defaultValues: { relatedToType: "", relatedToId: "", title: "" },
  });

  const relatedToType = watch("relatedToType") ?? "";
  const relatedToId = watch("relatedToId") ?? "";

  function userLabel(userId: string): string {
    return users.find((u) => u.id === userId)?.fullName ?? userId;
  }

  function addApprover() {
    setApproverListError(null);
    if (!nextApproverId) return;
    if (approverIds.includes(nextApproverId)) {
      setApproverListError("That teammate is already a step in this chain.");
      return;
    }
    setApproverIds([...approverIds, nextApproverId]);
    setNextApproverId("");
  }

  function removeApprover(userId: string) {
    setApproverIds(approverIds.filter((id) => id !== userId));
  }

  function moveApprover(index: number, direction: -1 | 1) {
    const target = index + direction;
    if (target < 0 || target >= approverIds.length) return;
    const next = [...approverIds];
    [next[index], next[target]] = [next[target], next[index]];
    setApproverIds(next);
  }

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    setApproverListError(null);
    if (approverIds.length === 0) {
      setApproverListError("Add at least one approver.");
      return;
    }
    try {
      const request = await createApprovalRequest({
        relatedToType: values.relatedToType as ApprovalRelatedToType,
        relatedToId: values.relatedToId,
        title: values.title,
        approverUserIds: approverIds,
      });
      navigate(`/approvals/${request.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  const availableUsers = users.filter((u) => !approverIds.includes(u.id));

  return (
    <div className="flex max-w-2xl flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New approval request</h1>
        <p className="mt-1 text-sm text-slate-500">Name an ordered chain of approvers - each one signs off before the next is asked.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-5 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <TextField label="Title" error={errors.title?.message} {...register("title")} />

        <ApprovalRelatedToPicker
          relatedToType={relatedToType}
          relatedToId={relatedToId}
          onChange={(type, id) => {
            setValue("relatedToType", type, { shouldValidate: true });
            setValue("relatedToId", id, { shouldValidate: true });
          }}
          typeError={errors.relatedToType?.message}
          idError={errors.relatedToId?.message}
        />

        <div className="flex flex-col gap-2">
          <span className="text-sm font-medium text-slate-700">Approval chain</span>

          {approverListError && <Alert variant="error">{approverListError}</Alert>}

          {approverIds.length > 0 && (
            <ol className="flex flex-col gap-2">
              {approverIds.map((userId, index) => (
                <li key={userId} className="flex items-center justify-between gap-3 rounded-md border border-slate-200 px-3 py-2 text-sm">
                  <span>
                    <span className="mr-2 inline-block w-6 rounded-full bg-slate-100 text-center text-xs font-medium text-slate-600">
                      {index + 1}
                    </span>
                    {userLabel(userId)}
                  </span>
                  <span className="flex gap-2">
                    <button
                      type="button"
                      onClick={() => moveApprover(index, -1)}
                      disabled={index === 0}
                      className="text-xs text-slate-500 hover:text-slate-900 disabled:opacity-30"
                    >
                      Up
                    </button>
                    <button
                      type="button"
                      onClick={() => moveApprover(index, 1)}
                      disabled={index === approverIds.length - 1}
                      className="text-xs text-slate-500 hover:text-slate-900 disabled:opacity-30"
                    >
                      Down
                    </button>
                    <button type="button" onClick={() => removeApprover(userId)} className="text-xs text-red-600 hover:underline">
                      Remove
                    </button>
                  </span>
                </li>
              ))}
            </ol>
          )}

          <div className="flex items-end gap-3">
            <div className="flex-1">
              <Select
                label="Add an approver"
                placeholder="Choose a teammate"
                options={availableUsers.map((u) => ({ value: u.id, label: u.fullName }))}
                value={nextApproverId}
                onChange={(e) => setNextApproverId(e.target.value)}
              />
            </div>
            <Button type="button" variant="secondary" onClick={addApprover}>
              Add step
            </Button>
          </div>
        </div>

        <div className="flex justify-end">
          <Button type="submit" isLoading={isSubmitting}>
            Submit for approval
          </Button>
        </div>
      </form>
    </div>
  );
}
