import React from 'react';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import { Layout } from './components/Layout';
import { Dashboard } from './pages/Dashboard';
import { FilingSearch } from './pages/FilingSearch';
import { FilingDetail } from './pages/FilingDetail';
import { Companies } from './pages/Companies';
import { Downloads } from './pages/Downloads';
import { Settings } from './pages/Settings';
import { Form13FPage } from './pages/Form13F';
import { Form13DGPage } from './pages/Form13DG';
import { Form8KPage } from './pages/Form8K';

export default function App() {
  return (
    <Router>
      <Layout>
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/search" element={<FilingSearch />} />
          <Route path="/filing/:id" element={<FilingDetail />} />
          <Route path="/companies" element={<Companies />} />
          <Route path="/form13f" element={<Form13FPage />} />
          <Route path="/form13dg" element={<Form13DGPage />} />
          <Route path="/form8k" element={<Form8KPage />} />
          <Route path="/downloads" element={<Downloads />} />
          <Route path="/settings" element={<Settings />} />
        </Routes>
      </Layout>
    </Router>
  );
}
