import React from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Database, Search, Building2, Download, Settings, LayoutDashboard, Briefcase, Users, FileText, UserPlus, FileCheck, Globe } from 'lucide-react';

export function Layout({ children }: { children: React.ReactNode }) {
  const location = useLocation();

  const navItems = [
    { path: '/', label: 'Dashboard', icon: LayoutDashboard },
    { path: '/search', label: 'Search', icon: Search },
    { path: '/companies', label: 'Companies', icon: Building2 },
    { path: '/form3', label: 'Form 3', icon: UserPlus },
    { path: '/form5', label: 'Form 5', icon: FileCheck },
    { path: '/form6k', label: '6-K', icon: Globe },
    { path: '/form8k', label: '8-K', icon: FileText },
    { path: '/form13f', label: '13F', icon: Briefcase },
    { path: '/form13dg', label: '13D/G', icon: Users },
    { path: '/downloads', label: 'Downloads', icon: Download },
    { path: '/settings', label: 'Settings', icon: Settings }
  ];
  
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
            
            <nav className="hidden md:flex items-center gap-6">
              {navItems.map(item => {
                const Icon = item.icon;
                const isActive = location.pathname === item.path;
                return (
                  <Link
                    key={item.path}
                    to={item.path}
                    className={`flex items-center gap-2 px-3 py-2 rounded-md transition-colors ${
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
          <div className="grid grid-cols-11 gap-1 min-w-max">
            {navItems.map(item => {
              const Icon = item.icon;
              const isActive = location.pathname === item.path;
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
