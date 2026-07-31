import React, { useState } from 'react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

interface ThemeFormTabsProps {
  children: React.ReactNode;
  activeTab: string;
  setActiveTab: (tab: string) => void;
}

interface ThemeFormTabProps {
  id: string;
  label: string;
  children: React.ReactNode;
  activeTab: string;
}

export function ThemeFormTabs({ children, activeTab, setActiveTab }: ThemeFormTabsProps) {
  const tabs = React.Children.toArray(children) as React.ReactElement<ThemeFormTabProps>[];

  return (
    <div className="w-full">
      <div className="flex border-b border-[#8B7355]/30 mb-4"> {/* cafe-com-leite */}
        {tabs.map((tab) => (
          <Button
            key={tab.props.id}
            variant="ghost"
            type="button" // Adiciona type="button" para evitar submit do formulário
            className={cn(
              'border-b-2 border-transparent -mb-px px-4 py-2 text-sm font-medium',
              activeTab === tab.props.id
                ? 'border-[#2A1F1B] text-[#2A1F1B] bg-transparent hover:bg-[#8B7355]/30' // cafe-dark-roast with hover effect
                : 'border-transparent text-[#2A1F1B]/70 hover:text-[#2A1F1B] hover:bg-[#8B7355]/30' // cafe-dark-roast and cafe-com-leite
            )}
            onClick={() => setActiveTab(tab.props.id)}
          >
            {tab.props.label}
          </Button>
        ))}
      </div>
      <div className="mt-4">
        {tabs.map((tab) => (
          tab.props.id === activeTab && (
            <div key={tab.props.id} role="tabpanel" className="tab-content">
              {tab}
            </div>
          )
        ))}
      </div>
    </div>
  );
}

export function ThemeFormTab({ children }: ThemeFormTabProps) {
  return (
    <div role="tabpanel">
      {children}
    </div>
  );
}