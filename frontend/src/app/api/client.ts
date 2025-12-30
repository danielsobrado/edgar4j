import axios, { AxiosError, AxiosInstance, AxiosRequestConfig } from 'axios';
import { ApiResponse } from './types';
import { getApiBaseUrl, devLog } from '../config/env';
import { API_CONFIG, ERROR_MESSAGES } from '../config/constants';

const API_BASE_URL = getApiBaseUrl();

class ApiClient {
  private client: AxiosInstance;

  constructor() {
    this.client = axios.create({
      baseURL: `${API_BASE_URL}/api`,
      timeout: API_CONFIG.TIMEOUT_MS,
      headers: API_CONFIG.DEFAULT_HEADERS,
    });

    this.client.interceptors.request.use(
      (config) => {
        devLog.log(`[API] ${config.method?.toUpperCase()} ${config.url}`);
        return config;
      },
      (error) => {
        devLog.error('[API] Request error:', error);
        return Promise.reject(error);
      }
    );

    this.client.interceptors.response.use(
      (response) => {
        return response;
      },
      (error: AxiosError<ApiResponse<unknown>>) => {
        const message = error.response?.data?.message || error.message || ERROR_MESSAGES.GENERIC;
        devLog.error('[API] Response error:', message);
        return Promise.reject(new Error(message));
      }
    );
  }

  async get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    const response = await this.client.get<ApiResponse<T>>(url, config);
    return response.data.data;
  }

  async post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    const response = await this.client.post<ApiResponse<T>>(url, data, config);
    return response.data.data;
  }

  async put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    const response = await this.client.put<ApiResponse<T>>(url, data, config);
    return response.data.data;
  }

  async delete<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    const response = await this.client.delete<ApiResponse<T>>(url, config);
    return response.data.data;
  }

  async downloadFile(url: string, data?: unknown): Promise<Blob> {
    const response = await this.client.post(url, data, {
      responseType: 'blob',
    });
    return response.data;
  }

  getBaseUrl(): string {
    return API_BASE_URL;
  }
}

export const apiClient = new ApiClient();
