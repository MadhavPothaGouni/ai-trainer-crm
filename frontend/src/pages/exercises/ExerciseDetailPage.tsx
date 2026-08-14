import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { deleteExercise, getExercise, updateExercise } from "../../api/exercises";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createExerciseSchema, type CreateExerciseFormValues } from "../../lib/validation";
import {
  EXERCISE_CATEGORIES,
  EXERCISE_DIFFICULTY_LEVELS,
  EXERCISE_EQUIPMENT,
  EXERCISE_MUSCLE_GROUPS,
  type ExerciseCategory,
  type ExerciseDifficultyLevel,
  type ExerciseDto,
  type ExerciseEquipment,
  type ExerciseMuscleGroup,
} from "../../types/api";
import { ExerciseCategoryBadge } from "./ExerciseListPage";

export default function ExerciseDetailPage() {
  const { exerciseId } = useParams<{ exerciseId: string }>();
  const navigate = useNavigate();
  const [exercise, setExercise] = useState<ExerciseDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<CreateExerciseFormValues>({ resolver: zodResolver(createExerciseSchema) });

  useEffect(() => {
    if (!exerciseId) return;
    let cancelled = false;
    getExercise(exerciseId)
      .then((data) => {
        if (cancelled) return;
        setExercise(data);
        reset({
          name: data.name,
          description: data.description ?? "",
          category: data.category,
          primaryMuscleGroup: data.primaryMuscleGroup,
          equipment: data.equipment,
          difficultyLevel: data.difficultyLevel,
          videoUrl: data.videoUrl ?? "",
        });
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this exercise.");
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [exerciseId, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!exerciseId || !exercise) return;
    setFormError(null);
    try {
      const updated = await updateExercise(exerciseId, {
        name: values.name,
        description: blankToUndefined(values.description),
        category: values.category as ExerciseCategory,
        primaryMuscleGroup: values.primaryMuscleGroup as ExerciseMuscleGroup,
        equipment: values.equipment as ExerciseEquipment,
        difficultyLevel: values.difficultyLevel as ExerciseDifficultyLevel,
        videoUrl: blankToUndefined(values.videoUrl),
        active: exercise.active,
      });
      setExercise(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function toggleActive() {
    if (!exerciseId || !exercise) return;
    try {
      const updated = await updateExercise(exerciseId, {
        name: exercise.name,
        description: exercise.description ?? undefined,
        category: exercise.category,
        primaryMuscleGroup: exercise.primaryMuscleGroup,
        equipment: exercise.equipment,
        difficultyLevel: exercise.difficultyLevel,
        videoUrl: exercise.videoUrl ?? undefined,
        active: !exercise.active,
      });
      setExercise(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update this exercise.");
    }
  }

  async function handleDelete() {
    if (!exerciseId || !window.confirm("Delete this exercise?")) return;
    setIsDeleting(true);
    try {
      await deleteExercise(exerciseId);
      navigate("/exercises");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this exercise.");
      setIsDeleting(false);
    }
  }

  if (error && !exercise) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!exercise) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/exercises" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Exercise library
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{exercise.name}</h1>
            <ExerciseCategoryBadge category={exercise.category} />
            {exercise.active ? (
              <span className="inline-block rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-medium text-emerald-700">Active</span>
            ) : (
              <span className="inline-block rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-500">Inactive</span>
            )}
          </div>
        </div>
        <div className="flex gap-3">
          <Button variant="secondary" onClick={() => void toggleActive()}>
            {exercise.active ? "Deactivate" : "Activate"}
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

        <TextField label="Name" error={errors.name?.message} {...register("name")} />

        <div className="grid gap-4 sm:grid-cols-2">
          <Select
            label="Category"
            options={EXERCISE_CATEGORIES.map((category) => ({ value: category, label: category }))}
            error={errors.category?.message}
            {...register("category")}
          />
          <Select
            label="Primary muscle group"
            options={EXERCISE_MUSCLE_GROUPS.map((group) => ({ value: group, label: group.replace("_", " ") }))}
            error={errors.primaryMuscleGroup?.message}
            {...register("primaryMuscleGroup")}
          />
          <Select
            label="Equipment"
            options={EXERCISE_EQUIPMENT.map((equipment) => ({ value: equipment, label: equipment.replace("_", " ") }))}
            error={errors.equipment?.message}
            {...register("equipment")}
          />
          <Select
            label="Difficulty"
            options={EXERCISE_DIFFICULTY_LEVELS.map((level) => ({ value: level, label: level }))}
            error={errors.difficultyLevel?.message}
            {...register("difficultyLevel")}
          />
        </div>

        <TextField label="Video URL" error={errors.videoUrl?.message} {...register("videoUrl")} />

        <TextArea label="Description" error={errors.description?.message} {...register("description")} />

        <div className="flex justify-end">
          <Button type="submit" isLoading={isSubmitting}>
            Save changes
          </Button>
        </div>
      </form>
    </div>
  );
}
