import { useEffect, useMemo, useState } from 'react';
import { getTask, getTaskList, retryTask, type TaskItem, type TaskListResponse } from '../api/taskApi';

type TaskStatus = 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED';

const STATUS_STYLES: Record<TaskStatus, string> = {
  PENDING: 'border-zinc-700 bg-zinc-800 text-zinc-300',
  PROCESSING: 'border-sky-700 bg-sky-500/10 text-sky-300',
  SUCCESS: 'border-emerald-700 bg-emerald-500/10 text-emerald-300',
  FAILED: 'border-rose-700 bg-rose-500/10 text-rose-300',
};

function TaskListPage() {
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [pages, setPages] = useState(1);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [retryingTaskId, setRetryingTaskId] = useState<string | null>(null);

  const loadTasks = async (currentPage = page, currentPageSize = pageSize) => {
    setLoading(true);
    setErrorMessage('');

    try {
      const response: TaskListResponse = await getTaskList(currentPage, currentPageSize);
      setTasks(response.records);
      setTotal(response.total);
      setPages(response.pages);
      setPage(response.current);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '加载任务失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadTasks(1, pageSize);
  }, []);

  useEffect(() => {
    const pendingTaskIds = tasks
      .filter((task) => task.status === 'PENDING' || task.status === 'PROCESSING')
      .map((task) => task.taskId);

    if (pendingTaskIds.length === 0) {
      return;
    }

    const timer = window.setInterval(async () => {
      try {
        const refreshedTasks = await Promise.all(
          pendingTaskIds.map(async (taskId) => getTask(taskId))
        );

        setTasks((currentTasks) =>
          currentTasks.map((task) => {
            const refreshed = refreshedTasks.find((item) => item.taskId === task.taskId);
            return refreshed ? refreshed : task;
          })
        );
      } catch {
        // ignore polling errors for now
      }
    }, 3000);

    return () => window.clearInterval(timer);
  }, [tasks]);

  const handleRetry = async (taskId: string) => {
    setRetryingTaskId(taskId);
    try {
      await retryTask(taskId);
      await loadTasks(page, pageSize);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '重试失败');
    } finally {
      setRetryingTaskId(null);
    }
  };

  const totalPages = useMemo(() => Math.max(1, pages), [pages]);

  return (
    <div className="min-h-screen bg-zinc-950 px-4 py-10 text-zinc-100 sm:px-6 lg:px-8">
      <div className="mx-auto flex max-w-6xl flex-col gap-6">
        <header className="rounded-xl border border-zinc-800 bg-zinc-900/80 p-6 shadow-sm shadow-black/20">
          <p className="text-sm font-medium uppercase tracking-[0.2em] text-zinc-400">AsyncTaskHub</p>
          <h1 className="mt-2 text-2xl font-semibold text-white">任务列表</h1>
          <p className="mt-3 text-sm leading-6 text-zinc-400">
            查看所有任务的最新状态，并对失败任务执行重触发。未到终态的任务会每 3 秒自动刷新一次。
          </p>
        </header>

        <section className="rounded-xl border border-zinc-800 bg-zinc-900/80 p-6 shadow-sm shadow-black/20">
          <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
            <div>
              <h2 className="text-lg font-semibold text-white">任务记录</h2>
              <p className="text-sm text-zinc-400">共 {total} 条记录</p>
            </div>
            <div className="flex items-center gap-2">
              <label className="text-sm text-zinc-400">每页</label>
              <select
                className="rounded-lg border border-zinc-700 bg-zinc-950 px-3 py-2 text-sm text-zinc-200"
                value={pageSize}
                onChange={(event) => {
                  const nextSize = Number(event.target.value);
                  setPageSize(nextSize);
                  void loadTasks(1, nextSize);
                }}
              >
                <option value={10}>10</option>
                <option value={20}>20</option>
                <option value={50}>50</option>
              </select>
            </div>
          </div>

          {errorMessage ? (
            <div className="mb-4 rounded-lg border border-rose-800/60 bg-rose-950/50 px-3 py-2 text-sm text-rose-300">
              {errorMessage}
            </div>
          ) : null}

          {loading && tasks.length === 0 ? (
            <div className="rounded-lg border border-zinc-800 bg-zinc-950/60 p-4 text-sm text-zinc-400">
              正在加载任务...
            </div>
          ) : (
            <div className="overflow-hidden rounded-lg border border-zinc-800">
              <div className="overflow-x-auto">
                <table className="min-w-full divide-y divide-zinc-800 text-sm">
                  <thead className="bg-zinc-950/80 text-left text-zinc-400">
                    <tr>
                      <th className="px-4 py-3 font-medium">Task ID</th>
                      <th className="px-4 py-3 font-medium">任务类型</th>
                      <th className="px-4 py-3 font-medium">状态</th>
                      <th className="px-4 py-3 font-medium">创建时间</th>
                      <th className="px-4 py-3 font-medium">结果</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-zinc-800 bg-zinc-900/70">
                    {tasks.map((task) => (
                      <tr key={task.taskId} className="align-top">
                        <td className="px-4 py-4 font-mono text-xs text-zinc-200">{task.taskId.slice(0, 8)}...</td>
                        <td className="px-4 py-4 text-zinc-300">{task.taskType}</td>
                        <td className="px-4 py-4">
                          <span className={`inline-flex rounded-full border px-2.5 py-1 text-xs font-medium ${STATUS_STYLES[task.status as TaskStatus]}`}>
                            {task.status}
                          </span>
                        </td>
                        <td className="px-4 py-4 text-zinc-400">{task.createTime}</td>
                        <td className="px-4 py-4 text-zinc-300">
                          {task.status === 'SUCCESS' && task.resultPath ? (
                            <a href={task.resultPath} target="_blank" rel="noreferrer" className="text-blue-400 hover:text-blue-300">
                              查看图片
                            </a>
                          ) : null}

                          {task.status === 'FAILED' ? (
                            <div className="space-y-2">
                              <p className="max-w-xs text-xs text-rose-300">{task.errorMsg || '任务失败'}</p>
                              <button
                                type="button"
                                onClick={() => void handleRetry(task.taskId)}
                                disabled={retryingTaskId === task.taskId}
                                className="rounded-lg border border-zinc-700 bg-zinc-950 px-3 py-1.5 text-xs font-medium text-zinc-200 transition hover:border-blue-600 hover:text-blue-300 disabled:cursor-not-allowed disabled:opacity-60"
                              >
                                {retryingTaskId === task.taskId ? '重试中...' : '重触发'}
                              </button>
                            </div>
                          ) : null}

                          {task.status !== 'SUCCESS' && task.status !== 'FAILED' ? (
                            <span className="text-xs text-zinc-500">等待结果</span>
                          ) : null}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          <div className="mt-4 flex items-center justify-between gap-3 text-sm text-zinc-400">
            <span>
              第 {page} / {totalPages} 页
            </span>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => {
                  const nextPage = Math.max(1, page - 1);
                  void loadTasks(nextPage, pageSize);
                }}
                disabled={page <= 1}
                className="rounded-lg border border-zinc-700 bg-zinc-950 px-3 py-2 text-zinc-200 transition disabled:cursor-not-allowed disabled:opacity-60"
              >
                上一页
              </button>
              <button
                type="button"
                onClick={() => {
                  const nextPage = Math.min(totalPages, page + 1);
                  void loadTasks(nextPage, pageSize);
                }}
                disabled={page >= totalPages}
                className="rounded-lg border border-zinc-700 bg-zinc-950 px-3 py-2 text-zinc-200 transition disabled:cursor-not-allowed disabled:opacity-60"
              >
                下一页
              </button>
            </div>
          </div>
        </section>
      </div>
    </div>
  );
}

export default TaskListPage;
