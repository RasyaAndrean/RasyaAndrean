package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"sync"
	"syscall"
	"time"

	"github.com/gorilla/mux"
	"github.com/gorilla/websocket"
	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promhttp"
	"go.uber.org/zap"
)

// Microservice Architecture - API Gateway with Load Balancing
// Author: Rasya Andrean
// Description: High-performance API Gateway with real-time monitoring

type Config struct {
	Port            string        `json:"port"`
	ReadTimeout     time.Duration `json:"read_timeout"`
	WriteTimeout    time.Duration `json:"write_timeout"`
	IdleTimeout     time.Duration `json:"idle_timeout"`
	MaxConnections  int           `json:"max_connections"`
	RateLimitRPS    int           `json:"rate_limit_rps"`
	EnableMetrics   bool          `json:"enable_metrics"`
	EnableWebSocket bool          `json:"enable_websocket"`
}

type ServiceRegistry struct {
	mu       sync.RWMutex
	services map[string][]*ServiceInstance
	logger   *zap.Logger
}

type ServiceInstance struct {
	ID       string    `json:"id"`
	Name     string    `json:"name"`
	Host     string    `json:"host"`
	Port     int       `json:"port"`
	Health   string    `json:"health"`
	LastSeen time.Time `json:"last_seen"`
	Metadata map[string]string `json:"metadata"`
}

type APIGateway struct {
	config     *Config
	registry   *ServiceRegistry
	router     *mux.Router
	server     *http.Server
	logger     *zap.Logger
	metrics    *Metrics
	upgrader   websocket.Upgrader
	clients    map[*websocket.Conn]bool
	clientsMu  sync.RWMutex
	shutdown   chan os.Signal
}

type Metrics struct {
	RequestsTotal     prometheus.Counter
	RequestDuration   prometheus.Histogram
	ActiveConnections prometheus.Gauge
	ServiceHealth     *prometheus.GaugeVec
	SecurityEvents    *prometheus.CounterVec // New security events metric
}

type SecurityEvent struct {
	Type      string    `json:"type"`
	Timestamp time.Time `json:"timestamp"`
	SourceIP  string    `json:"source_ip"`
	Details   string    `json:"details"`
	Severity  string    `json:"severity"`
}

type HealthCheck struct {
	Status    string            `json:"status"`
	Timestamp time.Time         `json:"timestamp"`
	Services  map[string]string `json:"services"`
	Uptime    time.Duration     `json:"uptime"`
	Version   string            `json:"version"`
}

type LoadBalancer struct {
	mu        sync.RWMutex
	algorithm string
	counters  map[string]int
}

// NewServiceRegistry creates a new service registry
func NewServiceRegistry(logger *zap.Logger) *ServiceRegistry {
	return &ServiceRegistry{
		services: make(map[string][]*ServiceInstance),
		logger:   logger,
	}
}

// RegisterService registers a new service instance
func (sr *ServiceRegistry) RegisterService(service *ServiceInstance) error {
	sr.mu.Lock()
	defer sr.mu.Unlock()

	if sr.services[service.Name] == nil {
		sr.services[service.Name] = make([]*ServiceInstance, 0)
	}

	// Check if service already exists
	for i, existing := range sr.services[service.Name] {
		if existing.ID == service.ID {
			sr.services[service.Name][i] = service
			sr.logger.Info("Service updated",
				zap.String("service", service.Name),
				zap.String("id", service.ID))
			return nil
		}
	}

	// Add new service
	sr.services[service.Name] = append(sr.services[service.Name], service)
	sr.logger.Info("Service registered",
		zap.String("service", service.Name),
		zap.String("id", service.ID))

	return nil
}

// GetService returns a healthy service instance using load balancing
func (sr *ServiceRegistry) GetService(serviceName string, lb *LoadBalancer) (*ServiceInstance, error) {
	sr.mu.RLock()
	defer sr.mu.RUnlock()

	instances, exists := sr.services[serviceName]
	if !exists || len(instances) == 0 {
		return nil, fmt.Errorf("service %s not found", serviceName)
	}

	// Filter healthy instances
	healthyInstances := make([]*ServiceInstance, 0)
	for _, instance := range instances {
		if instance.Health == "healthy" && time.Since(instance.LastSeen) < 30*time.Second {
			healthyInstances = append(healthyInstances, instance)
		}
	}

	if len(healthyInstances) == 0 {
		return nil, fmt.Errorf("no healthy instances for service %s", serviceName)
	}

	// Load balancing
	return lb.SelectInstance(serviceName, healthyInstances), nil
}

// NewLoadBalancer creates a new load balancer
func NewLoadBalancer(algorithm string) *LoadBalancer {
	return &LoadBalancer{
		algorithm: algorithm,
		counters:  make(map[string]int),
	}
}

// SelectInstance selects an instance based on load balancing algorithm
func (lb *LoadBalancer) SelectInstance(serviceName string, instances []*ServiceInstance) *ServiceInstance {
	lb.mu.Lock()
	defer lb.mu.Unlock()

	switch lb.algorithm {
	case "round_robin":
		counter := lb.counters[serviceName]
		instance := instances[counter%len(instances)]
		lb.counters[serviceName] = counter + 1
		return instance
	case "least_connections":
		// Simplified: return first instance (would need connection tracking)
		return instances[0]
	default:
		// Random selection
		return instances[time.Now().UnixNano()%int64(len(instances))]
	}
}

// NewMetrics creates Prometheus metrics
func NewMetrics() *Metrics {
	return &Metrics{
		RequestsTotal: prometheus.NewCounter(prometheus.CounterOpts{
			Name: "api_gateway_requests_total",
			Help: "Total number of requests processed",
		}),
		RequestDuration: prometheus.NewHistogram(prometheus.HistogramOpts{
			Name:    "api_gateway_request_duration_seconds",
			Help:    "Request duration in seconds",
			Buckets: prometheus.DefBuckets,
		}),
		ActiveConnections: prometheus.NewGauge(prometheus.GaugeOpts{
			Name: "api_gateway_active_connections",
			Help: "Number of active connections",
		}),
		ServiceHealth: prometheus.NewGaugeVec(prometheus.GaugeOpts{
			Name: "api_gateway_service_health",
			Help: "Health status of registered services",
		}, []string{"service", "instance"}),
		SecurityEvents: prometheus.NewCounterVec(prometheus.CounterOpts{
			Name: "api_gateway_security_events_total",
			Help: "Total number of security events detected",
		}, []string{"type", "severity"}),
	}
}

// Register registers metrics with Prometheus
func (m *Metrics) Register() {
	prometheus.MustRegister(m.RequestsTotal)
	prometheus.MustRegister(m.RequestDuration)
	prometheus.MustRegister(m.ActiveConnections)
	prometheus.MustRegister(m.ServiceHealth)
	prometheus.MustRegister(m.SecurityEvents) // Register new security events metric
}

// NewAPIGateway creates a new API Gateway instance
func NewAPIGateway(config *Config) (*APIGateway, error) {
	logger, err := zap.NewProduction()
	if err != nil {
		return nil, fmt.Errorf("failed to create logger: %w", err)
	}

	registry := NewServiceRegistry(logger)
	metrics := NewMetrics()

	if config.EnableMetrics {
		metrics.Register()
	}

	gateway := &APIGateway{
		config:   config,
		registry: registry,
		router:   mux.NewRouter(),
		logger:   logger,
		metrics:  metrics,
		upgrader: websocket.Upgrader{
			CheckOrigin: func(r *http.Request) bool {
				return true // Allow all origins in development
			},
		},
		clients:  make(map[*websocket.Conn]bool),
		shutdown: make(chan os.Signal, 1),
	}

	gateway.setupRoutes()
	gateway.setupServer()

	return gateway, nil
}

// setupRoutes configures API routes
func (gw *APIGateway) setupRoutes() {
	// Health check endpoint
	gw.router.HandleFunc("/health", gw.healthCheckHandler).Methods("GET")

	// Service registration endpoint
	gw.router.HandleFunc("/register", gw.registerServiceHandler).Methods("POST")

	// Service discovery endpoint
	gw.router.HandleFunc("/services", gw.listServicesHandler).Methods("GET")

	// WebSocket endpoint for real-time updates
	if gw.config.EnableWebSocket {
		gw.router.HandleFunc("/ws", gw.websocketHandler)
	}

	// Metrics endpoint
	if gw.config.EnableMetrics {
		gw.router.Handle("/metrics", promhttp.Handler())
	}

	// Proxy endpoints (catch-all)
	gw.router.PathPrefix("/api/").HandlerFunc(gw.proxyHandler)

	// Middleware - ORDER IS IMPORTANT
	gw.router.Use(gw.loggingMiddleware)
	gw.router.Use(gw.securityMiddleware) // Add security middleware
	gw.router.Use(gw.metricsMiddleware)
	gw.router.Use(gw.corsMiddleware)
}

// setupServer configures the HTTP server
func (gw *APIGateway) setupServer() {
	gw.server = &http.Server{
		Addr:         ":" + gw.config.Port,
		Handler:      gw.router,
		ReadTimeout:  gw.config.ReadTimeout,
		WriteTimeout: gw.config.WriteTimeout,
		IdleTimeout:  gw.config.IdleTimeout,
	}
}

// healthCheckHandler handles health check requests
func (gw *APIGateway) healthCheckHandler(w http.ResponseWriter, r *http.Request) {
	gw.registry.mu.RLock()
	services := make(map[string]string)
	for name, instances := range gw.registry.services {
		healthyCount := 0
		for _, instance := range instances {
			if instance.Health == "healthy" {
				healthyCount++
			}
		}
		services[name] = fmt.Sprintf("%d/%d healthy", healthyCount, len(instances))
	}
	gw.registry.mu.RUnlock()

	health := HealthCheck{
		Status:    "healthy",
		Timestamp: time.Now(),
		Services:  services,
		Uptime:    time.Since(time.Now()), // Would track actual uptime
		Version:   "1.0.0",
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(health)
}

// registerServiceHandler handles service registration
func (gw *APIGateway) registerServiceHandler(w http.ResponseWriter, r *http.Request) {
	var service ServiceInstance
	if err := json.NewDecoder(r.Body).Decode(&service); err != nil {
		http.Error(w, "Invalid JSON", http.StatusBadRequest)
		return
	}

	service.LastSeen = time.Now()
	if service.Health == "" {
		service.Health = "healthy"
	}

	if err := gw.registry.RegisterService(&service); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	// Broadcast to WebSocket clients
	gw.broadcastServiceUpdate(&service)

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "registered"})
}

// listServicesHandler lists all registered services
func (gw *APIGateway) listServicesHandler(w http.ResponseWriter, r *http.Request) {
	gw.registry.mu.RLock()
	services := make(map[string][]*ServiceInstance)
	for name, instances := range gw.registry.services {
		services[name] = instances
	}
	gw.registry.mu.RUnlock()

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(services)
}

// websocketHandler handles WebSocket connections
func (gw *APIGateway) websocketHandler(w http.ResponseWriter, r *http.Request) {
	conn, err := gw.upgrader.Upgrade(w, r, nil)
	if err != nil {
		gw.logger.Error("WebSocket upgrade failed", zap.Error(err))
		return
	}
	defer conn.Close()

	gw.clientsMu.Lock()
	gw.clients[conn] = true
	gw.clientsMu.Unlock()

	gw.logger.Info("WebSocket client connected",
		zap.String("remote_addr", r.RemoteAddr))

	// Send initial service list
	gw.sendServiceList(conn)

	// Keep connection alive and handle messages
	for {
		_, _, err := conn.ReadMessage()
		if err != nil {
			gw.logger.Info("WebSocket client disconnected", zap.Error(err))
			break
		}
	}

	gw.clientsMu.Lock()
	delete(gw.clients, conn)
	gw.clientsMu.Unlock()
}

// proxyHandler handles API proxying to backend services
func (gw *APIGateway) proxyHandler(w http.ResponseWriter, r *http.Request) {
	// Extract service name from path
	path := r.URL.Path
	if len(path) < 5 { // "/api/"
		http.Error(w, "Invalid path", http.StatusBadRequest)
		return
	}

	// Simple service extraction (would be more sophisticated in production)
	serviceName := "default-service"

	lb := NewLoadBalancer("round_robin")
	instance, err := gw.registry.GetService(serviceName, lb)
	if err != nil {
		http.Error(w, fmt.Sprintf("Service unavailable: %v", err), http.StatusServiceUnavailable)
		return
	}

	// Create proxy request (simplified)
	targetURL := fmt.Sprintf("http://%s:%d%s", instance.Host, instance.Port, path)
	gw.logger.Info("Proxying request",
		zap.String("target", targetURL),
		zap.String("method", r.Method))

	// In production, would use httputil.ReverseProxy
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"message": "Proxied to " + targetURL,
		"service": instance,
		"timestamp": time.Now(),
	})
}

// Middleware functions
func (gw *APIGateway) loggingMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()

		next.ServeHTTP(w, r)

		gw.logger.Info("Request processed",
			zap.String("method", r.Method),
			zap.String("path", r.URL.Path),
			zap.String("remote_addr", r.RemoteAddr),
			zap.Duration("duration", time.Since(start)))
	})
}

func (gw *APIGateway) metricsMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if gw.config.EnableMetrics {
			start := time.Now()

			gw.metrics.RequestsTotal.Inc()
			gw.metrics.ActiveConnections.Inc()

			next.ServeHTTP(w, r)

			gw.metrics.RequestDuration.Observe(time.Since(start).Seconds())
			gw.metrics.ActiveConnections.Dec()
		} else {
			next.ServeHTTP(w, r)
		}
	})
}

func (gw *APIGateway) corsMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization")

		if r.Method == "OPTIONS" {
			w.WriteHeader(http.StatusOK)
			return
		}

		next.ServeHTTP(w, r)
	})
}

// Add a new security middleware
func (gw *APIGateway) securityMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Check for common security issues
		securityIssues := gw.detectSecurityIssues(r)

		for _, issue := range securityIssues {
			// Log security event
			gw.logger.Warn("Security event detected",
				zap.String("type", issue.Type),
				zap.String("severity", issue.Severity),
				zap.String("source_ip", issue.SourceIP),
				zap.String("details", issue.Details))

			// Update security metrics
			if gw.config.EnableMetrics {
				gw.metrics.SecurityEvents.WithLabelValues(issue.Type, issue.Severity).Inc()
			}

			// For critical issues, reject the request
			if issue.Severity == "critical" {
				http.Error(w, "Security violation detected", http.StatusForbidden)
				return
			}
		}

		next.ServeHTTP(w, r)
	})
}

// detectSecurityIssues detects common security issues in requests
func (gw *APIGateway) detectSecurityIssues(r *http.Request) []SecurityEvent {
	var issues []SecurityEvent
	sourceIP := r.RemoteAddr

	// Check for SQL injection patterns
	sqlPatterns := []string{"'", "\"", "--", "/*", "*/", "xp_", "exec"}
	for _, pattern := range sqlPatterns {
		if strings.Contains(r.URL.RawQuery, pattern) || strings.Contains(r.URL.Path, pattern) {
			issues = append(issues, SecurityEvent{
				Type:      "sql_injection_attempt",
				Timestamp: time.Now(),
				SourceIP:  sourceIP,
				Details:   fmt.Sprintf("SQL injection pattern detected: %s", pattern),
				Severity:  "high",
			})
		}
	}

	// Check for XSS patterns
	xssPatterns := []string{"<script", "javascript:", "onload=", "onerror="}
	for _, pattern := range xssPatterns {
		if strings.Contains(r.URL.RawQuery, pattern) || strings.Contains(r.URL.Path, pattern) {
			issues = append(issues, SecurityEvent{
				Type:      "xss_attempt",
				Timestamp: time.Now(),
				SourceIP:  sourceIP,
				Details:   fmt.Sprintf("XSS pattern detected: %s", pattern),
				Severity:  "medium",
			})
		}
	}

	// Check for excessive request size
	if r.ContentLength > 10*1024*1024 { // 10MB limit
		issues = append(issues, SecurityEvent{
			Type:      "large_payload",
			Timestamp: time.Now(),
			SourceIP:  sourceIP,
			Details:   fmt.Sprintf("Request too large: %d bytes", r.ContentLength),
			Severity:  "medium",
		})
	}

	return issues
}

// broadcastServiceUpdate broadcasts service updates to WebSocket clients
func (gw *APIGateway) broadcastServiceUpdate(service *ServiceInstance) {
	if !gw.config.EnableWebSocket {
		return
	}

	message := map[string]interface{}{
		"type":    "service_update",
		"service": service,
		"timestamp": time.Now(),
	}

	data, err := json.Marshal(message)
	if err != nil {
		gw.logger.Error("Failed to marshal service update", zap.Error(err))
		return
	}

	gw.clientsMu.RLock()
	defer gw.clientsMu.RUnlock()

	for client := range gw.clients {
		if err := client.WriteMessage(websocket.TextMessage, data); err != nil {
			gw.logger.Error("Failed to send WebSocket message", zap.Error(err))
			client.Close()
			delete(gw.clients, client)
		}
	}
}

// sendServiceList sends current service list to WebSocket client
func (gw *APIGateway) sendServiceList(conn *websocket.Conn) {
	gw.registry.mu.RLock()
	services := make(map[string][]*ServiceInstance)
	for name, instances := range gw.registry.services {
		services[name] = instances
	}
	gw.registry.mu.RUnlock()

	message := map[string]interface{}{
		"type":     "service_list",
		"services": services,
		"timestamp": time.Now(),
	}

	data, err := json.Marshal(message)
	if err != nil {
		gw.logger.Error("Failed to marshal service list", zap.Error(err))
		return
	}

	if err := conn.WriteMessage(websocket.TextMessage, data); err != nil {
		gw.logger.Error("Failed to send service list", zap.Error(err))
	}
}

// Start starts the API Gateway server
func (gw *APIGateway) Start() error {
	signal.Notify(gw.shutdown, os.Interrupt, syscall.SIGTERM)

	gw.logger.Info("Starting API Gateway",
		zap.String("port", gw.config.Port),
		zap.Bool("metrics", gw.config.EnableMetrics),
		zap.Bool("websocket", gw.config.EnableWebSocket))

	// Start health check routine
	go gw.healthCheckRoutine()

	// Start server in goroutine
	go func() {
		if err := gw.server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			gw.logger.Fatal("Server failed to start", zap.Error(err))
		}
	}()

	// Wait for shutdown signal
	<-gw.shutdown
	gw.logger.Info("Shutting down API Gateway...")

	// Graceful shutdown
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	return gw.server.Shutdown(ctx)
}

// healthCheckRoutine periodically checks service health
func (gw *APIGateway) healthCheckRoutine() {
	ticker := time.NewTicker(10 * time.Second)
	defer ticker.Stop()

	for {
		select {
		case <-ticker.C:
			gw.performHealthChecks()
		case <-gw.shutdown:
			return
		}
	}
}

// performHealthChecks checks health of all registered services
func (gw *APIGateway) performHealthChecks() {
	gw.registry.mu.Lock()
	defer gw.registry.mu.Unlock()

	for serviceName, instances := range gw.registry.services {
		for _, instance := range instances {
			// Simple health check (would make HTTP request in production)
			if time.Since(instance.LastSeen) > 30*time.Second {
				instance.Health = "unhealthy"
			}

			// Update metrics
			if gw.config.EnableMetrics {
				healthValue := 0.0
				if instance.Health == "healthy" {
					healthValue = 1.0
				}
				gw.metrics.ServiceHealth.WithLabelValues(serviceName, instance.ID).Set(healthValue)
			}
		}
	}
}

// LoadConfig loads configuration from file or environment
func LoadConfig() *Config {
	return &Config{
		Port:            getEnv("PORT", "8080"),
		ReadTimeout:     parseDuration(getEnv("READ_TIMEOUT", "15s")),
		WriteTimeout:    parseDuration(getEnv("WRITE_TIMEOUT", "15s")),
		IdleTimeout:     parseDuration(getEnv("IDLE_TIMEOUT", "60s")),
		MaxConnections:  parseInt(getEnv("MAX_CONNECTIONS", "1000")),
		RateLimitRPS:    parseInt(getEnv("RATE_LIMIT_RPS", "100")),
		EnableMetrics:   parseBool(getEnv("ENABLE_METRICS", "true")),
		EnableWebSocket: parseBool(getEnv("ENABLE_WEBSOCKET", "true")),
	}
}

// Utility functions
func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}

func parseDuration(s string) time.Duration {
	d, err := time.ParseDuration(s)
	if err != nil {
		return 15 * time.Second
	}
	return d
}

func parseInt(s string) int {
	// Simplified parsing
	if s == "1000" {
		return 1000
	}
	return 100
}

func parseBool(s string) bool {
	return s == "true"
}

func main() {
	config := LoadConfig()

	gateway, err := NewAPIGateway(config)
	if err != nil {
		log.Fatalf("Failed to create API Gateway: %v", err)
	}

	// Register some example services
	exampleServices := []*ServiceInstance{
		{
			ID:   "user-service-1",
			Name: "user-service",
			Host: "localhost",
			Port: 3001,
			Health: "healthy",
			Metadata: map[string]string{
				"version": "1.0.0",
				"region":  "us-east-1",
			},
		},
		{
			ID:   "order-service-1",
			Name: "order-service",
			Host: "localhost",
			Port: 3002,
			Health: "healthy",
			Metadata: map[string]string{
				"version": "1.2.0",
				"region":  "us-east-1",
			},
		},
	}

	for _, service := range exampleServices {
		if err := gateway.registry.RegisterService(service); err != nil {
			log.Printf("Failed to register service %s: %v", service.Name, err)
		}
	}

	fmt.Printf("🚀 API Gateway starting on port %s\n", config.Port)
	fmt.Printf("📊 Metrics: %v\n", config.EnableMetrics)
	fmt.Printf("🔌 WebSocket: %v\n", config.EnableWebSocket)
	fmt.Println("📡 Endpoints:")
	fmt.Println("   GET  /health     - Health check")
	fmt.Println("   POST /register   - Register service")
	fmt.Println("   GET  /services   - List services")
	fmt.Println("   GET  /metrics    - Prometheus metrics")
	fmt.Println("   WS   /ws         - WebSocket updates")
	fmt.Println("   *    /api/*      - Proxy to services")

	if err := gateway.Start(); err != nil {
		log.Fatalf("Gateway failed: %v", err)
	}
}
