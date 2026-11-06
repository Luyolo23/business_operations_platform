import {
  LayoutDashboard,
  Ticket,
  PlusCircle,
  BarChart3,
  FileText,
  Brain,
  Settings,
} from 'lucide-react'

const navigation = [
  { name: 'Dashboard', icon: LayoutDashboard },
  { name: 'Tickets', icon: Ticket },
  { name: 'Submit Ticket', icon: PlusCircle },
  { name: 'Analytics', icon: BarChart3 },
  { name: 'Reports', icon: FileText },
  { name: 'AI Insights', icon: Brain },
  { name: 'Settings', icon: Settings },
]

function Sidebar() {
  return (
    <aside className="flex min-h-screen w-64 flex-col bg-slate-900 text-white">
      <div className="border-b border-slate-700 px-6 py-6">
        <h1 className="text-xl font-bold">OpsPlatform</h1>
        <p className="mt-1 text-sm text-slate-400">
          Business Operations
        </p>
      </div>

      <nav className="flex-1 space-y-2 px-4 py-6">
        {navigation.map((item, index) => {
          const Icon = item.icon

          return (
            <a
              key={item.name}
              href="#"
              className={`flex items-center gap-3 rounded-lg px-4 py-3 text-sm transition ${
                index === 0
                  ? 'bg-blue-600 text-white'
                  : 'text-slate-300 hover:bg-slate-800 hover:text-white'
              }`}
            >
              <Icon size={20} />
              {item.name}
            </a>
          )
        })}
      </nav>

      <div className="border-t border-slate-700 p-4">
        <p className="font-medium">Team Member</p>
        <p className="text-sm text-slate-400">Administrator</p>
      </div>
    </aside>
  )
}

export default Sidebar