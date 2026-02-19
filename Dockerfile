# Stage 1: Build the application
FROM amazoncorretto:17-alpine-jdk AS build
WORKDIR /app
COPY . .
RUN ./gradlew installDist --no-daemon

# Stage 2: Create the runtime image
FROM amazoncorretto:17-alpine-jdk
WORKDIR /app
# Copy the start script and libs from the build stage
COPY --from=build /app/build/install/gameserver/ .
EXPOSE 8080
ENTRYPOINT ["/app/bin/gameserver"]
