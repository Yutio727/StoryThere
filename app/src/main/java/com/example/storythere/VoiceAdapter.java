package com.example.storythere;


import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.speech.tts.Voice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
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
        String voiceName = voice.getName();

        int iconDrawableResId;
        String displayedVoiceName;

        // Set custom names and icons for Russian voices
        if (voiceName.equals("ru-ru-x-ruc-local")) {
            displayedVoiceName = "Яна";
            iconDrawableResId = R.drawable.storythere_icon;
        } else if (voiceName.equals("ru-ru-x-ruf-local")) {
            displayedVoiceName = "Ярослав";
            iconDrawableResId = R.drawable.dictor_yaroslav_icon;
        } else if (voiceName.equals("ru-ru-x-rud-network")) {
                displayedVoiceName = "Артем (требуется интернет)";
            iconDrawableResId = R.drawable.dictor_artem_icon;
        } else {
            // Fallback for other voices (if any are shown)
            displayedVoiceName = voiceName;
            iconDrawableResId = R.drawable.ic_voice_male; // Default icon
        }
        // Create layered drawable with the icon and circle frameAdd commentMore actions
        LayerDrawable layeredDrawable = (LayerDrawable) ContextCompat.getDrawable(holder.itemView.getContext(), R.drawable.circle_framed_icon);
        if (layeredDrawable != null) {
            Drawable iconDrawable = ContextCompat.getDrawable(holder.itemView.getContext(), iconDrawableResId);
            if (iconDrawable != null) {
                layeredDrawable.setDrawableByLayerId(R.id.icon_layer, iconDrawable); // Assuming you add an id for the icon layer in circle_framed_icon.xml
                holder.voiceIcon.setImageDrawable(layeredDrawable);
            } else {
                holder.voiceIcon.setImageResource(iconDrawableResId); // Fallback if layering fails
            }
        } else {
            holder.voiceIcon.setImageResource(iconDrawableResId); // Fallback if layered drawable fails
        }

        holder.voiceName.setText(displayedVoiceName);
        holder.voiceDetails.setVisibility(View.GONE);

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
        ImageView voiceIcon;
        TextView voiceName;
        TextView voiceDetails;

        VoiceViewHolder(View itemView) {
            super(itemView);
            voiceIcon = itemView.findViewById(R.id.voiceIcon);
            voiceName = itemView.findViewById(R.id.voiceName);
            voiceDetails = itemView.findViewById(R.id.voiceDetails);
        }
    }
} 