# Kubernetes Thermometer Probe (`probe.siwko.org`)

A live, interactive temperature monitoring web application hosted on OpenLiberty in Kubernetes. The application queries real-time temperature readings from the `all_readings` table in the PostgreSQL `sdr433` database (`prod-postgres-rw:5432`) and presents telemetry graphs in Fahrenheit (°F) at **`probe.siwko.org`**.

---

## 🏛️ Architecture & Project Structure

```
c:\dev\kubernetes-thermometer\
├── app/                      # Java 25 / Jakarta EE 10 probe web application
│   ├── pom.xml               # Maven project config (Java 25)
│   ├── Dockerfile            # Multi-stage build producing lightweight init-container image
│   ├── Jenkinsfile           # Pipeline for building & deploying probe-app
│   └── src/
│       └── main/
│           ├── java/org/siwko/thermometer/
│           │   ├── api/
│           │   │   ├── ApplicationConfig.java
│           │   │   └── ProbeResource.java  # REST API endpoints
│           │   ├── dao/ReadingDao.java     # DB queries & JSON parsing
│           │   └── model/
│           │       ├── ProbeInfo.java      # Probe metadata & alias model
│           │       └── ReadingPoint.java   # Fahrenheit temperature data point
│           └── webapp/
│               ├── index.html              # Glassmorphic dashboard UI
│               ├── css/style.css           # Modern dark-mode styling
│               └── js/app.js               # Chart.js graph & live polling logic
├── app-server/               # OpenLiberty server definition
│   ├── Dockerfile            # OpenLiberty image with Postgres JDBC driver
│   ├── Jenkinsfile           # Pipeline for base server deployment
│   └── liberty/
│       └── server.xml        # Liberty features, SSL & jdbc/sdr433DS DataSource
├── k8s/                      # Kubernetes manifests
│   ├── probe-deployment.yaml # OpenLiberty deployment with probe-app init container
│   ├── probe-service.yaml    # Service exposing HTTP (9080) & HTTPS (9443)
│   └── probe-ingress.yaml    # Ingress routing probe.siwko.org to OpenLiberty
├── Jenkinsfile               # Orchestration pipeline for server & app builds
└── README.md
```

---

## ⚡ Key Features

- **Decoupled Server & App Deployments**:
  - The OpenLiberty server (`probe-server`) and probe web app (`probe-app`) are built and versioned separately.
  - The Kubernetes Deployment mounts an `emptyDir` volume at `/config/apps`.
  - The `probe-app` image runs as an init-container copying `probe.war` into `/config/apps`.
  - Application updates deploy new WAR versions onto the running OpenLiberty server without rebuilding the base server image.

- **Interactive Fahrenheit (°F) Graph**:
  - Displays temperature trends strictly in degrees Fahrenheit.
  - **Auto-Scaling Y-Axis**: Automatically calculates Y-axis bounds based on minimum/maximum temperatures in the active dataset.
  - **Live Reading Age Indicator**: Prominently overlays the age of the newest reading (*"Last reading: 12s ago"*) and updates every second.
  - **Probe Selection & Custom Naming**: Allows assigning friendly custom names (e.g. *"Patio Weather Station"*) to probes by `(model, id)`. Alias mappings persist in the `probe_names` table in PostgreSQL.
  - **Time Window Selection**: Quick filters for 15m, 30m, **1h (default)**, 6h, 12h, 24h, and 7d.
  - **Live Updates**: Automatic background polling every 5 seconds.

---

## 🔌 REST API Endpoints

- **`GET /api/probes`**: Returns all available probes with custom aliases, channels, and latest reading timestamps.
- **`POST /api/probes/name`**: Assigns/updates a custom friendly name for a probe.
  - Payload: `{ "model": "Acurite-5n1", "id": "976", "customName": "Patio Weather Station" }`
- **`GET /api/readings?model=...&id=...&window=1h`**: Returns timestamped temperature data points in °F for chart rendering.
- **`GET /api/health`**: Health status endpoint.

---

## 🛠️ Building & Deploying

### 1. Local Maven Build
Compile the Java 25 source code and package `probe.war`:
```bash
mvn -f app/pom.xml clean package
```

### 2. Docker Image Builds
Build Docker images targeting the local registry (`kregistry.siwko.org:5000`):

```bash
# Build base OpenLiberty server image
docker build -t kregistry.siwko.org:5000/probe-server:latest -f app-server/Dockerfile app-server

# Build probe application init-container image
docker build -t kregistry.siwko.org:5000/probe-app:latest -f app/Dockerfile app
```

### 3. Kubernetes Deployment
Apply the Kubernetes manifests to deploy to the cluster:
```bash
kubectl apply -f k8s/probe-service.yaml
kubectl apply -f k8s/probe-ingress.yaml
kubectl apply -f k8s/probe-deployment.yaml
```

### 4. CI/CD in Jenkins
Jenkins builds can be triggered from:
- `Jenkinsfile` (repository root): Builds both `probe-server` and `probe-app` and deploys to Kubernetes.
- `app/Jenkinsfile`: Rebuilds and deploys `probe-app` to update the application on the running server.
- `app-server/Jenkinsfile`: Rebuilds and redeploys the base OpenLiberty server.
