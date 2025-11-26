import React, { useEffect, useState } from 'react';
import { ExternalLink, FileText, Clock } from 'lucide-react';

type UrlPayload = {
    id: number;
    url: string;
    parserType: string;
    status: string;
    lastCrawled?: string;
    errorMessage?: string;
};

export const DocsPage: React.FC = () => {
    const [docs, setDocs] = useState<UrlPayload[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        const fetchDocs = async () => {
            try {
                const response = await fetch('/api/urls', {
                    credentials: 'include',
                    headers: { Accept: 'application/json' },
                });
                if (!response.ok) throw new Error('Failed to fetch documentation list');
                const data = await response.json();
                // Filter for successfully crawled docs
                setDocs(data.filter((d: UrlPayload) => d.status === 'SUCCESS'));
            } catch (err) {
                setError(err instanceof Error ? err.message : 'An error occurred');
            } finally {
                setLoading(false);
            }
        };

        fetchDocs();
    }, []);

    if (loading) {
        return (
            <div className="flex items-center justify-center h-64">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
            </div>
        );
    }

    if (error) {
        return (
            <div className="p-4 rounded-lg bg-destructive/10 text-destructive">
                Error: {error}
            </div>
        );
    }

    return (
        <div className="max-w-7xl mx-auto py-8 px-4 space-y-8">
            <div className="space-y-2">
                <h1 className="text-3xl font-bold tracking-tight">Documentation Library</h1>
                <p className="text-muted-foreground">
                    Browse all indexed documentation sources available for search.
                </p>
            </div>

            <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
                {docs.map((doc) => (
                    <div
                        key={doc.id}
                        className="group relative rounded-xl border border-border/50 bg-card/50 p-6 hover:bg-card/80 transition-colors"
                    >
                        <div className="flex items-start justify-between mb-4">
                            <div className="p-2 rounded-lg bg-primary/10 text-primary">
                                <FileText className="h-6 w-6" />
                            </div>
                            <a
                                href={doc.url}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="text-muted-foreground hover:text-primary transition-colors"
                            >
                                <ExternalLink className="h-5 w-5" />
                            </a>
                        </div>

                        <h3 className="font-semibold text-lg mb-2 line-clamp-2" title={doc.url}>
                            {doc.url}
                        </h3>

                        <div className="space-y-2 text-sm text-muted-foreground">
                            <div className="flex items-center gap-2">
                                <span className="px-2 py-0.5 rounded-full bg-secondary text-secondary-foreground text-xs font-medium">
                                    {doc.parserType}
                                </span>
                            </div>
                            
                            <div className="flex items-center gap-2 text-xs">
                                <Clock className="h-3 w-3" />
                                <span>Last updated: {doc.lastCrawled ? new Date(doc.lastCrawled).toLocaleDateString() : 'Never'}</span>
                            </div>
                        </div>
                    </div>
                ))}

                {docs.length === 0 && (
                    <div className="col-span-full text-center py-12 text-muted-foreground">
                        No documentation sources indexed yet. Add URLs in the Queue section.
                    </div>
                )}
            </div>
        </div>
    );
};
