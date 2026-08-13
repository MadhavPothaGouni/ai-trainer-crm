import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { createCourseEnrollment, deleteCourse, getCourse, listCourseEnrollments, updateCourse, updateCourseEnrollmentProgress } from "../../api/courses";
import { listUsers } from "../../api/users";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import {
  blankToUndefined,
  createCourseEnrollmentSchema,
  createCourseSchema,
  type CreateCourseEnrollmentFormValues,
  type CreateCourseFormValues,
} from "../../lib/validation";
import type { CourseCategory, CourseDto, CourseEnrollmentDto, CourseEnrollmentStatus, UserDto } from "../../types/api";
import { CourseCategoryBadge } from "./CourseListPage";

const CATEGORIES: CourseCategory[] = ["SALES", "PRODUCT", "COMPLIANCE", "ONBOARDING", "LEADERSHIP", "TECHNICAL"];

const ENROLLMENT_STATUS_CLASSES: Record<CourseEnrollmentStatus, string> = {
  NOT_STARTED: "bg-slate-100 text-slate-600",
  IN_PROGRESS: "bg-blue-100 text-blue-700",
  COMPLETED: "bg-emerald-100 text-emerald-700",
  FAILED: "bg-red-100 text-red-700",
};

export default function CourseDetailPage() {
  const { courseId } = useParams<{ courseId: string }>();
  const navigate = useNavigate();
  const [course, setCourse] = useState<CourseDto | null>(null);
  const [enrollments, setEnrollments] = useState<CourseEnrollmentDto[]>([]);
  const [users, setUsers] = useState<UserDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<CreateCourseFormValues>({ resolver: zodResolver(createCourseSchema) });

  function loadEnrollments() {
    if (!courseId) return;
    listCourseEnrollments({ size: 200, sort: "createdAt,desc" })
      .then((res) => setEnrollments(res.content.filter((e) => e.courseId === courseId)))
      .catch(() => undefined);
  }

  useEffect(() => {
    if (!courseId) return;
    let cancelled = false;
    getCourse(courseId)
      .then((data) => {
        if (cancelled) return;
        setCourse(data);
        reset({
          title: data.title,
          description: data.description ?? "",
          category: data.category,
          durationMinutes: String(data.durationMinutes),
          passingScorePercent: String(data.passingScorePercent),
        });
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this course.");
      });
    listUsers({ size: 200 })
      .then((res) => {
        if (!cancelled) setUsers(res.content);
      })
      .catch(() => undefined);
    loadEnrollments();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [courseId, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!courseId || !course) return;
    setFormError(null);
    try {
      const updated = await updateCourse(courseId, {
        title: values.title,
        description: blankToUndefined(values.description),
        category: values.category as CourseCategory,
        durationMinutes: Number(values.durationMinutes),
        passingScorePercent: Number(values.passingScorePercent),
        active: course.active,
      });
      setCourse(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function toggleActive() {
    if (!courseId || !course) return;
    try {
      const updated = await updateCourse(courseId, {
        title: course.title,
        description: course.description ?? undefined,
        category: course.category,
        durationMinutes: course.durationMinutes,
        passingScorePercent: course.passingScorePercent,
        active: !course.active,
      });
      setCourse(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update this course.");
    }
  }

  async function handleDelete() {
    if (!courseId || !window.confirm("Delete this course?")) return;
    setIsDeleting(true);
    try {
      await deleteCourse(courseId);
      navigate("/courses");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this course.");
      setIsDeleting(false);
    }
  }

  function userLabel(userId: string): string {
    return users.find((u) => u.id === userId)?.fullName ?? "Unknown teammate";
  }

  if (error && !course) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!course) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/courses" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Training catalog
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{course.title}</h1>
            <CourseCategoryBadge category={course.category} />
            {course.active ? (
              <span className="inline-block rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-medium text-emerald-700">Active</span>
            ) : (
              <span className="inline-block rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-500">Inactive</span>
            )}
          </div>
        </div>
        <div className="flex gap-3">
          <Button variant="secondary" onClick={() => void toggleActive()}>
            {course.active ? "Deactivate" : "Activate"}
          </Button>
          <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
            Delete
          </Button>
        </div>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Edit</h2>

        {formError && <Alert variant="error">{formError}</Alert>}

        <TextField label="Title" error={errors.title?.message} {...register("title")} />

        <div className="grid gap-4 sm:grid-cols-3">
          <Select
            label="Category"
            options={CATEGORIES.map((category) => ({ value: category, label: category }))}
            error={errors.category?.message}
            {...register("category")}
          />
          <TextField label="Duration (minutes)" type="number" min={0} error={errors.durationMinutes?.message} {...register("durationMinutes")} />
          <TextField
            label="Passing score (%)"
            type="number"
            min={0}
            max={100}
            error={errors.passingScorePercent?.message}
            {...register("passingScorePercent")}
          />
        </div>

        <TextArea label="Description" error={errors.description?.message} {...register("description")} />

        <div className="flex justify-end">
          <Button type="submit" isLoading={isSubmitting}>
            Save changes
          </Button>
        </div>
      </form>

      <EnrollmentsPanel enrollments={enrollments} userLabel={userLabel} onChanged={loadEnrollments} setError={setError} />
      <EnrollForm courseId={course.id} users={users} onEnrolled={loadEnrollments} />
    </div>
  );
}

function EnrollmentsPanel({
  enrollments,
  userLabel,
  onChanged,
  setError,
}: {
  enrollments: CourseEnrollmentDto[];
  userLabel: (userId: string) => string;
  onChanged: () => void;
  setError: (message: string | null) => void;
}) {
  const [actioningId, setActioningId] = useState<string | null>(null);

  async function markComplete(enrollment: CourseEnrollmentDto, scorePercent: number) {
    setActioningId(enrollment.id);
    try {
      await updateCourseEnrollmentProgress(enrollment.id, { status: "COMPLETED", scorePercent });
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update this enrollment.");
    } finally {
      setActioningId(null);
    }
  }

  async function markInProgress(enrollment: CourseEnrollmentDto) {
    setActioningId(enrollment.id);
    try {
      await updateCourseEnrollmentProgress(enrollment.id, { status: "IN_PROGRESS" });
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update this enrollment.");
    } finally {
      setActioningId(null);
    }
  }

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-5">
      <h2 className="text-sm font-medium text-slate-500">Enrollments</h2>
      <p className="mt-1 text-xs text-slate-400">
        A score at or above the course's passing bar lands COMPLETED - a lower score is automatically reported as FAILED, even if
        COMPLETED was requested.
      </p>
      <div className="mt-3 overflow-hidden rounded-md border border-slate-100">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-100 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-3 py-2 font-medium">Learner</th>
              <th className="px-3 py-2 font-medium">Status</th>
              <th className="px-3 py-2 font-medium">Score</th>
              <th className="px-3 py-2 font-medium" />
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {enrollments.length === 0 && (
              <tr>
                <td className="px-3 py-4 text-center text-slate-400" colSpan={4}>
                  No one is enrolled yet.
                </td>
              </tr>
            )}
            {enrollments.map((enrollment) => (
              <tr key={enrollment.id}>
                <td className="px-3 py-2 text-slate-900">{userLabel(enrollment.userId)}</td>
                <td className="px-3 py-2">
                  <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${ENROLLMENT_STATUS_CLASSES[enrollment.status]}`}>
                    {enrollment.status.replace("_", " ")}
                  </span>
                </td>
                <td className="px-3 py-2 text-slate-600">{enrollment.scorePercent ?? "—"}</td>
                <td className="px-3 py-2 text-right">
                  {enrollment.status === "NOT_STARTED" && (
                    <Button variant="secondary" isLoading={actioningId === enrollment.id} onClick={() => void markInProgress(enrollment)}>
                      Start
                    </Button>
                  )}
                  {(enrollment.status === "IN_PROGRESS" || enrollment.status === "FAILED") && (
                    <ScoreSubmitButton
                      isLoading={actioningId === enrollment.id}
                      onSubmitScore={(score) => void markComplete(enrollment, score)}
                    />
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function ScoreSubmitButton({ isLoading, onSubmitScore }: { isLoading: boolean; onSubmitScore: (score: number) => void }) {
  const [score, setScore] = useState("");
  return (
    <div className="flex items-center justify-end gap-2">
      <input
        type="number"
        min={0}
        max={100}
        value={score}
        onChange={(event) => setScore(event.target.value)}
        placeholder="Score"
        className="w-20 rounded-md border border-slate-300 px-2 py-1 text-sm"
      />
      <Button
        variant="secondary"
        isLoading={isLoading}
        disabled={score === ""}
        onClick={() => onSubmitScore(Number(score))}
      >
        Submit score
      </Button>
    </div>
  );
}

function EnrollForm({ courseId, users, onEnrolled }: { courseId: string; users: UserDto[]; onEnrolled: () => void }) {
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<CreateCourseEnrollmentFormValues>({
    resolver: zodResolver(createCourseEnrollmentSchema),
    defaultValues: { courseId, userId: "", dueDate: "" },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await createCourseEnrollment({
        courseId,
        userId: blankToUndefined(values.userId),
        dueDate: blankToUndefined(values.dueDate),
      });
      reset({ courseId, userId: "", dueDate: "" });
      onEnrolled();
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
      <h2 className="text-sm font-medium text-slate-900">Enroll a teammate</h2>

      {formError && <Alert variant="error">{formError}</Alert>}

      <div className="grid gap-4 sm:grid-cols-2">
        <Select
          label="Teammate"
          placeholder="Myself"
          options={users.map((user) => ({ value: user.id, label: user.fullName }))}
          error={errors.userId?.message}
          {...register("userId")}
        />
        <TextField label="Due date" type="date" error={errors.dueDate?.message} {...register("dueDate")} />
      </div>

      <div className="flex justify-end">
        <Button type="submit" isLoading={isSubmitting}>
          Enroll
        </Button>
      </div>
    </form>
  );
}
