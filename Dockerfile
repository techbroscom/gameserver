FROM amazoncorretto:17-alpine-jdk

WORKDIR /app

# Copy the start script and libs from installDist output
# Assuming the user runs `./gradlew installDist` before building docker image
COPY build/install/gameserver/ /app/

EXPOSE 8080

ENTRYPOINT ["/app/bin/gameserver"]
