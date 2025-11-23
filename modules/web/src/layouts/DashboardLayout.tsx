import React from 'react';
import { LayoutDashboard, Search, Settings, LogOut, Database, ListPlus, Activity } from 'lucide-react';
import { cn } from '../lib/utils';

export type DashboardSection = 'search' | 'overview' | 'settings' | 'queue' | 'activity';

interface DashboardLayoutProps {
    children: React.ReactNode;
    activeSection: DashboardSection;
    onSectionChange: (section: DashboardSection) => void;
    onSignOut: () => void;
    signingOut?: boolean;
}

export const DashboardLayout: React.FC<DashboardLayoutProps> = ({
    children,
    activeSection,
    onSectionChange,
    onSignOut,
    signingOut = false,
}) => {
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
                    <NavItem
                        icon={<Search className="h-5 w-5" />}
                        label="Search"
                        active={activeSection === 'search'}
                        onClick={() => onSectionChange('search')}
                    />
                    <NavItem
                        icon={<LayoutDashboard className="h-5 w-5" />}
                        label="Overview"
                        active={activeSection === 'overview'}
                        onClick={() => onSectionChange('overview')}
                    />
                    <NavItem
                        icon={<Settings className="h-5 w-5" />}
                        label="Settings"
                        active={activeSection === 'settings'}
                        onClick={() => onSectionChange('settings')}
                    />
                    <NavItem
                        icon={<ListPlus className="h-5 w-5" />}
                        label="Queue URLs"
                        active={activeSection === 'queue'}
                        onClick={() => onSectionChange('queue')}
                    />
                    <NavItem
                        icon={<Activity className="h-5 w-5" />}
                        label="Crawler state"
                        active={activeSection === 'activity'}
                        onClick={() => onSectionChange('activity')}
                    />
                </nav>

                <div className="p-4 border-t border-border">
                    <button
                        type="button"
                        onClick={onSignOut}
                        disabled={signingOut}
                        className={cn(
                            'flex items-center gap-3 w-full px-4 py-3 text-sm font-medium rounded-lg transition-colors',
                            signingOut
                                ? 'text-muted-foreground/60 cursor-not-allowed'
                                : 'text-muted-foreground hover:text-foreground hover:bg-accent'
                        )}
                    >
                        <LogOut className="h-5 w-5" />
                        <span>{signingOut ? 'Signing out…' : 'Sign Out'}</span>
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

const NavItem = ({
    icon,
    label,
    active = false,
    onClick,
}: {
    icon: React.ReactNode;
    label: string;
    active?: boolean;
    onClick?: () => void;
}) => (
    <button
        type="button"
        onClick={onClick}
        className={cn(
            'flex items-center gap-3 w-full px-4 py-3 text-sm font-medium rounded-lg transition-all',
            active ? 'bg-primary/10 text-primary' : 'text-muted-foreground hover:text-foreground hover:bg-accent'
        )}
        aria-current={active ? 'page' : undefined}
    >
        {icon}
        <span>{label}</span>
    </button>
);
