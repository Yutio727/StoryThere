<!-- RU -->
# StoryThere

<div align="center">
  <img width="160" height="160" alt="docs/media/logo.png" src="https://github.com/user-attachments/assets/0d0c194b-bec8-4e33-a0ff-a302aeb4bd43" />

  <h3>StoryThere</h3>
  <p><i>Читайте, слушайте и управляйте своей библиотекой в одном приложении.</i></p>

  <img src="https://img.shields.io/badge/Платформа-Android%208.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 8.0+"/>
  <img src="https://img.shields.io/badge/Java-8-ED8B00?logo=openjdk&logoColor=white" alt="Java 8"/>
  <img src="https://img.shields.io/badge/Лицензия-не%20указана-lightgrey" alt="Лицензия"/>
</div>

> **Статус:** в активной разработке.

---

## 🌐 Экосистема проекта

| Репозиторий | Описание |
|---|---|
| [**StoryThere**](https://github.com/Yutio727/StoryThere) | Android-клиент (этот репозиторий) |
| [**StoryThere API**](https://github.com/<owner>/<backend-repo>) | Бэкенд: каталог, библиотека пользователя, рекомендации, админ-API |

**Локализация:** 🇷🇺 русский — полностью · 🇬🇧 английский — полностью

---

## 📥 Скачать

<table>
  <tr>
    <td align="center" width="50%">
      <a href="https://github.com/Yutio727/StoryThere/releases/latest">
        <img src="https://img.shields.io/badge/Скачать-APK-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Скачать APK"/>
      </a>
    </td>
    <td width="50%">
      <b>Требования</b><br/>
      • Android 8.0 (API 26) и новее<br/>
      • ОЗУ: от 2 ГБ<br/>
      • Память: ~100 МБ + ваши книги<br/>
      • Интернет: для входа, каталога и ИИ-функций
    </td>
  </tr>
</table>

---

## 📸 Скриншоты и видео

<table>
  <tr>
    <td align="center"><img width="117.029702969" height="240" alt="Главная и рекомендации" src="https://github.com/user-attachments/assets/6781c0d4-8240-4b90-95fe-e86c91919eab" />
<br/><sub>Главная и рекомендации</sub></td>
    <td align="center"><img width="117.029702969" height="240" alt="Личная библиотека" src="https://github.com/user-attachments/assets/b7cdeec1-820b-4f31-9edd-1e7b8660e71a" />
<br/><sub>Личная библиотека</sub></td>
        <td align="center"><img width="117.029702969" height="240" alt="Читалка" src="https://github.com/user-attachments/assets/752b59d6-416c-4b4a-b7ff-6a4599a5efdc" />
<br/><sub>Читалка</sub></td>
  </tr>
  <tr>
    <td align="center"><img width="117.029702969" height="240" alt="Режим прослушивания" src="https://github.com/user-attachments/assets/f1cd3de0-7f8a-4c67-bfd7-7c46c4f4174a" /><br/><sub>Режим прослушивания</sub></td>
    <td align="center"><img width="117.029702969" height="240" alt="Каталог" src="https://github.com/user-attachments/assets/01d3e2bf-9d0b-4adf-81c2-80b794a22630" />
<br/><sub>Каталогg</sub></td>
    <td align="center"><img width="117.029702969" height="240" alt="Страница авторов" src="https://github.com/user-attachments/assets/bf72b4af-eebe-4e9a-91c2-bfb6683cb618" />
<br/><sub>Страница авторов</sub></td>
  </tr>
</table>

---

## ✨ Возможности и архитектура

### 📱 Мобильный клиент
- **Чтение** — импорт PDF, EPUB и TXT; настройки текста, закладки, выделение текста, сохранение прогресса.
- **Прослушивание** — встроенный синтез речи на русском и английском с выбором голоса и скорости; отдельный плеер аудиокниг.
- **Библиотека** — избранное, «прочитано», личная библиотека с синхронизацией на сервере; локальное хранение в Room для работы офлайн.
- **Каталог** — поиск, страницы авторов, персональные рекомендации книг и аудиокниг; онбординг с выбором жанров, авторов и книг.
- **ИИ-инструменты** — аннотации через GigaChat, генерация обложек через HuggingFace (FLUX.1-dev).
- **Аккаунты** — регистрация, вход и сброс пароля через Firebase Authentication; экран согласия на обработку персональных данных.
- **Интерфейс** — Material Design, светлая и тёмная темы, RU/EN.

### 🛠 Администрирование
- Пользователям с ролью `admin` доступно управление каталогом прямо в приложении: добавление, редактирование и удаление книг, аудиокниг и авторов.
- Загрузка обложек и аудиофайлов на сервер.

---

## 🧰 Технологии

| Область | Технологии |
|---|---|
| Платформа | `Java 8` `Android SDK 35 (min 26)` `Material Components` `ViewBinding` `LiveData / ViewModel` |
| Данные и документы | `Room` `Firebase Firestore` `Firebase Storage` `iText 7` `epub4j` `jsoup` `Glide` |
| Сеть и авторизация | `Retrofit` `OkHttp` `Gson` `Firebase Auth` |
| ИИ | `GigaChat API` `HuggingFace Inference API` |

---

## 🔨 Сборка из исходников

**Требуется:** JDK 17, Android Studio (последняя версия), Android SDK 35, Git.

1. Клонируйте репозиторий:
   ```sh
   git clone https://github.com/Yutio727/StoryThere.git
   cd StoryThere
   ```
2. Создайте `local.properties` в корне проекта (без него сборка не запустится):
   ```properties
   sdk.dir=/path/to/Android/Sdk
   GIGACHAT_DEFAULT_KEY=ваш_ключ_gigachat
   HUGGINGFACE_API_KEY=ваш_ключ_huggingface
   STORYTHERE_API_BASE_URL=http://10.0.2.2:8000/
   ```
   `STORYTHERE_API_BASE_URL` необязателен: по умолчанию эмулятор обращается к бэкенду, запущенному на хост-машине.
3. Замените `app/google-services.json` конфигом своего Firebase-проекта (с включёнными Authentication, Firestore и Storage).
4. Соберите debug-APK:
   ```sh
   ./gradlew assembleDebug
   ```

APK появится в `app/build/outputs/apk/debug/app-debug.apk`.

---
<!-- EN -->
# StoryThere

<div align="center">
  <img width="160" height="160" alt="docs/media/logo.png" src="https://github.com/user-attachments/assets/0d0c194b-bec8-4e33-a0ff-a302aeb4bd43" />



  <h3>StoryThere</h3>
  <p><i>Read, listen to, and manage your book collection — all in one app.</i></p>

  <img src="https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 8.0+"/>
  <img src="https://img.shields.io/badge/Java-8-ED8B00?logo=openjdk&logoColor=white" alt="Java 8"/>
  <img src="https://img.shields.io/badge/License-not%20specified-lightgrey" alt="License"/>
</div>

> **Status:** in active development.

---

## 🌐 Project Ecosystem

| Repository | Description |
|---|---|
| [**StoryThere**](https://github.com/Yutio727/StoryThere) | Android client (this repository) |
| [**StoryThere API**](https://github.com/<owner>/<backend-repo>) | Backend: catalog, user library, recommendations, admin API |

**Localization:** 🇷🇺 Russian — full · 🇬🇧 English — full

---

## 📥 Download

<table>
  <tr>
    <td align="center" width="50%">
      <a href="https://github.com/Yutio727/StoryThere/releases/latest">
        <img src="https://img.shields.io/badge/Download-APK-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Download APK"/>
      </a>
    </td>
    <td width="50%">
      <b>Requirements</b><br/>
      • Android 8.0 (API 26) or newer<br/>
      • RAM: 2 GB+<br/>
      • Storage: ~100 MB + your books<br/>
      • Internet: for sign-in, catalog, and AI features
    </td>
  </tr>
</table>

---

## 📸 Showcase & Media

<table>
  <tr>
    <td align="center"><img width="117.029702969" height="240" alt="Home recommendations" src="https://github.com/user-attachments/assets/6781c0d4-8240-4b90-95fe-e86c91919eab" />
<br/><sub>Home &amp; recommendations</sub></td>
    <td align="center"><img width="117.029702969" height="240" alt="Personal library" src="https://github.com/user-attachments/assets/b7cdeec1-820b-4f31-9edd-1e7b8660e71a" />
<br/><sub>Personal library</sub></td>
        <td align="center"><img width="117.029702969" height="240" alt="Reader" src="https://github.com/user-attachments/assets/752b59d6-416c-4b4a-b7ff-6a4599a5efdc" />
<br/><sub>Reader</sub></td>
  </tr>
  <tr>
    <td align="center"><img width="117.029702969" height="240" alt="Listening mode" src="https://github.com/user-attachments/assets/f1cd3de0-7f8a-4c67-bfd7-7c46c4f4174a" /><br/><sub>Listening mode</sub></td>
    <td align="center"><img width="117.029702969" height="240" alt="Catalog" src="https://github.com/user-attachments/assets/01d3e2bf-9d0b-4adf-81c2-80b794a22630" />
<br/><sub>Catalog</sub></td>
    <td align="center"><img width="117.029702969" height="240" alt="Author page" src="https://github.com/user-attachments/assets/bf72b4af-eebe-4e9a-91c2-bfb6683cb618" />
<br/><sub>Author page</sub></td>
  </tr>
</table>

---

## ✨ Features & Architecture

### 📱 Mobile client
- **Reading** — import PDF, EPUB, and TXT; adjustable text settings, bookmarks, text selection, progress tracking.
- **Listening** — built-in Text-to-Speech in Russian and English with voice selection and playback speed; dedicated audiobook player.
- **Library** — favourites, "already read", per-user library synced with the server; local storage via Room for offline access.
- **Catalog & discovery** — search, author pages, personalized book and audiobook recommendations; onboarding with genre, author, and book preferences.
- **AI tools** — book annotations via GigaChat, cover generation via HuggingFace (FLUX.1-dev).
- **Accounts** — registration, login, and password reset via Firebase Authentication; personal data consent screen.
- **UI** — Material Design, light/dark themes, RU/EN interface.

### 🛠 Admin tools
- Users with the `admin` role get in-app management of the catalog: create, edit, and delete books, audiobooks, and authors.
- Upload of covers and audio assets to the server.

---

## 🧰 Tech Stack

| Area | Technologies |
|---|---|
| Core platform | `Java 8` `Android SDK 35 (min 26)` `Material Components` `ViewBinding` `LiveData / ViewModel` |
| Data & documents | `Room` `Firebase Firestore` `Firebase Storage` `iText 7` `epub4j` `jsoup` `Glide` |
| Networking & auth | `Retrofit` `OkHttp` `Gson` `Firebase Auth` |
| AI | `GigaChat API` `HuggingFace Inference API` |

---

## 🔨 Building from Source

**Prerequisites:** JDK 17, Android Studio (latest), Android SDK 35, Git.

1. Clone the repository:
   ```sh
   git clone https://github.com/Yutio727/StoryThere.git
   cd StoryThere
   ```
2. Create `local.properties` in the project root (the build fails without it):
   ```properties
   sdk.dir=/path/to/Android/Sdk
   GIGACHAT_DEFAULT_KEY=your_gigachat_key
   HUGGINGFACE_API_KEY=your_huggingface_key
   STORYTHERE_API_BASE_URL=http://10.0.2.2:8000/
   ```
   `STORYTHERE_API_BASE_URL` is optional; the default points to a backend running on the host machine from the Android emulator.
3. Replace `app/google-services.json` with the config of your own Firebase project (Authentication, Firestore, and Storage enabled).
4. Build the debug APK:
   ```sh
   ./gradlew assembleDebug
   ```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

---
