# Running the Code Examples

This guide explains how to run the enhanced code examples in this directory.

## Prerequisites

Make sure you have the following installed:

- Node.js 16+ (for JavaScript examples)
- Python 3.8+ (for Python examples)
- Go 1.19+ (for Go examples)
- Rust 1.65+ (for Rust examples)
- Java 11+ (for Java examples)
- Android Studio (for Kotlin examples)

## JavaScript Examples - Enhanced Real-time Analytics Dashboard

### Installation

1. Navigate to the code-examples directory:

   ```bash
   cd code-examples
   ```

2. Install dependencies:
   ```bash
   npm install
   ```

### Running the WebSocket Server

1. Start the WebSocket server:

   ```bash
   npm start
   ```

   The server will start on port 3001.

### Running the Dashboard Demo

1. Open `dashboard_demo.html` in a web browser

2. The dashboard should connect to the WebSocket server and start displaying real-time data

### Features Demonstrated

- Real-time data streaming via WebSocket
- Interactive charts using Chart.js
- Predictive analytics with linear regression and confidence scoring
- Anomaly detection using statistical methods
- Historical data analysis and model training
- Export functionality
- Security events monitoring
- Trend analysis

## Python Examples - Enhanced AI Code Reviewer

### Installation

1. Create a virtual environment:

   ```bash
   python -m venv venv
   ```

2. Activate the virtual environment:

   ```bash
   # On Windows
   venv\Scripts\activate

   # On macOS/Linux
   source venv/bin/activate
   ```

3. Install dependencies:
   ```bash
   pip install tensorflow transformers numpy
   ```

### Running the Example

1. Run the Python example:
   ```bash
   python python_example.py
   ```

### Features Demonstrated

- Static code analysis using AST
- Pattern-based vulnerability detection with confidence scoring
- AI-powered code complexity analysis
- Cyclomatic complexity calculation
- Security vulnerability detection
- Performance optimization suggestions
- Comprehensive reporting system with confidence scores
- Enhanced security pattern matching

## Go Examples - Enhanced Microservices API Gateway

### Installation

1. Make sure you have Go installed (1.19+)

2. Initialize Go modules:
   ```bash
   go mod init api-gateway
   go mod tidy
   ```

### Running the Example

1. Run the Go example:
   ```bash
   go run go_example.go
   ```

### Features Demonstrated

- Load balancing algorithms
- Service discovery and registration
- Real-time monitoring with Prometheus
- WebSocket support for live updates
- Security event monitoring
- Enhanced metrics collection
- Request security analysis

## Rust Examples - Enhanced Blockchain Simulator

### Installation

1. Make sure you have Rust installed (1.65+)

2. Create a new Rust project:

   ```bash
   cargo new blockchain-simulator
   cd blockchain-simulator
   ```

3. Copy the code to `src/main.rs`

4. Update `Cargo.toml` with dependencies:
   ```toml
   [dependencies]
   tokio = { version = "1.0", features = ["full"] }
   serde = { version = "1.0", features = ["derive"] }
   serde_json = "1.0"
   sha2 = "0.10"
   ```

### Running the Example

1. Run the Rust example:
   ```bash
   cargo run
   ```

### Features Demonstrated

- Blockchain consensus algorithms (PoW, PoS, DPoS)
- Smart contract virtual machine
- Network simulation
- Transaction processing
- Security event monitoring
- Smart contract security analysis
- Threat level tracking
- Enhanced security checks

## Java Examples - Enhanced Order Management System

### Installation

1. Make sure you have Java 11+ and Maven installed

2. Create a Spring Boot project with the required dependencies

### Running the Example

1. Run the Java example:
   ```bash
   mvn spring-boot:run
   ```

### Features Demonstrated

- Enterprise-grade architecture
- Event-driven design with Kafka
- Reactive programming with Project Reactor
- Comprehensive validation and security
- Security event monitoring
- Anomaly detection in orders
- Enhanced analytics with security metrics
- Confidence scoring for anomalies

## Kotlin Examples - Enhanced Cryptocurrency Portfolio Tracker

### Installation

1. Open in Android Studio

2. Build the project

### Running the Example

1. Run on emulator or device

### Features Demonstrated

- Modern UI with Jetpack Compose
- Clean Architecture pattern
- Dependency injection with Hilt
- Real-time cryptocurrency data
- Security event monitoring
- Portfolio analytics with risk assessment
- Enhanced security dashboard
- Anomaly score calculation

## Development Notes

### JavaScript Enhancements

The JavaScript example has been enhanced with:

1. Predictive analytics using linear regression with confidence scoring
2. Anomaly detection using statistical methods
3. Historical data analysis for model training
4. Real-time notifications for anomalies
5. Security events monitoring
6. Data export functionality
7. Trend analysis capabilities

### Python Enhancements

The Python example has been enhanced with:

1. Additional security vulnerability patterns with confidence scoring
2. Performance optimization detection
3. Code complexity analysis with cyclomatic complexity
4. Improved reporting with confidence scores
5. Enhanced pattern matching for security issues

### Go Enhancements

The Go example has been enhanced with:

1. Security event monitoring middleware
2. Enhanced metrics collection with security events
3. Request security analysis
4. Threat detection capabilities

### Rust Enhancements

The Rust example has been enhanced with:

1. Security monitoring system
2. Smart contract security analysis
3. Threat level tracking
4. Enhanced virtual machine security checks

### Java Enhancements

The Java example has been enhanced with:

1. Security monitoring service
2. Anomaly detection in orders
3. Enhanced analytics with security metrics
4. Confidence scoring for anomalies
5. Request validation with security checks

### Kotlin Enhancements

The Kotlin example has been enhanced with:

1. Security event monitoring
2. Portfolio analytics with risk assessment
3. Enhanced security dashboard
4. Anomaly score calculation
5. Security metrics visualization

## Troubleshooting

### WebSocket Connection Issues

If the dashboard cannot connect to the WebSocket server:

1. Ensure the server is running (`npm start`)
2. Check that port 3001 is not blocked by firewall
3. Verify the server URL in the dashboard configuration

### Python Dependencies

If you encounter issues with Python dependencies:

1. Ensure you're using Python 3.8 or higher
2. Try installing dependencies individually:
   ```bash
   pip install tensorflow
   pip install transformers
   pip install numpy
   ```

### Browser Compatibility

The dashboard demo requires a modern browser with ES6 module support:

- Chrome 61+
- Firefox 60+
- Safari 11+
- Edge 16+

## Contributing

These examples are continuously updated with:

- Latest technology versions
- Security patches
- Performance improvements
- New features and patterns
- Community feedback
- Enhanced security monitoring
- Improved analytics capabilities

Feel free to submit issues or pull requests for improvements.
