package mirror;
import android.app.Activity;
import android.bluetooth.*;
import android.content.pm.PackageManager;
import android.os.Build;
import java.io.*;
import java.util.UUID;

abstract class Link extends Activity {
    static final UUID CHANNEL = UUID.fromString("91b0fabc-606b-40ad-b317-57fd28468c3f");
    static final int LIMIT = 512 * 1024;
    BluetoothAdapter adapter() {
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        return manager == null ? null : manager.getAdapter();
    }
    boolean permitted() {
        if (Build.VERSION.SDK_INT < 31 || checkSelfPermission("android.permission.BLUETOOTH_CONNECT") == PackageManager.PERMISSION_GRANTED) return true;
        requestPermissions(new String[]{"android.permission.BLUETOOTH_CONNECT"}, 1);
        return false;
    }
    static void close(Closeable resource) {
        if (resource != null) try { resource.close(); } catch (IOException ignored) {}
    }
}
