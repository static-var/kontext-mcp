import { useEffect, useMemo, useState } from 'react';
import { AlertTriangle, CheckCircle2, Loader2, RefreshCw, Upload } from 'lucide-react';
import { cn } from '../lib/utils';

type UrlRecord = {
    id: number;
    url: string;
    parserType: string;
    status: string;
    lastCrawled?: string | null;
    etag?: string | null;
    lastModified?: string | null;
    errorMessage?: string | null;
};

type BulkFailure = {
    url: string;
    reason: string;
};

type BulkResponse = {
    successes: UrlRecord[];
    failures: BulkFailure[];
};

const statusClass = (status: string) => {
    switch (status) {
        case 'SUCCESS':
            return 'bg-emerald-500/15 text-emerald-400 border-emerald-500/30';
        case 'IN_PROGRESS':
            return 'bg-amber-500/15 text-amber-400 border-amber-500/30';
        case 'FAILED':
            return 'bg-rose-500/15 text-rose-400 border-rose-500/30';
        case 'DISABLED':
            return 'bg-gray-500/15 text-gray-400 border-gray-500/30';
        default:
            return 'bg-slate-500/10 text-slate-300 border-slate-500/20';
    }
};

const formatDateTime = (value?: string | null) => {
    if (!value) return '—';
    try {
        return new Date(value).toLocaleString();
    } catch {
        return value;
    }
};

const parseLine = (line: string): { url: string; parserType?: string } => {
    const cleaned = line.trim();
    if (!cleaned) {
        return { url: '' };
    }
    const delimiter = cleaned.includes('|') ? '|' : cleaned.includes(',') ? ',' : null;
    if (delimiter) {
        const [rawUrl, rawParser] = cleaned.split(delimiter);
        return {
            url: rawUrl.trim(),
            parserType: rawParser?.trim() || undefined,
        };
    }
    return { url: cleaned };
};

export const QueuePage = () => {
    const [records, setRecords] = useState<UrlRecord[]>([]);
    const [loading, setLoading] = useState(true);
    const [refreshing, setRefreshing] = useState(false);
    const [parserOptions, setParserOptions] = useState<string[]>([]);
    const [bulkInput, setBulkInput] = useState('');
    const [bulkParser, setBulkParser] = useState('');
    const [bulkResult, setBulkResult] = useState<BulkResponse | null>(null);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const fetchQueue = async () => {
        setRefreshing(true);
        try {
            const response = await fetch('/api/urls', {
                credentials: 'include',
                headers: { Accept: 'application/json' },
            });
            if (!response.ok) {
                throw new Error('Failed to load queue');
            }
            const data = (await response.json()) as UrlRecord[];
            setRecords(data);
            setError(null);
        } catch (err) {
            const message = err instanceof Error ? err.message : 'Failed to load queue';
            setError(message);
        } finally {
            setLoading(false);
            setRefreshing(false);
        }
    };

    const fetchParserOptions = async () => {
        try {
            const response = await fetch('/api/meta/parsers', {
                credentials: 'include',
                headers: { Accept: 'application/json' },
            });
            if (!response.ok) {
                return;
            }
            const data = (await response.json()) as string[];
            setParserOptions(data);
        } catch {
            // ignore optional metadata failure
        }
    };

    useEffect(() => {
        void fetchQueue();
        void fetchParserOptions();
    }, []);

    const queueSummary = useMemo(() => {
        return records.reduce(
            (acc, record) => {
                acc[record.status] = (acc[record.status] || 0) + 1;
                return acc;
            },
            {} as Record<string, number>,
        );
    }, [records]);

    const handleBulkSubmit = async () => {
        const lines = bulkInput.split('\n').map((line) => parseLine(line)).filter((entry) => entry.url);
        if (lines.length === 0) {
            setBulkResult(null);
            setError('Add at least one URL to queue.');
            return;
        }

        setSubmitting(true);
        setError(null);
        try {
            const entries = lines.map((entry) => ({
                url: entry.url,
                parserType: entry.parserType || bulkParser || undefined,
            }));

            const response = await fetch('/api/urls/bulk', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    Accept: 'application/json',
                },
                credentials: 'include',
                body: JSON.stringify({ entries }),
            });

            if (!response.ok) {
                throw new Error('Bulk enqueue failed');
            }

            const result = (await response.json()) as BulkResponse;
            setBulkResult(result);
            setBulkInput('');
            void fetchQueue();
        } catch (err) {
            const message = err instanceof Error ? err.message : 'Bulk enqueue failed';
            setError(message);
        } finally {
            setSubmitting(false);
        }
    };

    const totalQueued = records.length;

    return (
        <div className="max-w-6xl mx-auto py-10 space-y-10">
            <div className="space-y-2">
                <h1 className="text-3xl font-bold tracking-tight">Source queue</h1>
                <p className="text-muted-foreground max-w-2xl">
                    Paste URLs in bulk, optionally override parser detection, and monitor the pending crawl queue.
                </p>
            </div>

            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                <SummaryCard label="Tracked sources" value={totalQueued.toString()} />
                <SummaryCard label="Pending" value={(queueSummary['PENDING'] || 0).toString()} accent="text-amber-400" />
                <SummaryCard label="In progress" value={(queueSummary['IN_PROGRESS'] || 0).toString()} accent="text-sky-400" />
                <SummaryCard label="Failures" value={(queueSummary['FAILED'] || 0).toString()} accent="text-rose-400" />
            </div>

            <div className="grid gap-6 lg:grid-cols-[2fr,3fr]">
                <div className="rounded-2xl border border-border/60 bg-card/60 p-6 space-y-4">
                    <div className="flex items-center justify-between">
                        <div>
                            <h2 className="text-xl font-semibold">Bulk enqueue</h2>
                            <p className="text-sm text-muted-foreground">One URL per line. Optionally add “|PARSER_TYPE” to force a parser.</p>
                        </div>
                    </div>

                    <textarea
                        className="w-full min-h-[180px] rounded-xl border border-border/40 bg-background/60 p-4 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40"
                        placeholder={['https://developer.android.com/topic/architecture', 'https://kotlinlang.org/docs/home.html|KOTLIN_LANG'].join('\n')}
                        value={bulkInput}
                        onChange={(event) => setBulkInput(event.target.value)}
                    />

                    <div className="flex flex-col gap-3">
                        <label className="text-sm font-medium">Default parser override</label>
                        <select
                            className="rounded-lg border border-border/40 bg-background/70 px-3 py-2 text-sm"
                            value={bulkParser}
                            onChange={(event) => setBulkParser(event.target.value)}
                        >
                            <option value="">Auto-detect per URL</option>
                            {parserOptions.map((parser) => (
                                <option value={parser} key={parser}>
                                    {parser}
                                </option>
                            ))}
                        </select>
                    </div>

                    <button
                        type="button"
                        onClick={handleBulkSubmit}
                        disabled={submitting}
                        className={cn(
                            'inline-flex items-center justify-center gap-2 rounded-xl bg-primary px-4 py-2 text-sm font-semibold text-white transition hover:bg-primary/90 disabled:cursor-not-allowed disabled:bg-primary/60',
                        )}
                    >
                        {submitting ? (
                            <>
                                <Loader2 className="h-4 w-4 animate-spin" />
                                <span>Enqueuing…</span>
                            </>
                        ) : (
                            <>
                                <Upload className="h-4 w-4" />
                                <span>Queue URLs</span>
                            </>
                        )}
                    </button>

                    {bulkResult && (
                        <div className="rounded-xl border border-border/50 bg-background/60 p-4 text-sm space-y-2">
                            <p>
                                <CheckCircle2 className="mr-2 inline h-4 w-4 text-emerald-400" />
                                {bulkResult.successes.length} URL(s) queued or refreshed.
                            </p>
                            {bulkResult.failures.length > 0 && (
                                <div className="text-rose-400">
                                    <p className="flex items-center gap-2 font-medium">
                                        <AlertTriangle className="h-4 w-4" />
                                        {bulkResult.failures.length} URL(s) failed:
                                    </p>
                                    <ul className="mt-1 space-y-1 text-xs text-rose-300/90">
                                        {bulkResult.failures.slice(0, 5).map((failure) => (
                                            <li key={`${failure.url}-${failure.reason}`}>{failure.url} — {failure.reason}</li>
                                        ))}
                                        {bulkResult.failures.length > 5 && <li>…and more</li>}
                                    </ul>
                                </div>
                            )}
                        </div>
                    )}

                    {error && (
                        <div className="rounded-xl border border-rose-500/30 bg-rose-500/10 p-3 text-sm text-rose-200">{error}</div>
                    )}
                </div>

                <div className="rounded-2xl border border-border/60 bg-card/60 p-6 space-y-4">
                    <div className="flex items-center justify-between">
                        <div>
                            <h2 className="text-xl font-semibold">Queue snapshot</h2>
                            <p className="text-sm text-muted-foreground">Latest {Math.min(records.length, 250)} sources.</p>
                        </div>
                        <button
                            type="button"
                            className="inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground"
                            onClick={() => void fetchQueue()}
                            disabled={refreshing}
                        >
                            <RefreshCw className={cn('h-4 w-4', refreshing && 'animate-spin')} /> Refresh
                        </button>
                    </div>

                    {loading ? (
                        <div className="flex h-40 items-center justify-center">
                            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
                        </div>
                    ) : (
                        <div className="overflow-x-auto rounded-2xl border border-border/40 bg-background/40">
                            <table className="min-w-full divide-y divide-border/40 text-sm">
                                <thead>
                                    <tr className="text-left text-xs uppercase tracking-wide text-muted-foreground">
                                        <th className="px-4 py-3">URL</th>
                                        <th className="px-4 py-3">Parser</th>
                                        <th className="px-4 py-3">Status</th>
                                        <th className="px-4 py-3">Last crawled</th>
                                        <th className="px-4 py-3">Embeddings</th>
                                        <th className="px-4 py-3">Notes</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-border/30">
                                    {records.map((record) => (
                                        <tr key={record.id} className="hover:bg-white/5">
                                            <td className="px-4 py-3">
                                                <div className="max-w-xs truncate text-sm font-medium text-primary/90" title={record.url}>
                                                    {record.url}
                                                </div>
                                            </td>
                                            <td className="px-4 py-3 text-xs text-muted-foreground">{record.parserType}</td>
                                            <td className="px-4 py-3">
                                                <span className={cn('inline-flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-semibold', statusClass(record.status))}>
                                                    {record.status}
                                                </span>
                                            </td>
                                            <td className="px-4 py-3 text-sm">{formatDateTime(record.lastCrawled)}</td>
                                            <td className="px-4 py-3">
                                                {record.status === 'SUCCESS' ? (
                                                    <span className="inline-flex items-center gap-1 text-emerald-400">
                                                        <CheckCircle2 className="h-4 w-4" /> Ready
                                                    </span>
                                                ) : (
                                                    <span className="text-muted-foreground">Pending</span>
                                                )}
                                            </td>
                                            <td className="px-4 py-3 text-xs text-rose-300/80">
                                                {record.errorMessage ? record.errorMessage : '—'}
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
};

const SummaryCard = ({ label, value, accent = 'text-primary' }: { label: string; value: string; accent?: string }) => (
    <div className="rounded-2xl border border-border/40 bg-card/60 p-4">
        <p className="text-xs uppercase tracking-wide text-muted-foreground">{label}</p>
        <p className={cn('mt-2 text-3xl font-semibold', accent)}>{value}</p>
    </div>
);
