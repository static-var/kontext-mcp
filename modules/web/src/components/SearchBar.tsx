import React from 'react';
import { Search, SlidersHorizontal } from 'lucide-react';
import { Input } from './ui/Input';
import { Button } from './ui/Button';
import { Switch } from './ui/Switch';

interface SearchBarProps {
    query: string;
    setQuery: (q: string) => void;
    onSearch: () => void;
    rerankingEnabled: boolean;
    setRerankingEnabled: (enabled: boolean) => void;
    isLoading: boolean;
}

export const SearchBar: React.FC<SearchBarProps> = ({
    query,
    setQuery,
    onSearch,
    rerankingEnabled,
    setRerankingEnabled,
    isLoading
}) => {
    const handleKeyDown = (e: React.KeyboardEvent) => {
        if (e.key === 'Enter') {
            onSearch();
        }
    };

    return (
        <div className="w-full max-w-3xl mx-auto space-y-4">
            <div className="relative group">
                <div className="absolute -inset-0.5 bg-gradient-to-r from-primary to-purple-600 rounded-lg blur opacity-20 group-hover:opacity-40 transition duration-500"></div>
                <div className="relative flex items-center">
                    <Search className="absolute left-4 h-5 w-5 text-muted-foreground" />
                    <Input
                        value={query}
                        onChange={(e) => setQuery(e.target.value)}
                        onKeyDown={handleKeyDown}
                        placeholder="Search documentation..."
                        className="pl-12 pr-32 h-14 text-lg bg-card border-border/50 shadow-xl placeholder:text-muted-foreground/50 focus:ring-0 focus:border-primary/50"
                    />
                    <div className="absolute right-2 flex items-center gap-2">
                        <Button
                            onClick={onSearch}
                            disabled={isLoading}
                            className="h-10 px-6 rounded-md bg-primary hover:bg-primary/90 text-white shadow-lg shadow-primary/20"
                        >
                            {isLoading ? "Searching..." : "Search"}
                        </Button>
                    </div>
                </div>
            </div>

            <div className="flex items-center justify-between px-2">
                <div className="flex items-center gap-4 text-sm text-muted-foreground">
                    <div className="flex items-center gap-2 bg-card/50 px-3 py-1.5 rounded-full border border-border/50">
                        <Switch
                            checked={rerankingEnabled}
                            onCheckedChange={setRerankingEnabled}
                            className="scale-75"
                        />
                        <span className={rerankingEnabled ? "text-primary font-medium" : ""}>
                            Reranking {rerankingEnabled ? "On" : "Off"}
                        </span>
                    </div>
                    <span className="text-xs opacity-50">Powered by BGE-Reranker</span>
                </div>

                <Button variant="ghost" size="sm" className="text-muted-foreground hover:text-foreground gap-2">
                    <SlidersHorizontal className="h-4 w-4" />
                    Filters
                </Button>
            </div>
        </div>
    );
};
