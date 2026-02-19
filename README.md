# Ktor Game Backend

A production-ready real-time multiplayer game backend built with Ktor, WebSockets, and Exposed.

## Tech Stack
- **Language:** Kotlin
- **Framework:** Ktor (Netty)
- **Database:** PostgreSQL + Exposed ORM
- **Cache:** Redis (Lettuce)
- **Real-time:** WebSockets
- **Auth:** JWT

## Setup

### Prerequisites
- Docker & Docker Compose
- JDK 17+

### Running the Infrastructure
Start PostgreSQL and Redis:
```bash
docker-compose up -d
```

### Running the Server
```bash
./gradlew run
```

The server will start on port `8080`.

## API Documentation

### Auth
- `POST /auth/register` - Create account
- `POST /auth/login` - Login
- `POST /auth/refresh` - Refresh token

### WebSockets
- `/game` - Main game connection (requires JWT)

## Game Types
- **BINGO**: 5x5 grid, first to 5 lines wins.
- **NUMBER_GUESS**: Guess a number between 1-100.
