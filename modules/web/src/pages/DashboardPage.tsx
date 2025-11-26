import React, { useMemo, useState } from 'react';
import { DashboardLayout, type DashboardSection } from '../layouts/DashboardLayout';
import { SearchBar } from '../components/SearchBar';
import { SearchResults, type SearchResult } from '../components/SearchResults';
import { Settings, ShieldAlert, RefreshCw, Cpu, BarChart3, Search } from 'lucide-react';
import { cn } from '../lib/utils';
import { QueuePage } from './QueuePage';
import { CrawlerInsightsPage } from './CrawlerInsightsPage';
import { DocsPage } from './DocsPage';

interface DashboardPageProps {
    onLogout: () => Promise<void> | void;
}

export const DashboardPage: React.FC<DashboardPageProps> = ({ onLogout }) => {
    const [query, setQuery] = useState('');
    const [results, setResults] = useState<SearchResult[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const [rerankingEnabled, setRerankingEnabled] = useState(true);
    const [activeSection, setActiveSection] = useState<DashboardSection>('search');
    const [signingOut, setSigningOut] = useState(false);

    const handleSearch = async () => {
        if (!query.trim()) return;

        setIsLoading(true);
        try {
            const response = await fetch('/api/search', {
                method: 'POST',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                },
                body: JSON.stringify({
                    query: query,
                    tokenBudget: 2000,
                    similarityThreshold: 0.7
                })
            });

            if (!response.ok) {
                throw new Error('Search failed');
            }

            const data = await response.json();
            
            // Transform backend response to frontend format
            const searchResults: SearchResult[] = data.chunks.map((chunk: any, index: number) => ({
                id: `${index}`,
                title: chunk.source,
                content: chunk.content,
                url: chunk.source,
                score: chunk.similarity,
                metadata: chunk.metadata
            }));

            setResults(searchResults);
        } catch (err) {
            console.error('Search error:', err);
            setResults([]);
        } finally {
            setIsLoading(false);
        }
    };

    const handleSignOut = async () => {
        if (signingOut) return;
        setSigningOut(true);
        try {
            await onLogout();
        } finally {
            setSigningOut(false);
        }
    };

    const currentSectionContent = useMemo(() => {
        switch (activeSection) {
            case 'overview':
                return <OverviewPanel />;
            case 'settings':
                return <SettingsPanel />;
            case 'queue':
                return <QueuePage />;
            case 'activity':
                return <CrawlerInsightsPage />;
            case 'docs':
                return <DocsPage />;
            default:
                return (
                    <SearchSection
                        query={query}
                        setQuery={setQuery}
                        rerankingEnabled={rerankingEnabled}
                        setRerankingEnabled={setRerankingEnabled}
                        onSearch={handleSearch}
                        isLoading={isLoading}
                        results={results}
                    />
                );
        }
    }, [activeSection, query, rerankingEnabled, isLoading, results]);

    return (
        <DashboardLayout
            activeSection={activeSection}
            onSectionChange={setActiveSection}
            onSignOut={handleSignOut}
            signingOut={signingOut}
        >
            {currentSectionContent}
        </DashboardLayout>
    );
};

const SearchSection: React.FC<{
    query: string;
    setQuery: (value: string) => void;
    rerankingEnabled: boolean;
    setRerankingEnabled: (value: boolean) => void;
    onSearch: () => void;
    isLoading: boolean;
    results: SearchResult[];
}> = ({
    query,
    setQuery,
    rerankingEnabled,
    setRerankingEnabled,
    onSearch,
    isLoading,
    results,
}) => (
    <div className="max-w-5xl mx-auto py-12 space-y-12">
        <div className="text-center space-y-4">
            <h1 className="text-4xl font-bold tracking-tight bg-clip-text text-transparent bg-gradient-to-r from-foreground to-foreground/70">
                Documentation Search
            </h1>
            <p className="text-lg text-muted-foreground max-w-2xl mx-auto">
                Search across all project documentation, architecture decision records, and code references using semantic search.
            </p>
        </div>

        <SearchBar
            query={query}
            setQuery={setQuery}
            onSearch={onSearch}
            rerankingEnabled={rerankingEnabled}
            setRerankingEnabled={setRerankingEnabled}
            isLoading={isLoading}
        />

        <SearchResults results={results} />
    </div>
);

interface SystemStats {
    embeddingModel: string;
    embeddingDimension: number;
    embeddingQuantized: boolean;
    maxTokensPerChunk: number;
    rerankingEnabled: boolean;
    rerankingModel: string | null;
    rerankingQuantized: boolean | null;
    defaultTokenBudget: number;
    maxTokenBudget: number;
    defaultSimilarityThreshold: number;
    topKCandidates: number;
    targetChunkTokens: number;
    overlapTokens: number;
    totalSources: number;
    successfulSources: number;
    pendingSources: number;
    failedSources: number;
    totalDocuments: number;
    totalEmbeddings: number;
    estimatedEmbeddingSizeMB: number;
}

const OverviewPanel: React.FC = () => {
    const [stats, setStats] = React.useState<SystemStats | null>(null);
    const [loading, setLoading] = React.useState(true);
    const [error, setError] = React.useState<string | null>(null);

    React.useEffect(() => {
        const fetchStats = async () => {
            try {
                const response = await fetch('/api/stats', {
                    credentials: 'include',
                    headers: { Accept: 'application/json' }
                });
                if (!response.ok) throw new Error('Failed to fetch stats');
                const data = await response.json();
                setStats(data);
            } catch (err) {
                setError(err instanceof Error ? err.message : 'Unknown error');
            } finally {
                setLoading(false);
            }
        };
        fetchStats();
    }, []);

    if (loading) {
        return (
            <div className="max-w-5xl mx-auto py-12 flex items-center justify-center">
                <RefreshCw className="h-6 w-6 animate-spin text-primary" />
            </div>
        );
    }

    if (error || !stats) {
        return (
            <div className="max-w-5xl mx-auto py-12">
                <PanelCard className="text-center text-red-400">
                    Failed to load stats: {error}
                </PanelCard>
            </div>
        );
    }

    const formatNumber = (n: number) => n.toLocaleString();
    const formatStorage = (mb: number) => mb >= 1024 ? `${(mb / 1024).toFixed(2)} GB` : `${mb.toFixed(2)} MB`;

    return (
        <div className="max-w-5xl mx-auto py-12 space-y-8">
            <div>
                <h2 className="text-2xl font-semibold">System Overview</h2>
                <p className="text-muted-foreground">Real-time statistics from your Kontext instance.</p>
            </div>
            
            {/* Main Stats */}
            <div className="grid gap-4 md:grid-cols-4">
                <StatCard label="Total Sources" value={formatNumber(stats.totalSources)} trend={`${stats.successfulSources} indexed`} />
                <StatCard label="Documents" value={formatNumber(stats.totalDocuments)} trend="Parsed & stored" />
                <StatCard label="Embeddings" value={formatNumber(stats.totalEmbeddings)} trend={formatStorage(stats.estimatedEmbeddingSizeMB)} />
                <StatCard label="Pending/Failed" value={`${formatNumber(stats.pendingSources)}/${formatNumber(stats.failedSources)}`} trend="Queue status" />
            </div>
            
            {/* Performance Metrics */}
            <div className="grid gap-4 md:grid-cols-3">
                <div className="rounded-lg border border-border/50 bg-card/30 p-4">
                    <div className="text-xs text-muted-foreground uppercase tracking-wide mb-1">Avg Chunks per Doc</div>
                    <div className="text-2xl font-semibold">{stats.totalDocuments > 0 ? (stats.totalEmbeddings / stats.totalDocuments).toFixed(1) : '0'}</div>
                    <div className="text-xs text-muted-foreground mt-1">Chunking granularity</div>
                </div>
                <div className="rounded-lg border border-border/50 bg-card/30 p-4">
                    <div className="text-xs text-muted-foreground uppercase tracking-wide mb-1">Success Rate</div>
                    <div className="text-2xl font-semibold">{stats.totalSources > 0 ? ((stats.successfulSources / stats.totalSources) * 100).toFixed(1) : '0'}%</div>
                    <div className="text-xs text-muted-foreground mt-1">{formatNumber(stats.successfulSources)}/{formatNumber(stats.totalSources)} sources</div>
                </div>
                <div className="rounded-lg border border-border/50 bg-card/30 p-4">
                    <div className="text-xs text-muted-foreground uppercase tracking-wide mb-1">Avg Bytes per Chunk</div>
                    <div className="text-2xl font-semibold">{stats.totalEmbeddings > 0 ? ((stats.estimatedEmbeddingSizeMB * 1024 * 1024) / stats.totalEmbeddings).toFixed(0) : '0'} B</div>
                    <div className="text-xs text-muted-foreground mt-1">Storage efficiency</div>
                </div>
            </div>

            {/* Model Configuration */}
            <PanelCard className="space-y-4">
                <div className="flex items-center gap-2 text-primary">
                    <Cpu className="h-4 w-4" />
                    <span className="font-medium">Model Configuration</span>
                </div>
                <SectionDivider />
                <div className="grid md:grid-cols-2 gap-4 text-sm">
                    <div className="space-y-2">
                        <ConfigItem label="Embedding Model" value={stats.embeddingModel} />
                        <ConfigItem label="Embedding Dimension" value={`${stats.embeddingDimension}d`} />
                        <ConfigItem label="Quantized (INT8)" value={stats.embeddingQuantized ? 'Yes' : 'No'} />
                        <ConfigItem label="Bytes per Embedding" value={`${(stats.embeddingDimension * (stats.embeddingQuantized ? 1 : 4)).toLocaleString()} B`} />
                    </div>
                    <div className="space-y-2">
                        <ConfigItem label="Reranking Enabled" value={stats.rerankingEnabled ? 'Yes' : 'No'} />
                        {stats.rerankingModel && <ConfigItem label="Reranking Model" value={stats.rerankingModel} />}
                        {stats.rerankingQuantized !== null && <ConfigItem label="Reranker Quantized" value={stats.rerankingQuantized ? 'Yes' : 'No'} />}
                        <ConfigItem label="Similarity Threshold" value={stats.defaultSimilarityThreshold.toFixed(2)} />
                    </div>
                </div>
                <div className="text-xs text-muted-foreground pt-2 border-t border-border/50">
                    <p>💡 Cosine similarity range: 0.0 (unrelated) to 1.0 (identical). Threshold filters low-relevance chunks.</p>
                </div>
            </PanelCard>

            {/* Search Configuration */}
            <PanelCard className="space-y-4">
                <div className="flex items-center gap-2 text-primary">
                    <Search className="h-4 w-4" />
                    <span className="font-medium">Search & Chunking Configuration</span>
                </div>
                <SectionDivider />
                <div className="grid md:grid-cols-2 gap-4 text-sm">
                    <div className="space-y-2">
                        <ConfigItem label="Default Token Budget" value={formatNumber(stats.defaultTokenBudget)} />
                        <ConfigItem label="Max Token Budget" value={formatNumber(stats.maxTokenBudget)} />
                        <ConfigItem label="Top K Candidates" value={formatNumber(stats.topKCandidates)} />
                        <ConfigItem label="Reranking Multiplier" value={stats.rerankingEnabled ? `${stats.topKCandidates}×5 → ${stats.topKCandidates}` : 'N/A'} />
                    </div>
                    <div className="space-y-2">
                        <ConfigItem label="Target Chunk Tokens" value={formatNumber(stats.targetChunkTokens)} />
                        <ConfigItem label="Overlap Tokens" value={formatNumber(stats.overlapTokens)} />
                        <ConfigItem label="Max Tokens Per Chunk" value={formatNumber(stats.maxTokensPerChunk)} />
                        <ConfigItem label="Overlap %" value={`${((stats.overlapTokens / stats.targetChunkTokens) * 100).toFixed(1)}%`} />
                    </div>
                </div>
                <div className="text-xs text-muted-foreground pt-2 border-t border-border/50">
                    <p>💡 Token budgets control response size. Reranking fetches {stats.rerankingEnabled ? `${stats.topKCandidates * 5}` : stats.topKCandidates} candidates, then re-scores with cross-encoder for precision.</p>
                </div>
            </PanelCard>

            {/* Source Status Breakdown */}
            <PanelCard className="space-y-4">
                <div className="flex items-center gap-2 text-primary">
                    <BarChart3 className="h-4 w-4" />
                    <span className="font-medium">Sources by Status</span>
                </div>
                <SectionDivider />
                <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
                    <div className="text-center p-3 rounded-lg bg-background/50">
                        <p className="text-2xl font-semibold">{stats.successfulSources}</p>
                        <p className="text-xs text-muted-foreground uppercase tracking-wide">SUCCESS</p>
                    </div>
                    <div className="text-center p-3 rounded-lg bg-background/50">
                        <p className="text-2xl font-semibold">{stats.pendingSources}</p>
                        <p className="text-xs text-muted-foreground uppercase tracking-wide">PENDING</p>
                    </div>
                    <div className="text-center p-3 rounded-lg bg-background/50">
                        <p className="text-2xl font-semibold">{stats.failedSources}</p>
                        <p className="text-xs text-muted-foreground uppercase tracking-wide">FAILED</p>
                    </div>
                    <div className="text-center p-3 rounded-lg bg-background/50">
                        <p className="text-2xl font-semibold">{stats.totalSources}</p>
                        <p className="text-xs text-muted-foreground uppercase tracking-wide">TOTAL</p>
                    </div>
                </div>
            </PanelCard>
        </div>
    );
};

const ConfigItem: React.FC<{ label: string; value: string }> = ({ label, value }) => (
    <div className="flex justify-between">
        <span className="text-muted-foreground">{label}</span>
        <span className="font-mono text-foreground">{value}</span>
    </div>
);

const SettingsPanel: React.FC = () => (
    <div className="max-w-3xl mx-auto py-12 space-y-6">
        <div className="flex items-center gap-3">
            <Settings className="h-5 w-5 text-primary" />
            <div>
                <h2 className="text-2xl font-semibold">Workspace settings</h2>
                <p className="text-muted-foreground">Coming soon — manage credentials, connectors, and safety checks.</p>
            </div>
        </div>
        <PanelCard className="space-y-4">
            <div className="flex items-center gap-2 text-amber-500">
                <ShieldAlert className="h-4 w-4" />
                <span className="font-medium">Read-only preview</span>
            </div>
            <p className="text-sm text-muted-foreground">
                This UI focuses on the search experience. Administrative controls will be added as the MCP crawler matures. Use the CLI or API to manage
                schedules in the meantime.
            </p>
        </PanelCard>
    </div>
);

const StatCard: React.FC<{ label: string; value: string; trend: string }> = ({ label, value, trend }) => (
    <PanelCard className="space-y-2">
        <p className="text-sm text-muted-foreground">{label}</p>
        <p className="text-3xl font-semibold">{value}</p>
        <p className="text-xs text-primary/80">{trend}</p>
    </PanelCard>
);

interface PanelCardProps {
    className?: string;
    children: React.ReactNode;
}

const PanelCard: React.FC<PanelCardProps> = ({ className, children }) => (
    <div className={cn('rounded-2xl border border-border/60 bg-card/50 p-6 shadow-lg shadow-black/10', className)}>{children}</div>
);

const SectionDivider = () => <div className="h-px w-full bg-border" role="presentation" />;
