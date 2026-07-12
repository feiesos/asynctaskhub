import type { ReactNode } from 'react';

interface PageShellProps {
  title: string;
  description?: string;
  children: ReactNode;
}

function PageShell({ title, description, children }: PageShellProps) {
  return (
    <div className="min-h-screen bg-zinc-950 px-4 py-10 text-zinc-100 sm:px-6 lg:px-8">
      <div className="mx-auto flex max-w-5xl flex-col gap-6">
        <header className="rounded-xl border border-zinc-800 bg-zinc-900/80 p-6 shadow-sm shadow-black/20">
          <p className="text-sm font-medium uppercase tracking-[0.2em] text-zinc-400">AsyncTaskHub</p>
          <h1 className="mt-2 text-2xl font-semibold text-white">{title}</h1>
          {description ? <p className="mt-3 max-w-2xl text-sm leading-6 text-zinc-400">{description}</p> : null}
        </header>
        {children}
      </div>
    </div>
  );
}

export default PageShell;
