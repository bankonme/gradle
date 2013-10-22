/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.cache.internal.filelock;

import java.io.*;
import java.nio.channels.FileLock;

public class StateInfoAccess {
    private final LockStateSerializer protocol;
    private static final int REGION_START = 0;
    private static final int STATE_INFO_START = 1;
    private final int stateRegionSize;

    public StateInfoAccess(LockStateSerializer protocol) {
        this.protocol = protocol;
        stateRegionSize = STATE_INFO_START + protocol.getSize();
    }

    public LockState ensureLockState(RandomAccessFile lockFileAccess) throws IOException {
        if (lockFileAccess.length() == 0) {
            // File did not exist before locking
            return markDirty(lockFileAccess); //TODO SF add coverage that we're actually marking dirty here
        } else {
            return readState(lockFileAccess);
        }
    }

    public LockState markClean(RandomAccessFile lockFileAccess, int ownerId) throws IOException {
        LockState lockState = new LockState(ownerId, false);
        writeState(lockFileAccess, lockState);
        return lockState;
    }

    public LockState markDirty(RandomAccessFile lockFileAccess) throws IOException {
        LockState lockState = new LockState(LockState.UNKNOWN_PREVIOUS_OWNER, true);
        writeState(lockFileAccess, lockState);
        return lockState;
    }

    private void writeState(RandomAccessFile lockFileAccess, LockState lockState) throws IOException {
        ByteArrayOutputStream outstr = new ByteArrayOutputStream();
        DataOutputStream dataOutput = new DataOutputStream(outstr);
        dataOutput.writeByte(protocol.getVersion());
        dataOutput.flush();

        protocol.write(dataOutput, lockState);
        lockFileAccess.seek(REGION_START);
        lockFileAccess.write(outstr.toByteArray());
        assert lockFileAccess.getFilePointer() == stateRegionSize;
    }

    public LockState readState(RandomAccessFile lockFileAccess) throws IOException {
        try {
            byte[] buffer = new byte[stateRegionSize];
            lockFileAccess.seek(REGION_START);

            int readPos = 0;
            while (readPos < buffer.length) {
                int nread = lockFileAccess.read(buffer, readPos, buffer.length - readPos);
                if (nread < 0) {
                    break;
                }
                readPos += nread;
            }

            ByteArrayInputStream inputStream = new ByteArrayInputStream(buffer, 0, readPos);
            DataInputStream dataInput = new DataInputStream(inputStream);

            byte protocolVersion = dataInput.readByte();
            if (protocolVersion != protocol.getVersion()) {
                throw new IllegalStateException(String.format("Unexpected lock protocol found in lock file. Expected %s, found %s.", protocol.getVersion(), protocolVersion));
            }
            return protocol.read(dataInput);
        } catch (EOFException e) {
            return new LockState(LockState.UNKNOWN_PREVIOUS_OWNER, true);
        }
    }

    public FileLock tryLock(RandomAccessFile lockFileAccess, boolean shared) throws IOException {
        return lockFileAccess.getChannel().tryLock(REGION_START, stateRegionSize, shared);
    }

    public int getRegionEnd() {
        return stateRegionSize;
    }
}