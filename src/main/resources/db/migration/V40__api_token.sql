-- Personal API tokens for the REST API and the MCP server (docs/adr/0007-api-tokens-and-mcp.md). The table keeps the
-- SHA-256 hash of a token, never the token. Removing a user removes the tokens.
CREATE TABLE api_token (
    id           BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    name         VARCHAR(64)  NOT NULL,
    token_hash   CHAR(64)     NOT NULL UNIQUE,
    scope        VARCHAR(8)   NOT NULL CHECK (scope IN ('READ', 'WRITE')),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    last_used_at TIMESTAMP WITH TIME ZONE,
    CHECK (expires_at > created_at)
);

CREATE INDEX api_token_user ON api_token (user_id);
