import React from 'react';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import { Layout } from './components/Layout';
import { Dashboard } from './pages/Dashboard';
import { InsiderPurchasesPage } from './pages/InsiderPurchases';
import { FilingSearch } from './pages/FilingSearch';
import { FilingDetail } from './pages/FilingDetail';
import { Companies } from './pages/Companies';
import { CompanyFundamentals } from './pages/CompanyFundamentals';
import { Downloads } from './pages/Downloads';
import { Settings } from './pages/Settings';
import { Form13FPage } from './pages/Form13F';
import { Form13DGPage } from './pages/Form13DG';
import { Form8KPage } from './pages/Form8K';
import { Form3Page } from './pages/Form3';
import { Form4Page } from './pages/Form4';
import { Form5Page } from './pages/Form5';
import { Form6KPage } from './pages/Form6K';
import { Form20FPage } from './pages/Form20F';
import { RemoteEdgar } from './pages/RemoteEdgar';
import { Alerts } from './pages/Alerts';
import { AppErrorBoundary } from './components/common/AppErrorBoundary';

export default function App() {
  return (
    <Router>
      <AppErrorBoundary>
        <Layout>
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/insider-purchases" element={<InsiderPurchasesPage />} />
            <Route path="/search" element={<FilingSearch />} />
            <Route path="/filing/:id" element={<FilingDetail />} />
            <Route path="/companies" element={<Companies />} />
            <Route path="/companies/:cik/fundamentals" element={<CompanyFundamentals />} />
            <Route path="/form13f" element={<Form13FPage />} />
            <Route path="/form13dg" element={<Form13DGPage />} />
            <Route path="/form8k" element={<Form8KPage />} />
            <Route path="/form3" element={<Form3Page />} />
            <Route path="/form4" element={<Form4Page />} />
            <Route path="/form5" element={<Form5Page />} />
            <Route path="/form6k" element={<Form6KPage />} />
            <Route path="/form20f" element={<Form20FPage />} />
            <Route path="/remote-edgar" element={<RemoteEdgar />} />
            <Route path="/alerts" element={<Alerts />} />
            <Route path="/downloads" element={<Downloads />} />
            <Route path="/settings" element={<Settings />} />
          </Routes>
        </Layout>
      </AppErrorBoundary>
    </Router>
  );
}
