Student Management System

Project layout

- frontend/  — Static web UI (index.html, app.js, styles.css, uploads/)
- backend/   — Java backend and sources
  - backend/src/ — Java source files (`DBConnection.java`, `StudentDatabaseWebServer.java`, `StudentManagement.java`)
  - backend/lib/ — third-party jars (MySQL connector)
  - backend/bin/ — compiled classes (optional)
- backup/    — backups of earlier files

Quickstart

Prerequisites
- Java 17+ (or compatible)
- MySQL server with database `student_db` accessible with credentials in `backend/src/DBConnection.java`

Build backend

From project root:

```bash
javac -cp "backend/lib/mysql-connector-j-9.7.0.jar" -d backend/bin backend/src/*.java
```

Run backend (serves static files from `frontend/` and exposes API at `/api`)

```bash
java -cp "backend/bin:backend/lib/mysql-connector-j-9.7.0.jar" StudentDatabaseWebServer
```

Open the app

- Backend-served UI: http://localhost:8080
- Or run a static server for frontend only (no API):

```bash
python3 -m http.server 8000 --directory frontend
```

Notes

- `DBConnection.java` contains the DB URL/user/password used to connect to MySQL. Update if needed.
- The server expects the frontend static files in the `frontend/` directory (this change was made as part of the reorganization).

Git

- Project reorganized to preserve history (used `git mv`).
