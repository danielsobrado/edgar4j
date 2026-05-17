# Edgar4j Build & Deployment Work Log

**Last Updated:** February 14, 2026  
**Status:** In Progress - MongoDB Connection Issue in Docker

---

## Summary of Completed Work

### ✅ Phase 1: Java Compilation Fixes
- **Fixed 7 Java source files** with syntax errors:
  1. `OpenApiConfig.java` - Converted text block to string concatenation
  2. `Form4Transaction.java` - Switch expression → traditional switch
  3. `DownloadJobExecutor.java` - Switch expression → traditional switch
  4. `DownloadJobServiceImpl.java` - Dual switch expression fixes
  5. `StatementReconstructor.java` - Switch expression fix
  6. `XbrlParser.java` - Switch expression fix
  7. `NamespaceResolver.java` - Record to class conversion with proper accessor methods

- **Updated pom.xml:** Changed Java version from 21 to 17 (Spring Boot 4.0.1 compatibility)

- **Disabled Elasticsearch:** 
  - Commented out `spring-boot-starter-data-elasticsearch` in pom.xml
  - Updated `MasterIndexEntryRepository` to use `MongoRepository`
  - Updated `DailyMasterIndexRepository` to use `MongoRepository`
  - Replaced `ElasticsearchStartup.java` with stub implementation

### ✅ Phase 2: Maven Build Success
- **Build Status:** ✅ `[INFO] BUILD SUCCESS`
- **JAR Created:** `C:\code\edgar4j\target\edgar4j-0.0.1-SNAPSHOT.jar`
- **All 180 Java source files compiled successfully**

### ✅ Phase 3: Frontend Build
- **npm install:** ✅ Completed in `frontend/`
- **npm run build:** ✅ React app built successfully

### ✅ Phase 4: Docker Image & Container Creation
- **Docker Images Built:**
  - `edgar4j-backend` ✅
  - `edgar4j-frontend` ✅
  - `mongo:7.0` ✅

- **Docker Containers Running:**
  - `edgar4j-mongodb` ✅ Healthy on port 27017
  - `edgar4j-backend` ⚠️ Running but not healthy (see issues below)
  - `edgar4j-frontend` ⚠️ Running but unhealthy

---

## Current Issues & Blockers

### 🔴 Critical Issue: MongoDB Connection Not Working in Backend Container

**Problem:** Backend container is attempting to connect to `localhost:27017` instead of `mongodb:27017`

**Symptoms:**
- Backend logs show: `servers=[{address=localhost:27017, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}`
- Application fails to start - timeout waiting for MongoDB
- MongoTemplate bean creation fails

**Root Cause Analysis:**
- Spring Boot auto-configuration for MongoDB is not reading the configured host/port from application-docker.yml
- Attempted solutions that FAILED:
  1. ✗ Setting `SPRING_DATA_MONGODB_URI` env var in docker-compose
  2. ✗ Hardcoding `uri:` in application-docker.yml
  3. ✗ Using individual properties (host, port, username, password)
  4. ✗ JVM system properties via Dockerfile `-Dspring.data.mongodb.uri=...`
  
- **Spring Cloud Config interference suspected:** Config Server tries to connect to `http://localhost:8888` which fails, then defaults to localhost

**Latest Attempt:**
- Set MongoDB URI directly in Dockerfile ENTRYPOINT as JVM property:
  ```bash
  java $JAVA_OPTS -Dspring.data.mongodb.uri='mongodb://admin:admin123@mongodb:27017/edgar?authSource=admin' -jar app.jar
  ```
- Backend container started but status unknown (last attempt was in progress)

---

## Configuration Files Modified

### application.yml
```yaml
# Current state: Removed default MongoDB URI
spring:
  cloud:
    config:
      enabled: ${SPRING_CLOUD_CONFIG_ENABLED:false}
      fail-fast: false
  data:
    mongodb:
      database: ${MONGODB_DATABASE:edgar}
      auto-index-creation: ${MONGODB_AUTO_INDEX_CREATION:true}
      # NO uri or host set - should use profile-specific config
```

### application-docker.yml
```yaml
spring:
  cloud:
    config:
      enabled: false  # Explicitly disable Cloud Config in Docker
  data:
    mongodb:
      uri: mongodb://admin:admin123@mongodb:27017/edgar?authSource=admin
```

### docker-compose.yml
```yaml
backend:
  environment:
    SPRING_PROFILES_ACTIVE: docker
    SPRING_DATA_MONGODB_URI: mongodb://admin:admin123@mongodb:27017/edgar?authSource=admin
    SPRING_DATA_MONGODB_DATABASE: edgar
```

### Dockerfile.backend (ENTRYPOINT)
```dockerfile
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dspring.data.mongodb.uri='mongodb://admin:admin123@mongodb:27017/edgar?authSource=admin' -jar app.jar"]
```

---

## File Changes Summary

| File | Change | Reason |
|------|--------|--------|
| `pom.xml` | Elasticsearch dependency commented out | Not available in dev environment |
| `src/main/resources/application.yml` | Removed default MongoDB URI | Allow profile-specific config |
| `src/main/resources/application-docker.yml` | Added explicit MongoDB URI | Docker network uses `mongodb` hostname |
| `docker/Dockerfile.backend` | Added JVM property for MongoDB URI | System property approach |
| `docker/Dockerfile.frontend` | Fixed context paths | Corrected build context |
| `docker-compose.yml` | Updated frontend build context & environment vars | Proper Docker network setup |
| `src/main/java/org/jds/edgar4j/repository/*Repository.java` | Changed from Elasticsearch to Mongo | Align with disabled Elasticsearch |
| `src/main/java/org/jds/edgar4j/startup/ElasticsearchStartup.java` | Replaced with stub | Disable Elasticsearch initialization |

---

## Next Steps to Resolve MongoDB Issue

### Approach 1: Debug Environment Variable Loading
```bash
# Check what MongoDB URI the container is actually using
docker exec edgar4j-backend java -version
docker exec edgar4j-backend env | grep SPRING

# Verify DNS resolution
docker exec edgar4j-backend nslookup mongodb
# Expected: Should resolve to 172.18.0.2 (or similar)
```

### Approach 2: Check Health & Logs
```bash
# Full backend logs
docker logs edgar4j-backend 2>&1 | tail -200

# Check if application actually started
docker ps  # Check if container is still running
docker exec edgar4j-backend curl -s http://localhost:8080/actuator/health
```

### Approach 3: Simplify Configuration
If environment/YAML approaches continue to fail:
- Create a custom `MongoConfig` bean that explicitly uses `mongodb:27017`
- Override Spring Boot's auto-configuration completely
- Use a Java-based configuration instead of YAML

### Approach 4: Alternative Docker Setup
- Use environment variable substitution in shell before Java runs
- Parse environment in bootstrap or build properties
- Use Spring Cloud PropertySourceLocator custom implementation

---

## Testing Checklist

- [ ] Backend health endpoint responds: `GET http://localhost:8080/actuator/health`
- [ ] MongoDB connection verified in logs: "Successfully connected to MongoDB"
- [ ] Frontend loads: `GET http://localhost:3000`
- [ ] Frontend API calls to backend work
- [ ] No ERROR messages in docker logs

---

## Environment Details

**Installed Versions:**
- Java: JDK-21 (used for Docker) / JDK-17 (build target)
- Maven: 3.x (via mvnw wrapper)
- Node: 20.x (in Docker build)
- MongoDB: 7.0
- Docker: Desktop (installed but had connection issues earlier)

**Container Network:**
- Network: `edgar4j_edgar4j-network`
- MongoDB hostname: `mongodb` (resolves to 172.18.0.2)
- Backend hostname: `edgar4j-backend`
- Frontend hostname: `edgar4j-frontend`

---

## Known Working State

✅ **Backend:**
- JAR builds successfully
- Container starts (status "Up Xs (health: starting)")
- Network DNS resolution works
- Port 8080 exposed correctly

⚠️ **Frontend:**
- React build successful
- Container starts
- Nginx configured
- Status shows "unhealthy" (likely due to backend dependency)

⚠️ **MongoDB:**
- Container healthy
- Accessible on port 27017 locally
- Auth credentials: admin/admin123
- Database: edgar

---

## Command Reference for Restart

```bash
# 1. Check containers
docker ps

# 2. View recent logs
docker logs edgar4j-backend 2>&1 | tail -100

# 3. Restart containers
cd c:\code\edgar4j
docker-compose down
docker-compose up -d

# 4. Test health
curl -s http://localhost:8080/actuator/health

# 5. Check frontend
curl -s http://localhost:3000 | head -20

# 6. Rebuild if needed
docker-compose down
docker-compose build --no-cache edgar4j-backend
docker-compose up -d
```

---

## Git-Like Change History

1. **Initial Build Issues** → Fixed 7 Java syntax errors
2. **Java Version Mismatch** → Updated pom.xml to Java 17
3. **Elasticsearch Disabled** → Removed dependency, updated repos
4. **Docker Compose Setup** → Created containers
5. **MongoDB Connection Failure** → Attempting various config approaches
   - Environment variables ✗
   - YAML properties ✗
   - JVM system properties (in progress)

---

## Notes for Next Session

- **The core issue is Spring Boot property loading order** - profile-specific configs not overriding
- **Docker network DNS works** - `mongodb` hostname resolves correctly
- **The Dockerfile ENTRYPOINT approach** with JVM `-D` properties is the most promising
- **Spring Cloud Config** might be interfering - ensure `fail-fast: false` in application.yml
- **Last action:** Set JVM property in Dockerfile; need to verify if it worked

