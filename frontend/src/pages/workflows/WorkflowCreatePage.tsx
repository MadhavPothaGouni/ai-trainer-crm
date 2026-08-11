import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { listUsers } from "../../api/users";
import { createWorkflow } from "../../api/workflows";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createWorkflowSchema, type CreateWorkflowFormValues } from "../../lib/validation";
import {
  WORKFLOW_TRIGGER_EVENTS,
  WORKFLOW_TRIGGER_RESOURCES,
  type UserDto,
  type WorkflowTriggerEvent,
  type WorkflowTriggerResource,
} from "../../types/api";

export default function WorkflowCreatePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);
  const [users, setUsers] = useState<UserDto[]>([]);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateWorkflowFormValues>({
    resolver: zodResolver(createWorkflowSchema),
    defaultValues: { triggerResource: "LEAD", triggerEvent: "CREATED" },
  });

  useEffect(() => {
    listUsers({ size: 100 })
      .then((res) => setUsers(res.content))
      .catch(() => undefined);
  }, []);

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const workflow = await createWorkflow({
        name: values.name,
        description: blankToUndefined(values.description),
        triggerResource: values.triggerResource as WorkflowTriggerResource,
        triggerEvent: values.triggerEvent as WorkflowTriggerEvent,
        taskSubject: values.taskSubject,
        taskAssigneeUserId: values.taskAssigneeUserId || undefined,
      });
      navigate(`/workflows/${workflow.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New workflow</h1>
        <p className="mt-1 text-sm text-slate-500">The trigger can't be changed after creation - choose it carefully.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <TextField label="Name" error={errors.name?.message} {...register("name")} />
        <TextArea label="Description" rows={2} error={errors.description?.message} {...register("description")} />

        <div className="grid gap-4 sm:grid-cols-2">
          <Select
            label="When a"
            options={WORKFLOW_TRIGGER_RESOURCES.map((resource) => ({ value: resource, label: resource }))}
            error={errors.triggerResource?.message}
            {...register("triggerResource")}
          />
          <Select
            label="Is"
            options={WORKFLOW_TRIGGER_EVENTS.map((event) => ({ value: event, label: event.toLowerCase() }))}
            error={errors.triggerEvent?.message}
            {...register("triggerEvent")}
          />
        </div>

        <TextField
          label="Create a task with subject"
          placeholder="Follow up with the new lead"
          error={errors.taskSubject?.message}
          {...register("taskSubject")}
        />

        <Select
          label="Assign the task to"
          placeholder="Whoever owns the triggering record"
          options={users.map((user) => ({ value: user.id, label: user.fullName }))}
          {...register("taskAssigneeUserId")}
        />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/workflows")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Create workflow
          </Button>
        </div>
      </form>
    </div>
  );
}
