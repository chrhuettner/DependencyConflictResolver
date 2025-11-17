FROM amazoncorretto:24
MAINTAINER "Christoph Hüttner (chrhuettner@edu.aau.at)"

WORKDIR /app
COPY target/DependencyConflictResolver-1.0-SNAPSHOT.jar app.jar
COPY Java_Src Java_Src
ENTRYPOINT ["java","-jar","/app/app.jar"]