package gal.uvigo.burnout_app.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import gal.uvigo.burnout_app.R;
import gal.uvigo.burnout_app.data.entity.DailyCommMetricsEntity;
import gal.uvigo.burnout_app.data.entity.HourlyCommMetricsEntity;
import gal.uvigo.burnout_app.data.repo.CommunicationRepository;
import gal.uvigo.burnout_app.helpers.TimeKey;

public class CommunicationViewModel extends AndroidViewModel {

    public static class UiState {
        public final int date;
        public final int callsCount;
        public final int messagesCount;
        public final long totalCommMs;
        public final String dominantChannel;
        public final long[] totalByHour;
        public final long[] voiceByHour;
        public final long[] textByHour;

        public UiState(int date,
                       int callsCount,
                       int messagesCount,
                       long totalCommMs,
                       String dominantChannel,
                       long[] totalByHour,
                       long[] voiceByHour,
                       long[] textByHour) {
            this.date = date;
            this.callsCount = callsCount;
            this.messagesCount = messagesCount;
            this.totalCommMs = totalCommMs;
            this.dominantChannel = dominantChannel;
            this.totalByHour = totalByHour;
            this.voiceByHour = voiceByHour;
            this.textByHour = textByHour;
        }
    }

    private final CommunicationRepository communicationRepository;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<UiState> uiState = new MutableLiveData<>();

    private int currentDay;

    public CommunicationViewModel(@NonNull Application application) {
        super(application);
        communicationRepository = new CommunicationRepository(application);

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
            DailyCommMetricsEntity dailyCommMetrics = communicationRepository.getDailyCommMetrics(date);

            int callsCount = (dailyCommMetrics != null) ? dailyCommMetrics.callsCount : 0;
            int messagesCount = (dailyCommMetrics != null) ? dailyCommMetrics.messagesCount : 0;

            long voiceDayMs = (dailyCommMetrics != null) ? Math.max(0L, dailyCommMetrics.voiceMs) : 0L;
            long textDayMs = (dailyCommMetrics != null) ? Math.max(0L, dailyCommMetrics.textMs) : 0L;

            long totalDayMs;
            if (dailyCommMetrics != null) {
                totalDayMs = Math.max(0L, dailyCommMetrics.totalCommMs);
                if (totalDayMs <= 0L) {
                    totalDayMs = voiceDayMs + textDayMs;
                }
            } else {
                totalDayMs = 0L;
            }

            String dominantChannel = "—";
            long maxChannelMs = Math.max(voiceDayMs, textDayMs);
            if (maxChannelMs > 0L) {
                dominantChannel = (voiceDayMs >= textDayMs) ? getApplication().getString(R.string.communications_channel_voice) : getApplication().getString(R.string.communications_channel_text);
            }

            long[] totalByHour = new long[24];
            long[] voiceByHour = new long[24];
            long[] textByHour = new long[24];

            List<HourlyCommMetricsEntity> hourlyCommMetrics =
                    communicationRepository.getHourlyCommMetricsByDate(date);
            if (hourlyCommMetrics == null) {
                hourlyCommMetrics = Collections.emptyList();
            }

            for (HourlyCommMetricsEntity hourlyRow : hourlyCommMetrics) {
                if (hourlyRow == null) continue;

                int hour = hourlyRow.hour;
                if (hour < 0 || hour > TimeKey.MAX_HOUR_OF_DAY) continue;

                long voiceValue = Math.max(0L, hourlyRow.voiceValue);
                long textValue = Math.max(0L, hourlyRow.textValue);

                voiceByHour[hour] = voiceValue;
                textByHour[hour] = textValue;

                long totalValue = Math.max(0L, hourlyRow.totalValue);
                if (totalValue <= 0L) {
                    totalValue = voiceValue + textValue;
                }
                totalByHour[hour] = totalValue;
            }

            uiState.postValue(new UiState(
                    date,
                    Math.max(0, callsCount),
                    Math.max(0, messagesCount),
                    totalDayMs,
                    dominantChannel,
                    totalByHour,
                    voiceByHour,
                    textByHour
            ));
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        ioExecutor.shutdown();
    }
}