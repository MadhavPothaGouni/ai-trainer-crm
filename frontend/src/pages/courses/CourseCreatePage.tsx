import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { createCourse } from "../../api/courses";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createCourseSchema, type CreateCourseFormValues } from "../../lib/validation";
import type { CourseCategory } from "../../types/api";

const CATEGORIES: CourseCategory[] = ["SALES", "PRODUCT", "COMPLIANCE", "ONBOARDING", "LEADERSHIP", "TECHNICAL"];

export default function CourseCreatePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateCourseFormValues>({
    resolver: zodResolver(createCourseSchema),
    defaultValues: { category: "SALES", durationMinutes: "30", passingScorePercent: "70" },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const course = await createCourse({
        title: values.title,
        description: blankToUndefined(values.description),
        category: values.category as CourseCategory,
        durationMinutes: Number(values.durationMinutes),
        passingScorePercent: Number(values.passingScorePercent),
      });
      navigate(`/courses/${course.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New course</h1>
        <p className="mt-1 text-sm text-slate-500">Add a course to the training catalog.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
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

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/courses")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Create course
          </Button>
        </div>
      </form>
    </div>
  );
}
