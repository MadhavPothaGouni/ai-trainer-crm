import { Link } from "react-router-dom";

export default function NotFoundPage() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-3 bg-slate-50 text-center">
      <p className="text-sm font-medium text-slate-400">404</p>
      <h1 className="text-xl font-semibold text-slate-900">Page not found</h1>
      <Link to="/" className="text-sm font-medium text-slate-900 hover:underline">
        Go back home
      </Link>
    </div>
  );
}
