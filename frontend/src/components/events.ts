/** Window event fired when the signed-in user's notifications may have changed. */
export const NOTIFICATIONS_CHANGED = 'ops:notifications-changed';

/** Ask the layout to refresh its unread badge without a full page reload. */
export function notifyNotificationsChanged() {
  window.dispatchEvent(new Event(NOTIFICATIONS_CHANGED));
}
