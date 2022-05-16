package UDisk.DllInterface;

import com.sun.jna.Library;
import com.sun.jna.Native;

public interface IsLocalDisk extends Library {
    IsLocalDisk INSTANCE = Native.load("isLocalDisk", IsLocalDisk.class);

    boolean isDiskNTFS(String disk);
}
