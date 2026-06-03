FROM amazoncorretto:25-alpine

ARG JAR_FILE=fraud-detection-engine-1.0.0.jar
ENV JAR_FILE=$JAR_FILE

COPY target/$JAR_FILE /app.jar

USER 1000
EXPOSE 8080
ENV TZ="Africa/Johannesburg"

ENTRYPOINT exec java $JAVA_OPTS -jar /app.jar
