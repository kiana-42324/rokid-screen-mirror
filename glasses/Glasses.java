package mirror;
import android.bluetooth.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;

public class Glasses extends Link {
    private TextView label;
    private ImageView screen;
    private FrameLayout content;
    private int turns;
    private volatile boolean closed;
    private BluetoothServerSocket server;
    private BluetoothSocket client;
    public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        FrameLayout layout=new FrameLayout(this);
        layout.setBackgroundColor(0xff000000);
        turns = getPreferences(MODE_PRIVATE).getInt("quarterTurns", 3);
        content = new FrameLayout(this);
        layout.addView(content, new FrameLayout.LayoutParams(1, 1, Gravity.CENTER));
        layout.addOnLayoutChangeListener((v,l,t,r,bt,ol,ot,or,ob) -> {
            if (r-l != or-ol || bt-t != ob-ot) fitContent(r-l, bt-t);
        });
        layout.setOnClickListener(v -> rotateContent());
        screen=new ImageView(this); screen.setScaleType(ImageView.ScaleType.FIT_CENTER);
        content.addView(screen);
        label=new TextView(this); label.setGravity(Gravity.CENTER);
        label.setTextColor(0xffaaffaa); label.setTextSize(20);
        content.addView(label); setContentView(layout);
        if(permitted()) listen();
    }
    private void fitContent(int width, int height) {
        boolean sideways = turns % 2 == 1;
        content.setLayoutParams(new FrameLayout.LayoutParams(sideways ? height : width,
                sideways ? width : height, Gravity.CENTER));
        content.setRotation(turns * 90f);
    }
    private void rotateContent() {
        turns = (turns + 1) % 4;
        getPreferences(MODE_PRIVATE).edit().putInt("quarterTurns", turns).apply();
        View parent = (View)content.getParent();
        fitContent(parent.getWidth(), parent.getHeight());
    }
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            rotateContent();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }
    public void onRequestPermissionsResult(int r,String[] p,int[] g) {
        super.onRequestPermissionsResult(r,p,g);
        if(g.length>0 && g[0]==0) listen(); else message("근처 기기 권한이 필요합니다.");
    }
    private void message(String text) {
        runOnUiThread(()->{ if(!closed) { screen.setImageDrawable(null); label.setText(text); } });
    }
    private void listen() {
        new Thread(()->{
            try {
                BluetoothAdapter bt=adapter();
                if(bt==null || !bt.isEnabled()) { message("Bluetooth를 켠 뒤 다시 여세요."); return; }
                synchronized(this) {
                    if(closed) return;
                    server=bt.listenUsingRfcommWithServiceRecord("Screen Mirror",CHANNEL);
                }
                while(!closed) {
                    message("↑ 위쪽\n휴대폰 연결 대기 중\n탭하여 방향 변경");
                    BluetoothSocket peer=server.accept();
                    synchronized(this) {
                        if(closed) { Link.close(peer); return; }
                        client=peer;
                    }
                    try { receive(peer); } catch(IOException disconnected) { /* Wait for the next session. */ }
                    finally { Link.close(peer); }
                }
            } catch(Exception failure) { message("수신을 시작할 수 없습니다.\nBluetooth와 권한을 확인하세요."); }
        },"glasses-input").start();
    }
    private void receive(BluetoothSocket peer) throws IOException {
        DataInputStream input=new DataInputStream(new BufferedInputStream(peer.getInputStream()));
        while(!closed) {
            int size=input.readInt();
            if(size<1 || size>LIMIT) throw new IOException("frame size");
            byte[] frame=new byte[size]; input.readFully(frame);
            BitmapFactory.Options bounds=new BitmapFactory.Options();
            bounds.inJustDecodeBounds=true;
            BitmapFactory.decodeByteArray(frame,0,size,bounds);
            if(bounds.outWidth<=0 || bounds.outHeight<=0 || Math.max(bounds.outWidth,bounds.outHeight)>640) throw new IOException("dimensions");
            Bitmap bitmap=BitmapFactory.decodeByteArray(frame,0,size);
            if(bitmap==null) throw new IOException("image");
            runOnUiThread(()->{ if(!closed) { label.setText(""); screen.setImageBitmap(bitmap); } });
        }
    }
    public synchronized void onDestroy() {
        closed=true; Link.close(client); Link.close(server); super.onDestroy();
    }
}
