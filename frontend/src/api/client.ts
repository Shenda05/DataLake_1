import axios from 'axios';

const TOKEN_KEY = 'data-lake-auth-token';
const PROFILE_KEY = 'data-lake-auth-profile';
const AUTH_EXPIRED_MESSAGE = '登录已失效，请重新登录';
let redirectingToLogin = false;

type ApiEnvelope<T> = {
  code: number;
  message: string;
  data: T;
  timestamp: string;
  requestId: string;
};

const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
  timeout: 20000
});

function clearStoredAuth() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(PROFILE_KEY);
}

function createAuthExpiredError() {
  const error = new Error(AUTH_EXPIRED_MESSAGE);
  error.name = 'AuthExpiredError';
  return error;
}

function handleAuthExpired() {
  clearStoredAuth();
  if (typeof window === 'undefined') {
    return;
  }
  if (window.location.pathname === '/login' || redirectingToLogin) {
    return;
  }
  redirectingToLogin = true;
  window.setTimeout(() => {
    window.location.replace('/login');
    redirectingToLogin = false;
  }, 0);
}

export function isAuthExpiredError(error: unknown) {
  return error instanceof Error && error.name === 'AuthExpiredError';
}

client.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

client.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error?.response?.status;
    const requestUrl = String(error?.config?.url || '');
    if (status === 401 && !requestUrl.includes('/auth/login')) {
      handleAuthExpired();
      return Promise.reject(createAuthExpiredError());
    }
    const message = error?.response?.data?.message || error?.message || '请求失败';
    return Promise.reject(new Error(message));
  }
);

export async function apiGet<T>(url: string, params?: Record<string, unknown>) {
  const response = await client.get<ApiEnvelope<T>>(url, { params });
  return response.data.data;
}

export async function apiPost<T>(url: string, data?: unknown, config?: Record<string, unknown>) {
  const response = await client.post<ApiEnvelope<T>>(url, data, config);
  return response.data.data;
}

export async function apiPut<T>(url: string, data?: unknown) {
  const response = await client.put<ApiEnvelope<T>>(url, data);
  return response.data.data;
}

export async function apiDelete<T>(url: string) {
  const response = await client.delete<ApiEnvelope<T>>(url);
  return response.data.data;
}

export async function apiUpload<T>(url: string, formData: FormData) {
  const response = await client.post<ApiEnvelope<T>>(url, formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  });
  return response.data.data;
}

export async function apiDownload(url: string, params?: Record<string, unknown>) {
  const response = await client.get(url, {
    params,
    responseType: 'blob'
  });
  return resolveDownload(response);
}

export async function apiDownloadPost(url: string, data?: unknown, params?: Record<string, unknown>) {
  const response = await client.post(url, data, {
    params,
    responseType: 'blob'
  });
  return resolveDownload(response);
}

function resolveDownload(response: { data: Blob; headers: Record<string, unknown> }) {
  const disposition = response.headers['content-disposition'] as string | undefined;
  const filename = disposition?.match(/filename\*=UTF-8''([^;]+)/)?.[1];
  return {
    blob: response.data as Blob,
    filename: filename ? decodeURIComponent(filename) : undefined
  };
}
