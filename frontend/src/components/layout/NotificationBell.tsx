import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { getUnreadCount, listNotifications, markNotificationRead } from "../../api/notifications";
import type { NotificationDto } from "../../types/api";

const POLL_INTERVAL_MS = 30_000;

/**
 * A header bell with an unread badge and a small dropdown of the latest few unread
 * notifications - the "did anything just happen" glance every other page in this nav
 * doesn't cover. Polls the unread count on an interval rather than a websocket/SSE
 * connection - the same tradeoff every other "is there something new" surface in this
 * codebase makes (there's no push-notification transport anywhere yet).
 */
export function NotificationBell() {
  const [unreadCount, setUnreadCount] = useState(0);
  const [isOpen, setIsOpen] = useState(false);
  const [preview, setPreview] = useState<NotificationDto[]>([]);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let cancelled = false;
    function refreshCount() {
      getUnreadCount()
        .then((res) => {
          if (!cancelled) setUnreadCount(res.unreadCount);
        })
        .catch(() => undefined);
    }
    refreshCount();
    const interval = setInterval(refreshCount, POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, []);

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  function toggleOpen() {
    if (!isOpen) {
      listNotifications({ unreadOnly: true, size: 5, sort: "createdAt,desc" })
        .then((res) => setPreview(res.content))
        .catch(() => setPreview([]));
    }
    setIsOpen((prev) => !prev);
  }

  async function handleMarkRead(notificationId: string) {
    try {
      await markNotificationRead(notificationId);
      setPreview((prev) => prev.filter((n) => n.id !== notificationId));
      setUnreadCount((prev) => Math.max(0, prev - 1));
    } catch {
      // A stale badge count is a minor annoyance, not worth surfacing an error banner for.
    }
  }

  return (
    <div ref={containerRef} className="relative">
      <button
        type="button"
        onClick={toggleOpen}
        className="relative rounded-md p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-900"
        aria-label={`Notifications${unreadCount > 0 ? ` (${unreadCount} unread)` : ""}`}
      >
        <BellIcon />
        {unreadCount > 0 && (
          <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-red-600 px-1 text-[10px] font-semibold text-white">
            {unreadCount > 9 ? "9+" : unreadCount}
          </span>
        )}
      </button>

      {isOpen && (
        <div className="absolute right-0 z-10 mt-2 w-80 rounded-lg border border-slate-200 bg-white p-2 shadow-lg">
          <p className="px-2 py-1 text-xs font-medium uppercase tracking-wide text-slate-400">Unread notifications</p>
          {preview.length === 0 && <p className="px-2 py-3 text-sm text-slate-400">You&apos;re all caught up.</p>}
          <ul className="flex flex-col">
            {preview.map((notification) => (
              <li key={notification.id} className="flex items-start justify-between gap-2 rounded-md px-2 py-2 hover:bg-slate-50">
                <div>
                  <p className="text-sm font-medium text-slate-900">{notification.title}</p>
                  {notification.body && <p className="mt-0.5 line-clamp-2 text-xs text-slate-500">{notification.body}</p>}
                </div>
                <button
                  type="button"
                  onClick={() => void handleMarkRead(notification.id)}
                  className="shrink-0 text-xs text-slate-400 hover:text-slate-900 hover:underline"
                >
                  Mark read
                </button>
              </li>
            ))}
          </ul>
          <Link
            to="/notifications"
            onClick={() => setIsOpen(false)}
            className="mt-1 block rounded-md px-2 py-2 text-center text-sm font-medium text-slate-600 hover:bg-slate-50 hover:text-slate-900"
          >
            View all
          </Link>
        </div>
      )}
    </div>
  );
}

function BellIcon() {
  return (
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.75} className="h-5 w-5">
      <path strokeLinecap="round" strokeLinejoin="round" d="M15 17h5l-1.4-1.4A2 2 0 0 1 18 14.2V11a6 6 0 1 0-12 0v3.2c0 .5-.2 1-.6 1.4L4 17h5m6 0v1a3 3 0 1 1-6 0v-1m6 0H9" />
    </svg>
  );
}
