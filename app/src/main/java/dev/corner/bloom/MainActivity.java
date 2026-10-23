package dev.corner.bloom;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final int BG=0xff101119,CARD=0xff1b1c29,INK=0xfff2efff,MUTED=0xffa6a4b8,PURPLE=0xffb6a1ff;
    private Prefs prefs;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private List<AppEntry> all=new ArrayList<>();
    private BloomView practice;
    private TextView status,appsSummary,bootHint,tapHint;
    private Button start;
    private boolean ready;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private EditText holdTest;
    private View calibration;
    private WindowManager calibrationWm;
    private int calibrationGeneration;
    private boolean foreground;
    private final Runnable refreshRuntime=new Runnable(){
        public void run(){if(!foreground)return;refreshStatus();refreshTapHint();ui.postDelayed(this,1000);}
    };
    int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);prefs=new Prefs(this);prefs.migrateInputV3();prefs.recoverTapMode();
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        makeScreen();
        applyRecentsPreference();
        worker.execute(()->{
            List<AppEntry> loaded=AppEntry.load(this);
            runOnUiThread(()->{if(isDestroyed())return;all=loaded;ready=true;refreshApps();refreshStatus();});
        });
    }
    private GradientDrawable background(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView text(String value,int size,int color){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(3),1);return t;
    }
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout card(LinearLayout parent){
        LinearLayout c=column();c.setPadding(dp(18),dp(18),dp(18),dp(18));c.setBackground(background(CARD,24));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(14);parent.addView(c,p);return c;
    }
    private void title(LinearLayout parent,String s){TextView t=text(s,18,INK);t.setTypeface(null,Typeface.BOLD);parent.addView(t);}
    private void gap(LinearLayout p,int h){p.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));}
    private Button button(String label,boolean primary){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(primary?0xff211938:INK);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primary?PURPLE:0xff343344));
        b.setMinHeight(dp(48));return b;
    }
    private void makeScreen(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        LinearLayout content=column();content.setPadding(dp(20),dp(22),dp(20),dp(28));content.setFocusableInTouchMode(true);content.requestFocus();scroll.addView(content);
        if(Build.VERSION.SDK_INT>=30){
            scroll.setOnApplyWindowInsetsListener((v,insets)->{
                android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                v.setPadding(b.left,b.top,b.right,b.bottom);return insets;
            });
        }
        TextView eyebrow=text("CORNER BLOOM  /  0.2.1",11,PURPLE);eyebrow.setLetterSpacing(.17f);content.addView(eyebrow);gap(content,9);
        TextView name=text("角落绽放",32,INK);name.setTypeface(null,Typeface.BOLD);content.addView(name);gap(content,7);
        content.addView(text("轻轻一划，让常用应用从指尖散开。",14,MUTED));gap(content,22);

        LinearLayout setup=card(content);title(setup,"让入口就位");gap(setup,8);
        status=text("正在读取本机应用…",13,MUTED);setup.addView(status);gap(setup,12);
        start=button("开启角落手势",true);setup.addView(start,new LinearLayout.LayoutParams(-1,dp(50)));
        start.setOnClickListener(v->toggleService());
        Button restore=button("入口不见了？恢复入口",false);setup.addView(restore,new LinearLayout.LayoutParams(-1,dp(48)));
        restore.setOnClickListener(v->startOverlay(OverlayService.RESTORE));
        Button permission=button("悬浮窗权限",false);setup.addView(permission,new LinearLayout.LayoutParams(-1,dp(48)));
        permission.setOnClickListener(v->openOverlayPermission());
        gap(setup,5);setup.addView(text("入口默认在右下角，底部预留 36 dp。若与系统手势冲突，可以在下方挪动入口。",12,MUTED));
        setup.addView(text("恢复入口会重新挂载悬浮窗。其他悬浮窗可能遮住亮条；安全页面也可能由系统暂时隐藏悬浮窗，离开后再试。",12,MUTED));

        LinearLayout background=card(content);title(background,"后台与启动");gap(background,8);
        Switch hide=preferenceSwitch(background,"在最近任务中隐藏","hide_recents",false);
        hide.setOnCheckedChangeListener((v,checked)->{prefs.set("hide_recents",checked);applyRecentsPreference();});
        background.addView(text("隐藏应用设置页的最近任务卡片；仍可通过桌面图标或运行通知打开。",12,MUTED));gap(background,8);
        Switch boot=preferenceSwitch(background,"开机自动启动","start_on_boot",false);
        boot.setOnCheckedChangeListener((v,checked)->{
            prefs.set("start_on_boot",checked);refreshBootHint();
            if(checked&&!Settings.canDrawOverlays(this))Toast.makeText(this,"请先允许悬浮窗权限，开机后才能显示入口",Toast.LENGTH_LONG).show();
            if(checked&&Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},42);
        });
        bootHint=text("",12,MUTED);background.addView(bootHint);refreshBootHint();gap(background,8);
        Button systemSettings=button("打开系统应用设置",false);background.addView(systemSettings,new LinearLayout.LayoutParams(-1,dp(48)));
        systemSettings.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));

        LinearLayout tapCard=card(content);title(tapCard,"轻点与上滑分开处理");gap(tapCard,8);
        Switch pass=preferenceSwitch(tapCard,"轻点穿透（需无障碍）","tap_through",false);
        pass.setOnCheckedChangeListener((v,checked)->{
            prefs.set("tap_through",checked);refreshTapHint();
            if(checked&&!TapAccessibilityService.available())startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });
        Switch hold=preferenceSwitch(tapCard,"按住删除键连续删（免 Root）","hold_delete",false);
        hold.setEnabled(Build.VERSION.SDK_INT>=33);
        hold.setOnCheckedChangeListener((v,checked)->{
            prefs.set("hold_delete",checked);
            if(checked){
                boolean opening=!pass.isChecked();pass.setChecked(true);
                if(!opening&&!TapAccessibilityService.available())startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
            TapAccessibilityService.updateDeleteMode(checked);refreshTapHint();
        });
        tapCard.addView(text("适用于底部固定的文字键盘。开启后先标记删除键位置；在入口覆盖删除键的区域按住即可连续删除，松手停止，滑动取消。切换符号、表情等布局后需重新标记。",12,MUTED));gap(tapCard,8);
        holdTest=new EditText(this);holdTest.setTextColor(INK);holdTest.setTextSize(14);
        holdTest.setText("在这里测试长按删除，松手后应该停止。");holdTest.setSingleLine(false);
        holdTest.setMinLines(2);tapCard.addView(holdTest,new LinearLayout.LayoutParams(-1,-2));
        Button mark=button("标记键盘删除键",false);mark.setEnabled(Build.VERSION.SDK_INT>=33);
        tapCard.addView(mark,new LinearLayout.LayoutParams(-1,dp(48)));
        mark.setOnClickListener(v->{
            if(!hold.isChecked()){hold.setChecked(true);if(!TapAccessibilityService.available())return;}
            beginCalibration();
        });
        tapHint=text("",12,MUTED);tapCard.addView(tapHint);refreshTapHint();gap(tapCard,8);
        Button accessibility=button("打开无障碍设置",false);tapCard.addView(accessibility,new LinearLayout.LayoutParams(-1,dp(48)));
        accessibility.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        LinearLayout preview=card(content);title(preview,"先在这里试一划");gap(preview,5);
        preview.addView(text("练习区只提示选择结果，放心试手感。",12,MUTED));gap(preview,12);
        practice=new BloomView(this,prefs,false,new BloomView.Host(){
            public void expand(){} public void collapse(){}
            public void launch(AppEntry app){Toast.makeText(MainActivity.this,"已选中「"+app.name+"」",Toast.LENGTH_SHORT).show();}
        });
        practice.setBackground(background(0xff12131e,18));
        preview.addView(practice,new LinearLayout.LayoutParams(-1,dp(390)));

        LinearLayout apps=card(content);title(apps,"你的常用应用");gap(apps,7);
        appsSummary=text("正在读取…",13,MUTED);apps.addView(appsSummary);gap(apps,10);
        Button choose=button("选择应用 · 最多 15 个",true);apps.addView(choose,new LinearLayout.LayoutParams(-1,dp(48)));
        choose.setOnClickListener(v->{if(ready)showPicker();});
        Button order=button("调整排列顺序",false);apps.addView(order,new LinearLayout.LayoutParams(-1,dp(48)));
        order.setOnClickListener(v->{if(ready)showOrder();});

        LinearLayout position=card(content);title(position,"把入口挪到顺手的位置");gap(position,9);
        slider(position,"距右边", "right",0,180,0,"dp");
        slider(position,"距底部", "bottom",24,260,36,"dp");
        slider(position,"触发宽度", "width",8,100,16,"dp");
        slider(position,"触发高度", "height",40,180,88,"dp");
        toggle(position,"显示入口亮条","guide",true);
        gap(position,6);position.addView(text("默认窄入口。开启穿透并授权后，入口覆盖在键盘上，保持原位置；未开启时仍由键盘优先接收点击。",12,MUTED));

        LinearLayout motion=card(content);title(motion,"调出你的手感");gap(motion,9);
        slider(motion,"展开范围","spread",80,115,100,"%");
        slider(motion,"旋转与回弹","motion",0,150,100,"%");
        slider(motion,"局部雾效 · 0% 关闭","fog_strength",0,50,22,"%");
        slider(motion,"雾效边缘柔和度","fog_feather",60,180,120,"dp");
        toggle(motion,"滑过图标时轻震","haptic",true);
        gap(motion,8);motion.addView(text("上划展开 → 滑到图标 → 松手打开\n滑回角落，或在没有高亮图标时松手，即可取消。",13,MUTED));

        TextView footer=text("为单手操作而做 · 本地运行\n首次在一加手机上使用，建议先保留系统手势，通过位置设置避开；如果容易关闭，也可以关闭原有手势。",12,MUTED);
        footer.setGravity(Gravity.CENTER);content.addView(footer);setContentView(scroll);
    }
    private void slider(LinearLayout parent,String label,String key,int min,int max,int fallback,String unit){
        TextView value=text(label+"  "+prefs.get(key,fallback)+" "+unit,13,INK);parent.addView(value);
        SeekBar bar=new SeekBar(this);bar.setMax(max-min);bar.setProgress(prefs.get(key,fallback)-min);
        parent.addView(bar,new LinearLayout.LayoutParams(-1,dp(40)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int n,boolean from){value.setText(label+"  "+(n+min)+" "+unit);}
            public void onStartTrackingTouch(SeekBar s){}
            public void onStopTrackingTouch(SeekBar s){prefs.set(key,s.getProgress()+min);practice.reset();refreshApps();}
        });
    }
    private void toggle(LinearLayout parent,String label,String key,boolean fallback){
        Switch s=new Switch(this);s.setText(label);s.setTextColor(INK);s.setTextSize(13);s.setChecked(prefs.get(key,fallback));
        parent.addView(s,new LinearLayout.LayoutParams(-1,dp(46)));
        s.setOnCheckedChangeListener((v,checked)->{prefs.set(key,checked);practice.invalidate();});
    }
    private Switch preferenceSwitch(LinearLayout parent,String label,String key,boolean fallback){
        Switch s=new Switch(this);s.setText(label);s.setTextColor(INK);s.setTextSize(13);s.setChecked(prefs.get(key,fallback));
        parent.addView(s,new LinearLayout.LayoutParams(-1,dp(48)));return s;
    }
    private void applyRecentsPreference(){
        ActivityManager manager=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
        boolean hidden=prefs.get("hide_recents",false);
        try{for(ActivityManager.AppTask task:manager.getAppTasks())task.setExcludeFromRecents(hidden);}
        catch(RuntimeException e){android.util.Log.w("CornerBloom","Unable to update recent task visibility",e);}
    }
    private void refreshBootHint(){
        if(bootHint==null)return;
        String hint=!prefs.get("start_on_boot",false)?"开启后，在开机并解锁后自动恢复悬浮入口。":
                !Settings.canDrawOverlays(this)?"请先允许悬浮窗权限，自启动才能显示入口。":
                prefs.get("boot_failed",false)?"上次自动启动未能完成，请检查悬浮窗权限及系统的自启动、后台运行设置。":
                "已开启：开机并解锁后自动恢复入口。若未生效，请在系统应用设置中允许自启动和后台运行。";
        bootHint.setText(hint);
    }
    private void refreshTapHint(){
        if(tapHint==null)return;
        String hint=!prefs.get("tap_through",false)?"开启并授权后，轻点入口会点击下层同一位置，上滑仍展开。入口保持原位置，可显示在键盘上。":
                !TapAccessibilityService.available()?TapAccessibilityService.connectionHint():
                "轻点服务已就绪，上滑仍展开。\n"+TapAccessibilityService.lastTapHint();
        if(prefs.get("hold_delete",false))hint+="\n"+(prefs.get("delete_w",0)==0?"长按删除：请先标记删除键":
                "长按删除：已标记；键盘显示且入口覆盖删除键时生效")+"\n"+TapAccessibilityService.lastDeleteHint();
        tapHint.setText(hint);
    }
    private void beginCalibration(){
        if(!Settings.canDrawOverlays(this)){openOverlayPermission();return;}
        if(!TapAccessibilityService.available()){
            Toast.makeText(this,"请先开启点击穿透的无障碍服务，再回来标记",Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));return;
        }
        holdTest.requestFocus();holdTest.setSelection(holdTest.length());
        ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE))
                .showSoftInput(holdTest,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        int request=++calibrationGeneration;
        ui.postDelayed(()->waitForKeyboard(request,0),150);
    }
    private void waitForKeyboard(int request,int attempt){
        if(request!=calibrationGeneration||!foreground||isFinishing()||Build.VERSION.SDK_INT<33)return;
        calibrationWm=(WindowManager)getApplicationContext().getSystemService(WINDOW_SERVICE);
        WindowInsets insets;
        try{insets=calibrationWm.getCurrentWindowMetrics().getWindowInsets();}
        catch(RuntimeException e){Toast.makeText(this,"暂时无法检测键盘，请重新调出键盘后再试",Toast.LENGTH_LONG).show();return;}
        if(!insets.isVisible(WindowInsets.Type.ime())||insets.getInsets(WindowInsets.Type.ime()).bottom<=0){
            if(attempt<15)ui.postDelayed(()->waitForKeyboard(request,attempt+1),100);
            else Toast.makeText(this,"请点测试文本调出键盘，再点标记按钮",Toast.LENGTH_LONG).show();
            return;
        }
        showCalibration();
    }
    private void showCalibration(){
        if(calibration!=null)return;
        calibration=new View(this){
            final android.graphics.Paint p=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(android.graphics.Canvas c){
                p.setColor(0xf2232130);c.drawRoundRect(dp(16),dp(54),getWidth()-dp(16),dp(145),dp(18),dp(18),p);
                p.setColor(INK);p.setTextSize(dp(16));c.drawText("点一下键盘删除键的中心",dp(30),dp(88),p);
                p.setColor(MUTED);p.setTextSize(dp(12));c.drawText("只记录位置，不会删除文字",dp(30),dp(116),p);
                p.setColor(PURPLE);p.setTextAlign(android.graphics.Paint.Align.RIGHT);
                c.drawText("取消",getWidth()-dp(28),dp(135),p);p.setTextAlign(android.graphics.Paint.Align.LEFT);
            }
        };
        final boolean[] multiple={false};
        calibration.setOnTouchListener((v,event)->{
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN)multiple[0]=false;
            if(action==MotionEvent.ACTION_POINTER_DOWN)multiple[0]=true;
            if(action!=MotionEvent.ACTION_UP||multiple[0])return true;
            float x=event.getRawX(),y=event.getRawY();
            if(y>=dp(54)&&y<=dp(145)){cancelCalibration();return true;}
            WindowMetrics metrics;
            try{metrics=calibrationWm.getCurrentWindowMetrics();}
            catch(RuntimeException e){cancelCalibration();Toast.makeText(this,"暂时无法检测键盘，请重新标记",Toast.LENGTH_LONG).show();return true;}
            WindowInsets insets=metrics.getWindowInsets();int height=insets.getInsets(WindowInsets.Type.ime()).bottom;
            if(!insets.isVisible(WindowInsets.Type.ime())||height<=0||y<metrics.getBounds().bottom-height){
                Toast.makeText(this,"请点键盘上的删除键",Toast.LENGTH_SHORT).show();return true;
            }
            android.util.DisplayMetrics display=new android.util.DisplayMetrics();calibrationWm.getDefaultDisplay().getRealMetrics(display);
            prefs.set("delete_x",Math.round(x));prefs.set("delete_y",Math.round(y));
            prefs.set("delete_w",display.widthPixels);prefs.set("delete_h",display.heightPixels);prefs.set("delete_ime_h",height);
            cancelCalibration();refreshTapHint();
            Toast.makeText(this,"已标记。开启角落手势后，按住入口覆盖删除键的位置测试",Toast.LENGTH_LONG).show();
            return true;
        });
        WindowManager.LayoutParams params=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.LEFT;params.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        params.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        params.setFitInsetsTypes(0);params.windowAnimations=0;params.setTitle("角落绽放删除键标记");
        try{calibrationWm.addView(calibration,params);}
        catch(RuntimeException e){calibration=null;Toast.makeText(this,"标记层未能显示，请检查悬浮窗权限",Toast.LENGTH_LONG).show();}
    }
    private void cancelCalibration(){
        calibrationGeneration++;
        if(calibration!=null){try{calibrationWm.removeViewImmediate(calibration);}catch(RuntimeException ignored){}calibration=null;}
    }
    private void openOverlayPermission(){
        try{startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));}
        catch(ActivityNotFoundException e){startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}
    }
    private void toggleService(){
        if(OverlayService.running||prefs.get("overlay_enabled",false)){
            prefs.set("overlay_enabled",false);stopService(new Intent(this,OverlayService.class));
            ui.postDelayed(this::refreshStatus,150);return;
        }
        startOverlay(null);
    }
    private void startOverlay(String action){
        if(!ready){Toast.makeText(this,"应用列表还在读取，请稍等",Toast.LENGTH_SHORT).show();return;}
        if(AppEntry.favorites(all,prefs).isEmpty()){Toast.makeText(this,"先选择几个常用应用",Toast.LENGTH_SHORT).show();return;}
        if(!Settings.canDrawOverlays(this)){openOverlayPermission();return;}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},42);
        try{startForegroundService(new Intent(this,OverlayService.class).setAction(action));}
        catch(RuntimeException e){Toast.makeText(this,"启动失败，请检查悬浮窗和后台运行权限",Toast.LENGTH_LONG).show();}
        ui.postDelayed(this::refreshStatus,500);
    }
    private void refreshStatus(){
        boolean on=OverlayService.running;
        start.setText(on||prefs.get("overlay_enabled",false)?"关闭角落手势":"开启角落手势");
        status.setText(!Settings.canDrawOverlays(this)?"请允许悬浮窗权限，再恢复入口":on?OverlayService.statusHint:
                prefs.get("overlay_enabled",false)?"服务已停止，可点恢复入口重新开启":"角落手势未开启");
        status.setTextColor(on?0xff83e6cb:MUTED);
    }
    private void refreshApps(){
        List<AppEntry> favorites=AppEntry.favorites(all,prefs);practice.setApps(favorites);
        StringBuilder summary=new StringBuilder("已选 "+favorites.size()+" / 15");
        for(int i=0;i<favorites.size();i++)summary.append(i==0?"\n":" · ").append(favorites.get(i).name);
        if(favorites.isEmpty())summary.append("\n点击下面选择应用。");appsSummary.setText(summary);
    }
    private void showPicker(){
        List<String> selected=new ArrayList<>();for(AppEntry a:AppEntry.favorites(all,prefs))selected.add(a.id());
        LinearLayout body=column();body.setPadding(dp(18),dp(8),dp(18),0);
        TextView count=text("",13,MUTED);body.addView(count);
        TextView hint=text("按添加顺序排列，点已选应用可移除。",12,MUTED);body.addView(hint);
        EditText search=new EditText(this);search.setSingleLine();search.setTextColor(INK);search.setHintTextColor(MUTED);search.setHint("搜索应用");body.addView(search);
        ListView list=new ListView(this);list.setDividerHeight(0);body.addView(list,new LinearLayout.LayoutParams(-1,dp(330)));
        List<AppEntry> shown=new ArrayList<>(all);
        android.widget.BaseAdapter adapter=new android.widget.BaseAdapter(){
            public int getCount(){return shown.size();}public Object getItem(int p){return shown.get(p);}public long getItemId(int p){return p;}
            public View getView(int p,View old,android.view.ViewGroup parent){
                AppEntry a=shown.get(p);LinearLayout row=new LinearLayout(MainActivity.this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(8),0,dp(8));
                ImageView icon=new ImageView(MainActivity.this);icon.setImageBitmap(a.icon);row.addView(icon,new LinearLayout.LayoutParams(dp(36),dp(36)));
                TextView label=text(a.name,14,INK);label.setPadding(dp(12),0,0,0);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
                int index=selected.indexOf(a.id());TextView mark=text(index<0?"＋":String.valueOf(index+1),16,index<0?MUTED:PURPLE);mark.setGravity(Gravity.CENTER);row.addView(mark,new LinearLayout.LayoutParams(dp(36),dp(36)));return row;
            }
        };list.setAdapter(adapter);
        Runnable update=()->{count.setText("已选 "+selected.size()+" / 15");adapter.notifyDataSetChanged();};update.run();
        list.setOnItemClickListener((p,v,pos,id)->{
            String key=shown.get(pos).id();if(selected.contains(key))selected.remove(key);
            else if(selected.size()<15)selected.add(key);else Toast.makeText(this,"第一层最多放 15 个",Toast.LENGTH_SHORT).show();update.run();
        });
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int n){
                String q=s.toString().toLowerCase(Locale.ROOT);shown.clear();for(AppEntry a:all)if(a.name.toLowerCase(Locale.ROOT).contains(q))shown.add(a);adapter.notifyDataSetChanged();
            }public void afterTextChanged(Editable e){}
        });
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("选择常用应用").setView(body).setNegativeButton("取消",null)
                .setPositiveButton("保存",(d,w)->{prefs.selected(selected);practice.reset();refreshApps();}).create();
        dialog.setOnShowListener(d->{dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);});dialog.show();
    }
    private void showOrder(){
        List<AppEntry> ordered=new ArrayList<>(AppEntry.favorites(all,prefs));
        LinearLayout list=column();list.setPadding(dp(15),0,dp(15),0);ScrollView scroll=new ScrollView(this);scroll.addView(list);
        Runnable[] render=new Runnable[1];render[0]=()->{
            list.removeAllViews();
            for(int i=0;i<ordered.size();i++){
                final int index=i;LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
                TextView name=text((i+1)+"  "+ordered.get(i).name,14,INK);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
                Button up=button("↑",false);up.setMinWidth(0);up.setPadding(0,0,0,0);row.addView(up,new LinearLayout.LayoutParams(dp(48),dp(46)));
                Button down=button("↓",false);down.setMinWidth(0);down.setPadding(0,0,0,0);row.addView(down,new LinearLayout.LayoutParams(dp(48),dp(46)));
                up.setEnabled(i>0);down.setEnabled(i<ordered.size()-1);
                up.setOnClickListener(v->{Collections.swap(ordered,index,index-1);render[0].run();});
                down.setOnClickListener(v->{Collections.swap(ordered,index,index+1);render[0].run();});list.addView(row);
            }
        };render[0].run();
        new AlertDialog.Builder(this).setTitle("调整顺序 · 内圈到外圈").setView(scroll).setNegativeButton("取消",null)
                .setPositiveButton("保存",(d,w)->{List<String> ids=new ArrayList<>();for(AppEntry a:ordered)ids.add(a.id());prefs.selected(ids);practice.reset();refreshApps();}).show();
    }
    @Override public void onResume(){
        super.onResume();foreground=true;
        if(prefs!=null){
            applyRecentsPreference();
            // Only the visible Activity can request a fresh start after a vendor kill.
            if(prefs.get("overlay_enabled",false)&&Settings.canDrawOverlays(this)){
                try{startForegroundService(new Intent(this,OverlayService.class).setAction(OverlayService.CHECK));}
                catch(RuntimeException e){android.util.Log.w("CornerBloom","Unable to check overlay from settings",e);}
            }
        }
        if(status!=null)refreshStatus();refreshBootHint();refreshTapHint();
        ui.removeCallbacks(refreshRuntime);ui.postDelayed(refreshRuntime,1000);
    }
    @Override public void onPause(){foreground=false;ui.removeCallbacks(refreshRuntime);cancelCalibration();if(prefs!=null)applyRecentsPreference();super.onPause();}
    @Override protected void onDestroy(){ui.removeCallbacksAndMessages(null);cancelCalibration();worker.shutdownNow();if(practice!=null)practice.reset();super.onDestroy();}
}
