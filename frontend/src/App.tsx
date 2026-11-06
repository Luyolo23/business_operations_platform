import Sidebar from './components/Sidebar'
import Dashboard from './pages/Dashboard'

function App() {
  return (
    <div className="flex min-h-screen bg-slate-100">
      <Sidebar />

      <main className="min-w-0 flex-1 p-6 md:p-8">
        <header className="mb-8 flex items-center justify-between">
          <div>
            <p className="text-sm text-slate-500">
              Business Operations Platform
            </p>
          </div>

          <div className="flex h-10 w-10 items-center justify-center rounded-full bg-blue-600 font-semibold text-white">
            TM
          </div>
        </header>

        <Dashboard />
      </main>
    </div>
  )
}

export default App