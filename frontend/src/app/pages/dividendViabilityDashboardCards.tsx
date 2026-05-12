import React from 'react';

export function SnapshotCard({ label, value, note }: { label: string; value: string; note?: string }) {
  return (
    <div className="rounded-3xl border border-slate-200 bg-white p-4 shadow-sm">
      <p className="text-[11px] font-semibold uppercase tracking-[0.28em] text-slate-500">{label}</p>
      <p className="mt-3 text-2xl font-semibold text-slate-950">{value}</p>
      {note && <p className="mt-2 text-sm text-slate-500">{note}</p>}
    </div>
  );
}

export function TrendMetricCard({
  eyebrow,
  title,
  value,
  note,
  accentClassName = 'border-slate-200 bg-slate-50 text-slate-700',
}: {
  eyebrow: string;
  title: string;
  value: string;
  note: string;
  accentClassName?: string;
}) {
  return (
    <div className="rounded-3xl border border-slate-200 bg-white p-4 shadow-sm">
      <p className="text-[11px] font-semibold uppercase tracking-[0.24em] text-slate-500">{eyebrow}</p>
      <p className="mt-2 text-lg font-semibold text-slate-950">{title}</p>
      <p className="mt-3 text-2xl font-semibold text-slate-950">{value}</p>
      <p className={`mt-3 inline-flex rounded-full border px-3 py-1 text-xs font-medium ${accentClassName}`}>
        {note}
      </p>
    </div>
  );
}

export function DetailCard({
  title,
  icon: Icon,
  rows,
}: {
  title: string;
  icon: React.ComponentType<{ className?: string }>;
  rows: Array<{ label: string; value: string; note?: string }>;
}) {
  return (
    <section className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex items-center gap-3">
        <div className="rounded-2xl bg-slate-100 p-2 text-slate-700">
          <Icon className="h-5 w-5" />
        </div>
        <h2 className="text-lg font-semibold text-slate-950">{title}</h2>
      </div>
      <div className="mt-5 space-y-3">
        {rows.map((row) => (
          <div key={row.label} className="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3">
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="text-sm font-medium text-slate-700">{row.label}</p>
                {row.note && <p className="mt-1 text-xs text-slate-500">{row.note}</p>}
              </div>
              <p className="text-right text-sm font-semibold text-slate-950">{row.value}</p>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}

