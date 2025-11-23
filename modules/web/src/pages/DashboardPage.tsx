import React, { useState } from 'react';
import { DashboardLayout } from '../layouts/DashboardLayout';
import { SearchBar } from '../components/SearchBar';
import { SearchResults, type SearchResult } from '../components/SearchResults';

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

export const DashboardPage: React.FC = () => {
    const [query, setQuery] = useState('');
    const [results, setResults] = useState<SearchResult[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const [rerankingEnabled, setRerankingEnabled] = useState(false);

    const handleSearch = () => {
        if (!query.trim()) return;

        setIsLoading(true);
        // Simulate API call
        setTimeout(() => {
            setResults(MOCK_RESULTS); // In real app, we'd filter or fetch based on query
            setIsLoading(false);
        }, 800);
    };

    return (
        <DashboardLayout>
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
                    onSearch={handleSearch}
                    rerankingEnabled={rerankingEnabled}
                    setRerankingEnabled={setRerankingEnabled}
                    isLoading={isLoading}
                />

                <SearchResults results={results} />
            </div>
        </DashboardLayout>
    );
};
