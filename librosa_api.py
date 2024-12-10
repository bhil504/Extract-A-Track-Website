import json
import librosa
import numpy as np
import warnings
import sys

# Suppress warnings
warnings.filterwarnings("ignore", category=DeprecationWarning)

def analyze_audio(file_path):
    try:
        y, sr = librosa.load(file_path, sr=None)
        tempo, beats = librosa.beat.beat_track(y=y, sr=sr)
        spectral_centroid = librosa.feature.spectral_centroid(y=y, sr=sr).mean()
        rms = librosa.feature.rms(y=y).mean()

        chroma_stft = librosa.feature.chroma_stft(y=y, sr=sr)
        estimated_key = estimate_key_with_chroma(chroma_stft)

        beats_list = beats.tolist()
        melody = chroma_stft.mean(axis=1).tolist()
        mfcc = librosa.feature.mfcc(y=y, sr=sr, n_mfcc=13).mean(axis=1).tolist()
        spectral_features = {
            'centroid': float(spectral_centroid),
            'bandwidth': float(librosa.feature.spectral_bandwidth(y=y, sr=sr).mean()),
            'contrast': librosa.feature.spectral_contrast(y=y, sr=sr).mean(axis=1).tolist(),
            'rolloff': float(librosa.feature.spectral_rolloff(y=y, sr=sr, roll_percent=0.85).mean())
        }

        result = {
            'tempo': float(tempo),
            'spectral_centroid': float(spectral_centroid),
            'rms': float(rms),
            'key': estimated_key,
            'beats': beats_list,
            'melody': melody,
            'mfcc': mfcc,
            'spectral_features': spectral_features
        }

        print(json.dumps(result))  # Output JSON only

    except Exception as e:
        print(json.dumps({"error": str(e)}))

def estimate_key_with_chroma(chroma):
    try:
        chroma_mean = chroma.mean(axis=1)
        chroma_mean = chroma_mean / np.max(chroma_mean)
        filtered_chroma = np.where(chroma_mean > 0.2, chroma_mean, 0)

        note_mapping = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"]
        tonic_note_index = np.argmax(filtered_chroma)
        tonic_note = note_mapping[tonic_note_index % 12]

        mode = "major" if tonic_note_index % 2 == 0 else "minor"
        return f"{tonic_note} {mode}"
    except Exception:
        return "Unknown"

if __name__ == "__main__":
    if len(sys.argv) > 1:
        audio_file = sys.argv[1]
        analyze_audio(audio_file)
    else:
        print(json.dumps({"error": "No audio file provided"}))
