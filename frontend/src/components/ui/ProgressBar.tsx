/** A goal's percentComplete rendered as a filled bar - capped visually at 100% width even though the underlying number can exceed 100 (see SalesGoalDto#percentComplete's own comment on the backend: exceeding a quota is real and shown exactly as it is, just not by overflowing the bar). */
export function ProgressBar({ percent }: { percent: number }) {
  const widthPercent = Math.max(0, Math.min(100, percent));
  const isOverTarget = percent >= 100;

  return (
    <div className="flex items-center gap-2">
      <div className="h-2 flex-1 overflow-hidden rounded-full bg-slate-100">
        <div
          className={`h-full rounded-full transition-all ${isOverTarget ? "bg-emerald-600" : "bg-slate-900"}`}
          style={{ width: `${widthPercent}%` }}
        />
      </div>
      <span className={`w-14 shrink-0 text-right text-xs font-medium ${isOverTarget ? "text-emerald-700" : "text-slate-500"}`}>
        {percent}%
      </span>
    </div>
  );
}
