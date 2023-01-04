package no.ion.modulec.file;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static no.ion.modulec.util.Exceptions.uncheckIO;

public class UncheckedInputStream extends InputStream {
    private final InputStream inputStream;

    public UncheckedInputStream(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    @Override public int read(byte[] b) throws IOException { return inputStream.read(b); }
    @Override public int read(byte[] b, int off, int len) throws IOException { return inputStream.read(b, off, len); }
    @Override public byte[] readAllBytes() throws IOException { return inputStream.readAllBytes(); }
    @Override public byte[] readNBytes(int len) throws IOException {  return inputStream.readNBytes(len); }
    @Override public int readNBytes(byte[] b, int off, int len) throws IOException { return inputStream.readNBytes(b, off, len); }
    @Override public long skip(long n) throws IOException { return inputStream.skip(n); }
    @Override public void skipNBytes(long n) throws IOException { inputStream.skipNBytes(n); }
    @Override public int available() throws IOException { return inputStream.available(); }
    @Override public void close() { uncheckIO(inputStream::close); }
    @Override public synchronized void mark(int readlimit) { inputStream.mark(readlimit); }
    @Override public synchronized void reset() throws IOException { inputStream.reset(); }
    @Override public boolean markSupported() { return inputStream.markSupported(); }
    @Override public long transferTo(OutputStream out) throws IOException { return inputStream.transferTo(out); }
    @Override public int read() throws IOException { return inputStream.read(); }
}

