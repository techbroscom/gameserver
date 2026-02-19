# Frontend Integration Guide

This document outlines the API endpoints and WebSocket protocol for the Game Server.

## 1. HTTP API (REST)

Base URL: `http://<server>:8080`

### Authentication
**POST** `/auth/register`
```json
{ "username": "player1", "password": "password" }
```
Response:
```json
{ "token": "jwt_token", "player": { "id": "...", "username": "...", "level": 1 } }
```

**POST** `/auth/login`
```json
{ "username": "player1", "password": "password" }
```
Response: same as register.
**Note:** valid JWT token is required for all subsequent authenticated requests.

### Player
**GET** `/players/{id}/stats`
Returns: `Player` object with stats (coins, elo, etc).

**GET** `/players/{id}/transactions`
Query: `limit`, `offset`
Returns: List of transactions.

### Rooms & Matchmaking
**POST** `/matchmaking/join` (Auth Required)
Body: `{ "gameType": "BINGO", "minPlayers": 2, "maxPlayers": 2 }`

**GET** `/rooms`
Query: `gameType`
Returns: List of active rooms.

---

## 2. WebSocket Protocol (Real-time)

**Endpoint:** `ws://<server>:8080/game`
**Authentication:** You must authenticate the WebSocket connection. The server expects the JWT token to be validated during the handshake. 
*Implementation Note:* The current server implementation (`WebSockets.kt`) looks for the principal in the call, meaning you likely need to pass the token in the **Authorization header** (Bearer token) if your client supports it, or as a query parameter if the auth plugin is configured for it. *Check specific client library support for headers on WS handshake.*

### Message Structure

All messages are JSON objects.
**Discriminator:** The field `type` determines the message kind.

**Client -> Server (Request)**
Base fields:
- `type`: String (Discriminator)
- `requestId`: String (Unique UUID for tracking responses)

**Server -> Client (Response/Event)**
Base fields:
- `type`: String (Discriminator)
- `timestamp`: Long (Epoch millis)
- `requestId`: String (Optional, matches request if applicable)

---

### Client Messages (Requests)

#### 1. Join Lobby
Type: `JOIN_LOBBY`
```json
{
  "type": "JOIN_LOBBY",
  "requestId": "req-1",
  "jwt": "optional_if_already_authed" 
}
```

#### 2. Create Room
Type: `CREATE_ROOM`
```json
{
  "type": "CREATE_ROOM",
  "requestId": "req-2",
  "name": "My Room",
  "gameType": "BINGO",
  "maxPlayers": 4,
  "isPrivate": false,
  "password": null,
  "entryFee": 50
}
```

#### 3. Join Room
Type: `JOIN_ROOM`
```json
{
  "type": "JOIN_ROOM",
  "requestId": "req-3",
  "roomId": "room-uuid",
  "password": null
}
```

#### 4. List Rooms
Type: `LIST_ROOMS`
```json
{
  "type": "LIST_ROOMS",
  "requestId": "req-4",
  "gameType": "BINGO" 
}
```

#### 5. Leave Room
Type: `LEAVE_ROOM`
```json
{
  "type": "LEAVE_ROOM",
  "requestId": "req-5"
}
```

#### 6. Start Game (Host only)
Type: `START_GAME`
```json
{
  "type": "START_GAME",
  "requestId": "req-6"
}
```

#### 7. Send Game Event (In-Game Move)
Type: `SEND_EVENT`
```json
{
  "type": "SEND_EVENT",
  "requestId": "req-7",
  "opCode": 101, 
  "payload": { "x": 10, "y": 20, "cardId": 5 } 
}
```
*Note: `opCode` depends on the specific game rules.*

#### 8. Ping (Heartbeat)
Type: `PING`
```json
{
  "type": "PING",
  "requestId": "ping-1"
}
```

---

### Server Messages (Responses & Events)

#### 1. Error
Type: `ERROR`
```json
{
  "type": "ERROR",
  "requestId": "req-1",
  "code": 4001,
  "message": "Room is full",
  "timestamp": 123456789
}
```

#### 2. Room List Response
Type: `ROOM_LIST`
```json
{
  "type": "ROOM_LIST",
  "requestId": "req-4",
  "rooms": [ { "id": "...", "name": "...", "state": "WAITING" } ],
  "timestamp": 123456789
}
```

#### 3. Room Joined (Success)
Type: `ROOM_JOINED`
```json
{
  "type": "ROOM_JOINED",
  "requestId": "req-3",
  "room": { "id": "...", "currentPlayers": 1, "maxPlayers": 4, ... },
  "timestamp": 123456789
}
```

#### 4. Player Joined Room (Broadcast)
Type: `PLAYER_JOINED`
```json
{
  "type": "PLAYER_JOINED",
  "player": { "id": "p2", "username": "player2", "level": 5 },
  "timestamp": 123456789
}
```

#### 5. Game Started
Type: `GAME_STARTED`
```json
{
  "type": "GAME_STARTED",
  "initialDelta": { ...game_state... },
  "timestamp": 123456789
}
```

#### 6. Game Event (Broadcast)
Type: `EVENT`
```json
{
  "type": "EVENT",
  "senderId": "p2",
  "opCode": 101,
  "payload": { "x": 10, "y": 20 },
  "timestamp": 123456789
}
```

#### 7. Pong (Heartbeat Response)
Type: `PONG`
```json
{
  "type": "PONG",
  "requestId": "ping-1",
  "timestamp": 123456789
}
```

## 3. Standard Game Flow

1.  **Connect** to `wss://.../game` with Auth Header.
2.  **Send `JOIN_LOBBY`** (Optional, depending on UI needs).
3.  **Find a match**:
    *   *Option A:* Browse rooms with `LIST_ROOMS` and send `JOIN_ROOM`.
    *   *Option B:* Create a room with `CREATE_ROOM` and wait for players.
    *   *Option C:* Use HTTP `/matchmaking/join` endpoint to enter auto-queue.
4.  **Wait for Game Start**:
    *   Listen for `PLAYER_JOINED` events.
    *   If Host: Send `START_GAME` when ready.
    *   If Guest: Wait for `GAME_STARTED` message.
5.  **Game Loop**:
    *   Send `SEND_EVENT` for moves.
    *   Listen for `EVENT` messages from other players.
    *   Handle `GAME_OVER` message.

---

## 4. Bingo Game Specifics

**Game Type:** `BINGO`

### 4.1. Game State Structure
The `initialDelta` in `GAME_STARTED` and `payload` in `EVENT` (for state sync) will contain:

**Board Representation:**
- `board`: Array[25] of Integers (1-25, shuffled).
- `markedIndexes`: Array of Integers (indices 0-24 that are marked).
- `completedLines`: Array of Strings (e.g., "ROW_0", "COL_4", "DIAG_TL_BR").
- `bingoLetters`: Array of Strings (e.g., ["B", "I", "N"] for 3 lines).
- `lineCount`: Integer (0-5).

### 4.2. Specific Events

#### Client -> Server: Call Number
**Type:** `SEND_EVENT`
**OpCode:** `10`
**Payload:**
```json
{
  "number": 15
}
```
*Rule:* Only the player whose turn it is can call a number.

#### Server -> Client: Number Called (Broadcast)
**Type:** `EVENT`
**OpCode:** `10`
**Payload:**
```json
{
  "number": 15,
  "calledBy": "player-uuid",
  "boardUpdates": {
    "player-1-uuid": {
       "markedIndexes": [0, 5],
       "completedLines": [],
       "bingoLetters": [],
       "lineCount": 0
    },
    "player-2-uuid": { ... }
  }
}
```
*UI Action:* Highlight the number 15 on all boards. Update the "marked" status and "lines" for each player based on `boardUpdates` to keep UI in sync.

### 4.3. Game Over Data
**Type:** `GAME_OVER`
**Result Payload:**
```json
{
  "winnerIds": ["player-uuid"],
  "loserIds": ["opponent-uuid"],
  "rankings": [
    { "playerId": "player-uuid", "rank": 1, "score": 5 },
    { "playerId": "opponent-uuid", "rank": 2, "score": 3 }
  ],
  "coinDeltas": {
    "player-uuid": 100,
    "opponent-uuid": -20
  },
  "xpDeltas": { ... },
  "summary": {
    "totalNumbersCalled": 42
  }
}
```
