package UDisk.DllInterface;

import java.nio.file.Path;

public enum IsLocalDisk {
    INSTANCE;

    static {
        System.load(Path.of("user/isLocalDisk.dll").toAbsolutePath().toString());
    }

    public native boolean isDiskNTFS(String disk);
}
