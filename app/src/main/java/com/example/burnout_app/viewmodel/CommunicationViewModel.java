package com.example.burnout_app.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.burnout_app.data.entity.DailyCommMetricsEntity;
import com.example.burnout_app.data.entity.HourlyCommMetricsEntity;
import com.example.burnout_app.data.repo.CommunicationRepository;
import com.example.burnout_app.helpers.TimeKey;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CommunicationViewModel extends AndroidViewModel {

    public static class UiState {
        public final int date;

        public final int callsCount;
        public final int messagesCount;
        public final long totalCommMs;
        public final String dominantChannel; // "Voz" | "Texto" | "—"

        public final long[] totalByHour; // 24
        public final long[] voiceByHour; // 24
        public final long[] textByHour;  // 24

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

    private final CommunicationRepository repo;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final MutableLiveData<UiState> uiState = new MutableLiveData<>();

    private int currentDay;

    public CommunicationViewModel(@NonNull Application app) {
        super(app);
        repo = new CommunicationRepository(app);

        currentDay = TimeKey.epochDayLocal(System.currentTimeMillis());
        loadDay(currentDay);
    }

    public LiveData<UiState> getUiState() {
        return uiState;
    }

    public void loadDay(int date) {
        // OJO: esto puede evitar refrescar aunque la DB cambie.
        // Si quieres siempre refrescar, elimina este if.
        if (date == currentDay && uiState.getValue() != null) return;

        currentDay = date;

        io.execute(() -> {

            // ---------------------------
            // Daily (usa lo guardado en DB)
            // ---------------------------
            DailyCommMetricsEntity d = repo.getDailyCommForDay(date);

            int calls = (d != null) ? d.calls_count : 0;
            int msgs  = (d != null) ? d.messages_count : 0;

            long voiceDay = (d != null) ? Math.max(0L, d.voice_ms) : 0L;
            long textDay  = (d != null) ? Math.max(0L, d.text_ms)  : 0L;

            long totalDay;
            if (d != null) {
                totalDay = Math.max(0L, d.total_comm_ms);
                if (totalDay <= 0L) totalDay = voiceDay + textDay; // fallback
            } else {
                totalDay = 0L;
            }

            String dominant = "—";
            long max = Math.max(voiceDay, textDay);
            if (max > 0L) dominant = (voiceDay >= textDay) ? "Voz" : "Texto";

            // ---------------------------
            // Hourly (24h)
            // ---------------------------
            long[] totalByHour = new long[24];
            long[] voiceByHour = new long[24];
            long[] textByHour  = new long[24];

            List<HourlyCommMetricsEntity> rows = repo.getHourlyCommForDay(date);
            if (rows == null) rows = Collections.emptyList();

            for (HourlyCommMetricsEntity h : rows) {
                if (h == null) continue;

                int hour = h.hour;
                if (hour < 0 || hour > 23) continue;

                long v = Math.max(0L, h.voice_value);
                long t = Math.max(0L, h.text_value);

                voiceByHour[hour] = v;
                textByHour[hour] = t;

                long tot = Math.max(0L, h.total_value);
                if (tot <= 0L) tot = v + t;
                totalByHour[hour] = tot;
            }

            uiState.postValue(new UiState(
                    date,
                    Math.max(0, calls),
                    Math.max(0, msgs),
                    totalDay,
                    dominant,
                    totalByHour,
                    voiceByHour,
                    textByHour
            ));
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        io.shutdown();
    }
}