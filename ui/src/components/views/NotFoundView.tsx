import { Link } from 'react-router'

export default function NotFoundView() {
  return (
    <div className="flex flex-col gap-3 px-12 py-9">
      <h1 className="m-0 text-xl font-semibold">Page not found</h1>
      <p className="m-0 text-muted">There is nothing at this address.</p>
      <Link to="/chat" className="text-think">
        Go to chats
      </Link>
    </div>
  )
}
