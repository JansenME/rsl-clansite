package com.rsl.clansite.exceptions;

public class DiscordClientException extends RuntimeException {
    public DiscordClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
