import { Button } from "./Button";

interface PaginationProps {
  pageNumber: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  totalElements: number;
  onPageChange: (page: number) => void;
}

/** Zero-indexed page control matching the backend's PageResponse (pageNumber/first/last/totalElements). */
export function Pagination({ pageNumber, totalPages, first, last, totalElements, onPageChange }: PaginationProps) {
  if (totalElements === 0) return null;

  return (
    <div className="flex items-center justify-between border-t border-slate-200 pt-4">
      <p className="text-sm text-slate-500">
        Page {pageNumber + 1} of {Math.max(totalPages, 1)} &middot; {totalElements} total
      </p>
      <div className="flex gap-2">
        <Button variant="secondary" disabled={first} onClick={() => onPageChange(pageNumber - 1)}>
          Previous
        </Button>
        <Button variant="secondary" disabled={last} onClick={() => onPageChange(pageNumber + 1)}>
          Next
        </Button>
      </div>
    </div>
  );
}
