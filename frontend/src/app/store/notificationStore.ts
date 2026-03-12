import { create } from 'zustand';

type NotificationType = 'success' | 'error' | 'info' | 'warning';

interface Notification {
  id: string;
  type: NotificationType;
  title: string;
  message?: string;
  duration?: number;
}

interface NotificationState {
  notifications: Notification[];
  addNotification: (notification: Omit<Notification, 'id'>) => void;
  removeNotification: (id: string) => void;
  clearNotifications: () => void;
}

let notificationId = 0;

export const useNotificationStore = create<NotificationState>((set) => ({
  notifications: [],

  addNotification: (notification) => {
    const id = `notification-${++notificationId}`;
    const newNotification: Notification = {
      ...notification,
      id,
      duration: notification.duration ?? 5000,
    };

    set((state) => ({
      notifications: [...state.notifications, newNotification],
    }));

    if (newNotification.duration && newNotification.duration > 0) {
      setTimeout(() => {
        set((state) => ({
          notifications: state.notifications.filter((n) => n.id !== id),
        }));
      }, newNotification.duration);
    }
  },

  removeNotification: (id) => {
    set((state) => ({
      notifications: state.notifications.filter((n) => n.id !== id),
    }));
  },

  clearNotifications: () => {
    set({ notifications: [] });
  },
}));

// Helper functions
export const showSuccess = (title: string, message?: string) => {
  useNotificationStore.getState().addNotification({ type: 'success', title, message });
};

export const showError = (title: string, message?: string) => {
  useNotificationStore.getState().addNotification({ type: 'error', title, message });
};

export const showInfo = (title: string, message?: string) => {
  useNotificationStore.getState().addNotification({ type: 'info', title, message });
};

export const showWarning = (title: string, message?: string) => {
  useNotificationStore.getState().addNotification({ type: 'warning', title, message });
};
