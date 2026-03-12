import React from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Database, Search, Building2, Download, Settings, LayoutDashboard, Radio, Briefcase, Users, FileText, UserPlus, FileCheck, Globe, FileBarChart, TrendingUp, ChevronDown, Bell } from 'lucide-react';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from './ui/dropdown-menu';

export function Layout({ children }: { children: React.ReactNode }) {
  const location = useLocation();
  const searchParams = React.useMemo(() => new URLSearchParams(location.search), [location.search]);

  const primaryNavItems = [
    { path: '/', label: 'Dashboard', icon: LayoutDashboard },
    { path: '/search', label: 'Search', icon: Search },
    { path: '/companies', label: 'Companies', icon: Building2 },
    { path: '/remote-edgar', label: 'Remote', icon: Radio },
  ];

  const formNavItems = [
    { path: '/search', to: '/search?formType=10-K&autoSearch=1', label: '10-K', icon: FileText, isActive: location.pathname === '/search' && searchParams.get('formType') === '10-K' },
    { path: '/search', to: '/search?formType=10-Q&autoSearch=1', label: '10-Q', icon: FileText, isActive: location.pathname === '/search' && searchParams.get('formType') === '10-Q' },
    { path: '/form3', label: 'Form 3', icon: UserPlus },
    { path: '/form4', label: 'Form 4', icon: TrendingUp },
    { path: '/form5', label: 'Form 5', icon: FileCheck },
    { path: '/form6k', label: '6-K', icon: Globe },
    { path: '/form20f', label: '20-F', icon: FileBarChart },
    { path: '/form8k', label: '8-K', icon: FileText },
    { path: '/form13f', label: '13F', icon: Briefcase },
    { path: '/form13dg', label: '13D/G', icon: Users },
  ];

  const secondaryNavItems = [
    { path: '/alerts', label: 'Alerts', icon: Bell },
    { path: '/downloads', label: 'Downloads', icon: Download },
    { path: '/settings', label: 'Settings', icon: Settings }
  ];

  const isFormActive = formNavItems.some(item => item.isActive ?? location.pathname === item.path);
  const navItems = [...primaryNavItems, ...secondaryNavItems];
  
  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <header className="bg-[#1a1f36] text-white shadow-lg">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex items-center justify-between h-16">
            <div className="flex items-center gap-2">
              <Database className="w-8 h-8 text-green-400" />
              <span className="text-xl font-mono">edgar4j</span>
            </div>
            
            <nav className="hidden md:flex items-center gap-2 overflow-x-auto">
              {primaryNavItems.map(item => {
                const Icon = item.icon;
                const isActive = item.path === '/companies'
                  ? location.pathname.startsWith('/companies')
                  : location.pathname === item.path;
                return (
                  <Link
                    key={item.path}
                    to={item.path}
                    className={`flex items-center gap-2 px-3 py-2 rounded-md transition-colors shrink-0 whitespace-nowrap ${
                      isActive 
                        ? 'bg-white/10 text-white' 
                        : 'text-gray-300 hover:bg-white/5 hover:text-white'
                    }`}
                  >
                    <Icon className="w-4 h-4" />
                    <span>{item.label}</span>
                  </Link>
                );
              })}

              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <button
                    className={`flex items-center gap-2 px-3 py-2 rounded-md transition-colors shrink-0 whitespace-nowrap ${
                      isFormActive
                        ? 'bg-white/10 text-white'
                        : 'text-gray-300 hover:bg-white/5 hover:text-white'
                    }`}
                  >
                    <FileText className="w-4 h-4" />
                    <span>Forms</span>
                    <ChevronDown className="w-4 h-4" />
                  </button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="start" className="w-52">
                  {formNavItems.map(item => {
                    const Icon = item.icon;
                    return (
                      <DropdownMenuItem key={item.to ?? item.path} asChild>
                        <Link to={item.to ?? item.path} className="flex items-center gap-2">
                          <Icon className="w-4 h-4" />
                          <span>{item.label}</span>
                        </Link>
                      </DropdownMenuItem>
                    );
                  })}
                </DropdownMenuContent>
              </DropdownMenu>

              {secondaryNavItems.map(item => {
                const Icon = item.icon;
                const isActive = location.pathname === item.path;
                return (
                  <Link
                    key={item.path}
                    to={item.path}
                    className={`flex items-center gap-2 px-3 py-2 rounded-md transition-colors shrink-0 whitespace-nowrap ${
                      isActive 
                        ? 'bg-white/10 text-white' 
                        : 'text-gray-300 hover:bg-white/5 hover:text-white'
                    }`}
                  >
                    <Icon className="w-4 h-4" />
                    <span>{item.label}</span>
                  </Link>
                );
              })}
            </nav>
            
            <div className="flex items-center gap-2">
              <div className="w-8 h-8 rounded-full bg-green-500 flex items-center justify-center">
                <span className="text-sm">U</span>
              </div>
            </div>
          </div>
        </div>
      </header>
      
      {/* Mobile Navigation */}
      <div className="md:hidden bg-[#1a1f36] border-t border-white/10 overflow-x-auto">
        <div className="max-w-7xl mx-auto px-4">
          <div className="flex items-center gap-4 py-2 min-w-max">
            {navItems.map(item => {
              const Icon = item.icon;
              const isActive = item.path === '/companies'
                ? location.pathname.startsWith('/companies')
                : location.pathname === item.path;
              return (
                <Link
                  key={item.path}
                  to={item.path}
                  className={`flex flex-col items-center gap-1 py-3 ${
                    isActive ? 'text-white' : 'text-gray-400'
                  }`}
                >
                  <Icon className="w-5 h-5" />
                  <span className="text-xs">{item.label}</span>
                </Link>
              );
            })}

            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <button
                  className={`flex flex-col items-center gap-1 py-3 ${
                    isFormActive ? 'text-white' : 'text-gray-400'
                  }`}
                >
                  <FileText className="w-5 h-5" />
                  <span className="text-xs">Forms</span>
                </button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="start" className="w-48">
                {formNavItems.map(item => {
                  const Icon = item.icon;
                  return (
                    <DropdownMenuItem key={item.to ?? item.path} asChild>
                      <Link to={item.to ?? item.path} className="flex items-center gap-2">
                        <Icon className="w-4 h-4" />
                        <span>{item.label}</span>
                      </Link>
                    </DropdownMenuItem>
                  );
                })}
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </div>
      </div>
      
      {/* Main Content */}
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {children}
      </main>
    </div>
  );
}
