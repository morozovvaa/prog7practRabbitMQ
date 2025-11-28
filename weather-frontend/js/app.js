/**
 * Real-time Weather Analysis с WebSocket
 * 
 * Архитектура:
 * 1. Клиент устанавливает WebSocket соединение с сервером
 * 2. Отправляет список городов через WebSocket
 * 3. Получает инкрементальные обновления в реальном времени
 * 4. Динамически обновляет UI без перезагрузки
 * 
 * Паттерны:
 * - Publisher-Subscriber (WebSocket)
 * - Event-Driven Architecture
 * - Progressive Enhancement
 */

// --- КОНФИГУРАЦИЯ ---
const WS_URL = 'ws://localhost:8080/ws/weather';

// Глобальное состояние приложения
let appState = {
    socket: null,
    isConnected: false,
    isProcessing: false,
    correlationId: null,
    totalCities: 0,
    receivedCount: 0,
    results: new Map() // Key: city, Value: result data
};

// --- УТИЛИТЫ DOM ---

/**
 * Обновляет индикатор статуса WebSocket подключения
 */
function updateConnectionStatus(connected) {
    const indicator = document.getElementById('status-indicator');
    const statusText = document.getElementById('status-text');
    
    if (connected) {
        indicator.className = 'w-3 h-3 rounded-full bg-green-500';
        statusText.textContent = 'Подключено';
        statusText.className = 'text-sm text-green-400';
    } else {
        indicator.className = 'w-3 h-3 rounded-full bg-gray-500';
        statusText.textContent = 'Отключено';
        statusText.className = 'text-sm text-gray-400';
    }
}

/**
 * Генерирует HTML для карточки результата
 */
function createResultCardHTML(cityName, result) {
    let iconHTML, color, statusText, opacityClass = '';

    const icons = {
        success: '<svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>',
        error: '<svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>',
        pending: '<svg class="w-6 h-6 spinner" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m15.356-2H15m-3-2v5m0 0l-4 4m4-4l4 4m-4 4v5m0 0l-4-4m4 4l4-4"></path></svg>'
    };

    if (result && result.success !== undefined) {
        // Результат получен
        if (result.success) {
            iconHTML = icons.success;
            color = 'text-green-500 bg-green-500/10';
            statusText = `🌡️ ${result.temperature.toFixed(1)}°C, ${result.description}`;
        } else {
            iconHTML = icons.error;
            color = 'text-red-500 bg-red-500/10';
            statusText = `<span class="text-red-400">Ошибка: ${result.errorMessage}</span>`;
        }
    } else {
        // Ожидание результата
        iconHTML = icons.pending;
        color = 'text-gray-500 bg-gray-500/10';
        statusText = '<span class="processing">Обрабатывается...</span>';
        opacityClass = 'opacity-60';
    }

    return `
        <div id="card-${cityName}" class="p-4 rounded-xl shadow-md flex items-center transition-all duration-300 card-appear ${color} ${opacityClass}">
            <div class="w-6 h-6 flex-shrink-0">${iconHTML}</div>
            <div class="flex-grow ml-3">
                <h3 class="font-semibold text-lg text-gray-100">${cityName}</h3>
                <p class="text-sm text-gray-300">${statusText}</p>
            </div>
        </div>
    `;
}

/**
 * Обновляет прогресс-бар
 */
function updateProgress() {
    const progressPercentage = Math.round((appState.receivedCount / appState.totalCities) * 100);
    
    document.getElementById('progress-text').textContent = 
        `Прогресс: ${appState.receivedCount} из ${appState.totalCities}`;
    document.getElementById('progress-percentage').textContent = `${progressPercentage}%`;
    document.getElementById('progress-bar-fill').style.width = `${progressPercentage}%`;
}

/**
 * Обновляет статус-бейдж
 */
function updateStatusBadge(completed) {
    const statusBadge = document.getElementById('status-badge');
    
    if (completed) {
        statusBadge.textContent = 'Завершено';
        statusBadge.className = 'px-3 py-1 text-sm font-medium rounded-full bg-green-800 text-green-300';
    } else {
        statusBadge.textContent = 'В процессе';
        statusBadge.className = 'px-3 py-1 text-sm font-medium rounded-full bg-yellow-800 text-yellow-300';
    }
}

/**
 * Рендерит все результаты в контейнер
 */
function renderResults() {
    const container = document.getElementById('results-container');
    container.innerHTML = Array.from(appState.results.entries())
        .map(([city, result]) => createResultCardHTML(city, result))
        .join('');
}

/**
 * Обновляет отдельную карточку результата (более эффективно, чем полный рендер)
 */
function updateResultCard(cityName, result) {
    const existingCard = document.getElementById(`card-${cityName}`);
    const newCardHTML = createResultCardHTML(cityName, result);
    
    if (existingCard) {
        // Заменяем существующую карточку
        existingCard.outerHTML = newCardHTML;
    } else {
        // Добавляем новую карточку
        const container = document.getElementById('results-container');
        container.insertAdjacentHTML('beforeend', newCardHTML);
    }
}

/**
 * Показывает предупреждение о частичном результате
 */
function showPartialWarning(reason) {
    const warning = document.getElementById('partial-warning');
    const reasonText = document.getElementById('partial-reason');
    
    reasonText.textContent = reason;
    warning.classList.remove('hidden');
}

/**
 * Управление UI элементами
 */
function updateUI() {
    const statusSection = document.getElementById('status-section');
    const initialMessage = document.getElementById('initial-message');
    const startButton = document.getElementById('start-button');
    const citiesInput = document.getElementById('cities-input');
    const buttonIcon = document.getElementById('button-icon');
    const buttonText = document.getElementById('button-text');

    // Видимость секций
    if (appState.correlationId) {
        statusSection.classList.remove('hidden');
        initialMessage.classList.add('hidden');
    } else {
        statusSection.classList.add('hidden');
        initialMessage.classList.remove('hidden');
    }

    // Кнопка и input
    startButton.disabled = appState.isProcessing || !appState.isConnected || !citiesInput.value.trim();
    citiesInput.disabled = appState.isProcessing;

    if (appState.isProcessing) {
        buttonIcon.innerHTML = '<svg class="w-5 h-5 animate-spin" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m15.356-2H15m-3-2v5m0 0l-4 4m4-4l4 4m-4 4v5m0 0l-4-4m4 4l4-4"></path></svg>';
        buttonText.textContent = 'Обработка...';
    } else {
        buttonIcon.innerHTML = '<svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"></path></svg>';
        buttonText.textContent = 'Начать анализ';
    }
}

// --- WEBSOCKET ЛОГИКА ---

/**
 * Устанавливает WebSocket соединение
 */
function connectWebSocket() {
    console.log('🔌 Connecting to WebSocket:', WS_URL);
    
    appState.socket = new WebSocket(WS_URL);

    appState.socket.onopen = (event) => {
        console.log('✅ WebSocket connected');
        appState.isConnected = true;
        updateConnectionStatus(true);
        updateUI();
    };

    appState.socket.onmessage = (event) => {
        try {
            const message = JSON.parse(event.data);
            console.log('📨 Received message:', message);
            handleServerMessage(message);
        } catch (error) {
            console.error('Error parsing message:', error);
        }
    };

    appState.socket.onerror = (error) => {
        console.error('❌ WebSocket error:', error);
    };

    appState.socket.onclose = (event) => {
        console.log('🔌 WebSocket disconnected');
        appState.isConnected = false;
        updateConnectionStatus(false);
        updateUI();
        
        // Автоматическое переподключение через 3 секунды
        if (!event.wasClean) {
            console.log('🔄 Reconnecting in 3 seconds...');
            setTimeout(connectWebSocket, 3000);
        }
    };
}

/**
 * Обработчик сообщений от сервера
 */
function handleServerMessage(message) {
    switch (message.type) {
        case 'CONNECTION_ESTABLISHED':
            console.log('✅ Connection confirmed:', message.sessionId);
            break;

        case 'PROCESSING_STARTED':
            handleProcessingStarted(message);
            break;

        case 'INDIVIDUAL_RESULT':
            handleIndividualResult(message.data);
            break;

        case 'FINAL_REPORT':
            handleFinalReport(message.data);
            break;

        case 'ERROR':
            handleError(message.message);
            break;

        case 'CONNECTION_CLOSING':
            console.log('👋 Server closing connection');
            break;

        default:
            console.warn('Unknown message type:', message.type);
    }
}

/**
 * Обработка начала обработки
 */
function handleProcessingStarted(message) {
    console.log('🚀 Processing started:', message);
    
    appState.correlationId = message.correlationId;
    appState.totalCities = message.totalCities;
    appState.receivedCount = 0;
    appState.results.clear();

    // Инициализируем карточки для всех городов как "pending"
    message.cities.forEach(city => {
        appState.results.set(city, null); // null = pending
    });

    // Обновляем UI
    document.getElementById('correlation-id').textContent = `ID: ${message.correlationId}`;
    updateProgress();
    updateStatusBadge(false);
    renderResults();
    updateUI();
}

/**
 * Обработка индивидуального результата (REAL-TIME UPDATE!)
 */
function handleIndividualResult(data) {
    console.log('📦 Individual result received:', data);
    
    // Обновляем состояние
    appState.results.set(data.city, data);
    appState.receivedCount++;

    // Инкрементально обновляем UI
    updateResultCard(data.city, data);
    updateProgress();

    // Звуковая/визуальная обратная связь
    console.log(`✅ ${data.city}: ${data.success ? 'Success' : 'Failed'}`);
}

/**
 * Обработка финального отчета
 */
function handleFinalReport(report) {
    console.log('🏁 Final report received:', report);
    
    appState.isProcessing = false;
    updateStatusBadge(true);

    // Если частичный результат - показываем предупреждение
    if (report.partial) {
        showPartialWarning(report.partialReason);
    }

    updateUI();
    
    console.log(`📊 Final stats: ${report.successCount} successful, ${report.failureCount} failed`);
}

/**
 * Обработка ошибки
 */
function handleError(errorMessage) {
    console.error('❌ Server error:', errorMessage);
    alert(`Ошибка: ${errorMessage}`);
    
    appState.isProcessing = false;
    updateUI();
}

/**
 * Отправка запроса на обработку
 */
function startWeatherAnalysis() {
    if (!appState.isConnected) {
        alert('WebSocket не подключен. Попробуйте позже.');
        return;
    }

    if (appState.isProcessing) {
        return;
    }

    const citiesInput = document.getElementById('cities-input').value;
    const cities = citiesInput.split(',').map(c => c.trim()).filter(c => c.length > 0);

    if (cities.length === 0) {
        alert('Пожалуйста, введите хотя бы один город');
        return;
    }

    console.log('🚀 Starting weather analysis for:', cities);
    
    appState.isProcessing = true;
    updateUI();

    // Отправляем запрос через WebSocket
    const request = {
        cities: cities
    };

    appState.socket.send(JSON.stringify(request));
    console.log('📤 Request sent:', request);
}

// --- ИНИЦИАЛИЗАЦИЯ ---

document.addEventListener('DOMContentLoaded', () => {
    console.log('🎬 Application initialized');
    
    // Подключаемся к WebSocket
    connectWebSocket();

    // Назначаем обработчик на кнопку
    document.getElementById('start-button').addEventListener('click', startWeatherAnalysis);

    // Начальное обновление UI
    updateUI();

    // Обработка нажатия Enter в поле ввода
    document.getElementById('cities-input').addEventListener('keypress', (e) => {
        if (e.key === 'Enter') {
            startWeatherAnalysis();
        }
    });
});