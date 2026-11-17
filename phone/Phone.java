package mirror;
import android.app.*;
import android.os.*;
import android.content.*;
import android.bluetooth.*;
import android.media.projection.*;
import android.view.*;
import android.widget.*;
import java.util.*;

public class Phone extends Link {
    private String address;
    private TextView status;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        public void run() { status.setText(Cast.status); handler.postDelayed(this, 400); }
    };
    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (saved != null) address = saved.getString("address");
        LinearLayout layout = new LinearLayout(this);
        int pad = (int)(24 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad,pad,pad,pad);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setTextSize(32); title.setText("화면 미러"); layout.addView(title);
        TextView help = new TextView(this);
        help.setText("안경에서 수신 앱을 먼저 여세요. 연결할 안경을 선택한 후 공유할 화면을 정합니다.");
        help.setPadding(0,pad,0,pad); layout.addView(help);
        status = new TextView(this); layout.addView(status);
        Button start = new Button(this); start.setText("안경 선택 및 공유"); layout.addView(start);
        start.setOnClickListener(v -> { if (permitted()) choose(); });
        Button stop = new Button(this); stop.setText("공유 종료"); layout.addView(stop);
        stop.setOnClickListener(v -> stopService(new Intent(this, Cast.class)));
        setContentView(layout);
    }
    protected void onSaveInstanceState(Bundle b) { super.onSaveInstanceState(b); b.putString("address", address); }
    protected void onResume() { super.onResume(); refresh.run(); }
    protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
    public void onRequestPermissionsResult(int r,String[] p,int[] g) {
        super.onRequestPermissionsResult(r,p,g);
        if(r==1 && g.length>0 && g[0]==0) choose();
        else Cast.status="안경 연결에 근처 기기 권한이 필요합니다.";
    }
    private void choose() {
        if (Cast.active) { Cast.status="현재 공유를 먼저 종료하세요."; return; }
        BluetoothAdapter bt = adapter();
        if(bt == null || !bt.isEnabled()) { Cast.status="휴대폰 Bluetooth를 켜세요."; return; }
        ArrayList<BluetoothDevice> devices = new ArrayList<>(bt.getBondedDevices());
        if(devices.isEmpty()) { Cast.status="Hi Rokid에서 안경을 페어링한 뒤 다시 시도하세요."; return; }
        String[] names = new String[devices.size()];
        for(int i=0;i<names.length;i++) names[i]=devices.get(i).getName()+"\n"+devices.get(i).getAddress();
        new AlertDialog.Builder(this).setTitle("연결할 안경").setItems(names,(d,i)->{
            address=devices.get(i).getAddress();
            MediaProjectionManager mgr=getSystemService(MediaProjectionManager.class);
            startActivityForResult(mgr.createScreenCaptureIntent(),2);
        }).show();
    }
    protected void onActivityResult(int r,int code,Intent data) {
        super.onActivityResult(r,code,data);
        if(r!=2) return;
        if(code!=RESULT_OK || data==null || address==null) { Cast.status="화면 공유가 취소되었습니다."; return; }
        startForegroundService(new Intent(this,Cast.class).putExtra("address",address).putExtra("grant",data));
    }
}
