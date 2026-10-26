import type { Notice } from "@/types/settlement";

export function Alert({ notice, onClose }: { notice: Notice; onClose: () => void }) {
  const tone =
    notice.tone === "error"
      ? "border-red-200 bg-red-50 text-red-900"
      : "border-emerald-200 bg-emerald-50 text-emerald-900";

  return (
    <div className={`flex items-start justify-between gap-4 rounded-lg border px-4 py-3 ${tone}`} role="alert">
      <p className="text-sm">{notice.message}</p>
      <button type="button" onClick={onClose} className="text-xs font-medium opacity-70 hover:opacity-100">
        Fechar
      </button>
    </div>
  );
}
