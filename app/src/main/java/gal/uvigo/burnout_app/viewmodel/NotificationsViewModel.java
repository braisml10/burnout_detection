package gal.uvigo.burnout_app.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import gal.uvigo.burnout_app.data.repo.NotificationRepository;
import gal.uvigo.burnout_app.helpers.TimeKey;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificationsViewModel extends AndroidViewModel {

    public static class UiState {
        public final int date;
        public final int totalDaily;
        public final int avgPerHour;
        public final String mostIntrusiveApp;
        public final int[] notificationCountByHour;
        public final List<NotificationRepository.TopNotificationAppRow> topNotificationApps;
        public final List<NotificationRepository.NotificationCategoryCountRow> notificationCountByCategory;

        public UiState(int date,
                       int totalDaily,
                       int avgPerHour,
                       String mostIntrusiveApp,
                       int[] notificationCountByHour,
                       List<NotificationRepository.TopNotificationAppRow> topNotificationApps,
                       List<NotificationRepository.NotificationCategoryCountRow> notificationCountByCategory) {
            this.date = date;
            this.totalDaily = totalDaily;
            this.avgPerHour = avgPerHour;
            this.mostIntrusiveApp = mostIntrusiveApp;
            this.notificationCountByHour = notificationCountByHour;
            this.topNotificationApps = topNotificationApps;
            this.notificationCountByCategory = notificationCountByCategory;
        }
    }

    private final NotificationRepository notificationRepository;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private final MutableLiveData<UiState> uiState = new MutableLiveData<>();

    private int currentDay;

    public NotificationsViewModel(@NonNull Application application) {
        super(application);

        notificationRepository = new NotificationRepository(application);

        currentDay = TimeKey.epochDayLocal(System.currentTimeMillis());
        loadDay(currentDay);
    }

    public LiveData<UiState> getUiState() {
        return uiState;
    }

    public void loadDay(int date) {
        if (date == currentDay && uiState.getValue() != null) return;
        currentDay = date;

        ioExecutor.execute(() -> {
            int totalDaily = notificationRepository.getNotificationCountByDate(date);

            int activeHourCount = notificationRepository.getActiveHourCountByDate(date);
            if (activeHourCount <= 0) activeHourCount = 1;

            int avgPerHour = (int) Math.round(totalDaily / (double) activeHourCount);

            int[] notificationCountByHour = notificationRepository.getNotificationCountByHour(date);

            List<NotificationRepository.TopNotificationAppRow> topNotificationApps =
                    notificationRepository.getTopNotificationAppsByDate(date, 8);
            if (topNotificationApps == null) {
                topNotificationApps = Collections.emptyList();
            }

            String mostIntrusiveApp = "—";
            if (!topNotificationApps.isEmpty()) {
                mostIntrusiveApp = topNotificationApps.get(0).name;
            }

            List<NotificationRepository.NotificationCategoryCountRow> notificationCountByCategory =
                    notificationRepository.getNotificationCountByCategory(date);
            if (notificationCountByCategory == null) {
                notificationCountByCategory = Collections.emptyList();
            }

            uiState.postValue(new UiState(
                    date,
                    totalDaily,
                    avgPerHour,
                    mostIntrusiveApp,
                    notificationCountByHour,
                    topNotificationApps,
                    notificationCountByCategory
            ));
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        ioExecutor.shutdown();
    }
}