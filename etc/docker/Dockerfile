FROM openjdk:8-jdk-alpine
#VOLUME /tmp
WORKDIR /opt/backend
COPY build/libs/blockchain-0.0.1-SNAPSHOT.jar blockchain-service.jar
EXPOSE 8124
ENTRYPOINT ["java", "-jar", "-Djava.security.egd=file:/dev/./urandom", "-Dspring.profiles.active=dev", "blockchain-service.jar"]
