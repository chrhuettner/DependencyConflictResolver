#!/bin/bash
mvn clean package -f pom.xml
docker build -t dependencyconflictresolver:latest .
