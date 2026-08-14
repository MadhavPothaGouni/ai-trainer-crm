import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { createExercise } from "../../api/exercises";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createExerciseSchema, type CreateExerciseFormValues } from "../../lib/validation";
import {
  EXERCISE_CATEGORIES,
  EXERCISE_DIFFICULTY_LEVELS,
  EXERCISE_EQUIPMENT,
  EXERCISE_MUSCLE_GROUPS,
  type ExerciseCategory,
  type ExerciseDifficultyLevel,
  type ExerciseEquipment,
  type ExerciseMuscleGroup,
} from "../../types/api";

export default function ExerciseCreatePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateExerciseFormValues>({
    resolver: zodResolver(createExerciseSchema),
    defaultValues: { category: "STRENGTH", primaryMuscleGroup: "FULL_BODY", equipment: "NONE", difficultyLevel: "BEGINNER" },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const exercise = await createExercise({
        name: values.name,
        description: blankToUndefined(values.description),
        category: values.category as ExerciseCategory,
        primaryMuscleGroup: values.primaryMuscleGroup as ExerciseMuscleGroup,
        equipment: values.equipment as ExerciseEquipment,
        difficultyLevel: values.difficultyLevel as ExerciseDifficultyLevel,
        videoUrl: blankToUndefined(values.videoUrl),
      });
      navigate(`/exercises/${exercise.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New exercise</h1>
        <p className="mt-1 text-sm text-slate-500">Add a movement to the exercise library.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
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

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/exercises")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Create exercise
          </Button>
        </div>
      </form>
    </div>
  );
}
