# Voxa 🎙️

Voxa is a high-performance, immersive voice assistant application built with modern Android development practices. It specializes in managing personal itineraries and providing reliable, real-time alerts.

## ✨ Features

- **Voice-Driven Interactions:** Immersive recording screen with dynamic AGSL shaders.
- **Smart Itinerary Management:** Track your daily priorities with a sleek, count-down dashboard.
- **Lock Screen Alerts:** Custom, high-priority alert system that works directly from the lock screen.
- **Zero-Jank UI:** Optimized AGSL Shaders (Background, Orb, Waveforms) and centralized audio management for smooth transitions.
- **Battery Efficient:** Reliable background scheduling using `AlarmManager` and `WorkManager` with smart `WakeLock` handling.


## 🛠 Tech Stack

- **UI:** Jetpack Compose (Material 3)
- **Graphics:** AGSL Shaders (Android 13+)
- **Architecture:** MVVM (ViewModel, StateFlow)
- **Database:** Room Persistence Library
- **Storage:** Jetpack DataStore (Preferences)
- **Background Tasks:** WorkManager & AlarmManager
- **Image Loading:** Coil

## 🚀 Getting Started

1. **Clone the repository:**
   ```bash
   git clone https://github.com/hamzatahir17/Voxa.git
   ```

2. **Configure API Keys:**
   Open `local.properties` and add your Gemini API Key (if applicable):
   ```properties
   GEMINI_API_KEY=your_actual_key_here
   ```

3. **Build & Run:**
   Open the project in Android Studio (Ladybug or newer) and run the `app` module.

## 🔒 Security

This project uses a secure secrets management system. Sensitive files like `google-services.json`, keystores, and local property files are excluded from version control via `.gitignore`.

---
Developed by [hamzatahir17](https://github.com/hamzatahir17)
