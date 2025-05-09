package com.example.storythere;

import android.speech.tts.Voice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class VoiceAdapter extends RecyclerView.Adapter<VoiceAdapter.VoiceViewHolder> {
    private List<Voice> voices;
    private OnVoiceSelectedListener listener;

    public interface OnVoiceSelectedListener {
        void onVoiceSelected(Voice voice);
    }

    public VoiceAdapter(List<Voice> voices, OnVoiceSelectedListener listener) {
        this.voices = voices;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VoiceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_voice, parent, false);
        return new VoiceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VoiceViewHolder holder, int position) {
        Voice voice = voices.get(position);
        holder.voiceName.setText(voice.getName());
        
        // Build voice details string
        StringBuilder details = new StringBuilder();
        String language = voice.getLocale().getLanguage();
        String country = voice.getLocale().getCountry();
        
        // Add language name
        if (language.equals("en")) {
            details.append("English");
        } else if (language.equals("ru")) {
            details.append("Russian");
        }
        
        // Add country if available
        if (country != null && !country.isEmpty()) {
            details.append(" (").append(country).append(")");
        }
        
        // Add gender information
        if (voice.getName().toLowerCase().contains("female")) {
            details.append(" • Female Voice");
        } else if (voice.getName().toLowerCase().contains("male")) {
            details.append(" • Male Voice");
        }
        
        holder.voiceDetails.setText(details.toString());

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onVoiceSelected(voice);
            }
        });
    }

    @Override
    public int getItemCount() {
        return voices.size();
    }

    static class VoiceViewHolder extends RecyclerView.ViewHolder {
        TextView voiceName;
        TextView voiceDetails;

        VoiceViewHolder(View itemView) {
            super(itemView);
            voiceName = itemView.findViewById(R.id.voiceName);
            voiceDetails = itemView.findViewById(R.id.voiceDetails);
        }
    }
} 