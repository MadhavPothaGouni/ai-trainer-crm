import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState, type FormEvent } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { listUsers } from "../../api/users";
import { deleteWorkflow, getWorkflow, listWorkflowRuns, runWorkflow, setWorkflowActive, updateWorkflow } from "../../api/workflows";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, updateWorkflowSchema, type UpdateWorkflowFormValues } from "../../lib/validation";
import type { UserDto, WorkflowDto, WorkflowRunDto } from "../../types/api";

export default function WorkflowDetailPage() {
  const { workflowId } = useParams<{ workflowId: string }>();
  const navigate = useNavigate();
  const [workflow, setWorkflow] = useState<WorkflowDto | null>(null);
  const [runs, setRuns] = useState<WorkflowRunDto[]>([]);
  const [users, setUsers] = useState<UserDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isToggling, setIsToggling] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [runResourceId, setRunResourceId] = useState("");
  const [isRunning, setIsRunning] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<UpdateWorkflowFormValues>({ resolver: zodResolver(updateWorkflowSchema) });

  function reload() {
    if (!workflowId) return;
    getWorkflow(workflowId)
      .then((data) => {
        setWorkflow(data);
        reset({
          name: data.name,
          description: data.description ?? "",
          taskSubject: data.taskSubject,
          taskAssigneeUserId: data.taskAssigneeUserId ?? "",
        });
      })
      .catch((err: unknown) => setError(err instanceof ApiError ? err.message : "Could not load this workflow."));
    listWorkflowRuns(workflowId, { size: 20 })
      .then((res) => setRuns(res.content))
      .catch(() => undefined);
  }

  useEffect(() => {
    reload();
    listUsers({ size: 100 })
      .then((res) => setUsers(res.content))
      .catch(() => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [workflowId]);

  const onSave = handleSubmit(async (values) => {
    if (!workflowId) return;
    setFormError(null);
    try {
      const updated = await updateWorkflow(workflowId, {
        name: values.name,
        description: blankToUndefined(values.description),
        taskSubject: values.taskSubject,
        taskAssigneeUserId: values.taskAssigneeUserId || undefined,
      });
      setWorkflow(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function handleToggleActive() {
    if (!workflowId || !workflow) return;
    setIsToggling(true);
    setError(null);
    try {
      setWorkflow(await setWorkflowActive(workflowId, { active: !workflow.active }));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update this workflow's status.");
    } finally {
      setIsToggling(false);
    }
  }

  async function handleRun(event: FormEvent) {
    event.preventDefault();
    if (!workflowId || !runResourceId.trim()) return;
    setIsRunning(true);
    setError(null);
    try {
      setWorkflow(await runWorkflow(workflowId, { resourceId: runResourceId.trim() }));
      setRunResourceId("");
      reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not run this workflow.");
    } finally {
      setIsRunning(false);
    }
  }

  async function handleDelete() {
    if (!workflowId || !window.confirm("Delete this workflow?")) return;
    setIsDeleting(true);
    try {
      await deleteWorkflow(workflowId);
      navigate("/workflows");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this workflow.");
      setIsDeleting(false);
    }
  }

  if (error && !workflow) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!workflow || !workflowId) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/workflows" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Workflows
          </Link>
          <h1 className="mt-1 text-2xl font-semibold text-slate-900">{workflow.name}</h1>
          <p className="text-sm text-slate-500">
            When a {workflow.triggerResource} is {workflow.triggerEvent.toLowerCase()} &middot; ran {workflow.runCount.toLocaleString()}{" "}
            time{workflow.runCount === 1 ? "" : "s"}
          </p>
        </div>
        <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
          Delete
        </Button>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="flex items-center justify-between rounded-lg border border-slate-200 bg-white p-5">
        <div>
          <h2 className="text-sm font-medium text-slate-500">Status</h2>
          <span
            className={`mt-1 inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${
              workflow.active ? "bg-emerald-100 text-emerald-700" : "bg-slate-100 text-slate-500"
            }`}
          >
            {workflow.active ? "Active" : "Inactive"}
          </span>
        </div>
        <Button variant="secondary" onClick={() => void handleToggleActive()} isLoading={isToggling}>
          {workflow.active ? "Deactivate" : "Activate"}
        </Button>
      </div>

      <form onSubmit={onSave} noValidate className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <TextField label="Name" error={errors.name?.message} {...register("name")} />
        <TextArea label="Description" rows={2} error={errors.description?.message} {...register("description")} />
        <TextField label="Task subject" error={errors.taskSubject?.message} {...register("taskSubject")} />
        <Select
          label="Assign the task to"
          placeholder="Whoever owns the triggering record"
          options={users.map((user) => ({ value: user.id, label: user.fullName }))}
          {...register("taskAssigneeUserId")}
        />

        <div className="flex justify-end">
          <Button type="submit" isLoading={isSubmitting}>
            Save changes
          </Button>
        </div>
      </form>

      <div className="rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-500">Run now (testing)</h2>
        <p className="mt-1 text-xs text-slate-400">
          Fires this workflow immediately against a {workflow.triggerResource.toLowerCase()} id, even while inactive.
        </p>
        <form onSubmit={(event) => void handleRun(event)} className="mt-3 flex gap-2">
          <input
            type="text"
            value={runResourceId}
            onChange={(event) => setRunResourceId(event.target.value)}
            placeholder={`${workflow.triggerResource} id`}
            className="flex-1 rounded-md border border-slate-300 px-3 py-2 text-sm shadow-sm outline-none focus:border-slate-500 focus:ring-2 focus:ring-slate-200"
          />
          <Button type="submit" isLoading={isRunning} disabled={!runResourceId.trim()}>
            Run
          </Button>
        </form>
      </div>

      <div className="rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-500">Run history</h2>
        <div className="mt-3 overflow-hidden rounded-md border border-slate-100">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-3 py-2 font-medium">Ran at</th>
                <th className="px-3 py-2 font-medium">Record</th>
                <th className="px-3 py-2 font-medium">Status</th>
                <th className="px-3 py-2 font-medium">Detail</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {runs.length === 0 && (
                <tr>
                  <td className="px-3 py-4 text-center text-slate-400" colSpan={4}>
                    No runs yet.
                  </td>
                </tr>
              )}
              {runs.map((run) => (
                <tr key={run.id}>
                  <td className="px-3 py-2 text-slate-600">{new Date(run.ranAt).toLocaleString()}</td>
                  <td className="px-3 py-2 font-mono text-xs text-slate-500">{run.resourceId}</td>
                  <td className="px-3 py-2">
                    <span
                      className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${
                        run.status === "SUCCEEDED" ? "bg-emerald-100 text-emerald-700" : "bg-red-100 text-red-700"
                      }`}
                    >
                      {run.status}
                    </span>
                  </td>
                  <td className="px-3 py-2 text-xs text-slate-500">{run.errorMessage ?? (run.createdActivityId ? "Task created" : "")}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
