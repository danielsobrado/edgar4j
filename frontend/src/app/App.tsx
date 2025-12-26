import React from 'react';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import { Layout } from './components/Layout';
import { Dashboard } from './pages/Dashboard';
import { FilingSearch } from './pages/FilingSearch';
import { FilingDetail } from './pages/FilingDetail';
import { Companies } from './pages/Companies';
import { Downloads } from './pages/Downloads';
import { Settings } from './pages/Settings';

export default function App() {
  return (
    <Router>
      <Layout>
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/search" element={<FilingSearch />} />
          <Route path="/filing/:id" element={<FilingDetail />} />
          <Route path="/companies" element={<Companies />} />
          <Route path="/downloads" element={<Downloads />} />
          <Route path="/settings" element={<Settings />} />
        </Routes>
      </Layout>
    </Router>
  );
}
