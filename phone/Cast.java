package mirror;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.graphics.*;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import java.io.*;
import java.nio.ByteBuffer;

public class Cast extends Service {
    static volatile String status="공유 대기 중";
    static volatile boolean active;
    private final Handler main=new Handler(Looper.getMainLooper());
    private HandlerThread worker;
    private BluetoothSocket socket;
    private MediaProjection projection;
    private VirtualDisplay display;
    private volatile ImageReader reader;
    private Handler frames;
    private DataOutputStream stream;
    private int contentWidth, contentHeight;
    private volatile boolean ended;
    private boolean closing;
    private static final long FRAME_INTERVAL_MS = 80;
    private long nextFrameAt;
    private boolean frameScheduled;
    private int jpegQuality = 62;
    private final ByteArrayOutputStream encoded = new ByteArrayOutputStream(32768);
    private String finalStatus="공유가 종료되었습니다.";
    public IBinder onBind(Intent i) { return null; }
    public int onStartCommand(Intent i,int flags,int id) {
        if(i==null || "stop".equals(i.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if(active) return START_NOT_STICKY;
        active=true;
        try {
            NotificationManager manager=getSystemService(NotificationManager.class);
            manager.createNotificationChannel(new NotificationChannel("sharing","화면 공유",NotificationManager.IMPORTANCE_LOW));
            PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,Cast.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE);
            startForeground(1,new Notification.Builder(this,"sharing").setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("안경 화면 공유").setContentText("공유를 끝내려면 종료를 누르세요.")
                .setOngoing(true).addAction(android.R.drawable.ic_menu_close_clear_cancel,"종료",stop).build());
            MediaProjectionManager mgr=getSystemService(MediaProjectionManager.class);
            projection=mgr.getMediaProjection(Activity.RESULT_OK,(Intent)i.getParcelableExtra("grant"));
            projection.registerCallback(new MediaProjection.Callback() {
                public void onStop() { finishSession("시스템에서 화면 공유를 종료했습니다."); }
                @Override public void onCapturedContentResize(int width,int height) {
                    if (width <= 0 || height <= 0) return;
                    contentWidth = width;
                    contentHeight = height;
                    if (stream != null) configureCapture();
                }
            },main);
            BluetoothAdapter bt=getSystemService(BluetoothManager.class).getAdapter();
            socket=bt.getRemoteDevice(i.getStringExtra("address")).createRfcommSocketToServiceRecord(Link.CHANNEL);
            status="안경 수신 앱에 연결 중…";
            worker=new HandlerThread("frame-output"); worker.start();
            frames=new Handler(worker.getLooper());
            final BluetoothSocket peer=socket;
            frames.post(()->{
                try {
                    peer.connect();
                    DataOutputStream output=new DataOutputStream(peer.getOutputStream());
                    main.post(()->{ stream=output; configureCapture(); });
                } catch(Exception e) { main.post(()->finishSession("연결 실패 · 안경에서 수신 앱을 열고 다시 시도하세요.")); }
            });
        } catch(Exception e) { finishSession("시작 실패 · "+e.getClass().getSimpleName()); }
        return START_NOT_STICKY;
    }
    @Override public void onConfigurationChanged(android.content.res.Configuration config) {
        super.onConfigurationChanged(config);
        if (stream != null && contentWidth == 0) configureCapture();
    }
    private void configureCapture() {
        if (ended || closing) return;
        try {
            android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();
            getSystemService(android.view.WindowManager.class).getDefaultDisplay().getRealMetrics(metrics);
            int w=metrics.widthPixels,h=metrics.heightPixels;
            if (contentWidth > 0) { w=contentWidth; h=contentHeight; }
            float scale=640f/Math.max(w,h);
            w=Math.max(2,Math.round(w*scale)); h=Math.max(2,Math.round(h*scale));
            ImageReader previous=reader;
            if (previous != null && w==previous.getWidth() && h==previous.getHeight()) return;
            reader=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2);
            if (previous != null) {
                previous.setOnImageAvailableListener(null,null);
                frames.post(previous::close);
            }
            reader.setOnImageAvailableListener(r -> {
                if(ended || r!=reader || frameScheduled) return;
                frameScheduled=true;
                frames.postAtTime(this::sendLatestFrame, Math.max(SystemClock.uptimeMillis(), nextFrameAt));
            },frames);
            if (display == null) {
                display=projection.createVirtualDisplay("Phone screen",w,h,metrics.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,main);
            } else {
                display.setSurface(null);
                display.resize(w,h,metrics.densityDpi);
                display.setSurface(reader.getSurface());
            }
            status="화면 공유 중";
        } catch(Exception e) { finishSession("캡처 실패 · "+e.getClass().getSimpleName()); }
    }
    private void sendLatestFrame() {
        frameScheduled=false;
        if(ended) return;
        try {
            long start=SystemClock.uptimeMillis();
            try(Image frame=reader.acquireLatestImage()) {
                if(frame==null) return;
                encoded.reset();
                encodeFrame(frame);
            }
            nextFrameAt=start+FRAME_INTERVAL_MS;
            stream.writeInt(encoded.size());
            encoded.writeTo(stream);
            stream.flush();
            long elapsed=SystemClock.uptimeMillis()-start;
            if(elapsed>FRAME_INTERVAL_MS) jpegQuality=Math.max(42,jpegQuality-4);
            else if(elapsed<FRAME_INTERVAL_MS/2) jpegQuality=Math.min(62,jpegQuality+1);
        } catch(Exception e) { main.post(()->finishSession("전송이 끊겼습니다. 안경 연결을 확인하세요.")); }
    }
    private void encodeFrame(Image frame) throws IOException {
        Image.Plane plane=frame.getPlanes()[0];
        int width=frame.getWidth(),height=frame.getHeight();
        int stride=plane.getRowStride()/plane.getPixelStride();
        ByteBuffer pixels=plane.getBuffer();
        // The final scanline can omit padding.
        int required=stride*height*4;
        if(pixels.remaining()<required) {
            ByteBuffer padded=ByteBuffer.allocate(required);
            padded.put(pixels); padded.rewind(); pixels=padded;
        }
        Bitmap buffer=Bitmap.createBitmap(stride,height,Bitmap.Config.ARGB_8888);
        buffer.copyPixelsFromBuffer(pixels);
        Bitmap content=Bitmap.createBitmap(buffer,0,0,width,height);
        boolean compressed=content.compress(Bitmap.CompressFormat.JPEG,jpegQuality,encoded);
        if(content!=buffer) content.recycle();
        buffer.recycle();
        if(!compressed) throw new IOException("JPEG encoding failed");
    }
    private void finishSession(String message) {
        if(ended || closing) return;
        closing=true; finalStatus=message; stopSelf();
    }
    public void onDestroy() {
        ended=true; Link.close(socket);
        if(display!=null) display.release();
        if(projection!=null) projection.stop();
        if(reader!=null) {
            reader.setOnImageAvailableListener(null,null);
            new Handler(worker.getLooper()).post(reader::close);
        }
        if(worker!=null) worker.quitSafely();
        active=false; status=finalStatus; stopForeground(true); super.onDestroy();
    }
}

