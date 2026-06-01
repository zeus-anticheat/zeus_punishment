#!/bin/bash

echo "======================================"
echo "    Compiling ZeusPunishment"
echo "======================================"

# Build Core and Gateway (Maven)
echo "Building Java Core & Bukkit Gateway via Maven..."
mvn clean install

if [ $? -eq 0 ]; then
    echo "[SUCCESS] Maven Build Completed."
else
    echo "[ERROR] Maven Build Failed."
    exit 1
fi

# Build Fabric (Gradle)
echo "Building Fabric Gateway via Gradle..."
cd ZeusPunishmentFabric
gradle build

if [ $? -eq 0 ]; then
    echo "[SUCCESS] Gradle Build Completed."
else
    echo "[ERROR] Gradle Build Failed."
    exit 1
fi

# Collect Jars
echo "======================================"
echo "    Build Artifacts Collected"
echo "======================================"
mkdir -p ../out
cp ../ZeusPunishmentGateway/target/ZeusPunishmentGateway-1.0-SNAPSHOT.jar ../out/ZeusPunishment-Bukkit.jar
cp build/libs/ZeusPunishmentFabric-1.0-SNAPSHOT.jar ../out/ZeusPunishment-Fabric.jar
echo "Artifacts placed in /out directory."
