import axios, { AxiosError, type AxiosInstance } from 'axios';

const api: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000,
});

api.interceptors.response.use(
  (response) => {
    if (response.data && typeof response.data === 'object' && 'code' in response.data) {
      if (response.data.code !== 200) {
        return Promise.reject(new Error(response.data.message || 'Request failed'));
      }
      response.data = response.data.data;
    }
    return response;
  },
  (error: AxiosError<{ code?: number; message?: string }>) => {
    const message =
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

export interface TaskItem {
  taskId: string;
  taskType: string;
  status: string;
  createTime: string;
  resultPath?: string;
  errorMsg?: string;
}

export interface TaskListResponse {
  records: TaskItem[];
  total: number;
  size: number;
  current: number;
  pages: number;
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

export async function getTaskList(page = 1, pageSize = 20): Promise<TaskListResponse> {
  const { data } = await api.get<TaskListResponse>('/tasks', {
    params: {
      page,
      pageSize,
    },
  });
  return data;
}

export async function getTask(taskId: string): Promise<TaskItem> {
  const { data } = await api.get<TaskItem>(`/tasks/${taskId}`);
  return data;
}

export async function retryTask(taskId: string): Promise<{ taskId: string }> {
  const { data } = await api.post<{ taskId: string }>(`/tasks/${taskId}/retry`);
  return data;
}

export default api;
