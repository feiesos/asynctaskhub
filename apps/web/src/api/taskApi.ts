import axios, { AxiosError, type AxiosInstance } from 'axios';

const api: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000,
});

api.interceptors.response.use(
  (response) => response,
  (error: AxiosError<{ error?: string; message?: string }>) => {
    const message =
      error.response?.data?.error ||
      error.response?.data?.message ||
      error.message ||
      'Request failed';

    return Promise.reject(new Error(message));
  }
);

export interface CreateTaskPayload {
  taskType: string;
  filePath: string;
  params: Record<string, unknown>;
}

export interface CreateTaskResponse {
  taskId: string;
}

export interface UploadResponse {
  filePath: string;
}

export async function uploadImage(file: File): Promise<UploadResponse> {
  const formData = new FormData();
  formData.append('file', file);

  const { data } = await api.post<UploadResponse>('/uploads', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });

  return data;
}

export async function createTask(payload: CreateTaskPayload): Promise<CreateTaskResponse> {
  const { data } = await api.post<CreateTaskResponse>('/tasks', payload);
  return data;
}

export default api;
