# EN
# Project is in development state.

---
# StoryThere
StoryThere is an Android app that brings your world of stories to your phone. It allows you to read, listen to, and manage your book collection, with features like AI-powered book annotation and cover generation.

---

## Features

- **Read and Listen:**
  - Import and read books in PDF, EPUB, and TXT formats
  - Listen to books with built-in Text-to-Speech (TTS) in English and Russian
  - Smart navigation, bookmarks, and reading progress tracking
- **Book Management:**
  - Add, delete, and organize books
  - Mark books as favourites or already read
  - Author and book detail views
- **AI-Powered Tools:**
  - Generate book annotations (summaries) using GigaChat AI
  - Generate book covers using HuggingFace AI
- **User Accounts:**
  - Register, login, and reset password with Firebase Authentication
  - User data and books are managed with Firebase Firestore and Storage
- **UI:**
  - Material Design, light/dark themes, and multi-language support (English, Russian)

---

## Supported Formats
- PDF (.pdf)
- EPUB (.epub)
- TXT (.txt)

---

## Requirements
- Android Studio (latest recommended)
- Android SDK 26+
- Java 8+
- Firebase project (for authentication and storage)
- API keys for GigaChat and HuggingFace (for AI features)

---

## Setup & Build Instructions

1. **Clone the repository:**
   ```sh
   git clone https://github.com/Yutio727/StoryThere.git
   cd StoryThere
   ```

2. **Open in Android Studio:**
   - Open the `StoryThere` folder in Android Studio.

3. **AI API Keys:**
   - Add your GigaChat and HuggingFace API keys to `local.properties` for the generation features to work:
     ```
     GIGACHAT_DEFAULT_KEY=your_gigachat_api_key
     HUGGINGFACE_API_KEY=your_huggingface_api_key
     ```

4. **Build the app:**
   - Use Android Studio's Build > Assemble Project, or run:
     ```sh
     ./gradlew assembleDebug
     ```

5. **Run on device/emulator:**
   - Deploy via Android Studio or use:
     ```sh
     adb install app/build/outputs/apk/debug/app-debug.apk
     ```
- Or just download the last release:
https://github.com/Yutio727/StoryThere/releases/tag/0.2.0
---

## Permissions
- Storage (read/write): for importing and managing books and book covers
- Internet: for Firebase and AI features
- Network state: for connectivity checks

---

## Usage
1. **Login or Register:**
   - Create an account or log in with your email and password.
2. **Import Books:**
   - Tap the "+" button to add books from your device (PDF, EPUB, TXT).
3. **Read or Listen:**
   - Open a book to read or use the listen feature for TTS.
4. **AI Features:**
   - Generate a summary or cover for your book using the AI buttons in the book options.
5. **Manage Library:**
   - Organize, bookmark, and mark books as favourites or read.

---

## Offline mode
- No need to log in, if you have no internet connection
- This feature is bugged and not fully implemented yet

---

## FireBase setup 
- There is no guide for setting up your Firebase Project for this app yet

---

## License
No license here yet




# RU
# Проект находится на стадии разработки.

---
# StoryThere
StoryThere — это Android-приложение, которое переносит ваш мир историй на телефон. Оно позволяет читать, слушать и управлять вашей коллекцией книг, использует функции на базе ИИ — аннотирование и генерация обложек.

---

## Возможности

- **Чтение и прослушивание:**
  - Импорт и чтение книг в форматах PDF, EPUB и TXT
  - Прослушивание книг с помощью встроенного синтеза речи (TTS) на английском и русском языках
  - Умная навигация, закладки и отслеживание прогресса чтения

- **Управление книгами:**
  - Добавление, удаление и организация книг
  - Отметка книг как избранные или уже прочитанные
  - Просмотр информации об авторах и книгах

- **Инструменты на базе ИИ:**
  - Генерация аннотаций (кратких описаний) с помощью GigaChat AI
  - Генерация обложек книг с использованием HuggingFace AI

- **Пользовательские аккаунты:**
  - Регистрация, вход и восстановление пароля через Firebase Authentication
  - Управление данными пользователей и книг через Firebase Firestore и Storage

- **Интерфейс:**
  - Материальный дизайн, светлая/тёмная темы и поддержка нескольких языков (английский, русский)

---

## Поддерживаемые форматы
- PDF (.pdf)
- EPUB (.epub)
- TXT (.txt)

---

## Требования
- Android Studio (рекомендуется последняя версия)
- Android SDK 26+
- Java 8+
- Проект Firebase (для аутентификации и хранения данных)
- API-ключи GigaChat и HuggingFace (для функций ИИ)

---

## Инструкции по установке и сборке

1. **Клонируйте репозиторий:**
   ```sh
   git clone https://github.com/Yutio727/StoryThere.git
   cd StoryThere

2. **Откройте в Android Studio:**
   - Откройте папку 'StoryThere' в Android Studio.

3. **AI API Keys:**
   - Добавьте собственные API ключи от GigaChat и HuggingFace в файл 'local.properties', чтобы заработали функции генерации описания и обложек:
     ```
     GIGACHAT_DEFAULT_KEY=your_gigachat_api_key
     HUGGINGFACE_API_KEY=your_huggingface_api_key
     ```

4. **Забилдите проект:**
   - Используя Build > Assemble Project в Android Studio, либо запустите данные команды из терминала:
     ```sh
     ./gradlew assembleDebug
     ```

5. **Запустите на своём устройстве или эмуляторе:**
   - Запустите в Android Studio или сделайте это из терминала:
     ```sh
     adb install app/build/outputs/apk/debug/app-debug.apk
     ```
- Или просто скачайте последний версию:
https://github.com/Yutio727/StoryThere/releases/tag/0.2.0
---

## Оффлайн режим
- Логиниться не нужно, если нет интернет соединения
- Эта функция пока что не работает полным образом

---

## Установка собственного Firebase 
- Пока что нет гайда для прикручивания вашего Firebase проэкта к этому приложению

---

## Лицензия
Здесь пока что нет лицензии
