// ============================================================
// GlucoTrack - Client Application Logic
// Communicates with Java HttpServer Backend (Database: SQLite tracker.db)
// ============================================================

let sugarChartInstance = null;
let currentLogs = [];

// Determine API base URL (works seamlessly via http://localhost:8080 or file:///)
const API_BASE = (window.location.protocol === 'http:' || window.location.protocol === 'https:')
    ? ''
    : 'http://localhost:8080';

// Helper to display alert banner
function showAlert(message, type = 'error') {
    const alertBox = document.getElementById('alertBox');
    if (!alertBox) return;
    alertBox.textContent = message;
    alertBox.className = 'alert ' + (type === 'success' ? 'alert-success' : 'alert-error');
    alertBox.style.display = 'block';
}

function getCurrentUser() {
    return sessionStorage.getItem('glucotrack_user') || localStorage.getItem('glucotrack_user');
}

// ----------------- USER AUTHENTICATION -----------------

function handleRegister(event) {
    event.preventDefault();
    const username = document.getElementById('username').value.trim();

    if (!username) {
        showAlert('Please enter a username.');
        return;
    }

    fetch(API_BASE + '/api/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: username })
    })
    .then(res => res.json())
    .then(data => {
        if (data.success) {
            showAlert('Registration successful. Redirecting to login...', 'success');
            setTimeout(() => {
                window.location.href = 'login.html';
            }, 1000);
        } else {
            showAlert(data.message || 'Registration failed.');
        }
    })
    .catch(err => {
        console.warn('Backend connection failed, falling back to local verification:', err);
        // Fallback for offline file demonstration
        localStorage.setItem('glucotrack_user', username);
        showAlert('User registered (Local). Redirecting to login...', 'success');
        setTimeout(() => { window.location.href = 'login.html'; }, 1000);
    });
}

function handleLogin(event) {
    event.preventDefault();
    const username = document.getElementById('username').value.trim();

    if (!username) {
        showAlert('Please enter a username.');
        return;
    }

    fetch(API_BASE + '/api/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: username })
    })
    .then(res => res.json())
    .then(data => {
        if (data.success) {
            sessionStorage.setItem('glucotrack_user', data.username);
            localStorage.setItem('glucotrack_user', data.username);
            window.location.href = 'dashboard.html';
        } else {
            showAlert(data.message || 'User not found in SQL database.');
        }
    })
    .catch(err => {
        console.warn('Backend connection failed, falling back to local session:', err);
        sessionStorage.setItem('glucotrack_user', username);
        localStorage.setItem('glucotrack_user', username);
        window.location.href = 'dashboard.html';
    });
}

function logout() {
    sessionStorage.removeItem('glucotrack_user');
    localStorage.removeItem('glucotrack_user');
    window.location.href = 'login.html';
}

// ----------------- DASHBOARD & LOGS -----------------

function initDashboard() {
    const username = getCurrentUser();

    if (!username) {
        window.location.href = 'login.html';
        return;
    }

    const userDisplay = document.getElementById('userDisplayName');
    if (userDisplay) userDisplay.textContent = username;

    const dateInput = document.getElementById('logDate');
    if (dateInput) {
        const now = new Date();
        const offset = now.getTimezoneOffset() * 60000;
        dateInput.value = (new Date(now - offset)).toISOString().slice(0, 16);
    }

    loadDashboardData(username);
}

function loadDashboardData(username) {
    fetch(API_BASE + '/api/logs?username=' + encodeURIComponent(username))
        .then(res => res.json())
        .then(data => {
            if (data.success) {
                currentLogs = data.logs || [];
                renderStats(data.stats);
                renderChart(currentLogs);
                renderTable(currentLogs);
            } else {
                renderEmptyState();
            }
        })
        .catch(err => {
            console.warn('Unable to load from Java backend:', err);
            renderEmptyState();
        });
}

function renderEmptyState() {
    currentLogs = [];
    renderStats({
        totalReadings: 0,
        averageGlucose: 0,
        estimatedHbA1c: 0,
        status: 'No Data Yet'
    });
    renderChart([]);
    renderTable([]);
}

function renderStats(stats) {
    const totalElem = document.getElementById('totalReadings');
    const avgElem = document.getElementById('avgGlucose');
    const hba1cElem = document.getElementById('hba1cEstimate');
    const statusElem = document.getElementById('hba1cStatus');

    if (totalElem) totalElem.textContent = stats.totalReadings || 0;
    if (avgElem) avgElem.textContent = (stats.averageGlucose && stats.averageGlucose > 0) ? (stats.averageGlucose + ' mg/dL') : '--';
    if (hba1cElem) hba1cElem.textContent = (stats.estimatedHbA1c && stats.estimatedHbA1c > 0) ? (stats.estimatedHbA1c + '%') : '--';

    if (statusElem) {
        statusElem.textContent = stats.status || 'No Data Yet';
        statusElem.className = 'stat-badge';
        if (stats.status === 'Normal') statusElem.classList.add('badge-normal');
        else if (stats.status === 'Prediabetes') statusElem.classList.add('badge-prediabetes');
        else if (stats.status === 'Diabetes Range') statusElem.classList.add('badge-diabetes');
        else statusElem.classList.add('badge-nodata');
    }
}

function renderChart(logs) {
    const ctx = document.getElementById('sugarTrendChart');
    if (!ctx) return;

    if (typeof Chart === 'undefined') {
        console.error('Chart library is not loaded');
        return;
    }

    const labels = logs.map(l => {
        const d = new Date(l.log_date);
        return isNaN(d.getTime()) ? l.log_date : d.toLocaleDateString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
    });
    const values = logs.map(l => l.sugar_level);

    if (sugarChartInstance) {
        sugarChartInstance.destroy();
    }

    sugarChartInstance = new Chart(ctx.getContext('2d'), {
        type: 'line',
        data: {
            labels: labels,
            datasets: [{
                label: 'Blood Sugar (mg/dL)',
                data: values,
                borderColor: '#2563eb',
                backgroundColor: 'rgba(37, 99, 235, 0.12)',
                fill: true,
                tension: 0.35,
                pointBackgroundColor: '#2563eb',
                pointRadius: 5,
                pointHoverRadius: 7
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false }
            },
            scales: {
                y: {
                    suggestedMin: 70,
                    suggestedMax: 200,
                    grid: { color: '#f1f5f9' },
                    title: { display: true, text: 'Glucose (mg/dL)' }
                },
                x: {
                    grid: { display: false }
                }
            }
        }
    });
}

function renderTable(logs) {
    const tbody = document.getElementById('logsTableBody');
    if (!tbody) return;

    if (!logs || logs.length === 0) {
        tbody.innerHTML = '<tr><td colspan="5" style="text-align:center; color:#64748b; padding:2rem;">No logs recorded yet. Add your first reading above.</td></tr>';
        return;
    }

    // Display newest readings first
    const reversed = [...logs].reverse();
    tbody.innerHTML = reversed.map(log => {
        let badgeClass = 'badge-normal';
        if (log.sugar_level < 70) badgeClass = 'badge-prediabetes';
        else if (log.sugar_level > 180) badgeClass = 'badge-diabetes';
        else if (log.sugar_level > 140) badgeClass = 'badge-prediabetes';

        const dateStr = log.log_date ? new Date(log.log_date).toLocaleString() : 'N/A';

        return `
            <tr>
                <td><strong>${dateStr}</strong></td>
                <td><span class="stat-badge ${badgeClass}">${log.sugar_level} mg/dL</span></td>
                <td>${log.reading_type}</td>
                <td>${log.notes || '<span style="color:#94a3b8">—</span>'}</td>
                <td>
                    <button class="btn btn-danger" onclick="handleDeleteLog(${log.id})">Delete</button>
                </td>
            </tr>
        `;
    }).join('');
}

function handleAddLog(event) {
    event.preventDefault();
    const username = getCurrentUser();
    if (!username) return;

    const sugarLevel = parseFloat(document.getElementById('sugarLevel').value);
    const readingType = document.getElementById('readingType').value;
    const logDate = document.getElementById('logDate').value;
    const notes = document.getElementById('notes').value;

    if (isNaN(sugarLevel)) {
        alert('Please enter a valid glucose number.');
        return;
    }

    fetch(API_BASE + '/api/logs', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            username: username,
            sugar_level: sugarLevel,
            reading_type: readingType,
            log_date: logDate,
            notes: notes
        })
    })
    .then(res => res.json())
    .then(data => {
        if (data.success) {
            document.getElementById('addLogForm').reset();
            const now = new Date();
            const offset = now.getTimezoneOffset() * 60000;
            document.getElementById('logDate').value = (new Date(now - offset)).toISOString().slice(0, 16);
            loadDashboardData(username);
        } else {
            alert(data.message || 'Error saving reading.');
        }
    })
    .catch(err => {
        alert('Server communication error. Please ensure the Java server is running.');
    });
}

function handleDeleteLog(id) {
    const username = getCurrentUser();
    if (!username) return;

    if (!confirm('Are you sure you want to delete this reading?')) return;

    fetch(API_BASE + '/api/delete-log', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id: id, username: username })
    })
    .then(res => res.json())
    .then(data => {
        if (data.success) {
            loadDashboardData(username);
        } else {
            alert(data.message || 'Error deleting log.');
        }
    })
    .catch(err => {
        alert('Server communication error.');
    });
}

// ----------------- SHARING (WHATSAPP, EMAIL & CHART IMAGE) -----------------

function generateHealthReport() {
    const username = getCurrentUser() || 'User';
    const logs = [...currentLogs].sort((a, b) => new Date(b.log_date) - new Date(a.log_date));

    const total = logs.length;
    let sum = 0;
    logs.forEach(l => {
        const val = parseFloat(l.sugar_level);
        if (!isNaN(val)) sum += val;
    });

    const avg = total > 0 ? (Math.round((sum / total) * 10) / 10) : 0;
    const hba1c = avg > 0 ? (Math.round(((avg + 46.7) / 28.7) * 10) / 10) : 0;

    let statusText = 'Normal (<5.7%)';
    if (hba1c >= 6.5) statusText = 'Diabetes Range (>=6.5%)';
    else if (hba1c >= 5.7) statusText = 'Prediabetes (5.7% - 6.4%)';

    const today = new Date().toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });

    let report = `GLUCOTRACK CLINICAL HEALTH SUMMARY REPORT\n`;
    report += `Patient ID: ${username}\n`;
    report += `Date of Report: ${today}\n\n`;

    report += `CLINICAL METRICS:\n`;
    report += `- Estimated HbA1c: ${hba1c > 0 ? hba1c + '%' : 'N/A'} [${statusText}]\n`;
    report += `- Average Glucose: ${avg > 0 ? avg + ' mg/dL' : 'N/A'}\n`;
    report += `- Total Logged Readings: ${total}\n\n`;

    if (logs.length > 0) {
        report += `RECENT GLUCOSE READINGS:\n`;
        const recent = logs.slice(0, 5); // 5 latest readings
        recent.forEach((l, idx) => {
            const dateStr = l.log_date ? new Date(l.log_date).toLocaleString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : 'N/A';
            const notesStr = l.notes ? ` - Note: "${l.notes}"` : '';
            report += `${idx + 1}. ${dateStr}: ${l.sugar_level} mg/dL (${l.reading_type})${notesStr}\n`;
        });
    } else {
        report += `(No readings logged yet)\n`;
    }

    report += `\nGenerated via GlucoTrack Clinical System.`;
    return report;
}

/**
 * Creates a high-resolution composite clinical report image
 * containing patient ID, clinical metrics (HbA1c & Average), and the trend chart.
 */
function getReportImageBlob() {
    return new Promise(resolve => {
        const sourceCanvas = document.getElementById('sugarTrendChart');
        if (!sourceCanvas) {
            resolve(null);
            return;
        }

        const width = Math.max(720, sourceCanvas.width);
        const height = sourceCanvas.height + 150;
        const c = document.createElement('canvas');
        c.width = width;
        c.height = height;
        const ctx = c.getContext('2d');

        // Clean white background
        ctx.fillStyle = '#ffffff';
        ctx.fillRect(0, 0, width, height);

        // Header Title
        ctx.fillStyle = '#0f172a';
        ctx.font = 'bold 16px sans-serif';
        ctx.textAlign = 'left';
        ctx.fillText('GLUCOTRACK CLINICAL HEALTH REPORT', 25, 35);

        // Patient & Date metadata
        const username = getCurrentUser() || 'User';
        const today = new Date().toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
        ctx.fillStyle = '#64748b';
        ctx.font = '12px sans-serif';
        ctx.fillText(`Patient ID: ${username}   |   Date: ${today}`, 25, 58);

        // Metrics Banner Box
        const hba1cText = document.getElementById('hba1cEstimate') ? document.getElementById('hba1cEstimate').textContent : '--';
        const avgText = document.getElementById('avgGlucose') ? document.getElementById('avgGlucose').textContent : '--';
        const statusText = document.getElementById('hba1cStatus') ? document.getElementById('hba1cStatus').textContent : '';

        ctx.fillStyle = '#f8fafc';
        ctx.strokeStyle = '#e2e8f0';
        ctx.lineWidth = 1;
        ctx.fillRect(25, 75, width - 50, 42);
        ctx.strokeRect(25, 75, width - 50, 42);

        ctx.fillStyle = '#1e293b';
        ctx.font = 'bold 12px sans-serif';
        ctx.fillText(`Estimated HbA1c: ${hba1cText} (${statusText})       Average Glucose: ${avgText}`, 40, 101);

        // Draw the trend chart onto the report canvas
        ctx.drawImage(sourceCanvas, 25, 130, width - 50, sourceCanvas.height - 20);

        // Footer
        ctx.fillStyle = '#94a3b8';
        ctx.font = '10px sans-serif';
        ctx.textAlign = 'right';
        ctx.fillText('Generated via GlucoTrack Clinical System', width - 25, height - 10);

        c.toBlob(blob => resolve(blob), 'image/png');
    });
}

/**
 * Share to WhatsApp with Chart Image.
 * On Desktop: Directly opens WhatsApp Web (web.whatsapp.com) without app download prompts,
 * and copies the chart image to clipboard for instant Ctrl+V pasting.
 * On Mobile: Uses native share or mobile WhatsApp app.
 */
async function shareToWhatsApp() {
    const report = generateHealthReport();
    const imageBlob = await getReportImageBlob();
    const isMobile = /Android|iPhone|iPad|iPod/i.test(navigator.userAgent);

    // 1. Mobile devices: Use native share sheet if available
    if (isMobile && imageBlob && navigator.share) {
        try {
            const file = new File([imageBlob], 'glucotrack_clinical_report.png', { type: 'image/png' });
            if (navigator.canShare && navigator.canShare({ files: [file] })) {
                await navigator.share({
                    title: 'GlucoTrack Clinical Report',
                    text: report,
                    files: [file]
                });
                showToast('Report and chart image shared successfully.');
                return;
            }
        } catch (err) {
            if (err.name === 'AbortError') return;
        }
    }

    // 2. Desktop workflow: Copy chart image to clipboard for instant Ctrl+V pasting
    if (imageBlob && navigator.clipboard && window.ClipboardItem) {
        try {
            await navigator.clipboard.write([new ClipboardItem({ 'image/png': imageBlob })]);
        } catch (e) {
            console.warn('Clipboard image write:', e);
        }
    }

    // Download backup copy of report image
    if (imageBlob) {
        const a = document.createElement('a');
        a.download = 'glucotrack_clinical_report.png';
        a.href = URL.createObjectURL(imageBlob);
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
    }

    // 3. Open WhatsApp Web directly (avoids "Download WhatsApp App" landing page)
    const targetUrl = isMobile
        ? "https://api.whatsapp.com/send?text=" + encodeURIComponent(report)
        : "https://web.whatsapp.com/send?text=" + encodeURIComponent(report);

    window.open(targetUrl, '_blank');

    showToast('Opening WhatsApp Web. Chart image copied to clipboard; press Ctrl+V in the chat to paste.');
}

async function shareToEmail() {
    const username = getCurrentUser() || 'User';
    const report = generateHealthReport();
    const imageBlob = await getReportImageBlob();

    // Try native share with file if supported
    if (imageBlob && navigator.share) {
        try {
            const file = new File([imageBlob], 'glucotrack_clinical_report.png', { type: 'image/png' });
            if (navigator.canShare && navigator.canShare({ files: [file] })) {
                await navigator.share({
                    title: `GlucoTrack Clinical Health Report - ${username}`,
                    text: report,
                    files: [file]
                });
                return;
            }
        } catch (err) {
            if (err.name === 'AbortError') return;
        }
    }

    // Download image for attachment
    if (imageBlob) {
        const a = document.createElement('a');
        a.download = 'glucotrack_clinical_report.png';
        a.href = URL.createObjectURL(imageBlob);
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
    }

    const subject = encodeURIComponent(`GlucoTrack Clinical Health Report - ${username}`);
    const body = encodeURIComponent(report + "\n\n(Note: The clinical chart image 'glucotrack_clinical_report.png' has been downloaded to attach to this email.)");
    window.location.href = `mailto:?subject=${subject}&body=${body}`;
    showToast('Chart image downloaded. Attach it to your email.');
}

async function downloadChartImage() {
    const imageBlob = await getReportImageBlob();
    if (!imageBlob) return;
    const a = document.createElement('a');
    a.download = 'glucotrack_clinical_report.png';
    a.href = URL.createObjectURL(imageBlob);
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    showToast('Clinical report and chart image downloaded.');
}

async function copyChartImageToClipboard() {
    const imageBlob = await getReportImageBlob();
    if (!imageBlob) return;
    try {
        if (navigator.clipboard && window.ClipboardItem) {
            await navigator.clipboard.write([new ClipboardItem({ 'image/png': imageBlob })]);
            showToast('Clinical report image copied to clipboard.');
        } else {
            downloadChartImage();
        }
    } catch (e) {
        downloadChartImage();
    }
}

function copyReportToClipboard() {
    const report = generateHealthReport();
    if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(report).then(() => {
            showToast('Report text copied to clipboard.');
        }).catch(() => fallbackCopy(report));
    } else {
        fallbackCopy(report);
    }
}

function fallbackCopy(text) {
    const textarea = document.createElement('textarea');
    textarea.value = text;
    document.body.appendChild(textarea);
    textarea.select();
    try {
        document.execCommand('copy');
        showToast('Report text copied to clipboard.');
    } catch (e) {
        alert('Could not copy report: ' + e);
    }
    document.body.removeChild(textarea);
}

function showToast(msg) {
    const toast = document.getElementById('shareToast');
    if (toast) {
        toast.textContent = msg;
        toast.style.display = 'block';
        setTimeout(() => { toast.style.display = 'none'; }, 4000);
    }
}
