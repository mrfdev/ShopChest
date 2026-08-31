package de.epiceric.shopchest.advertising;

import java.util.Locale;
import java.util.Objects;

/** Strict command grammar for the advertising feature's delegated routes. */
public sealed interface AdvertisingCommand {

    static AdvertisingCommand parse(String[] arguments) {
        Objects.requireNonNull(arguments, "arguments");
        if (arguments.length == 0) {
            throw invalid("Missing advertising command");
        }

        final String root = lower(arguments[0]);
        if ("admin".equals(root)) {
            return parseAdminCurrency(arguments);
        }
        if (!"advertise".equals(root)) {
            throw invalid("Expected /shops advertise");
        }
        if (arguments.length == 1) {
            return new Dashboard();
        }

        return switch (lower(arguments[1])) {
            case "pass" -> parsePass(arguments);
            case "confirm" -> {
                if (arguments.length != 3) {
                    throw invalid("Usage: /shops advertise confirm <nonce>");
                }
                yield new ConfirmRequest(arguments[2]);
            }
            case "status" -> {
                if (arguments.length != 2) {
                    throw invalid("Usage: /shops advertise status");
                }
                yield new Status();
            }
            case "cancel" -> {
                if (arguments.length != 2) {
                    throw invalid("Usage: /shops advertise cancel");
                }
                yield new Cancel();
            }
            default -> throw invalid("Unknown advertising command");
        };
    }

    private static AdvertisingCommand parsePass(String[] arguments) {
        if (arguments.length == 2) {
            return new PassPreview();
        }
        if (arguments.length == 4 && "confirm".equals(lower(arguments[2]))) {
            return new ConfirmPass(arguments[3]);
        }
        throw invalid("Usage: /shops advertise pass [confirm <nonce>]");
    }

    private static AdvertisingCommand parseAdminCurrency(String[] arguments) {
        if (arguments.length != 4
                || !"advertise".equals(lower(arguments[1]))
                || !"currency".equals(lower(arguments[2]))) {
            throw invalid("Usage: /shops admin advertise currency <status|capture|clear>");
        }
        return switch (lower(arguments[3])) {
            case "status" -> new AdminCurrencyStatus();
            case "capture" -> new AdminCurrencyCapture();
            case "clear" -> new AdminCurrencyClear();
            default -> throw invalid("Unknown advertising currency command");
        };
    }

    private static String requireNonce(String nonce) {
        Objects.requireNonNull(nonce, "nonce");
        if (nonce.length() < 16 || nonce.length() > 128) {
            throw invalid("Confirmation nonce has an invalid length");
        }
        for (int index = 0; index < nonce.length(); index++) {
            final char character = nonce.charAt(index);
            final boolean asciiLetterOrDigit = character >= 'a' && character <= 'z'
                    || character >= 'A' && character <= 'Z'
                    || character >= '0' && character <= '9';
            if (!asciiLetterOrDigit
                    && character != '-' && character != '_') {
                throw invalid("Confirmation nonce contains an invalid character");
            }
        }
        return nonce;
    }

    private static String lower(String value) {
        return Objects.requireNonNull(value, "command argument").toLowerCase(Locale.ROOT);
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    record Dashboard() implements AdvertisingCommand {
    }

    record PassPreview() implements AdvertisingCommand {
    }

    record ConfirmPass(String nonce) implements AdvertisingCommand {

        public ConfirmPass {
            nonce = requireNonce(nonce);
        }
    }

    record ConfirmRequest(String nonce) implements AdvertisingCommand {

        public ConfirmRequest {
            nonce = requireNonce(nonce);
        }
    }

    record Status() implements AdvertisingCommand {
    }

    record Cancel() implements AdvertisingCommand {
    }

    record AdminCurrencyStatus() implements AdvertisingCommand {
    }

    record AdminCurrencyCapture() implements AdvertisingCommand {
    }

    record AdminCurrencyClear() implements AdvertisingCommand {
    }
}
