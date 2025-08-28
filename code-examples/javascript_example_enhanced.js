/**
 * Enhanced Real-time Analytics Dashboard - Frontend Module
 * Author: Rasya Andrean
 * Description: Advanced real-time data visualization with WebSocket integration,
 *              predictive analytics, and anomaly detection
 */

import { Chart, registerables } from 'chart.js';
import { EventEmitter } from 'events';
import { io } from 'socket.io-client';

Chart.register(...registerables);

class EnhancedRealTimeAnalyticsDashboard extends EventEmitter {
  constructor(config = {}) {
    super();

    this.config = {
      wsUrl: config.wsUrl || 'ws://localhost:3001',
      updateInterval: config.updateInterval || 1000,
      maxDataPoints: config.maxDataPoints || 100,
      charts: config.charts || ['line', 'bar', 'doughnut'],
      enablePredictiveAnalytics: config.enablePredictiveAnalytics !== false,
      enableAnomalyDetection: config.enableAnomalyDetection !== false,
      ...config,
    };

    this.socket = null;
    this.charts = new Map();
    this.dataBuffer = new Map();
    this.isConnected = false;
    this.metrics = {
      totalEvents: 0,
      eventsPerSecond: 0,
      lastUpdate: null,
      connectionUptime: 0,
    };

    // Predictive analytics
    this.predictiveModels = new Map();
    this.anomalyDetectors = new Map();
    this.historicalData = new Map();
    this.trendAnalyzers = new Map();

    // Export data storage
    this.exportData = [];

    this.init();
  }

  /**
   * Initialize dashboard components
   */
  async init() {
    try {
      await this.setupWebSocket();
      this.setupCharts();
      this.setupEventListeners();
      this.startMetricsCollection();
      this.initializePredictiveModels();
      this.initializeAnomalyDetectors();
      this.initializeTrendAnalyzers();

      console.log(
        '🚀 Enhanced Real-time Analytics Dashboard initialized successfully'
      );
      this.emit('initialized');
    } catch (error) {
      console.error('❌ Failed to initialize dashboard:', error);
      this.emit('error', error);
    }
  }

  /**
   * Setup WebSocket connection with auto-reconnect
   */
  async setupWebSocket() {
    return new Promise((resolve, reject) => {
      this.socket = io(this.config.wsUrl, {
        transports: ['websocket'],
        reconnection: true,
        reconnectionDelay: 1000,
        reconnectionAttempts: 5,
        timeout: 20000,
      });

      this.socket.on('connect', () => {
        console.log('🔗 WebSocket connected');
        this.isConnected = true;
        this.metrics.connectionUptime = Date.now();
        this.emit('connected');
        resolve();
      });

      this.socket.on('disconnect', reason => {
        console.log('🔌 WebSocket disconnected:', reason);
        this.isConnected = false;
        this.emit('disconnected', reason);
      });

      this.socket.on('analytics_data', data => {
        this.handleIncomingData(data);
      });

      this.socket.on('historical_data', data => {
        this.handleHistoricalData(data);
      });

      this.socket.on('alert', alert => {
        this.handleAlert(alert);
      });

      this.socket.on('connect_error', error => {
        console.error('🚫 WebSocket connection error:', error);
        reject(error);
      });

      // Subscribe to data streams
      this.socket.emit('subscribe', {
        streams: [
          'user_activity',
          'system_metrics',
          'business_kpis',
          'security_events',
        ],
        interval: this.config.updateInterval,
      });
    });
  }

  /**
   * Setup Chart.js instances for different visualizations
   */
  setupCharts() {
    const chartConfigs = {
      userActivity: {
        type: 'line',
        canvas: 'userActivityChart',
        options: {
          responsive: true,
          maintainAspectRatio: false,
          scales: {
            x: {
              type: 'time',
              time: {
                unit: 'minute',
                displayFormats: {
                  minute: 'HH:mm',
                },
              },
            },
            y: {
              beginAtZero: true,
              title: {
                display: true,
                text: 'Active Users',
              },
            },
          },
          plugins: {
            legend: {
              display: true,
              position: 'top',
            },
            tooltip: {
              mode: 'index',
              intersect: false,
            },
          },
          interaction: {
            mode: 'nearest',
            axis: 'x',
            intersect: false,
          },
        },
      },

      systemMetrics: {
        type: 'bar',
        canvas: 'systemMetricsChart',
        options: {
          responsive: true,
          maintainAspectRatio: false,
          scales: {
            y: {
              beginAtZero: true,
              max: 100,
              title: {
                display: true,
                text: 'Usage %',
              },
            },
          },
          plugins: {
            legend: {
              display: true,
            },
          },
        },
      },

      businessKpis: {
        type: 'doughnut',
        canvas: 'businessKpisChart',
        options: {
          responsive: true,
          maintainAspectRatio: false,
          plugins: {
            legend: {
              position: 'right',
            },
          },
        },
      },

      securityEvents: {
        type: 'line',
        canvas: 'securityEventsChart',
        options: {
          responsive: true,
          maintainAspectRatio: false,
          scales: {
            x: {
              type: 'time',
              time: {
                unit: 'minute',
                displayFormats: {
                  minute: 'HH:mm',
                },
              },
            },
            y: {
              beginAtZero: true,
              title: {
                display: true,
                text: 'Security Events',
              },
            },
          },
          plugins: {
            legend: {
              display: true,
              position: 'top',
            },
          },
        },
      },
    };

    Object.entries(chartConfigs).forEach(([key, config]) => {
      const canvas = document.getElementById(config.canvas);
      if (canvas) {
        const chart = new Chart(canvas, {
          type: config.type,
          data: this.getInitialChartData(config.type),
          options: config.options,
        });

        this.charts.set(key, chart);
        this.dataBuffer.set(key, []);
      }
    });
  }

  /**
   * Get initial chart data structure
   */
  getInitialChartData(type) {
    const baseData = {
      labels: [],
      datasets: [],
    };

    switch (type) {
      case 'line':
        return {
          ...baseData,
          datasets: [
            {
              label: 'Active Users',
              data: [],
              borderColor: 'rgb(75, 192, 192)',
              backgroundColor: 'rgba(75, 192, 192, 0.2)',
              tension: 0.4,
              fill: true,
            },
          ],
        };

      case 'bar':
        return {
          ...baseData,
          labels: ['CPU', 'Memory', 'Disk', 'Network'],
          datasets: [
            {
              label: 'System Usage',
              data: [0, 0, 0, 0],
              backgroundColor: [
                'rgba(255, 99, 132, 0.8)',
                'rgba(54, 162, 235, 0.8)',
                'rgba(255, 205, 86, 0.8)',
                'rgba(75, 192, 192, 0.8)',
              ],
            },
          ],
        };

      case 'doughnut':
        return {
          ...baseData,
          labels: ['Revenue', 'Costs', 'Profit'],
          datasets: [
            {
              data: [0, 0, 0],
              backgroundColor: [
                'rgba(34, 197, 94, 0.8)',
                'rgba(239, 68, 68, 0.8)',
                'rgba(59, 130, 246, 0.8)',
              ],
            },
          ],
        };

      default:
        return baseData;
    }
  }

  /**
   * Handle incoming real-time data
   */
  handleIncomingData(data) {
    try {
      this.metrics.totalEvents++;
      this.metrics.lastUpdate = new Date();

      // Store data for export
      this.exportData.push({
        timestamp: new Date(),
        type: data.type,
        payload: data.payload,
      });

      // Process different data types
      if (data.type === 'user_activity') {
        this.updateUserActivityChart(data.payload);
        if (this.config.enablePredictiveAnalytics) {
          this.performPredictiveAnalysis(data.payload);
        }
        if (this.config.enableAnomalyDetection) {
          this.detectAnomalies(data.payload);
        }
      } else if (data.type === 'system_metrics') {
        this.updateSystemMetricsChart(data.payload);
      } else if (data.type === 'business_kpis') {
        this.updateBusinessKpisChart(data.payload);
      } else if (data.type === 'security_events') {
        this.updateSecurityEventsChart(data.payload);
      }

      // Update dashboard metrics
      this.updateDashboardMetrics();

      this.emit('dataReceived', data);
    } catch (error) {
      console.error('❌ Error handling incoming data:', error);
      this.emit('error', error);
    }
  }

  /**
   * Handle historical data for model training
   */
  handleHistoricalData(data) {
    console.log('📊 Received historical data for model training');
    this.historicalData.set('user_activity', data);

    // Train predictive models with historical data
    if (this.config.enablePredictiveAnalytics) {
      this.trainPredictiveModels(data);
    }
  }

  /**
   * Handle alerts from the server
   */
  handleAlert(alert) {
    console.warn('⚠️ Alert received:', alert);
    this.emit('alert', alert);

    // Show notification to user
    if (typeof showNotification === 'function') {
      showNotification(`Alert: ${alert.message}`, 'warning');
    }
  }

  /**
   * Update user activity line chart
   */
  updateUserActivityChart(data) {
    const chart = this.charts.get('userActivity');
    if (!chart) return;

    const now = new Date();
    const buffer = this.dataBuffer.get('userActivity');

    // Add new data point
    buffer.push({
      x: now,
      y: data.activeUsers || 0,
    });

    // Keep only recent data points
    if (buffer.length > this.config.maxDataPoints) {
      buffer.shift();
    }

    // Update chart
    chart.data.datasets[0].data = [...buffer];
    chart.update('none'); // No animation for real-time updates
  }

  /**
   * Update system metrics bar chart
   */
  updateSystemMetricsChart(data) {
    const chart = this.charts.get('systemMetrics');
    if (!chart) return;

    const metrics = [
      data.cpu || 0,
      data.memory || 0,
      data.disk || 0,
      data.network || 0,
    ];

    chart.data.datasets[0].data = metrics;
    chart.update('none');
  }

  /**
   * Update business KPIs doughnut chart
   */
  updateBusinessKpisChart(data) {
    const chart = this.charts.get('businessKpis');
    if (!chart) return;

    const kpis = [data.revenue || 0, data.costs || 0, data.profit || 0];

    chart.data.datasets[0].data = kpis;
    chart.update('none');
  }

  /**
   * Update security events chart
   */
  updateSecurityEventsChart(data) {
    const chart = this.charts.get('securityEvents');
    if (!chart) return;

    const now = new Date();
    const buffer = this.dataBuffer.get('securityEvents') || [];

    // Add new data point
    buffer.push({
      x: now,
      y: data.eventCount || 0,
    });

    // Keep only recent data points
    if (buffer.length > this.config.maxDataPoints) {
      buffer.shift();
    }

    // Update chart
    if (!chart.data.datasets.length) {
      chart.data.datasets = [
        {
          label: 'Security Events',
          data: [],
          borderColor: 'rgb(255, 99, 132)',
          backgroundColor: 'rgba(255, 99, 132, 0.2)',
          tension: 0.4,
          fill: true,
        },
      ];
    }

    chart.data.datasets[0].data = [...buffer];
    chart.update('none');

    // Store buffer
    this.dataBuffer.set('securityEvents', buffer);
  }

  /**
   * Perform predictive analysis on user activity data
   */
  performPredictiveAnalysis(data) {
    // Simple linear regression for demonstration
    const buffer = this.dataBuffer.get('userActivity');
    if (!buffer || buffer.length < 5) return;

    // Calculate trend using linear regression
    const n = Math.min(buffer.length, 10); // Use last 10 points
    const points = buffer.slice(-n);

    let sumX = 0,
      sumY = 0,
      sumXY = 0,
      sumXX = 0;
    for (let i = 0; i < points.length; i++) {
      const x = i;
      const y = points[i].y;
      sumX += x;
      sumY += y;
      sumXY += x * y;
      sumXX += x * x;
    }

    const slope = (n * sumXY - sumX * sumY) / (n * sumXX - sumX * sumX);
    const intercept = (sumY - slope * sumX) / n;

    // Predict next value
    const predicted = slope * points.length + intercept;

    // Update dashboard with prediction
    const predictedElement = document.getElementById('predictedUsers');
    const confidenceElement = document.getElementById('predictionConfidence');

    if (predictedElement) {
      predictedElement.textContent = Math.max(0, Math.round(predicted));
    }

    if (confidenceElement) {
      // Simple confidence calculation based on recent variance
      const recentValues = points.map(p => p.y);
      const mean =
        recentValues.reduce((a, b) => a + b, 0) / recentValues.length;
      const variance =
        recentValues.reduce((sum, val) => sum + Math.pow(val - mean, 2), 0) /
        recentValues.length;
      const stdDev = Math.sqrt(variance);
      const confidence = Math.max(
        0,
        Math.min(100, 100 - (stdDev / mean) * 100)
      );

      confidenceElement.textContent = `${Math.round(confidence)}%`;
    }
  }

  /**
   * Detect anomalies in user activity data
   */
  detectAnomalies(data) {
    const buffer = this.dataBuffer.get('userActivity');
    if (!buffer || buffer.length < 10) return;

    // Calculate statistical thresholds
    const recentValues = buffer.slice(-10).map(p => p.y);
    const mean = recentValues.reduce((a, b) => a + b, 0) / recentValues.length;
    const variance =
      recentValues.reduce((sum, val) => sum + Math.pow(val - mean, 2), 0) /
      recentValues.length;
    const stdDev = Math.sqrt(variance);

    // Define anomaly threshold (2 standard deviations)
    const upperThreshold = mean + 2 * stdDev;
    const lowerThreshold = mean - 2 * stdDev;

    // Check if current value is anomalous
    if (
      data.activeUsers > upperThreshold ||
      data.activeUsers < lowerThreshold
    ) {
      console.warn('🚨 Anomaly detected in user activity:', data.activeUsers);

      // Emit anomaly event
      this.emit('anomalyDetected', {
        type: 'user_activity',
        value: data.activeUsers,
        threshold: { upper: upperThreshold, lower: lowerThreshold },
        timestamp: new Date(),
      });

      // Show notification
      if (typeof showNotification === 'function') {
        showNotification(
          `Anomaly detected: ${data.activeUsers} users (expected: ${Math.round(
            mean
          )})`,
          'warning'
        );
      }
    }
  }

  /**
   * Train predictive models with historical data
   */
  trainPredictiveModels(data) {
    console.log('🤖 Training predictive models with historical data');
    // In a real implementation, this would train ML models
    // For this example, we'll just store the data for reference
    this.predictiveModels.set('user_activity_model', {
      trained: true,
      dataPoints: data.length,
      lastTrained: new Date(),
    });
  }

  /**
   * Initialize predictive models
   */
  initializePredictiveModels() {
    console.log('🧠 Initializing predictive models');
    // Setup basic predictive model structures
    this.predictiveModels.set('user_activity_model', {
      trained: false,
      dataPoints: 0,
      lastTrained: null,
    });
  }

  /**
   * Initialize anomaly detectors
   */
  initializeAnomalyDetectors() {
    console.log('🔍 Initializing anomaly detectors');
    // Setup basic anomaly detector structures
    this.anomalyDetectors.set('user_activity_detector', {
      enabled: true,
      threshold: 2.0, // 2 standard deviations
      lastUpdated: null,
    });
  }

  /**
   * Initialize trend analyzers
   */
  initializeTrendAnalyzers() {
    console.log('📈 Initializing trend analyzers');
    // Setup basic trend analyzer structures
    this.trendAnalyzers.set('user_activity_trend', {
      enabled: true,
      windowSize: 10,
      lastAnalyzed: null,
    });
  }

  /**
   * Setup event listeners for dashboard interactions
   */
  setupEventListeners() {
    // Window resize handler
    window.addEventListener(
      'resize',
      this.debounce(() => {
        this.charts.forEach(chart => chart.resize());
      }, 250)
    );

    // Visibility change handler
    document.addEventListener('visibilitychange', () => {
      if (document.hidden) {
        this.pauseUpdates();
      } else {
        this.resumeUpdates();
      }
    });

    // Custom dashboard controls
    this.setupDashboardControls();
  }

  /**
   * Setup dashboard control buttons
   */
  setupDashboardControls() {
    const controls = {
      pause: document.getElementById('pauseBtn'),
      resume: document.getElementById('resumeBtn'),
      reset: document.getElementById('resetBtn'),
      export: document.getElementById('exportBtn'),
    };

    if (controls.pause) {
      controls.pause.addEventListener('click', () => this.pauseUpdates());
    }

    if (controls.resume) {
      controls.resume.addEventListener('click', () => this.resumeUpdates());
    }

    if (controls.reset) {
      controls.reset.addEventListener('click', () => this.resetDashboard());
    }

    if (controls.export) {
      controls.export.addEventListener('click', () => this.exportDataAsCSV());
    }
  }

  /**
   * Start metrics collection
   */
  startMetricsCollection() {
    setInterval(() => {
      if (this.isConnected) {
        const now = Date.now();
        const uptime = now - this.metrics.connectionUptime;
        this.metrics.eventsPerSecond =
          this.metrics.totalEvents / (uptime / 1000);
      }
    }, 1000);
  }

  /**
   * Update dashboard metrics display
   */
  updateDashboardMetrics() {
    const elements = {
      totalEvents: document.getElementById('totalEvents'),
      eventsPerSecond: document.getElementById('eventsPerSecond'),
      lastUpdate: document.getElementById('lastUpdate'),
      connectionStatus: document.getElementById('connectionStatus'),
    };

    if (elements.totalEvents) {
      elements.totalEvents.textContent =
        this.metrics.totalEvents.toLocaleString();
    }

    if (elements.eventsPerSecond) {
      elements.eventsPerSecond.textContent =
        this.metrics.eventsPerSecond.toFixed(2);
    }

    if (elements.lastUpdate && this.metrics.lastUpdate) {
      elements.lastUpdate.textContent =
        this.metrics.lastUpdate.toLocaleTimeString();
    }

    if (elements.connectionStatus) {
      elements.connectionStatus.textContent = this.isConnected
        ? '🟢 Connected'
        : '🔴 Disconnected';
      elements.connectionStatus.className = this.isConnected
        ? 'status-connected'
        : 'status-disconnected';
    }
  }

  /**
   * Pause real-time updates
   */
  pauseUpdates() {
    console.log('⏸️ Pausing updates');
    this.charts.forEach(chart => {
      chart.options.animation = false;
    });
    this.emit('paused');
  }

  /**
   * Resume real-time updates
   */
  resumeUpdates() {
    console.log('▶️ Resuming updates');
    this.charts.forEach(chart => {
      chart.options.animation = true;
    });
    this.emit('resumed');
  }

  /**
   * Reset dashboard to initial state
   */
  resetDashboard() {
    console.log('🔄 Resetting dashboard');

    // Clear data buffers
    this.dataBuffer.forEach(buffer => buffer.splice(0));

    // Reset charts
    this.charts.forEach(chart => {
      chart.data.datasets.forEach(dataset => {
        dataset.data = [];
      });
      chart.update();
    });

    // Reset metrics
    this.metrics = {
      totalEvents: 0,
      eventsPerSecond: 0,
      lastUpdate: null,
      connectionUptime: 0,
    };

    // Reset export data
    this.exportData = [];

    // Update display
    this.updateDashboardMetrics();

    this.emit('reset');
  }

  /**
   * Export data as CSV
   */
  exportDataAsCSV() {
    console.log('📊 Exporting data as CSV');

    if (this.exportData.length === 0) {
      console.warn('No data to export');
      if (typeof showNotification === 'function') {
        showNotification('No data available for export', 'warning');
      }
      return;
    }

    // Convert data to CSV format
    const headers = ['Timestamp', 'Type', 'Payload'];
    const rows = this.exportData.map(item => [
      item.timestamp.toISOString(),
      item.type,
      JSON.stringify(item.payload),
    ]);

    let csvContent = headers.join(',') + '\n';
    rows.forEach(row => {
      csvContent += row.map(field => `"${field}"`).join(',') + '\n';
    });

    // Create download link
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.setAttribute('href', url);
    link.setAttribute(
      'download',
      `analytics_data_${new Date().toISOString().slice(0, 10)}.csv`
    );
    link.style.visibility = 'hidden';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);

    console.log(`💾 Exported ${this.exportData.length} data points`);

    if (typeof showNotification === 'function') {
      showNotification(
        `Exported ${this.exportData.length} data points`,
        'success'
      );
    }

    this.emit('dataExported', { count: this.exportData.length });
  }

  /**
   * Utility function for debouncing
   */
  debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
      const later = () => {
        clearTimeout(timeout);
        func(...args);
      };
      clearTimeout(timeout);
      timeout = setTimeout(later, wait);
    };
  }

  /**
   * Cleanup resources
   */
  destroy() {
    // Close WebSocket connection
    if (this.socket) {
      this.socket.close();
    }

    // Destroy charts
    this.charts.forEach(chart => chart.destroy());

    // Remove event listeners
    window.removeEventListener('resize', this.debounce);
    document.removeEventListener('visibilitychange', this.setupEventListeners);

    console.log('🧹 Dashboard resources cleaned up');
  }
}

// Export the class
export default EnhancedRealTimeAnalyticsDashboard;
