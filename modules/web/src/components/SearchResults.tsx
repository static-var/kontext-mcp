import React from 'react';
import { ExternalLink, FileText, Zap } from 'lucide-react';
import { motion } from 'framer-motion';

export interface SearchResult {
    id: string;
    title: string;
    content: string;
    url: string;
    score: number;
    metadata?: Record<string, string>;
}

interface SearchResultsProps {
    results: SearchResult[];
}

export const SearchResults: React.FC<SearchResultsProps> = ({ results }) => {
    if (results.length === 0) return null;

    return (
        <div className="w-full max-w-3xl mx-auto mt-8 space-y-6">
            <h3 className="text-sm font-medium text-muted-foreground uppercase tracking-wider ml-1">
                Top Results ({results.length})
            </h3>

            <div className="space-y-4">
                {results.map((result, index) => (
                    <motion.div
                        key={result.id}
                        initial={{ opacity: 0, y: 20 }}
                        animate={{ opacity: 1, y: 0 }}
                        transition={{ delay: index * 0.1 }}
                        className="group relative bg-card hover:bg-accent/5 border border-border/50 hover:border-primary/20 rounded-xl p-6 transition-all hover:shadow-lg hover:shadow-primary/5"
                    >
                        <div className="flex items-start justify-between gap-4">
                            <div className="space-y-2 flex-1">
                                <div className="flex items-center gap-2">
                                    <div className="p-2 rounded-lg bg-primary/10 text-primary">
                                        <FileText className="h-4 w-4" />
                                    </div>
                                    <a
                                        href={result.url}
                                        target="_blank"
                                        rel="noopener noreferrer"
                                        className="font-semibold text-lg text-foreground hover:text-primary transition-colors flex items-center gap-2"
                                    >
                                        {result.title}
                                        <ExternalLink className="h-3 w-3 opacity-0 group-hover:opacity-50 transition-opacity" />
                                    </a>
                                </div>

                                <p className="text-muted-foreground text-sm leading-relaxed line-clamp-3">
                                    {result.content}
                                </p>

                                <div className="flex items-center gap-3 pt-2">
                                    {Object.entries(result.metadata || {}).map(([key, value]) => (
                                        <span key={key} className="inline-flex items-center px-2 py-1 rounded-md bg-secondary text-xs font-medium text-secondary-foreground">
                                            {key}: {value}
                                        </span>
                                    ))}
                                </div>
                            </div>

                            <div className="flex flex-col items-end gap-1">
                                <div className="flex items-center gap-1 text-xs font-medium text-muted-foreground bg-secondary/50 px-2 py-1 rounded-md">
                                    <Zap className="h-3 w-3 text-yellow-500" />
                                    {(result.score * 100).toFixed(1)}% match
                                </div>
                            </div>
                        </div>
                    </motion.div>
                ))}
            </div>
        </div>
    );
};
