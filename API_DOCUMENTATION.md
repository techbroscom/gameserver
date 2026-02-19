# Game Server API Documentation

## Authentication
Base URL: `/auth`

### Register
Create a new player account.
- **URL**: `/register`
- **Method**: `POST`
- **Body**: JSON
  ```json
  {
    "username": "player1",
    "password": "securepassword"
  }
  ```
- **Response**: `200 OK`
  ```json
  {
    "token": "jwt_token_here",
    "player": {
      "id": "uuid",
      "username": "player1",
      "level": 1
    }
  }
  ```

### Login
Authenticate an existing player.
- **URL**: `/login`
- **Method**: `POST`
- **Body**: JSON
  ```json
  {
    "username": "player1",
    "password": "securepassword"
  }
  ```
- **Response**: `200 OK`
  ```json
  {
    "token": "jwt_token_here",
    "player": {
      "id": "uuid",
      "username": "player1",
      "level": 1
    }
  }
  ```

---

## Player Management
Base URL: `/players`

### Get Player Stats
- **URL**: `/{id}/stats`
- **Method**: `GET`
- **Response**: `200 OK`
  ```json
  {
    "id": "uuid",
    "username": "player1",
    "coins": 100,
    "xp": 50,
    "level": 1,
    "elo": 1000,
    "gamesPlayed": 10,
    "wins": 5
  }
  ```

### Get Transactions
- **URL**: `/{id}/transactions`
- **Method**: `GET`
- **Query Params**:
  - `limit`: (Optional) Number of records (default 20)
  - `offset`: (Optional) Pagination offset (default 0)
- **Response**: `200 OK` (List of transactions)

---

## Matchmaking
Base URL: `/matchmaking`

### Join Queue
**Requires Authentication (Bearer Token)**
- **URL**: `/join`
- **Method**: `POST`
- **Body**: JSON
  ```json
  {
    "gameType": "BINGO",
    "minPlayers": 2,
    "maxPlayers": 2
  }
  ```
- **Response**: `200 OK`
  ```json
  {
    "status": "QUEUED"
  }
  ```

### Leave Queue
**Requires Authentication (Bearer Token)**
- **URL**: `/leave`
- **Method**: `POST`
- **Response**: `200 OK`
  ```json
  {
    "status": "LEFT_QUEUE"
  }
  ```

---

## Rooms & Games

### List Rooms
- **URL**: `/rooms`
- **Method**: `GET`
- **Query Params**:
  - `gameType`: (Optional) Filter by game type
- **Response**: `200 OK` (List of rooms)

### Get Room Details
- **URL**: `/rooms/{id}`
- **Method**: `GET`
- **Response**: `200 OK`

### List Game Types
- **URL**: `/games/types`
- **Method**: `GET`
- **Response**: `200 OK` (List of available game types)
  ```json
  ["BINGO"]
  ```

### Get Leaderboard
- **URL**: `/leaderboard`
- **Method**: `GET`
- **Query Params**:
  - `limit`: (Optional) Top N players (default 50)
- **Response**: `200 OK` (List of players with username, elo, wins)
