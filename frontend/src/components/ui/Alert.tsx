interface AlertProps {
  variant?: "error" | "success" | "info";
  children: React.ReactNode;
}

const VARIANT_CLASSES: Record<NonNullable<AlertProps["variant"]>, string> = {
  error: "bg-red-50 text-red-800 border-red-200",
  success: "bg-emerald-50 text-emerald-800 border-emerald-200",
  info: "bg-blue-50 text-blue-800 border-blue-200",
};

export function Alert({ variant = "info", children }: AlertProps) {
  return (
    <div role="alert" className={`rounded-md border px-3 py-2 text-sm ${VARIANT_CLASSES[variant]}`}>
      {children}
    </div>
  );
}
