import { useState } from 'react';
import TaskListPage from './pages/TaskListPage';
import UploadPage from './pages/UploadPage';

function App() {
  const [view, setView] = useState<'upload' | 'tasks'>('upload');

  return (
    <>
      <nav className="border-b border-zinc-800 bg-zinc-950/90 px-4 py-3 text-sm text-zinc-300 sm:px-6 lg:px-8">
        <div className="mx-auto flex max-w-6xl items-center justify-between">
          <span className="font-medium text-white">AsyncTaskHub</span>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setView('upload')}
              className={`rounded-lg px-3 py-2 transition ${view === 'upload' ? 'bg-zinc-800 text-white' : 'text-zinc-400 hover:text-white'}`}
            >
              上传任务
            </button>
            <button
              type="button"
              onClick={() => setView('tasks')}
              className={`rounded-lg px-3 py-2 transition ${view === 'tasks' ? 'bg-zinc-800 text-white' : 'text-zinc-400 hover:text-white'}`}
            >
              任务列表
            </button>
          </div>
        </div>
      </nav>
      {view === 'upload' ? <UploadPage onViewTasks={() => setView('tasks')} /> : <TaskListPage />}
    </>
  );
}

export default App;
