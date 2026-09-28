# MerelyMeSMP public Minecraft API

This is the minimum read-only contract needed before the website can publish plugin-backed combat statistics or ban records. It deliberately excludes administration and private moderation data.

## Ownership

- The Minecraft maintainer owns the plugin and endpoint implementation.
- The website maintainer owns the server-side proxy, validation, cache and public presentation.
- Neither side puts tokens, passwords, database credentials or private player data in Git.

## Required endpoints

All responses use HTTPS and JSON. Dates use UTC ISO 8601. A player is identified by UUID; names are display values and can change.

### GET /v1/leaderboard?season=current&limit=50

    {
      "generatedAt": "2026-08-31T13:30:00.000Z",
      "season": "season-1",
      "players": [
        {
          "uuid": "minecraft-uuid",
          "name": "PlayerName",
          "kills": 12,
          "deaths": 4,
          "playtimeMinutes": 360,
          "online": false,
          "updatedAt": "2026-08-31T13:29:00.000Z"
        }
      ]
    }

### GET /v1/players/{uuid}

Return the same verified public statistics for one player. Unknown UUIDs return 404. Never return an IP address, email, account token, inventory, location or private staff note.

### GET /v1/bans?limit=50

    {
      "generatedAt": "2026-08-31T13:30:00.000Z",
      "records": [
        {
          "id": "public-stable-id",
          "playerUuid": "minecraft-uuid",
          "playerName": "PlayerName",
          "reasonCategory": "cheating",
          "issuedAt": "2026-08-30T18:00:00.000Z",
          "expiresAt": null,
          "status": "active"
        }
      ]
    }

Allowed public statuses are active, expired and revoked. Use a broad public reason category; evidence, reporter identity, exact detection details, IP addresses and staff notes stay private.

## Security requirements

- Create one read-only token for the website. Store it only as a hosting secret and rotate it if exposed.
- Reject all write methods for this API. The website must never be able to ban, pardon, grant items or run commands.
- Apply a request timeout, response-size limit and rate limit. A practical starting point is 60 reads per minute per token.
- Return Cache-Control headers or allow the website to cache leaderboard data for 60 seconds and bans for 30 seconds.
- Validate every field and maximum length at the website boundary. Invalid data must produce an unavailable state, never partial invented values.

## Acceptance check

Before connection, provide the base HTTPS URL, a read-only token through a private secret channel and three redacted sample responses. The website maintainer then tests 200, 404, timeout and invalid-payload cases before enabling the public feed.
