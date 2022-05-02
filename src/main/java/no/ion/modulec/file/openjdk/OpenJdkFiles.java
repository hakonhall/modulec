/*
 * Copyright (c) 2007, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package no.ion.modulec.file.openjdk;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

/**
 * This file is a verbatim extract inaccessible parts of OpenJDK's java.nio.file.Files class.
 */
public class OpenJdkFiles {
    private static final int BUFFER_SIZE = 8192;

    /** A slight variation of {@link java.nio.file.Files#readAllBytes(Path)}. */
    public static byte[] readBytes(SeekableByteChannel seekableByteChannel) throws IOException {
        try (SeekableByteChannel sbc = seekableByteChannel;
             InputStream in = Channels.newInputStream(sbc)) {
            // FileChannelImpl is inaccessible, so this code needs to be out-commented:
            // if (sbc instanceof FileChannelImpl)
            //     ((FileChannelImpl) sbc).setUninterruptible();
            long size = sbc.size();
            if (size > (long) Integer.MAX_VALUE)
                throw new OutOfMemoryError("Required array size too large");
            return read(in, (int)size);
        }
    }

    private static byte[] read(InputStream source, int initialSize) throws IOException {
        int capacity = initialSize;
        byte[] buf = new byte[capacity];
        int nread = 0;
        int n;
        for (;;) {
            // read to EOF which may read more or less than initialSize (eg: file
            // is truncated while we are reading)
            while ((n = source.read(buf, nread, capacity - nread)) > 0)
                nread += n;

            // if last call to source.read() returned -1, we are done
            // otherwise, try to read one more byte; if that failed we're done too
            if (n < 0 || (n = source.read()) < 0)
                break;

            // one more byte was read; need to allocate a larger buffer
            capacity = Math.max(OpenJdkArraysSupport.newLength(capacity,
                                                               1,       /* minimum growth */
                                                               capacity /* preferred growth */),
                                BUFFER_SIZE);
            buf = Arrays.copyOf(buf, capacity);
            buf[nread++] = (byte)n;
        }
        return (capacity == nread) ? buf : Arrays.copyOf(buf, nread);
    }
}
