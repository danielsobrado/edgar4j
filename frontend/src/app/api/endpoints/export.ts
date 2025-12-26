import { apiClient } from '../client';
import { ExportRequest } from '../types';

export const exportApi = {
  exportToCsv: async (request: ExportRequest): Promise<void> => {
    const blob = await apiClient.downloadFile('/export/csv', request);
    downloadBlob(blob, 'filings-export.csv');
  },

  exportToJson: async (request: ExportRequest): Promise<void> => {
    const blob = await apiClient.downloadFile('/export/json', request);
    downloadBlob(blob, 'filings-export.json');
  },
};

function downloadBlob(blob: Blob, filename: string) {
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.URL.revokeObjectURL(url);
}
