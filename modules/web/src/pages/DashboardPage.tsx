import React, { useMemo, useState } from 'react';
import { DashboardLayout, type DashboardSection } from '../layouts/DashboardLayout';
import { SearchBar } from '../components/SearchBar';
import { SearchResults, type SearchResult } from '../components/SearchResults';
import { Settings, ShieldAlert, RefreshCw } from 'lucide-react';
import { cn } from '../lib/utils';
import { QueuePage } from './QueuePage';
import { CrawlerInsightsPage } from './CrawlerInsightsPage';

// Mock data for demonstration
const MOCK_RESULTS: SearchResult[] = [
    {
        id: '1',
        title: 'Introduction to MCP Server',
        content: 'The Model Context Protocol (MCP) server provides a standardized way to expose context to LLMs. It handles embedding, indexing, and retrieval of documentation.',
        url: 'https://mcp.dev/docs/intro',
        score: 0.95,
        metadata: { category: 'Documentation', version: '1.0' }
    },
    {
        id: '2',
        title: 'Reranking Service Architecture',
        content: 'The Reranking Service improves search relevance by using a cross-encoder model to re-score the top candidates retrieved by the vector search.',
        url: 'https://mcp.dev/docs/architecture/reranking',
        score: 0.88,
        metadata: { category: 'Architecture', type: 'Service' }
    },
    {
        id: '3',
        title: 'Configuring Embedding Models',
        content: 'You can configure the embedding model in application.conf. Supported models include BGE-M3 and other ONNX-compatible transformer models.',
        url: 'https://mcp.dev/docs/guides/configuration',
        score: 0.72,
        metadata: { category: 'Guide' }
    }
];

interface DashboardPageProps {
    onLogout: () => Promise<void> | void;
}

export const DashboardPage: React.FC<DashboardPageProps> = ({ onLogout }) => {
    const [query, setQuery] = useState('');
    const [results, setResults] = useState<SearchResult[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const [rerankingEnabled, setRerankingEnabled] = useState(false);
    const [activeSection, setActiveSection] = useState<DashboardSection>('search');
    const [signingOut, setSigningOut] = useState(false);

    const handleSearch = () => {
        if (!query.trim()) return;

        setIsLoading(true);
        // Simulate API call
        setTimeout(() => {
            setResults(MOCK_RESULTS); // In real app, we'd filter or fetch based on query
            setIsLoading(false);
        }, 800);
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

const OverviewPanel: React.FC = () => (
    <div className="max-w-5xl mx-auto py-12 space-y-8">
        <div>
            <h2 className="text-2xl font-semibold">Crawler overview</h2>
            <p className="text-muted-foreground">High-level signals from the latest crawl and indexing runs.</p>
        </div>
        <div className="grid gap-4 md:grid-cols-3">
            <StatCard label="Indexed sources" value="24" trend="↑ 6 new this week" />
            <StatCard label="Documents processed" value="12,481" trend="Stable" />
            <StatCard label="Pending jobs" value="2" trend="Scheduled" />
        </div>
        <PanelCard className="space-y-4">
            <div className="flex items-center gap-2 text-primary">
                <RefreshCw className="h-4 w-4" />
                <span className="font-medium">Last crawl summary</span>
            </div>
            <SectionDivider />
            <ul className="space-y-2 text-sm text-muted-foreground">
                <li>• Completed on Nov 22 at 22:14 UTC</li>
                <li>• 1 warning encountered (retry scheduled)</li>
                <li>• Average latency 420ms per document</li>
            </ul>
        </PanelCard>
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
