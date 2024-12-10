# Extract-A-Track

Extract-A-Track is a powerful web-based application that allows users to upload and process audio tracks. Using advanced tools like [Spleeter](https://github.com/deezer/spleeter) and [Librosa](https://librosa.org/), it enables stem extraction, audio analysis, and more, providing an easy way for musicians, producers, and audio enthusiasts to manage their music files.

---

## Features

- **Audio Upload and Processing:**
  - Users can upload WAV files, which are processed to extract stems (e.g., vocals, instrumentals).
  - Converts uploaded files to MP3 format for convenience.

- **Audio Analysis:**
  - Powered by `Librosa`, the application analyzes audio files for key details like tempo, beats, and pitch.

- **AWS S3 Integration:**
  - Uploaded tracks are securely stored and organized in an S3 bucket.
  - Temporary folders are created for storing files during processing.

- **Subscription-Based Access:**
  - Users must subscribe to upload tracks to the database.
  - Non-subscribed users can still use the free Spleeter form for stem extraction.

- **Download Options:**
  - Users can download individual stems directly or as a zipped file.

---

## Tech Stack

- **Backend:**
  - Java (Spring Boot)
  - Spleeter (Python)
  - Librosa (Python)
- **Frontend:**
  - HTML, CSS, JavaScript
  - JSP for dynamic views
- **Database:**
  - MySQL
- **Storage:**
  - AWS S3 for file management
- **Payments:**
  - Stripe for handling subscriptions

---

## Setup Instructions

### Prerequisites

1. **Install Dependencies:**
   - Java JDK (11 or later)
   - Python 3.10 with virtual environment for Spleeter and Librosa
   - MySQL
2. **AWS Configuration:**
   - Set up an S3 bucket and configure credentials.
3. **Environment Variables:**
   - Use `.env` or environment variables for sensitive data:
     - `STRIPE_API_KEY`
     - `DB_USERNAME`
     - `DB_PASSWORD`

### Steps

1. Clone the repository:
   ```bash
   git clone https://github.com/bhil504/Extract-A-Track-Website.git
   cd Extract-A-Track-Website

Usage
Upload Track:
Log in, upload a WAV file, and wait for processing.
Analyze Track:
Use the "Analyze with Librosa" feature to retrieve audio insights.
Download Stems:
Choose individual stems or download them as a zip.
Contributions
Contributions are welcome! Fork the repository and submit a pull request with improvements.

License
This project is licensed under the MIT License.

Contact
For questions, contact Bhillion Dollar Productions at:
📧 bhilliondollarproductions@gmail.com
🌐 www.bhilliondollar.com


---

Let me know if you'd like to tweak or add anything to the `README.md`! 😊
