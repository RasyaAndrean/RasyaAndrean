/**
 * Real-time Analytics Dashboard - Frontend Module
 * Author: Rasya Andrean
 * Description: Advanced real-time data visualization with WebSocket integration
 */

import { Chart, registerables } from 'chart.js';
import { EventEmitter } from 'events';
import { io } from 'socket.io-client';

Chart.register(...registerables);

class RealTimeAnalyticsDashboard extends EventEmitter {
  constructor(config = {}) {
    super();

    this.config = {
      wsUrl: config.wsUrl || 'ws://localhost:3001',
      updateInterval: config.updateInterval || 1000,
      maxDataPoints: config.maxDataPoints || 100,
      charts: config.charts || ['line', 'bar', 'doughnut'],
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

      console.log('🚀 Real-time Analytics Dashboard initialized successfully');
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

      this.socket.on('connect_error', error => {
        console.error('🚫 WebSocket connection error:', error);
        reject(error);
      });

      // Subscribe to data streams
      this.socket.emit('subscribe', {
        streams: ['user_activity', 'system_metrics', 'business_kpis'],
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

      // Process different data types
      if (data.type === 'user_activity') {
        this.updateUserActivityChart(data.payload);
      } else if (data.type === 'system_metrics') {
        this.updateSystemMetricsChart(data.payload);
      } else if (data.type === 'business_kpis') {
        this.updateBusinessKpisChart(data.payload);
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
      controls.reset.addEventListener('click', () => this.resetCharts());
    }

    if (controls.export) {
      controls.export.addEventListener('click', () => this.exportData());
    }
  }

  /**
   * Start collecting dashboard metrics
   */
  startMetricsCollection() {
    setInterval(() => {
      this.calculateEventsPerSecond();
      this.updateMetricsDisplay();
      this.runPredictiveAnalytics();
      this.detectAnomalies();
    }, 1000);
  }

  /**
   * Calculate events per second
   */
  calculateEventsPerSecond() {
    const now = Date.now();
    if (!this.lastMetricsUpdate) {
      this.lastMetricsUpdate = now;
      this.lastEventCount = this.metrics.totalEvents;
      return;
    }

    const timeDiff = (now - this.lastMetricsUpdate) / 1000;
    const eventDiff = this.metrics.totalEvents - this.lastEventCount;

    this.metrics.eventsPerSecond = Math.round(eventDiff / timeDiff);

    this.lastMetricsUpdate = now;
    this.lastEventCount = this.metrics.totalEvents;
  }

  /**
   * Update metrics display in UI
   */
  updateMetricsDisplay() {
    const elements = {
      totalEvents: document.getElementById('totalEvents'),
      eventsPerSecond: document.getElementById('eventsPerSecond'),
      connectionStatus: document.getElementById('connectionStatus'),
      lastUpdate: document.getElementById('lastUpdate'),
    };

    if (elements.totalEvents) {
      elements.totalEvents.textContent =
        this.metrics.totalEvents.toLocaleString();
    }

    if (elements.eventsPerSecond) {
      elements.eventsPerSecond.textContent = this.metrics.eventsPerSecond;
    }

    if (elements.connectionStatus) {
      elements.connectionStatus.textContent = this.isConnected
        ? '🟢 Connected'
        : '🔴 Disconnected';
      elements.connectionStatus.className = this.isConnected
        ? 'status-connected'
        : 'status-disconnected';
    }

    if (elements.lastUpdate && this.metrics.lastUpdate) {
      elements.lastUpdate.textContent =
        this.metrics.lastUpdate.toLocaleTimeString();
    }
  }

  /**
   * Initialize predictive models
   */
  initializePredictiveModels() {
    // Simple linear regression model for user activity prediction
    this.predictiveModels.set('userActivity', {
      coefficients: [0, 0], // [intercept, slope]
      lastTraining: null,
      accuracy: 0,
    });

    // Exponential smoothing for system metrics
    this.predictiveModels.set('systemMetrics', {
      alpha: 0.3, // Smoothing factor
      predictions: {},
      lastUpdate: null,
    });

    console.log('🔮 Predictive models initialized');
  }

  /**
   * Initialize anomaly detectors
   */
  initializeAnomalyDetectors() {
    // Statistical anomaly detection using z-score
    this.anomalyDetectors.set('userActivity', {
      mean: 0,
      std: 1,
      threshold: 2.5, // Z-score threshold
      windowSize: 100,
      values: [],
    });

    // Threshold-based anomaly detection for system metrics
    this.anomalyDetectors.set('systemMetrics', {
      thresholds: {
        cpu: 85,
        memory: 80,
        disk: 90,
        network: 75,
      },
    });

    console.log('🔍 Anomaly detectors initialized');
  }

  /**
   * Handle historical data for model training
   */
  handleHistoricalData(data) {
    if (!this.historicalData.has(data.type)) {
      this.historicalData.set(data.type, []);
    }

    const buffer = this.historicalData.get(data.type);
    buffer.push(data.payload);

    // Keep only recent historical data
    if (buffer.length > 1000) {
      buffer.shift();
    }

    // Retrain models periodically
    if (buffer.length % 50 === 0) {
      this.trainPredictiveModels(data.type);
    }
  }

  /**
   * Train predictive models with historical data
   */
  trainPredictiveModels(dataType) {
    const historicalData = this.historicalData.get(dataType);
    if (!historicalData || historicalData.length < 10) return;

    if (
      dataType === 'user_activity' &&
      this.predictiveModels.has('userActivity')
    ) {
      const model = this.predictiveModels.get('userActivity');

      // Simple linear regression
      const x = historicalData.map((_, i) => i);
      const y = historicalData.map(d => d.activeUsers || 0);

      const n = x.length;
      const sumX = x.reduce((a, b) => a + b, 0);
      const sumY = y.reduce((a, b) => a + b, 0);
      const sumXY = x.map((xi, i) => xi * y[i]).reduce((a, b) => a + b, 0);
      const sumXX = x.map(xi => xi * xi).reduce((a, b) => a + b, 0);

      const slope = (n * sumXY - sumX * sumY) / (n * sumXX - sumX * sumX);
      const intercept = (sumY - slope * sumX) / n;

      model.coefficients = [intercept, slope];
      model.lastTraining = new Date();

      // Calculate accuracy (simplified)
      const predictions = x.map(xi => intercept + slope * xi);
      const mse =
        y
          .map((yi, i) => Math.pow(yi - predictions[i], 2))
          .reduce((a, b) => a + b, 0) / n;
      model.accuracy = Math.max(0, 100 - mse / 100);

      console.log(
        `📈 User activity model trained. Accuracy: ${model.accuracy.toFixed(
          2
        )}%`
      );
    }
  }

  /**
   * Run predictive analytics
   */
  runPredictiveAnalytics() {
    // Predict next values for user activity
    if (this.predictiveModels.has('userActivity')) {
      const model = this.predictiveModels.get('userActivity');
      const buffer = this.dataBuffer.get('userActivity');

      if (buffer && buffer.length > 0) {
        const nextTime = buffer.length;
        const predictedValue =
          model.coefficients[0] + model.coefficients[1] * nextTime;

        // Update UI with prediction
        const predictionElement = document.getElementById('predictedUsers');
        if (predictionElement) {
          predictionElement.textContent = Math.max(
            0,
            Math.round(predictedValue)
          ).toLocaleString();
        }

        // Show confidence
        const confidenceElement = document.getElementById(
          'predictionConfidence'
        );
        if (confidenceElement) {
          confidenceElement.textContent = `${model.accuracy.toFixed(1)}%`;
        }
      }
    }

    // Predict system metrics using exponential smoothing
    if (this.predictiveModels.has('systemMetrics')) {
      const model = this.predictiveModels.get('systemMetrics');
      const buffer = this.dataBuffer.get('systemMetrics');

      if (buffer && buffer.length > 0) {
        const latestData = buffer[buffer.length - 1];

        // Exponential smoothing
        Object.keys(latestData).forEach(key => {
          if (typeof latestData[key] === 'number') {
            if (!model.predictions[key]) {
              model.predictions[key] = latestData[key];
            } else {
              model.predictions[key] =
                model.alpha * latestData[key] +
                (1 - model.alpha) * model.predictions[key];
            }
          }
        });

        model.lastUpdate = new Date();
      }
    }
  }

  /**
   * Detect anomalies in real-time data
   */
  detectAnomalies() {
    // Detect anomalies in user activity
    if (this.anomalyDetectors.has('userActivity')) {
      const detector = this.anomalyDetectors.get('userActivity');
      const buffer = this.dataBuffer.get('userActivity');

      if (buffer && buffer.length > 0) {
        const latestValue = buffer[buffer.length - 1].y;

        // Update statistics
        detector.values.push(latestValue);
        if (detector.values.length > detector.windowSize) {
          detector.values.shift();
        }

        // Calculate mean and standard deviation
        const mean =
          detector.values.reduce((a, b) => a + b, 0) / detector.values.length;
        const variance =
          detector.values
            .map(x => Math.pow(x - mean, 2))
            .reduce((a, b) => a + b, 0) / detector.values.length;
        const std = Math.sqrt(variance);

        detector.mean = mean;
        detector.std = std;

        // Detect anomaly using z-score
        if (std > 0) {
          const zScore = Math.abs((latestValue - mean) / std);
          if (zScore > detector.threshold) {
            console.warn(
              `🚨 Anomaly detected in user activity: ${latestValue} (z-score: ${zScore.toFixed(
                2
              )})`
            );
            this.emit('anomaly', {
              type: 'userActivity',
              value: latestValue,
              zScore: zScore,
              timestamp: new Date(),
            });

            // Show user notification
            showNotification(
              `Unusual user activity detected: ${latestValue}`,
              'warning'
            );
          }
        }
      }
    }

    // Detect anomalies in system metrics
    if (this.anomalyDetectors.has('systemMetrics')) {
      const detector = this.anomalyDetectors.get('systemMetrics');
      const buffer = this.dataBuffer.get('systemMetrics');

      if (buffer && buffer.length > 0) {
        const latestData = buffer[buffer.length - 1];

        // Check thresholds
        Object.keys(detector.thresholds).forEach(metric => {
          if (
            latestData[metric] &&
            latestData[metric] > detector.thresholds[metric]
          ) {
            console.warn(
              `🚨 System metric anomaly: ${metric} at ${latestData[metric]}%`
            );
            this.emit('anomaly', {
              type: 'systemMetrics',
              metric: metric,
              value: latestData[metric],
              threshold: detector.thresholds[metric],
              timestamp: new Date(),
            });

            // Show user notification
            showNotification(
              `High ${metric} usage: ${latestData[metric]}%`,
              'warning'
            );
          }
        });
      }
    }
  }

  /**
   * Pause real-time updates
   */
  pauseUpdates() {
    if (this.socket) {
      this.socket.emit('unsubscribe');
    }
    console.log('⏸️ Dashboard updates paused');
    this.emit('paused');
  }

  /**
   * Resume real-time updates
   */
  resumeUpdates() {
    if (this.socket && this.isConnected) {
      this.socket.emit('subscribe', {
        streams: ['user_activity', 'system_metrics', 'business_kpis'],
        interval: this.config.updateInterval,
      });
    }
    console.log('▶️ Dashboard updates resumed');
    this.emit('resumed');
  }

  /**
   * Reset all charts
   */
  resetCharts() {
    this.charts.forEach((chart, key) => {
      const buffer = this.dataBuffer.get(key);
      if (buffer) {
        buffer.length = 0;
      }

      if (chart.data.datasets[0].data) {
        chart.data.datasets[0].data.length = 0;
        chart.update();
      }
    });

    this.metrics.totalEvents = 0;
    this.metrics.eventsPerSecond = 0;

    console.log('🔄 Charts reset');
    this.emit('reset');
  }

  /**
   * Export dashboard data
   */
  async exportData() {
    try {
      const exportData = {
        timestamp: new Date().toISOString(),
        metrics: this.metrics,
        chartData: {},
      };

      // Collect data from all charts
      this.dataBuffer.forEach((buffer, key) => {
        exportData.chartData[key] = [...buffer];
      });

      // Create and download file
      const blob = new Blob([JSON.stringify(exportData, null, 2)], {
        type: 'application/json',
      });

      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `dashboard-export-${Date.now()}.json`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);

      console.log('📊 Data exported successfully');
      this.emit('exported', exportData);
    } catch (error) {
      console.error('❌ Export failed:', error);
      this.emit('error', error);
    }
  }

  /**
   * Utility: Debounce function
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
    if (this.socket) {
      this.socket.disconnect();
    }

    this.charts.forEach(chart => chart.destroy());
    this.charts.clear();
    this.dataBuffer.clear();

    this.removeAllListeners();

    console.log('🧹 Dashboard destroyed');
  }
}

// Usage example
document.addEventListener('DOMContentLoaded', () => {
  const dashboard = new RealTimeAnalyticsDashboard({
    wsUrl: 'ws://localhost:3001',
    updateInterval: 500,
    maxDataPoints: 50,
  });

  // Event listeners
  dashboard.on('initialized', () => {
    console.log('✅ Dashboard ready!');
  });

  dashboard.on('error', error => {
    console.error('Dashboard error:', error);
    // Show user-friendly error message
    showNotification('Connection error. Retrying...', 'error');
  });

  dashboard.on('connected', () => {
    showNotification('Connected to real-time data stream', 'success');
  });

  dashboard.on('disconnected', () => {
    showNotification('Disconnected from data stream', 'warning');
  });

  dashboard.on('anomaly', anomaly => {
    console.warn('Anomaly detected:', anomaly);
    // Log to analytics service
    if (dashboard.socket) {
      dashboard.socket.emit('anomaly_report', anomaly);
    }
  });

  dashboard.on('exported', data => {
    console.log('Data exported for further analysis');
    // Could send to ML service for deeper analysis
  });

  // Make dashboard globally available
  window.dashboard = dashboard;
});

/**
 * Show notification to user
 */
function showNotification(message, type = 'info') {
  const notification = document.createElement('div');
  notification.className = `notification notification-${type}`;
  notification.textContent = message;

  document.body.appendChild(notification);

  setTimeout(() => {
    notification.remove();
  }, 5000);
}

export default RealTimeAnalyticsDashboard;
