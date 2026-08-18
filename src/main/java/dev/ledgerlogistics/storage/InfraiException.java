package dev.ledgerlogistics.storage;

public final class InfraiException extends RuntimeException {
    private final String code;
    private final int status;

    public InfraiException(String code, String message, int status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() { return code; }
    public int status() { return status; }
    public boolean isClientRejection() { return status >= 400 && status < 500 && status != 429; }
}
