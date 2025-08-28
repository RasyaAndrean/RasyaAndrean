/**
 * WebSocket Server for Real-time Analytics Dashboard Demo
 * Author: Rasya Andrean
 * Description: Simple WebSocket server that simulates real-time data streaming
 */

import { createServer } from 'http';
import { WebSocketServer } from 'ws';

// Create HTTP server
const server = createServer();
const wss = new WebSocketServer({ server });

// Store connected clients
const clients = new Set();

// Simulation parameters
let userActivity = 100;
let systemMetrics = {
  cpu: 45,
  memory: 60,
  disk: 70,
  network: 30,
};
let businessKpis = {
  revenue: 50000,
  costs: 30000,
  profit: 20000,
};

// Handle WebSocket connections
wss.on('connection', ws => {
  console.log('📱 New client connected');
  clients.add(ws);

  // Send initial data
  sendInitialData(ws);

  // Handle messages from client
  ws.on('message', message => {
    try {
      const data = JSON.parse(message);

      if (data.type === 'subscribe') {
        console.log('📈 Client subscribed to data streams');
        startDataStreaming(ws, data.payload);
      } else if (data.type === 'unsubscribe') {
        console.log('⏸️ Client unsubscribed from data streams');
        stopDataStreaming(ws);
      }
    } catch (error) {
      console.error('❌ Error parsing message:', error);
    }
  });

  // Handle client disconnect
  ws.on('close', () => {
    console.log('🔚 Client disconnected');
    clients.delete(ws);
    stopDataStreaming(ws);
  });

  // Handle errors
  ws.on('error', error => {
    console.error('❌ WebSocket error:', error);
    clients.delete(ws);
  });
});

// Send initial data to client
function sendInitialData(ws) {
  // Send historical data for model training
  const historicalData = [];
  for (let i = 0; i < 50; i++) {
    historicalData.push({
      timestamp: Date.now() - (50 - i) * 1000,
      activeUsers: Math.floor(50 + Math.random() * 100),
      cpu: Math.floor(30 + Math.random() * 50),
      memory: Math.floor(40 + Math.random() * 40),
    });
  }

  ws.send(
    JSON.stringify({
      type: 'historical_data',
      payload: historicalData,
    })
  );
}

// Start data streaming to client
function startDataStreaming(ws, config = {}) {
  const interval = config.interval || 1000;

  // Clear any existing interval for this client
  if (ws.dataInterval) {
    clearInterval(ws.dataInterval);
  }

  // Start sending data at specified interval
  ws.dataInterval = setInterval(() => {
    // Generate simulated data
    const timestamp = new Date().toISOString();

    // Simulate user activity fluctuations
    userActivity = Math.max(10, userActivity + (Math.random() - 0.5) * 20);

    // Simulate system metrics changes
    systemMetrics.cpu = Math.max(
      0,
      Math.min(100, systemMetrics.cpu + (Math.random() - 0.5) * 10)
    );
    systemMetrics.memory = Math.max(
      0,
      Math.min(100, systemMetrics.memory + (Math.random() - 0.5) * 5)
    );
    systemMetrics.disk = Math.max(
      0,
      Math.min(100, systemMetrics.disk + (Math.random() - 0.5) * 2)
    );
    systemMetrics.network = Math.max(
      0,
      Math.min(100, systemMetrics.network + (Math.random() - 0.5) * 15)
    );

    // Simulate business KPIs changes
    businessKpis.revenue = Math.max(
      0,
      businessKpis.revenue + (Math.random() - 0.5) * 2000
    );
    businessKpis.costs = Math.max(
      0,
      businessKpis.costs + (Math.random() - 0.5) * 1000
    );
    businessKpis.profit = businessKpis.revenue - businessKpis.costs;

    // Send user activity data
    ws.send(
      JSON.stringify({
        type: 'analytics_data',
        payload: {
          type: 'user_activity',
          timestamp: timestamp,
          activeUsers: Math.floor(userActivity),
        },
      })
    );

    // Send system metrics data
    ws.send(
      JSON.stringify({
        type: 'analytics_data',
        payload: {
          type: 'system_metrics',
          timestamp: timestamp,
          ...systemMetrics,
        },
      })
    );

    // Send business KPIs data
    ws.send(
      JSON.stringify({
        type: 'analytics_data',
        payload: {
          type: 'business_kpis',
          timestamp: timestamp,
          ...businessKpis,
        },
      })
    );

    // Occasionally send anomalies for testing
    if (Math.random() < 0.05) {
      // 5% chance
      ws.send(
        JSON.stringify({
          type: 'analytics_data',
          payload: {
            type: 'user_activity',
            timestamp: timestamp,
            activeUsers: Math.floor(userActivity + 200 + Math.random() * 100), // Anomalous spike
          },
        })
      );
    }
  }, interval);
}

// Stop data streaming to client
function stopDataStreaming(ws) {
  if (ws.dataInterval) {
    clearInterval(ws.dataInterval);
    delete ws.dataInterval;
  }
}

// Broadcast message to all connected clients
function broadcast(message) {
  clients.forEach(client => {
    if (client.readyState === client.OPEN) {
      client.send(message);
    }
  });
}

// Start server
const PORT = process.env.PORT || 3001;
server.listen(PORT, () => {
  console.log(`🚀 WebSocket server running on port ${PORT}`);
  console.log(`📡 Connect to ws://localhost:${PORT}`);
});

// Handle server shutdown
process.on('SIGINT', () => {
  console.log('\n🛑 Shutting down server...');
  wss.close();
  server.close();
  process.exit(0);
});
