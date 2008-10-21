/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.android.camera;
/*
*  This file derives from the Apache version of the BufferedInputStream.
*  Mods to support passing in the buffer rather than creating one directly.
*/
import java.io.InputStream;
import java.io.FilterInputStream;
import java.io.IOException;

public class BufferedInputStream extends FilterInputStream {
    protected byte[] buf;
    protected int count;
    protected int marklimit;
    protected int markpos = -1;
    protected int pos;

    private boolean closed = false;

    public BufferedInputStream(InputStream in, byte [] buffer) {
        super(in);
        buf = buffer;
        if (buf == null) {
            throw new java.security.InvalidParameterException();
        }
    }

    @Override
    public synchronized int available() throws IOException {
        return count - pos + in.available();
    }

    @Override
    public synchronized void close() throws IOException {
        if (null != in) {
            super.close();
            in = null;
        }
        buf = null;
        closed = true;
    }

    private int fillbuf() throws IOException {
        if (markpos == -1 || (pos - markpos >= marklimit)) {
            /* Mark position not set or exceeded readlimit */
            int result = in.read(buf);
            if (result > 0) {
                markpos = -1;
                pos = 0;
                count = result == -1 ? 0 : result;
            }
            return result;
        }
        if (markpos == 0 && marklimit > buf.length) {
            /* Increase buffer size to accomodate the readlimit */
            int newLength = buf.length * 2;
            if (newLength > marklimit) {
                newLength = marklimit;
            }
            byte[] newbuf = new byte[newLength];
            System.arraycopy(buf, 0, newbuf, 0, buf.length);
            buf = newbuf;
        } else if (markpos > 0) {
            System.arraycopy(buf, markpos, buf, 0, buf.length - markpos);
        }
        /* Set the new position and mark position */
        pos -= markpos;
        count = markpos = 0;
        int bytesread = in.read(buf, pos, buf.length - pos);
        count = bytesread <= 0 ? pos : pos + bytesread;
        return bytesread;
    }

    @Override
    public synchronized void mark(int readlimit) {
        marklimit = readlimit;
        markpos = pos;
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public synchronized int read() throws IOException {
        if (in == null) {
            // K0059=Stream is closed
            throw new IOException(); //$NON-NLS-1$
        }

        /* Are there buffered bytes available? */
        if (pos >= count && fillbuf() == -1) {
            return -1; /* no, fill buffer */
        }

        /* Did filling the buffer fail with -1 (EOF)? */
        if (count - pos > 0) {
            return buf[pos++] & 0xFF;
        }
        return -1;
    }

    @Override
    public synchronized int read(byte[] buffer, int offset, int length)
            throws IOException {
        if (closed) {
            // K0059=Stream is closed
            throw new IOException(); //$NON-NLS-1$
        }
        // avoid int overflow
        if (offset > buffer.length - length || offset < 0 || length < 0) {
            throw new IndexOutOfBoundsException();
        }
        if (length == 0) {
            return 0;
        }
        if (null == buf) {
            throw new IOException(); //$NON-NLS-1$
        }

        int required;
        if (pos < count) {
            /* There are bytes available in the buffer. */
            int copylength = count - pos >= length ? length : count - pos;
            System.arraycopy(buf, pos, buffer, offset, copylength);
            pos += copylength;
            if (copylength == length || in.available() == 0) {
                return copylength;
            }
            offset += copylength;
            required = length - copylength;
        } else {
            required = length;
        }

        while (true) {
            int read;
            /*
             * If we're not marked and the required size is greater than the
             * buffer, simply read the bytes directly bypassing the buffer.
             */
            if (markpos == -1 && required >= buf.length) {
                read = in.read(buffer, offset, required);
                if (read == -1) {
                    return required == length ? -1 : length - required;
                }
            } else {
                if (fillbuf() == -1) {
                    return required == length ? -1 : length - required;
                }
                read = count - pos >= required ? required : count - pos;
                System.arraycopy(buf, pos, buffer, offset, read);
                pos += read;
            }
            required -= read;
            if (required == 0) {
                return length;
            }
            if (in.available() == 0) {
                return length - required;
            }
            offset += read;
        }
    }

    @Override
    public synchronized void reset() throws IOException {
        if (closed) {
            // K0059=Stream is closed
            throw new IOException(); //$NON-NLS-1$    
        }
        if (-1 == markpos) {
            // K005a=Mark has been invalidated.
            throw new IOException(); //$NON-NLS-1$
        }
        pos = markpos;
    }

    @Override
    public synchronized long skip(long amount) throws IOException {
        if (null == in) {
            // K0059=Stream is closed
            throw new IOException(); //$NON-NLS-1$
        }
        if (amount < 1) {
            return 0;
        }

        if (count - pos >= amount) {
            pos += amount;
            return amount;
        }
        long read = count - pos;
        pos = count;

        if (markpos != -1) {
            if (amount <= marklimit) {
                if (fillbuf() == -1) {
                    return read;
                }
                if (count - pos >= amount - read) {
                    pos += amount - read;
                    return amount;
                }
                // Couldn't get all the bytes, skip what we read
                read += (count - pos);
                pos = count;
                return read;
            }
            markpos = -1;
        }
        return read + in.skip(amount - read);
    }
}
