import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listCourses } from "../../api/courses";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { ApiError } from "../../lib/apiClient";
import type { CourseCategory, PageResponse, CourseDto } from "../../types/api";

const PAGE_SIZE = 20;

const CATEGORY_CLASSES: Record<CourseCategory, string> = {
  SALES: "bg-blue-100 text-blue-700",
  PRODUCT: "bg-violet-100 text-violet-700",
  COMPLIANCE: "bg-amber-100 text-amber-700",
  ONBOARDING: "bg-emerald-100 text-emerald-700",
  LEADERSHIP: "bg-rose-100 text-rose-700",
  TECHNICAL: "bg-slate-200 text-slate-700",
};

export function CourseCategoryBadge({ category }: { category: CourseCategory }) {
  return <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${CATEGORY_CLASSES[category]}`}>{category}</span>;
}

export default function CourseListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<CourseDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    listCourses({ page, size: PAGE_SIZE, sort: "title,asc" })
      .then((res) => {
        if (!cancelled) setResult(res);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load courses.");
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [page]);

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Training catalog</h1>
          <p className="mt-1 text-sm text-slate-500">Courses the team can be enrolled in, plus each course's passing bar.</p>
        </div>
        <Link to="/courses/new">
          <Button>New course</Button>
        </Link>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Title</th>
              <th className="px-4 py-3 font-medium">Category</th>
              <th className="px-4 py-3 font-medium">Duration</th>
              <th className="px-4 py-3 font-medium">Passing score</th>
              <th className="px-4 py-3 font-medium">Status</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {isLoading && (
              <tr>
                <td className="px-4 py-6 text-center text-slate-400" colSpan={5}>
                  Loading...
                </td>
              </tr>
            )}
            {!isLoading && result?.content.length === 0 && (
              <tr>
                <td className="px-4 py-6 text-center text-slate-400" colSpan={5}>
                  No courses yet.
                </td>
              </tr>
            )}
            {result?.content.map((course) => (
              <tr key={course.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/courses/${course.id}`} className="font-medium text-slate-900 hover:underline">
                    {course.title}
                  </Link>
                </td>
                <td className="px-4 py-3">
                  <CourseCategoryBadge category={course.category} />
                </td>
                <td className="px-4 py-3 text-slate-600">{course.durationMinutes} min</td>
                <td className="px-4 py-3 text-slate-600">{course.passingScorePercent}%</td>
                <td className="px-4 py-3">
                  {course.active ? (
                    <span className="inline-block rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-medium text-emerald-700">Active</span>
                  ) : (
                    <span className="inline-block rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-500">Inactive</span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {result && (
        <Pagination
          pageNumber={result.pageNumber}
          totalPages={result.totalPages}
          first={result.first}
          last={result.last}
          totalElements={result.totalElements}
          onPageChange={setPage}
        />
      )}
    </div>
  );
}
