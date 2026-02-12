import { useState, useEffect, useCallback } from 'react';
import { downloadsApi, DownloadJob, DownloadType } from '../api';

export function useDownloadJobs(limit: number = 10) {
  const [jobs, setJobs] = useState<DownloadJob[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchJobs = useCallback(async () => {
    setLoading(true);
    try {
      const data = await downloadsApi.getJobs(limit);
      setJobs(data);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load download jobs');
    } finally {
      setLoading(false);
    }
  }, [limit]);

  useEffect(() => {
    fetchJobs();
  }, [fetchJobs]);

  return { jobs, loading, error, refresh: fetchJobs };
}

export function useActiveDownloadJobs(pollInterval: number = 5000) {
  const [jobs, setJobs] = useState<DownloadJob[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchActiveJobs = useCallback(async () => {
    try {
      const data = await downloadsApi.getActiveJobs();
      setJobs(data);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load active jobs');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchActiveJobs();

    if (pollInterval > 0) {
      const interval = setInterval(fetchActiveJobs, pollInterval);
      return () => clearInterval(interval);
    }
  }, [fetchActiveJobs, pollInterval]);

  return { jobs, loading, error, refresh: fetchActiveJobs };
}

export function useDownloadJob(jobId: string | undefined, pollInterval: number = 2000) {
  const [job, setJob] = useState<DownloadJob | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchJob = useCallback(async () => {
    if (!jobId) return;

    try {
      const data = await downloadsApi.getJobById(jobId);
      setJob(data);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load job');
    } finally {
      setLoading(false);
    }
  }, [jobId]);

  useEffect(() => {
    if (!jobId) {
      setLoading(false);
      return;
    }

    fetchJob();

    if (pollInterval > 0) {
      const interval = setInterval(() => {
        if (job && (job.status === 'PENDING' || job.status === 'IN_PROGRESS')) {
          fetchJob();
        }
      }, pollInterval);
      return () => clearInterval(interval);
    }
  }, [jobId, pollInterval, fetchJob, job]);

  return { job, loading, error, refresh: fetchJob };
}

export function useDownloadActions() {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const downloadTickers = useCallback(async (type: DownloadType = 'TICKERS_ALL') => {
    setLoading(true);
    setError(null);
    try {
      const job = await downloadsApi.downloadTickers(type);
      return job;
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to start download';
      setError(message);
      throw err;
    } finally {
      setLoading(false);
    }
  }, []);

  const downloadSubmissions = useCallback(async (cik: string, userAgent?: string) => {
    setLoading(true);
    setError(null);
    try {
      const job = await downloadsApi.downloadSubmissions(cik, userAgent);
      return job;
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to start download';
      setError(message);
      throw err;
    } finally {
      setLoading(false);
    }
  }, []);

  const downloadBulk = useCallback(async (
    type: 'BULK_SUBMISSIONS' | 'BULK_COMPANY_FACTS',
    userAgent?: string
  ) => {
    setLoading(true);
    setError(null);
    try {
      const job = await downloadsApi.downloadBulk({ type, userAgent });
      return job;
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to start bulk download';
      setError(message);
      throw err;
    } finally {
      setLoading(false);
    }
  }, []);

  const cancelJob = useCallback(async (jobId: string) => {
    setLoading(true);
    setError(null);
    try {
      await downloadsApi.cancelJob(jobId);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to cancel job';
      setError(message);
      throw err;
    } finally {
      setLoading(false);
    }
  }, []);

  return {
    loading,
    error,
    downloadTickers,
    downloadSubmissions,
    downloadBulk,
    cancelJob,
  };
}

// Combined hook for Downloads page
export function useDownloads() {
  const { jobs, loading, error, refresh } = useDownloadJobs(50);
  const { jobs: activeJobs } = useActiveDownloadJobs(5000);
  const actions = useDownloadActions();

  return {
    jobs,
    activeJobs,
    loading,
    error,
    refresh,
    ...actions,
  };
}
