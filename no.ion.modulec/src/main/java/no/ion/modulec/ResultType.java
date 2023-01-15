package no.ion.modulec;

public enum ResultType {
    ERROR, NOOP, OK;

    public boolean success() { return this != ERROR; }
}
