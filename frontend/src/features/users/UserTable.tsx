import { Link } from 'react-router-dom'
import type { UserResponse } from '../../api/types'
import { Badge } from '../../components/ui/Badge'

interface UserTableProps {
  users: UserResponse[]
}

function formatDate(value: string) {
  return new Date(value).toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}

export function UserTable({ users }: UserTableProps) {
  return (
    <div className="overflow-x-auto rounded-xl border border-lb-border bg-lb-surface">
      <table className="w-full min-w-[35rem] text-left text-sm">
        <thead className="border-b border-lb-border bg-lb-elevated text-xs uppercase tracking-wide text-lb-muted">
          <tr>
            <th scope="col" className="px-5 py-4">Username</th>
            <th scope="col" className="w-32 px-5 py-4">Role</th>
            <th scope="col" className="w-44 px-5 py-4">Joined</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-lb-border">
          {users.map((user) => (
            <tr key={user.id} className="transition-colors hover:bg-lb-elevated/60">
              <td className="px-5 py-4">
                <Link
                  to={`/users/${user.id}`}
                  aria-label={`View profile for ${user.username}`}
                  className="font-medium text-lb-text underline-offset-4 hover:text-lb-focus hover:underline"
                >
                  {user.username}
                </Link>
              </td>
              <td className="px-5 py-4">
                <Badge tone={user.role === 'ADMIN' ? 'primary' : 'neutral'}>{user.role}</Badge>
              </td>
              <td className="px-5 py-4 text-lb-muted">
                <time dateTime={user.registeredAt}>{formatDate(user.registeredAt)}</time>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
