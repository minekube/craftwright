import Link from 'next/link';

export default function HomePage() {
  return (
    <main className="mx-auto flex w-full max-w-5xl flex-1 flex-col justify-center gap-8 px-6 py-20">
      <div className="max-w-3xl">
        <p className="mb-3 text-sm font-medium text-fd-muted-foreground">Craftless</p>
        <h1 className="mb-4 text-4xl font-semibold tracking-normal sm:text-5xl">
          Generated APIs for real Minecraft Java clients
        </h1>
        <p className="text-lg text-fd-muted-foreground">
          Craftless exposes local OpenAPI contracts for lifecycle, discovery, streams,
          and generated per-client automation surfaces.
        </p>
      </div>
      <div className="flex flex-wrap gap-3">
        <Link className="rounded-md bg-fd-primary px-4 py-2 text-sm font-medium text-fd-primary-foreground" href="/docs">
          Read Docs
        </Link>
        <Link className="rounded-md border px-4 py-2 text-sm font-medium" href="/docs/api-reference">
          API Reference
        </Link>
      </div>
    </main>
  );
}
