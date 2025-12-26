import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { FilingSearchRequest } from '../api';

interface SearchHistoryItem {
  query: string;
  type: string;
  timestamp: string;
}

interface SearchState {
  searchHistory: SearchHistoryItem[];
  lastSearchRequest: FilingSearchRequest | null;
  addToHistory: (query: string, type: string) => void;
  clearHistory: () => void;
  setLastSearchRequest: (request: FilingSearchRequest) => void;
}

export const useSearchStore = create<SearchState>()(
  persist(
    (set) => ({
      searchHistory: [],
      lastSearchRequest: null,

      addToHistory: (query: string, type: string) => {
        set((state) => {
          const newItem: SearchHistoryItem = {
            query,
            type,
            timestamp: new Date().toISOString(),
          };

          const filtered = state.searchHistory.filter(
            (item) => item.query !== query
          );

          return {
            searchHistory: [newItem, ...filtered].slice(0, 20),
          };
        });
      },

      clearHistory: () => {
        set({ searchHistory: [] });
      },

      setLastSearchRequest: (request: FilingSearchRequest) => {
        set({ lastSearchRequest: request });
      },
    }),
    {
      name: 'edgar4j-search-store',
    }
  )
);
