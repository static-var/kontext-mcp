import { useEffect, useMemo, useState } from 'react';
import { Activity, CheckCircle2, Loader2, RefreshCw } from 'lucide-react';
import { cn } from '../lib/utils';

type CrawlStatusResponse = {
    state: string;
    details: Record<string, string | null>;
};

type QueueSummaryPayload = {
    total: number;
    pending: number;
    inProgress: number;
    success: number;
    failed: number;
    disabled: number;
};

type SourceInsightPayload = {
    id: number;
    url: string;
    parserType: string;
    status: string;
    lastCrawled?: string | null;
    embeddingReady: boolean;
    errorMessage?: string | null;
};

type CrawlerInsightsPayload = {
    status: CrawlStatusResponse;
    queueSummary: QueueSummaryPayload;
    recentSources: SourceInsightPayload[];
};

const statusBadge = (status: string) => {
    switch (status) {
        case 'SUCCESS':
            return 'bg-emerald-500/15 text-emerald-400 border-emerald-500/30';
        case 'FAILED':
            return 'bg-rose-500/15 text-rose-400 border-rose-500/30';
        case 'IN_PROGRESS':
            return 'bg-amber-500/15 text-amber-400 border-amber-500/30';
        case 'PENDING':
            return 'bg-slate-500/15 text-slate-200 border-slate-500/30';
        default:
            return 'bg-slate-500/10 text-slate-300 border-slate-500/20';
    }
};

const formatTimestamp = (value?: string | null) => {
    if (!value) return '—';
    try {
        return new Date(value).toLocaleString();
    } catch {
        return value;
    }
};

export const CrawlerInsightsPage = () => {
    const [data, setData] = useState<CrawlerInsightsPayload | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [refreshing, setRefreshing] = useState(false);

    const fetchInsights = async () => {
        setRefreshing(true);
        try {
            const response = await fetch('/api/crawl/insights', {
                credentials: 'include',
                headers: { Accept: 'application/json' },
            });
            if (!response.ok) {
                throw new Error('Unable to load crawler insights');
            }
            const payload = (await response.json()) as CrawlerInsightsPayload;
            setData(payload);
            setError(null);
        } catch (err) {
            const message = err instanceof Error ? err.message : 'Unable to load crawler insights';
            setError(message);
        } finally {
            setLoading(false);
            setRefreshing(false);
        }
    };

    useEffect(() => {
        void fetchInsights();
        const interval = setInterval(() => void fetchInsights(), 6000);
        return () => clearInterval(interval);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const currentMessage = useMemo(() => data?.status.details?.message ?? null, [data]);

    const statusColor = useMemo(() => {
        switch (data?.status.state) {
            case 'running':
                return 'text-amber-400';
            case 'completed':
                return 'text-emerald-400';
            default:
                return 'text-muted-foreground';
        }
    }, [data]);

    return (
        <div className="max-w-6xl mx-auto py-10 space-y-10">
            <div className="flex flex-col gap-2">
                <h1 className="text-3xl font-bold tracking-tight">Crawler activity</h1>
                <p className="text-muted-foreground">Live crawl status, queue health, and the most recent documents processed.</p>
            </div>

            <div className="grid gap-4 md:grid-cols-2">
                <div className="rounded-2xl border border-border/60 bg-card/60 p-5 space-y-4">
                    <div className="flex items-center gap-3">
                        <Activity className="h-5 w-5 text-primary" />
                        <div>
                            <p className="text-xs uppercase tracking-wide text-muted-foreground">Crawler state</p>
                            <p className={cn('text-2xl font-semibold', statusColor)}>
                                {data?.status.state?.toUpperCase() || 'IDLE'}
                            </p>
                        </div>
                    </div>
                    <p className="text-sm text-muted-foreground min-h-[1.5rem]">
                        {currentMessage || 'No active jobs'}
                    </p>
                    <dl className="grid gap-3 text-xs text-muted-foreground">
                        {data &&
                            Object.entries(data.status.details || {}).map(([key, value]) => (
                                <div key={key} className="flex justify-between gap-4">
                                    <dt className="uppercase tracking-wide">{key}</dt>
                                    <dd className="text-foreground">{value || '—'}</dd>
                                </div>
                            ))}
                    </dl>
                    <button
                        type="button"
                        onClick={() => void fetchInsights()}
                        className="inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground"
                    >
                        <RefreshCw className={cn('h-4 w-4', refreshing && 'animate-spin')} /> Refresh
                    </button>
                </div>

                {data ? (
                    <div className="rounded-2xl border border-border/60 bg-card/60 p-5">
                        <p className="text-xs uppercase tracking-wide text-muted-foreground">Queue summary</p>
                        <div className="mt-4 grid gap-4 sm:grid-cols-2">
                            <Metric label="Tracked" value={data.queueSummary.total} />
                            <Metric label="Pending" value={data.queueSummary.pending} accent="text-amber-400" />
                            <Metric label="Running" value={data.queueSummary.inProgress} accent="text-sky-400" />
                            <Metric label="Failed" value={data.queueSummary.failed} accent="text-rose-400" />
                        </div>
                    </div>
                ) : (
                    <div className="rounded-2xl border border-border/60 bg-card/60 p-5 flex items-center justify-center">
                        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
                    </div>
                )}
            </div>

            {error && <div className="rounded-xl border border-rose-500/30 bg-rose-500/10 p-3 text-sm text-rose-200">{error}</div>}

            <div className="rounded-2xl border border-border/60 bg-card/60 p-6 space-y-4">
                <div className="flex items-center justify-between">
                    <div>
                        <h2 className="text-xl font-semibold">Recent sources</h2>
                        <p className="text-sm text-muted-foreground">Most recent 50 URLs processed or awaiting crawl.</p>
                    </div>
                </div>

                {loading ? (
                    <div className="flex h-40 items-center justify-center">
                        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
                    </div>
                ) : data ? (
                    <div className="overflow-x-auto rounded-2xl border border-border/40 bg-background/40">
                        <table className="min-w-full divide-y divide-border/40 text-sm">
                            <thead>
                                <tr className="text-left text-xs uppercase tracking-wide text-muted-foreground">
                                    <th className="px-4 py-3">URL</th>
                                    <th className="px-4 py-3">Status</th>
                                    <th className="px-4 py-3">Last event</th>
                                    <th className="px-4 py-3">Parser</th>
                                    <th className="px-4 py-3">Embeddings</th>
                                    <th className="px-4 py-3">Notes</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-border/30">
                                {data.recentSources.map((source) => (
                                    <tr key={source.id} className="hover:bg-white/5">
                                        <td className="px-4 py-3">
                                            <div className="max-w-xs truncate text-sm font-medium text-primary/90" title={source.url}>
                                                {source.url}
                                            </div>
                                        </td>
                                        <td className="px-4 py-3">
                                            <span className={cn('inline-flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-semibold', statusBadge(source.status))}>
                                                {source.status}
                                            </span>
                                        </td>
                                        <td className="px-4 py-3 text-sm">{formatTimestamp(source.lastCrawled)}</td>
                                        <td className="px-4 py-3 text-xs text-muted-foreground">{source.parserType}</td>
                                        <td className="px-4 py-3">
                                            {source.embeddingReady ? (
                                                <span className="inline-flex items-center gap-1 text-emerald-400">
                                                    <CheckCircle2 className="h-4 w-4" /> Ready
                                                </span>
                                            ) : (
                                                <span className="text-muted-foreground">Pending</span>
                                            )}
                                        </td>
                                        <td className="px-4 py-3 text-xs text-rose-300/80">{source.errorMessage || '—'}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                ) : (
                    <div className="text-sm text-muted-foreground">No crawl data yet.</div>
                )}
            </div>
        </div>
    );
};

const Metric = ({ label, value, accent = 'text-foreground' }: { label: string; value: number; accent?: string }) => (
    <div>
        <p className="text-xs uppercase tracking-wide text-muted-foreground">{label}</p>
        <p className={cn('mt-1 text-3xl font-semibold', accent)}>{value}</p>
    </div>
);
