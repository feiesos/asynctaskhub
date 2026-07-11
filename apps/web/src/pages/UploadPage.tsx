import { useMemo, useState, type ChangeEvent, type DragEvent } from 'react';
import { createTask, uploadImage } from '../api/taskApi';

type TaskType = 'COMPRESS' | 'THUMBNAIL' | 'WATERMARK';

const TASK_OPTIONS: Array<{ value: TaskType; label: string; description: string }> = [
  { value: 'COMPRESS', label: 'Compress', description: '压缩图片并生成更小的文件' },
  { value: 'THUMBNAIL', label: 'Thumbnail', description: '生成缩略图版本' },
  { value: 'WATERMARK', label: 'Watermark', description: '给图片添加水印' },
];

function UploadPage() {
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [taskType, setTaskType] = useState<TaskType>('COMPRESS');
  const [isDragging, setIsDragging] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [isCreatingTask, setIsCreatingTask] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [successMessage, setSuccessMessage] = useState('');
  const [taskId, setTaskId] = useState('');

  const previewUrl = useMemo(() => {
    if (!selectedFile) {
      return '';
    }
    return URL.createObjectURL(selectedFile);
  }, [selectedFile]);

  const handleFileSelection = (file: File | null) => {
    if (!file) {
      return;
    }

    if (!file.type.startsWith('image/')) {
      setErrorMessage('请选择图片文件');
      return;
    }

    setErrorMessage('');
    setSelectedFile(file);
  };

  const handleInputChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0] ?? null;
    handleFileSelection(file);
  };

  const handleDrop = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    setIsDragging(false);
    const file = event.dataTransfer.files?.[0] ?? null;
    handleFileSelection(file);
  };

  const handleSubmit = async () => {
    if (!selectedFile || isUploading || isCreatingTask) {
      return;
    }

    setIsUploading(true);
    setIsCreatingTask(true);
    setErrorMessage('');
    setSuccessMessage('');
    setTaskId('');

    try {
      const { filePath } = await uploadImage(selectedFile);
      const { taskId: createdTaskId } = await createTask({
        taskType,
        filePath,
        params: {},
      });

      setTaskId(createdTaskId);
      setSuccessMessage('任务已创建，任务 ID 已返回。');
    } catch (err) {
      setErrorMessage(err instanceof Error ? err.message : '创建任务失败');
    } finally {
      setIsUploading(false);
      setIsCreatingTask(false);
    }
  };

  return (
    <div className="min-h-screen bg-zinc-950 px-4 py-10 text-zinc-100 sm:px-6 lg:px-8">
      <div className="mx-auto flex max-w-5xl flex-col gap-6">
        <header className="rounded-xl border border-zinc-800 bg-zinc-900/80 p-6 shadow-sm shadow-black/20">
          <p className="text-sm font-medium uppercase tracking-[0.2em] text-zinc-400">AsyncTaskHub</p>
          <h1 className="mt-2 text-2xl font-semibold text-white">上传图片并创建处理任务</h1>
          <p className="mt-3 max-w-2xl text-sm leading-6 text-zinc-400">
            选择图片并提交任务，系统会在后台异步执行处理。当前版本仅提供任务创建入口，后续可继续扩展状态查看与结果预览。
          </p>
        </header>

        <div className="grid gap-6 lg:grid-cols-[1.15fr_0.85fr]">
          <section className="rounded-xl border border-zinc-800 bg-zinc-900/80 p-6 shadow-sm shadow-black/20">
            <div
              className={`rounded-xl border-2 border-dashed p-6 transition ${
                isDragging ? 'border-blue-500 bg-blue-500/10' : 'border-zinc-700 bg-zinc-950/60'
              }`}
              onDragOver={(event) => {
                event.preventDefault();
                setIsDragging(true);
              }}
              onDragLeave={() => setIsDragging(false)}
              onDrop={handleDrop}
            >
              <input id="fileInput" type="file" accept="image/*" className="hidden" onChange={handleInputChange} />
              <label htmlFor="fileInput" className="flex cursor-pointer flex-col items-center justify-center gap-3 text-center">
                <div className="rounded-full border border-zinc-700 bg-zinc-800 p-3 text-zinc-300">
                  <svg viewBox="0 0 24 24" className="h-6 w-6" fill="none" stroke="currentColor" strokeWidth="1.8">
                    <path d="M12 16V4m0 0 4 4m-4-4-4 4" strokeLinecap="round" strokeLinejoin="round" />
                    <path d="M4 16v1a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-1" strokeLinecap="round" />
                  </svg>
                </div>
                <div>
                  <p className="text-sm font-medium text-white">拖拽图片到这里，或点击选择</p>
                  <p className="mt-1 text-sm text-zinc-400">支持 JPG、PNG、WEBP 等常见图片格式</p>
                </div>
              </label>
            </div>

            {selectedFile ? (
              <div className="mt-5 rounded-lg border border-zinc-800 bg-zinc-950/70 p-4">
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <p className="text-sm font-medium text-white">已选择文件</p>
                    <p className="mt-1 text-sm text-zinc-400">{selectedFile.name}</p>
                  </div>
                  <span className="rounded-full border border-emerald-700/40 bg-emerald-500/10 px-3 py-1 text-xs font-medium text-emerald-400">
                    {Math.round(selectedFile.size / 1024)} KB
                  </span>
                </div>
                {previewUrl ? (
                  <img src={previewUrl} alt="Preview" className="mt-4 h-48 w-full rounded-lg object-cover" />
                ) : null}
              </div>
            ) : null}
          </section>

          <section className="rounded-xl border border-zinc-800 bg-zinc-900/80 p-6 shadow-sm shadow-black/20">
            <div className="space-y-6">
              <div>
                <label className="text-sm font-medium text-zinc-200">任务类型</label>
                <div className="mt-3 space-y-2">
                  {TASK_OPTIONS.map((option) => (
                    <label
                      key={option.value}
                      className={`flex cursor-pointer items-start gap-3 rounded-lg border px-3 py-3 transition ${
                        taskType === option.value
                          ? 'border-blue-500 bg-blue-500/10 text-white'
                          : 'border-zinc-800 bg-zinc-950/60 text-zinc-300'
                      }`}
                    >
                      <input
                        type="radio"
                        name="taskType"
                        className="mt-1 h-4 w-4 border-zinc-600 bg-zinc-900 text-blue-500"
                        checked={taskType === option.value}
                        onChange={() => setTaskType(option.value)}
                      />
                      <span>
                        <span className="block text-sm font-medium">{option.label}</span>
                        <span className="mt-1 block text-xs text-zinc-400">{option.description}</span>
                      </span>
                    </label>
                  ))}
                </div>
              </div>

              <div className="rounded-lg border border-zinc-800 bg-zinc-950/60 p-4 text-sm text-zinc-400">
                <p className="font-medium text-zinc-200">提交说明</p>
                <p className="mt-2 leading-6">
                  选中图片后，系统会先上传文件并生成可供后台处理的路径，再创建任务。提交期间按钮会保持禁用，避免重复点击。
                </p>
              </div>

              <button
                type="button"
                onClick={handleSubmit}
                disabled={!selectedFile || isUploading || isCreatingTask}
                className="w-full rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-blue-500 disabled:cursor-not-allowed disabled:bg-zinc-700"
              >
                {isUploading || isCreatingTask ? '提交中...' : '创建任务'}
              </button>

              {errorMessage ? (
                <div className="rounded-lg border border-red-800/60 bg-red-950/50 px-3 py-2 text-sm text-red-300">
                  {errorMessage}
                </div>
              ) : null}

              {successMessage ? (
                <div className="rounded-lg border border-emerald-800/60 bg-emerald-950/50 px-3 py-2 text-sm text-emerald-300">
                  <p>{successMessage}</p>
                  {taskId ? (
                    <div className="mt-3 flex flex-wrap items-center gap-2">
                      <span className="rounded-full border border-emerald-700/40 bg-emerald-500/10 px-2.5 py-1 text-xs font-medium text-emerald-400">
                        {taskId}
                      </span>
                      <a href="/tasks" className="text-sm font-medium text-blue-400 hover:text-blue-300">
                        查看任务列表
                      </a>
                    </div>
                  ) : null}
                </div>
              ) : null}
            </div>
          </section>
        </div>
      </div>
    </div>
  );
}

export default UploadPage;
