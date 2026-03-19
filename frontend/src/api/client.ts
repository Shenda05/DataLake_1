import axios from 'axios';

const TOKEN_KEY = 'data-lake-auth-token';

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
