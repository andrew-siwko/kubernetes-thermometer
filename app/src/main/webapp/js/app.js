document.addEventListener('DOMContentLoaded', () => {
    // State Variables
    let probes = [];
    let selectedModel = 'ALL';
    let selectedId = 'ALL';
    let activeWindow = '1h';
    let chart = null;
    let latestReadingTimestamp = null;
    let pollTimer = null;
    let tickerTimer = null;

    // DOM Elements
    const probeSelect = document.getElementById('probeSelect');
    const timeWindowBtns = document.getElementById('timeWindowBtns');
    const currentTempDisplay = document.getElementById('currentTempDisplay');
    const currentTempSub = document.getElementById('currentTempSub');
    const readingAgeDisplay = document.getElementById('readingAgeDisplay');
    const lastTimestampDisplay = document.getElementById('lastTimestampDisplay');
    const tempRangeDisplay = document.getElementById('tempRangeDisplay');
    const readingCountDisplay = document.getElementById('readingCountDisplay');
    const chartTitle = document.getElementById('chartTitle');
    const liveAgeText = document.getElementById('liveAgeText');
    const renameBtn = document.getElementById('renameBtn');
    const renameModal = document.getElementById('renameModal');
    const modalProbeSubtitle = document.getElementById('modalProbeSubtitle');
    const customNameInput = document.getElementById('customNameInput');
    const cancelRenameBtn = document.getElementById('cancelRenameBtn');
    const saveRenameBtn = document.getElementById('saveRenameBtn');
    const manageProbesBtn = document.getElementById('manageProbesBtn');
    const manageProbesModal = document.getElementById('manageProbesModal');
    const closeManageBtn = document.getElementById('closeManageBtn');
    const manageProbesTableBody = document.getElementById('manageProbesTableBody');
    const manageProbesMessage = document.getElementById('manageProbesMessage');

    // Initialize Chart
    function initChart() {
        const ctx = document.getElementById('tempChart').getContext('2d');
        chart = new Chart(ctx, {
            type: 'line',
            data: {
                datasets: [{
                    label: 'Temperature (°F)',
                    data: [],
                    borderColor: '#38bdf8',
                    backgroundColor: 'rgba(56, 189, 248, 0.12)',
                    borderWidth: 3,
                    fill: true,
                    tension: 0.35,
                    pointRadius: 3,
                    pointHoverRadius: 6,
                    pointBackgroundColor: '#38bdf8'
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                interaction: {
                    mode: 'index',
                    intersect: false
                },
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        backgroundColor: 'rgba(15, 23, 42, 0.9)',
                        titleColor: '#f8fafc',
                        bodyColor: '#38bdf8',
                        borderColor: 'rgba(255, 255, 255, 0.1)',
                        borderWidth: 1,
                        padding: 12,
                        displayColors: false,
                        callbacks: {
                            label: function(context) {
                                return `Temp: ${context.parsed.y.toFixed(1)} °F`;
                            }
                        }
                    }
                },
                scales: {
                    x: {
                        type: 'time',
                        time: {
                            displayFormats: {
                                minute: 'HH:mm',
                                hour: 'MMM d, HH:mm',
                                day: 'MMM d'
                            }
                        },
                        grid: { color: 'rgba(255, 255, 255, 0.05)' },
                        ticks: { color: '#94a3b8' }
                    },
                    y: {
                        grid: { color: 'rgba(255, 255, 255, 0.08)' },
                        ticks: {
                            color: '#94a3b8',
                            callback: function(val) { return val.toFixed(1) + ' °F'; }
                        }
                    }
                }
            }
        });
    }

    // Load Probes List
    async function fetchProbes() {
        try {
            const res = await fetch('api/probes');
            if (!res.ok) throw new Error('Failed to fetch probes');
            probes = await res.json();
            populateProbeDropdown();
        } catch (err) {
            console.error('Error fetching probes:', err);
        }
    }

    function populateProbeDropdown() {
        const currentVal = probeSelect.value;
        probeSelect.innerHTML = '<option value="ALL">All Probes</option>';

        probes.forEach(p => {
            const opt = document.createElement('option');
            const key = `${p.model}||${p.id}`;
            opt.value = key;
            opt.textContent = p.displayName;
            probeSelect.appendChild(opt);
        });

        if (currentVal && Array.from(probeSelect.options).some(o => o.value === currentVal)) {
            probeSelect.value = currentVal;
        } else {
            probeSelect.value = 'ALL';
        }
    }

    function renderManageProbesTable() {
        manageProbesTableBody.innerHTML = '';
        manageProbesMessage.textContent = '';

        if (!probes.length) {
            manageProbesMessage.textContent = 'No probes available to manage.';
            return;
        }

        probes.forEach(p => {
            const row = document.createElement('tr');
            const ageSeconds = p.lastAgeSeconds != null ? p.lastAgeSeconds : null;
            const ageText = ageSeconds == null ? 'Unknown' : formatAge(ageSeconds);

            row.innerHTML = `
                <td>${escapeHtml(p.model)}</td>
                <td>${escapeHtml(p.id)}</td>
                <td>
                    <input type="text" class="probe-alias-input" data-model="${escapeHtml(p.model)}" data-id="${escapeHtml(p.id)}" value="${escapeHtml(p.customName || '')}" placeholder="Alias..." />
                </td>
                <td>${ageText}</td>
                <td><button class="btn btn-primary btn-small save-alias-btn" data-model="${escapeHtml(p.model)}" data-id="${escapeHtml(p.id)}">Save</button></td>
            `;

            manageProbesTableBody.appendChild(row);
        });

        manageProbesTableBody.querySelectorAll('.save-alias-btn').forEach(btn => {
            btn.addEventListener('click', async (event) => {
                const model = event.target.dataset.model;
                const id = event.target.dataset.id;
                const input = manageProbesTableBody.querySelector(`input[data-model="${CSS.escape(model)}"][data-id="${CSS.escape(id)}"]`);
                const newName = input ? input.value.trim() : '';
                await saveProbeAlias(model, id, newName);
            });
        });
    }

    function escapeHtml(value) {
        if (value == null) return '';
        return value.replace(/[&<>"]+/g, function(match) {
            const escape = {
                '&': '&amp;',
                '<': '&lt;',
                '>': '&gt;',
                '"': '&quot;'
            };
            return escape[match];
        });
    }

    function formatAge(seconds) {
        if (seconds < 60) return `${seconds}s ago`;
        if (seconds < 3600) return `${Math.floor(seconds / 60)}m ${seconds % 60}s ago`;
        return `${Math.floor(seconds / 3600)}h ${Math.floor((seconds % 3600) / 60)}m ago`;
    }

    async function saveProbeAlias(model, id, customName) {
        try {
            const res = await fetch('api/probes/name', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ model, id, customName })
            });
            if (!res.ok) throw new Error('Failed to save alias');
            await fetchProbes();
            renderManageProbesTable();
            manageProbesMessage.textContent = 'Alias saved successfully.';
            setTimeout(() => manageProbesMessage.textContent = '', 2500);
        } catch (err) {
            manageProbesMessage.textContent = 'Error saving alias: ' + err.message;
        }
    }

    // Load Chart Readings
    async function fetchReadings() {
        try {
            let url = `api/readings?window=${activeWindow}`;
            if (selectedModel !== 'ALL') {
                url += `&model=${encodeURIComponent(selectedModel)}`;
            }
            if (selectedId !== 'ALL') {
                url += `&id=${encodeURIComponent(selectedId)}`;
            }

            const res = await fetch(url);
            if (!res.ok) throw new Error('Failed to fetch readings');
            const data = await res.json();

            updateDashboard(data);
        } catch (err) {
            console.error('Error fetching readings:', err);
        }
    }

    function updateDashboard(readings) {
        if (!readings || readings.length === 0) {
            currentTempDisplay.textContent = '-- °F';
            currentTempSub.textContent = '-- °C';
            tempRangeDisplay.textContent = '-- °F - -- °F';
            readingCountDisplay.textContent = '0 readings in window';
            readingAgeDisplay.textContent = 'No Data';
            lastTimestampDisplay.textContent = 'No readings found';
            liveAgeText.textContent = 'Last reading: N/A';
            chart.data.datasets[0].data = [];
            chart.update();
            return;
        }

        // Parse dataset points
        const points = readings.map(r => ({
            x: r.epochMillis,
            y: r.temperatureF,
            tempC: r.temperatureC,
            timestampStr: r.timestamp
        }));

        // Sort chronologically
        points.sort((a, b) => a.x - b.x);

        const latest = points[points.length - 1];
        latestReadingTimestamp = latest.x;

        // Current Temp
        currentTempDisplay.textContent = `${latest.y.toFixed(1)} °F`;
        if (latest.tempC != null) {
            currentTempSub.textContent = `${latest.tempC.toFixed(1)} °C`;
        }

        // Temp Range
        const temps = points.map(p => p.y);
        const minTemp = Math.min(...temps);
        const maxTemp = Math.max(...temps);

        tempRangeDisplay.textContent = `${minTemp.toFixed(1)} °F - ${maxTemp.toFixed(1)} °F`;
        readingCountDisplay.textContent = `${points.length} readings`;

        // Update Chart Data & Auto-Scale Y Axis
        chart.data.datasets[0].data = points;

        // Y-axis auto-scaling with padding
        const padding = Math.max((maxTemp - minTemp) * 0.15, 1.5);
        chart.options.scales.y.min = Math.floor(minTemp - padding);
        chart.options.scales.y.max = Math.ceil(maxTemp + padding);

        chart.update();

        // Update Reading Age
        updateAgeTicker();
    }

    // Ticker to update "seconds ago" text every second
    function updateAgeTicker() {
        if (!latestReadingTimestamp) return;

        const now = Date.now();
        const diffSec = Math.max(0, Math.floor((now - latestReadingTimestamp) / 1000));

        let ageStr = '';
        if (diffSec < 60) {
            ageStr = `${diffSec}s ago`;
        } else if (diffSec < 3600) {
            ageStr = `${Math.floor(diffSec / 60)}m ${diffSec % 60}s ago`;
        } else {
            ageStr = `${Math.floor(diffSec / 3600)}h ${Math.floor((diffSec % 3600) / 60)}m ago`;
        }

        readingAgeDisplay.textContent = ageStr;
        liveAgeText.textContent = `Last reading: ${ageStr}`;

        const d = new Date(latestReadingTimestamp);
        lastTimestampDisplay.textContent = d.toLocaleTimeString();
    }

    // Event Handlers
    probeSelect.addEventListener('change', (e) => {
        const val = e.target.value;
        if (val === 'ALL') {
            selectedModel = 'ALL';
            selectedId = 'ALL';
            chartTitle.textContent = 'All Temperature Probes (°F)';
        } else {
            const parts = val.split('||');
            selectedModel = parts[0];
            selectedId = parts[1];

            const selectedProbe = probes.find(p => p.model === selectedModel && p.id === selectedId);
            chartTitle.textContent = `${selectedProbe ? selectedProbe.displayName : val} (°F)`;
        }
        fetchReadings();
    });

    timeWindowBtns.addEventListener('click', (e) => {
        if (!e.target.classList.contains('btn-tab')) return;
        Array.from(timeWindowBtns.children).forEach(btn => btn.classList.remove('active'));
        e.target.classList.add('active');
        activeWindow = e.target.dataset.window;
        fetchReadings();
    });

    // Rename Modal Handlers
    renameBtn.addEventListener('click', () => {
        if (selectedModel === 'ALL') {
            alert('Please select a specific probe to rename.');
            return;
        }
        const selectedProbe = probes.find(p => p.model === selectedModel && p.id === selectedId);
        modalProbeSubtitle.textContent = `Renaming probe ${selectedModel} #${selectedId}`;
        customNameInput.value = selectedProbe ? (selectedProbe.customName || '') : '';
        renameModal.classList.add('active');
        customNameInput.focus();
    });

    cancelRenameBtn.addEventListener('click', () => {
        renameModal.classList.remove('active');
    });

    saveRenameBtn.addEventListener('click', async () => {
        const newName = customNameInput.value.trim();
        try {
            const res = await fetch('api/probes/name', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    model: selectedModel,
                    id: selectedId,
                    customName: newName
                })
            });

            if (!res.ok) throw new Error('Failed to update probe name');

            renameModal.classList.remove('active');
            await fetchProbes();
            fetchReadings();
        } catch (err) {
            alert('Error saving probe name: ' + err.message);
        }
    });

    manageProbesBtn.addEventListener('click', () => {
        renderManageProbesTable();
        manageProbesModal.classList.add('active');
    });

    closeManageBtn.addEventListener('click', () => {
        manageProbesModal.classList.remove('active');
    });

    manageProbesModal.addEventListener('click', (event) => {
        if (event.target === manageProbesModal) {
            manageProbesModal.classList.remove('active');
        }
    });

    // Start App
    initChart();
    fetchProbes().then(() => fetchReadings());

    // Live Auto-Refresh (every 5 seconds)
    pollTimer = setInterval(fetchReadings, 5000);

    // Live Ticker (every 1 second)
    tickerTimer = setInterval(updateAgeTicker, 1000);
});
