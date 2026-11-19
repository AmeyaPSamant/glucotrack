# GlucoTrack - Diabetes Tracker Web Application
**Full Stack Java Project (Second Year Labs)**

GlucoTrack is a clinical blood glucose tracking web application built using **Core Java (built-in `HttpServer`)**, a genuine **SQLite SQL Database (`tracker.db`)**, and an **HTML5/CSS3/JavaScript** frontend with canvas data visualization.

---

## 👥 Team & Contributors
This project was developed by:
* **Ameya Samant**
* **Anish Sawant**
* **Omkar Sawant**

---

## How to Run the Application

### Option 1: Double-Click the Executable (`GlucoTrack.exe`) - Recommended
1. Open the project folder: `c:\Ameya\College\Second Year Labs\FSJP`.
2. Double-click **`GlucoTrack.exe`**.
3. It automatically:
   - Starts the Java backend server with SQLite (`tracker.db`).
   - Launches your default web browser and navigates directly to **`http://localhost:8080`**!

### Option 2: Run via Batch Script (`run.bat`)
1. Double-click **`run.bat`** in the `FSJP` directory.
2. Open your web browser and go to: **[http://localhost:8080](http://localhost:8080)**.

### Option 3: Running via BlueJ
1. Open **BlueJ**.
2. Go to **Project** > **Open Project...** and select:
   `c:\Ameya\College\Second Year Labs\FSJP`.
3. In BlueJ, configure the SQLite library:
   - Click **Tools** > **Preferences** > **Libraries**.
   - Click **Add File** and select `FSJP\lib\sqlite-jdbc.jar`.
   - Click **OK** (and restart BlueJ if prompted).
4. Right-click on the **`DiabetesTrackerServer`** class box.
5. Select **`void main(String[] args)`** and click **OK**.
6. Open your web browser and go to: **[http://localhost:8080](http://localhost:8080)**.

---

## Technical Stack

* **Backend**:
  * **Core Java (Java SE)**: Built-in `com.sun.net.httpserver.HttpServer` (zero external web containers or Tomcat required).
  * **Database**: **SQLite (`tracker.db`)** via standard JDBC driver (`sqlite-jdbc.jar`).
* **Frontend**:
  * **HTML5 & CSS3**: Professional clinical interface using CSS Flexbox and CSS Grid.
  * **Vanilla JavaScript**: Asynchronous API calls (`fetch`), canvas rendering, and state management.
* **Data Visualization**:
  * **HTML5 Canvas Line Chart**: Custom lightweight renderer (`js/chart.min.js`) plotting glucose trends over time with dynamic threshold scaling.
* **Clinical Calculation**:
  * **American Diabetes Association (ADA / Nathan et al.) Formula**:
    $$\text{Estimated HbA1c (\%)} = \frac{\text{Average Glucose} + 46.7}{28.7}$$
  * Classifies readings into **Normal** (< 5.7%), **Prediabetes** (5.7% - 6.4%), and **Diabetes Range** (>= 6.5%).

---

## Relational SQL Schema (`tracker.db`)

* **`users` Table**:
  ```sql
  CREATE TABLE IF NOT EXISTS users (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      username TEXT UNIQUE NOT NULL COLLATE NOCASE
  );
  ```

* **`sugar_logs` Table**:
  ```sql
  CREATE TABLE IF NOT EXISTS sugar_logs (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      username TEXT NOT NULL COLLATE NOCASE,
      sugar_level REAL NOT NULL,
      reading_type TEXT NOT NULL,
      log_date TEXT NOT NULL,
      notes TEXT,
      FOREIGN KEY (username) REFERENCES users(username)
  );
  ```

---

## Key Features

1. **Passwordless Authentication**: Register and log in instantly with a unique username, verified against the SQLite `users` table.
2. **Glucose Reading Management (CRUD)**: Log readings in `mg/dL` categorized by type (Fasting, Post-Meal, Bedtime, etc.) with timestamps, notes, and single-click deletion.
3. **Clinical HbA1c Estimation**: Real-time average glucose and estimated HbA1c calculation based on the ADA formula.
4. **Interactive Trend Graph**: Real-time Canvas line chart showing glucose fluctuations with coordinate badges.
5. **Live SQL Database Inspector (`database.html`)**:
   - Inspect all records in `users` and `sugar_logs`.
   - Built-in SQL Console allowing you and your professor to execute custom `SELECT` queries against `tracker.db` in real time.
   - One-click SQL Dump Export (`.sql` file) containing standard `CREATE TABLE` and `INSERT INTO` statements.
6. **WhatsApp & Email Sharing**: Generates a clinical health report and automatically encloses the chart image for sharing directly via WhatsApp Web or email.

---

## File Structure

```
FSJP/
├── GlucoTrack.exe             # Standalone Windows executable launcher
├── GlucoTrack.jar             # Executable JAR archive
├── Database.java              # SQLite connection and JDBC CRUD operations
├── DiabetesTrackerServer.java # Java HttpServer and REST API endpoints
├── compile.bat                # Batch script to compile Java code
├── run.bat                    # Batch script to launch server with one click
├── package.bluej              # BlueJ project workspace file
├── tracker.db                 # Auto-generated SQLite database file
├── lib/
│   └── sqlite-jdbc.jar        # SQLite JDBC driver
├── index.html                 # Homepage
├── login.html                 # Login page
├── register.html              # Registration page
├── dashboard.html             # Patient dashboard with chart, HbA1c, and logs
├── database.html              # SQL Database Inspector & .sql exporter
├── css/
│   └── style.css              # Responsive clinical stylesheet
└── js/
    ├── app.js                 # Frontend API calls and clinical calculations
    └── chart.min.js           # Lightweight Canvas line chart renderer
```
