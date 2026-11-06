import {
  Ticket,
  Clock,
  CheckCircle,
  AlertCircle,
  TrendingUp,
} from 'lucide-react'

const stats = [
  {
    title: 'Total Tickets',
    value: '248',
    description: 'All submitted tickets',
    icon: Ticket,
    color: 'bg-blue-100 text-blue-600',
  },
  {
    title: 'Open Tickets',
    value: '64',
    description: 'Awaiting attention',
    icon: Clock,
    color: 'bg-amber-100 text-amber-600',
  },
  {
    title: 'Resolved Tickets',
    value: '156',
    description: 'Successfully completed',
    icon: CheckCircle,
    color: 'bg-green-100 text-green-600',
  },
  {
    title: 'High Priority',
    value: '28',
    description: 'Require urgent attention',
    icon: AlertCircle,
    color: 'bg-red-100 text-red-600',
  },
]

const recentTickets = [
  {
    id: '#TKT-001',
    title: 'Unable to access company email',
    department: 'IT',
    priority: 'High',
    status: 'Open',
  },
  {
    id: '#TKT-002',
    title: 'Leave application assistance',
    department: 'HR',
    priority: 'Medium',
    status: 'In Progress',
  },
  {
    id: '#TKT-003',
    title: 'Invoice payment query',
    department: 'Finance',
    priority: 'Low',
    status: 'Resolved',
  },
  {
    id: '#TKT-004',
    title: 'Laptop performance issues',
    department: 'IT',
    priority: 'High',
    status: 'Open',
  },
]

function Dashboard() {
  return (
    <div className="space-y-8">
      {/* Welcome section */}
      <div>
        <h2 className="text-2xl font-bold text-slate-800">
          Dashboard Overview
        </h2>
        <p className="mt-1 text-slate-500">
          Welcome back! Here's what's happening with your operations.
        </p>
      </div>

      {/* Summary cards */}
      <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 xl:grid-cols-4">
        {stats.map((stat) => {
          const Icon = stat.icon

          return (
            <div
              key={stat.title}
              className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm"
            >
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm text-slate-500">{stat.title}</p>
                  <h3 className="mt-2 text-3xl font-bold text-slate-800">
                    {stat.value}
                  </h3>
                </div>

                <div className={`rounded-lg p-3 ${stat.color}`}>
                  <Icon size={24} />
                </div>
              </div>

              <p className="mt-4 text-sm text-slate-400">
                {stat.description}
              </p>
            </div>
          )
        })}
      </div>

      {/* Ticket table */}
      <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
        <div className="flex items-center justify-between border-b border-slate-200 p-5">
          <div>
            <h3 className="font-semibold text-slate-800">Recent Tickets</h3>
            <p className="mt-1 text-sm text-slate-500">
              Latest requests submitted by employees
            </p>
          </div>

          <button className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700">
            View all
          </button>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-slate-500">
              <tr>
                <th className="px-5 py-3">Ticket ID</th>
                <th className="px-5 py-3">Title</th>
                <th className="px-5 py-3">Department</th>
                <th className="px-5 py-3">Priority</th>
                <th className="px-5 py-3">Status</th>
              </tr>
            </thead>

            <tbody className="divide-y divide-slate-100">
              {recentTickets.map((ticket) => (
                <tr key={ticket.id} className="hover:bg-slate-50">
                  <td className="whitespace-nowrap px-5 py-4 font-medium text-blue-600">
                    {ticket.id}
                  </td>
                  <td className="whitespace-nowrap px-5 py-4 text-slate-700">
                    {ticket.title}
                  </td>
                  <td className="px-5 py-4 text-slate-600">
                    {ticket.department}
                  </td>
                  <td className="px-5 py-4">
                    <span className={`rounded-full px-3 py-1 text-xs font-medium ${
                      ticket.priority === 'High'
                        ? 'bg-red-100 text-red-700'
                        : ticket.priority === 'Medium'
                        ? 'bg-amber-100 text-amber-700'
                        : 'bg-green-100 text-green-700'
                    }`}>
                      {ticket.priority}
                    </span>
                  </td>
                  <td className="px-5 py-4">
                    <span className={`rounded-full px-3 py-1 text-xs font-medium ${
                      ticket.status === 'Resolved'
                        ? 'bg-green-100 text-green-700'
                        : ticket.status === 'In Progress'
                        ? 'bg-blue-100 text-blue-700'
                        : 'bg-amber-100 text-amber-700'
                    }`}>
                      {ticket.status}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* AI insights panel */}
      <div className="rounded-xl bg-blue-50 p-6">
        <div className="flex items-center gap-3">
          <div className="rounded-lg bg-blue-100 p-3 text-blue-600">
            <TrendingUp size={24} />
          </div>

          <div>
            <h3 className="font-semibold text-slate-800">
              AI Operations Insight
            </h3>
            <p className="mt-1 text-sm text-slate-600">
              IT currently has the highest number of open tickets.
              Consider prioritising those requests.
            </p>
          </div>
        </div>
      </div>
    </div>
  )
}

export default Dashboard