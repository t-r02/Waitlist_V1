@echo off
echo Starting Waitlist Platform...
echo.

echo Starting infrastructure (Kafka, Redis, MailHog)...
docker-compose up -d
echo.

echo Waiting for infrastructure to be ready...
timeout /t 15 /nobreak > nul
echo.

echo Starting Ingestion Service on port 8081...
start "Ingestion Service" cmd /k "cd ingestion-service && set JAVA_OPTS=-Xmx256m -Xms128m && ..\gradlew bootRun"
timeout /t 20 /nobreak > nul

echo Starting Admin Service on port 8082...
start "Admin Service" cmd /k "cd admin-service && set JAVA_OPTS=-Xmx256m -Xms128m && ..\gradlew bootRun"
timeout /t 20 /nobreak > nul

echo Starting Notification Service on port 8083...
start "Notification Service" cmd /k "cd notification-service && set JAVA_OPTS=-Xmx256m -Xms128m && ..\gradlew bootRun"

echo.
echo All services started!
echo.
echo MailHog UI: http://localhost:8025
echo Ingestion API: http://localhost:8081
echo Admin API: http://localhost:8082
echo Notification Service: http://localhost:8083
echo.
echo Press any key to exit...
pause > nul
