import React from 'react';
import { LayoutDashboard, Search, Settings, LogOut, Database } from 'lucide-react';
import { cn } from '../lib/utils';

interface DashboardLayoutProps {
    children: React.ReactNode;
}

export const DashboardLayout: React.FC<DashboardLayoutProps> = ({ children }) => {
    return (
        <div className="min-h-screen bg-background flex">
            {/* Sidebar */}
            <aside className="w-64 border-r border-border bg-card/50 backdrop-blur-xl hidden md:flex flex-col">
                <div className="p-6">
                    <div className="flex items-center gap-2 font-bold text-xl text-primary">
                        <Database className="h-6 w-6" />
                        <span>MCP Search</span>
                    </div>
                </div>

                <nav className="flex-1 px-4 space-y-2">
                    <NavItem icon={<Search className="h-5 w-5" />} label="Search" active />
                    <NavItem icon={<LayoutDashboard className="h-5 w-5" />} label="Overview" />
                    <NavItem icon={<Settings className="h-5 w-5" />} label="Settings" />
                </nav>

                <div className="p-4 border-t border-border">
                    <button className="flex items-center gap-3 w-full px-4 py-3 text-sm font-medium text-muted-foreground hover:text-foreground hover:bg-accent rounded-lg transition-colors">
                        <LogOut className="h-5 w-5" />
                        <span>Sign Out</span>
                    </button>
                </div>
            </aside>

            {/* Main Content */}
            <main className="flex-1 flex flex-col min-w-0 overflow-hidden">
                {/* Mobile Header could go here */}
                <div className="flex-1 overflow-y-auto p-4 md:p-8">
                    {children}
                </div>
            </main>
        </div>
    );
};

const NavItem = ({ icon, label, active = false }: { icon: React.ReactNode, label: string, active?: boolean }) => (
    <button
        className={cn(
            "flex items-center gap-3 w-full px-4 py-3 text-sm font-medium rounded-lg transition-all",
            active
                ? "bg-primary/10 text-primary"
                : "text-muted-foreground hover:text-foreground hover:bg-accent"
        )}
    >
        {icon}
        <span>{label}</span>
    </button>
);
