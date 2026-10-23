package dev.corner.bloom;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private int BG=0xfff5f5f5,CARD=0xffffffff,INK=0xff181818,MUTED=0xff858585,PURPLE=0xff3478f6;
    private Prefs prefs;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private List<AppEntry> all=new ArrayList<>();
    private TextView status,appsSummary,bootHint,tapHint;
    private Switch master;
    private Dialog pickerDialog;
    private PageHost pages;
    private PageHost.Page settingsPage,positionPage;
    private android.window.OnBackInvokedCallback backCallback;
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
        if((getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES){
            BG=0xff111111;CARD=0xff1c1c1e;INK=0xfff2f2f2;MUTED=0xff96969c;
        }
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        makeScreen();
        if(Build.VERSION.SDK_INT>=33){
            backCallback=this::goBack;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,backCallback);
        }
        applyRecentsPreference();
        worker.execute(()->{
            List<AppEntry> loaded=AppEntry.load(this);
            runOnUiThread(()->{if(isDestroyed())return;all=loaded;ready=true;refreshApps();refreshStatus();});
        });
    }
    private GradientDrawable background(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private void pressFeedback(View view){
        view.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf((INK&0xffffff)|0x14000000),
                null,background(0xffffffff,12)));view.setFocusable(true);
    }
    private void updateText(TextView view,String value){if(!value.contentEquals(view.getText()))view.setText(value);}
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
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(primary?0xffffffff:INK);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primary?PURPLE:BG));
        b.setMinHeight(dp(48));return b;
    }
    private void makeScreen(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        LinearLayout content=column();content.setPadding(dp(22),dp(18),dp(22),dp(24));
        content.setFocusableInTouchMode(true);content.requestFocus();scroll.addView(content);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView name=text("角落绽放",32,INK);name.setTypeface(null,Typeface.BOLD);
        header.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        TextView restore=text("恢复入口",14,PURPLE);restore.setGravity(Gravity.CENTER);
        pressFeedback(restore);
        restore.setOnClickListener(v->startOverlay(OverlayService.RESTORE));
        header.addView(restore,new LinearLayout.LayoutParams(dp(76),dp(48)));content.addView(header);
        gap(content,6);content.addView(text("让常用应用触手可及",14,MUTED));gap(content,24);

        LinearLayout stateCard=card(content);
        LinearLayout stateRow=new LinearLayout(this);stateRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout stateText=column();title(stateText,"角落手势");gap(stateText,6);
        status=text("正在读取应用…",12,MUTED);stateText.addView(status);
        stateRow.addView(stateText,new LinearLayout.LayoutParams(0,-2,1));
        master=new Switch(this);master.setContentDescription("开启或关闭角落手势");
        stateRow.addView(master,new LinearLayout.LayoutParams(-2,dp(52)));stateCard.addView(stateRow);
        master.setOnClickListener(v->toggleService());
        section(content,"配置");
        LinearLayout configure=card(content);configure.setPadding(dp(18),0,dp(18),0);
        appsSummary=navigationRow(configure,"常用应用","正在读取…",this::showAppsSettings);
        divider(configure);
        navigationRow(configure,"位置与排列","拖动预览，调整入口与图标",this::showPositionEditor);
        divider(configure);
        navigationRow(configure,"外观与手感","动画、雾效、亮条与震动",this::showAppearanceSettings);
        section(content,"服务");
        LinearLayout services=card(content);services.setPadding(dp(18),0,dp(18),0);
        navigationRow(services,"显示与穿透","入口模式、轻点和长按删除",this::showInputSettings);
        divider(services);
        navigationRow(services,"后台与启动","开机启动与最近任务",this::showBackgroundSettings);
        gap(content,5);
        TextView version=text("角落绽放  1.0.0",12,MUTED);version.setGravity(Gravity.CENTER);
        content.addView(version);
        pages=new PageHost(this,BG);applyInsets(pages);pages.setRoot(scroll);setContentView(pages);
    }
    private void applyInsets(View root){
        if(Build.VERSION.SDK_INT>=30)root.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;
        });
    }
    private void section(LinearLayout parent,String label){
        TextView t=text(label,13,MUTED);t.setPadding(dp(4),dp(4),0,dp(9));parent.addView(t);
    }
    private void divider(LinearLayout parent){
        View line=new View(this);line.setBackgroundColor(BG);parent.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
    }
    private TextView navigationRow(LinearLayout parent,String label,String detail,Runnable action){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0,dp(12),0,dp(12));row.setMinimumHeight(dp(64));
        LinearLayout labels=column();TextView heading=text(label,16,INK);labels.addView(heading);
        TextView summary=text(detail,12,MUTED);labels.addView(summary);
        row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        TextView chevron=text("›",26,MUTED);chevron.setPadding(dp(14),0,0,0);row.addView(chevron);
        pressFeedback(row);
        row.setOnClickListener(v->{if(pages.canPush())action.run();});parent.addView(row,new LinearLayout.LayoutParams(-1,-2));
        return summary;
    }
    private LinearLayout openSettingsPage(String label){
        if(settingsPage!=null)settingsPage.closeImmediately();
        LinearLayout root=column();root.setBackgroundColor(BG);
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(16),dp(8),dp(20),dp(8));
        TextView back=text("‹",34,INK);back.setGravity(Gravity.CENTER);back.setContentDescription("返回");
        pressFeedback(back);
        bar.addView(back,new LinearLayout.LayoutParams(dp(42),dp(48)));back.setOnClickListener(v->goBack());
        TextView heading=text(label,22,INK);heading.setTypeface(null,Typeface.BOLD);bar.addView(heading);
        root.addView(bar);
        ScrollView scroll=new ScrollView(this);LinearLayout body=column();body.setPadding(dp(22),dp(14),dp(22),dp(24));
        scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        settingsPage=pages.push(root,page->{
            if(settingsPage==page){cancelCalibration();tapHint=null;bootHint=null;holdTest=null;settingsPage=null;}
        });
        return body;
    }
    private void showAppsSettings(){
        LinearLayout body=openSettingsPage("常用应用");
        LinearLayout apps=card(body);title(apps,"常用应用");gap(apps,10);
        apps.addView(text("最多选择 15 个应用。图标位置在可视化预览中拖动调整。",13,MUTED));gap(apps,12);
        Button choose=button("选择应用",true);apps.addView(choose,new LinearLayout.LayoutParams(-1,dp(50)));
        choose.setOnClickListener(v->{if(ready)showPicker();});
        Button order=button("拖动调整图标排列",false);apps.addView(order,new LinearLayout.LayoutParams(-1,dp(50)));
        order.setOnClickListener(v->showPositionEditor());
    }
    private void showAppearanceSettings(){
        LinearLayout body=openSettingsPage("外观与手感");
        section(body,"图标与布局");LinearLayout layout=card(body);
        RadioGroup styles=new RadioGroup(this);styles.setOrientation(RadioGroup.VERTICAL);
        String[] names={"整齐网格","规整扇形","经典散开"};
        for(int i=0;i<names.length;i++){
            RadioButton option=new RadioButton(this);option.setId(700+i);option.setText(names[i]);option.setTextColor(INK);option.setTextSize(14);
            styles.addView(option,new RadioGroup.LayoutParams(-1,dp(44)));
        }
        styles.check(700+LauncherGeometry.style(prefs.get("layout_style",LauncherGeometry.GRID)));
        styles.setOnCheckedChangeListener((group,id)->prefs.set("layout_style",LauncherGeometry.style(id-700)));
        layout.addView(styles);gap(layout,8);
        layout.addView(text("网格和规整扇形保持图标正向，经典散开保留旋转效果。",12,MUTED));gap(layout,10);
        LinearLayout samples=new LinearLayout(this);samples.setGravity(Gravity.CENTER);layout.addView(samples,new LinearLayout.LayoutParams(-1,dp(78)));
        List<ImageView> icons=new ArrayList<>();List<AppEntry> favorites=ready?AppEntry.favorites(all,prefs):Collections.emptyList();
        int sampleCount=getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density>=360?3:2;
        if(favorites.isEmpty())samples.setVisibility(View.GONE);
        for(int i=0;i<Math.min(sampleCount,favorites.size());i++){
            ImageView icon=new ImageView(this);icon.setImageBitmap(favorites.get(i).icon);icon.setContentDescription(favorites.get(i).name);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(47),dp(47));lp.setMargins(dp(8),0,dp(8),0);samples.addView(icon,lp);icons.add(icon);
        }
        slider(layout,"图标大小","icon_scale",60,140,100,"%",percent->{
            int size=dp(47*percent/100f);
            for(ImageView icon:icons){android.view.ViewGroup.LayoutParams lp=icon.getLayoutParams();if(lp.width!=size){lp.width=lp.height=size;icon.setLayoutParams(lp);}}
        });
        layout.addView(text("展开范围不会改变图标大小；间距过小或超出屏幕时会限制范围。",12,MUTED));gap(layout,8);
        Button preview=button("预览与调整排列",false);layout.addView(preview,new LinearLayout.LayoutParams(-1,dp(48)));
        preview.setOnClickListener(v->showPositionEditor());
        section(body,"展开动画");LinearLayout motion=card(body);
        slider(motion,"展开范围与间距","spread",80,115,100,"%");
        slider(motion,"经典样式旋转与回弹","motion",0,150,100,"%");
        toggle(motion,"滑过图标时轻震","haptic",true);
        section(body,"背景");LinearLayout fog=card(body);
        slider(fog,"雾效强度 · 0% 关闭","fog_strength",0,50,22,"%");
        slider(fog,"雾效边缘柔和度","fog_feather",60,180,120,"dp");
        section(body,"手势入口");LinearLayout handle=card(body);
        toggle(handle,"显示入口亮条","guide",true);
        slider(handle,"触发宽度","width",8,100,16,"dp");
        slider(handle,"触发高度","height",40,180,88,"dp");
    }
    private void showBackgroundSettings(){
        LinearLayout body=openSettingsPage("后台与启动");
        LinearLayout background=card(body);
        Switch hide=preferenceSwitch(background,"在最近任务中隐藏","hide_recents",false);
        hide.setOnCheckedChangeListener((v,checked)->{prefs.set("hide_recents",checked);applyRecentsPreference();});
        divider(background);
        Switch boot=preferenceSwitch(background,"开机自动启动","start_on_boot",false);
        boot.setOnCheckedChangeListener((v,checked)->{
            prefs.set("start_on_boot",checked);refreshBootHint();
            if(checked&&!prefs.get("accessibility_overlay",false)&&!Settings.canDrawOverlays(this))
                Toast.makeText(this,"请先允许悬浮窗权限",Toast.LENGTH_LONG).show();
            if(checked&&Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},42);
        });
        bootHint=text("",12,MUTED);body.addView(bootHint);refreshBootHint();gap(body,16);
        Button system=button("系统应用设置",false);body.addView(system,new LinearLayout.LayoutParams(-1,dp(48)));
        system.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));
    }
    private void showInputSettings(){
        LinearLayout body=openSettingsPage("显示与穿透");
        section(body,"入口显示");LinearLayout display=card(body);
        Switch mode=preferenceSwitch(display,"通过无障碍显示入口","accessibility_overlay",false);
        mode.setOnCheckedChangeListener((v,checked)->{
            cancelCalibration();prefs.set("accessibility_overlay",checked);refreshStatus();
            if(checked&&!TapAccessibilityService.available())startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            else if(!checked&&!Settings.canDrawOverlays(this))openOverlayPermission();
        });
        display.addView(text("无障碍入口可减少普通悬浮窗的系统提醒。服务断开时暂停入口，重连后恢复。",12,MUTED));
        gap(display,8);
        Button permission=button("悬浮窗权限",false);display.addView(permission,new LinearLayout.LayoutParams(-1,dp(48)));
        permission.setOnClickListener(v->openOverlayPermission());
        section(body,"输入辅助");LinearLayout tap=card(body);
        Switch pass=preferenceSwitch(tap,"轻点穿透","tap_through",false);
        pass.setOnCheckedChangeListener((v,checked)->{
            prefs.set("tap_through",checked);refreshTapHint();
            if(checked&&!TapAccessibilityService.available())startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });
        divider(tap);
        Switch hold=preferenceSwitch(tap,"按住删除键连续删","hold_delete",false);hold.setEnabled(Build.VERSION.SDK_INT>=33);
        hold.setOnCheckedChangeListener((v,checked)->{
            prefs.set("hold_delete",checked);
            if(checked){
                boolean opening=!pass.isChecked();pass.setChecked(true);
                if(!opening&&!TapAccessibilityService.available())startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
            TapAccessibilityService.updateDeleteMode(checked);refreshTapHint();
        });
        tap.addView(text("需无障碍服务，无需 Root。长按删除支持 Android 13+，先标记键盘删除键；更换键盘布局后需重新标记。",12,MUTED));
        gap(tap,12);
        holdTest=new EditText(this);holdTest.setTextColor(INK);holdTest.setTextSize(14);
        holdTest.setText("在这里测试长按删除，松手后应该停止。");holdTest.setMinLines(2);tap.addView(holdTest,new LinearLayout.LayoutParams(-1,-2));
        Button mark=button("标记键盘删除键",false);mark.setEnabled(Build.VERSION.SDK_INT>=33);
        tap.addView(mark,new LinearLayout.LayoutParams(-1,dp(48)));
        mark.setOnClickListener(v->{
            if(!hold.isChecked()){hold.setChecked(true);if(!TapAccessibilityService.available())return;}beginCalibration();
        });
        tapHint=text("",12,MUTED);body.addView(tapHint);refreshTapHint();gap(body,16);
        Button accessibility=button("系统无障碍设置",false);body.addView(accessibility,new LinearLayout.LayoutParams(-1,dp(48)));
        accessibility.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
    }
    private void showPositionEditor(){
        if(!ready){Toast.makeText(this,"应用列表还在读取，请稍等",Toast.LENGTH_SHORT).show();return;}
        List<AppEntry> favorites=AppEntry.favorites(all,prefs);
        if(favorites.isEmpty()){Toast.makeText(this,"先选择几个常用应用",Toast.LENGTH_SHORT).show();return;}
        if(positionPage!=null||!pages.canPush())return;
        cancelCalibration();
        ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(),0);
        android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();
        ((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(metrics);
        ArrangementDraft draft=new ArrangementDraft(metrics.widthPixels/metrics.density,metrics.heightPixels/metrics.density,
                prefs.get("width",16),prefs.get("height",88),prefs.get("right",0),prefs.get("bottom",36),favorites.size());
        LinearLayout root=column();root.setBackgroundColor(BG);
        LinearLayout heading=column();heading.setPadding(dp(24),dp(20),dp(24),dp(6));
        title(heading,"位置与排列");gap(heading,6);
        heading.addView(text("拖动亮条移动入口，拖动图标交换位置",13,MUTED));root.addView(heading);
        TextView detail=text("",12,MUTED);detail.setGravity(Gravity.CENTER);detail.setPadding(dp(8),dp(5),dp(8),dp(8));
        Runnable update=()->updateText(detail,"距右边 "+draft.right+" dp   ·   距底部 "+draft.bottom+" dp");
        PositionEditorView editor=new PositionEditorView(this,draft,favorites,prefs.get("spread",100)/100f,
                prefs.get("icon_scale",100),prefs.get("layout_style",LauncherGeometry.GRID),update::run);
        root.addView(editor,new LinearLayout.LayoutParams(-1,0,1));root.addView(detail);
        LinearLayout actions=new LinearLayout(this);actions.setPadding(dp(18),dp(4),dp(18),dp(14));
        Button cancel=button("取消",false),reset=button("重置入口",false),save=button("保存",true);
        for(Button b:new Button[]{cancel,reset,save}){
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(50),1);lp.setMargins(dp(4),0,dp(4),0);actions.addView(b,lp);
        }
        root.addView(actions);update.run();
        cancel.setOnClickListener(v->goBack());reset.setOnClickListener(v->editor.resetPosition());
        save.setOnClickListener(v->{
            List<String> ids=new ArrayList<>();for(int index:draft.order)ids.add(favorites.get(index).id());
            prefs.arrangement(draft.right,draft.bottom,ids);refreshApps();goBack();
        });
        OverlayService.positionEditorVisible(true);
        try{
            positionPage=pages.push(root,page->{
                editor.cancelDrag();if(positionPage==page)positionPage=null;
                OverlayService.positionEditorVisible(false);
            });
            if(positionPage==null)OverlayService.positionEditorVisible(false);
        }catch(RuntimeException e){positionPage=null;OverlayService.positionEditorVisible(false);throw e;}
    }
    private void slider(LinearLayout parent,String label,String key,int min,int max,int fallback,String unit){
        slider(parent,label,key,min,max,fallback,unit,null);
    }
    private void slider(LinearLayout parent,String label,String key,int min,int max,int fallback,String unit,java.util.function.IntConsumer changed){
        int initial=Math.max(min,Math.min(max,prefs.get(key,fallback)));
        TextView value=text(label+"  "+initial+" "+unit,13,INK);parent.addView(value);
        SeekBar bar=new SeekBar(this);bar.setMax(max-min);bar.setProgress(initial-min);
        parent.addView(bar,new LinearLayout.LayoutParams(-1,dp(40)));
        if(changed!=null)changed.accept(initial);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int n,boolean from){updateText(value,label+"  "+(n+min)+" "+unit);if(changed!=null)changed.accept(n+min);}
            public void onStartTrackingTouch(SeekBar s){}
            public void onStopTrackingTouch(SeekBar s){prefs.set(key,s.getProgress()+min);}
        });
    }
    private void toggle(LinearLayout parent,String label,String key,boolean fallback){
        Switch s=new Switch(this);s.setText(label);s.setTextColor(INK);s.setTextSize(13);s.setChecked(prefs.get(key,fallback));
        parent.addView(s,new LinearLayout.LayoutParams(-1,dp(46)));
        s.setOnCheckedChangeListener((v,checked)->{prefs.set(key,checked);});
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
                !prefs.get("accessibility_overlay",false)&&!Settings.canDrawOverlays(this)?"请先允许悬浮窗权限，自启动才能显示入口。":
                prefs.get("boot_failed",false)?"上次自动启动未能完成，请检查悬浮窗权限及系统的自启动、后台运行设置。":
                "已开启：开机并解锁后自动恢复入口。若未生效，请在系统应用设置中允许自启动和后台运行。";
        if(prefs.get("accessibility_overlay",false)&&prefs.get("start_on_boot",false))hint+="\n无障碍模式需等待对应服务连接后才能显示入口。";
        updateText(bootHint,hint);
    }
    private void refreshTapHint(){
        if(tapHint==null)return;
        String hint=!prefs.get("tap_through",false)?"开启并授权后，轻点入口会点击下层同一位置，上滑仍展开。入口保持原位置，可显示在键盘上。":
                !TapAccessibilityService.available()?TapAccessibilityService.connectionHint():
                "轻点服务已就绪，上滑仍展开。\n"+TapAccessibilityService.lastTapHint();
        if(prefs.get("hold_delete",false))hint+="\n"+(prefs.get("delete_w",0)==0?"长按删除：请先标记删除键":
                "长按删除：已标记；键盘显示且入口覆盖删除键时生效")+"\n"+TapAccessibilityService.lastDeleteHint();
        updateText(tapHint,hint);
    }
    private void beginCalibration(){
        if(!prefs.get("accessibility_overlay",false)&&!Settings.canDrawOverlays(this)){openOverlayPermission();return;}
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
        boolean accessibilityMode=prefs.get("accessibility_overlay",false);
        calibrationWm=accessibilityMode?TapAccessibilityService.overlayWindowManager():
                (WindowManager)getApplicationContext().getSystemService(WINDOW_SERVICE);
        if(calibrationWm==null){Toast.makeText(this,"无障碍服务已断开，请重新开启后再标记",Toast.LENGTH_LONG).show();return;}
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
        WindowManager.LayoutParams params=new WindowManager.LayoutParams(-1,-1,accessibilityMode?
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY:WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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
        if(prefs.get("accessibility_overlay",false)){
            if(!TapAccessibilityService.available()){
                Toast.makeText(this,"请先开启「角落绽放 · 点击穿透」无障碍服务",Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));return;
            }
        }else if(!Settings.canDrawOverlays(this)){openOverlayPermission();return;}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},42);
        try{startForegroundService(new Intent(this,OverlayService.class).setAction(action));}
        catch(RuntimeException e){Toast.makeText(this,"启动失败，请检查悬浮窗和后台运行权限",Toast.LENGTH_LONG).show();}
        ui.postDelayed(this::refreshStatus,500);
    }
    private void refreshStatus(){
        boolean on=OverlayService.running;
        boolean checked=on||prefs.get("overlay_enabled",false);
        if(master.isChecked()!=checked)master.setChecked(checked);
        boolean accessibilityMode=prefs.get("accessibility_overlay",false);
        updateText(status,accessibilityMode&&!TapAccessibilityService.available()?"请开启无障碍服务，再恢复入口":
                !accessibilityMode&&!Settings.canDrawOverlays(this)?"请允许悬浮窗权限，再恢复入口":on?OverlayService.statusHint:
                prefs.get("overlay_enabled",false)?"服务已停止，可点恢复入口重新开启":"角落手势未开启");
        int color=on?PURPLE:MUTED;if(status.getCurrentTextColor()!=color)status.setTextColor(color);
    }
    private void refreshApps(){
        List<AppEntry> favorites=AppEntry.favorites(all,prefs);
        updateText(appsSummary,favorites.isEmpty()?"点击选择应用":"已选择 "+favorites.size()+" 个应用");
    }
    private void showPicker(){
        if(pickerDialog!=null||pages.isTransitioning())return;
        List<String> selected=new ArrayList<>();for(AppEntry a:AppEntry.favorites(all,prefs))selected.add(a.id());
        LinearLayout body=column();body.setPadding(dp(18),dp(8),dp(18),0);
        body.setFocusableInTouchMode(true);body.requestFocus();
        TextView count=text("",13,MUTED);body.addView(count);
        TextView hint=text("按添加顺序排列，点已选应用可移除。",12,MUTED);body.addView(hint);
        EditText search=new EditText(this);search.setSingleLine();search.setTextColor(INK);search.setHintTextColor(MUTED);search.setHint("搜索应用");body.addView(search);
        ListView list=new ListView(this);list.setDividerHeight(0);body.addView(list,new LinearLayout.LayoutParams(-1,dp(330)));
        List<AppEntry> shown=new ArrayList<>(all);
        List<String> names=new ArrayList<>();for(AppEntry a:all)names.add(a.name.toLowerCase(Locale.ROOT));
        android.widget.BaseAdapter adapter=new android.widget.BaseAdapter(){
            public int getCount(){return shown.size();}public Object getItem(int p){return shown.get(p);}public long getItemId(int p){return p;}
            public View getView(int p,View old,android.view.ViewGroup parent){
                AppEntry a=shown.get(p);AppRow holder;
                if(old==null){
                    LinearLayout row=new LinearLayout(MainActivity.this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(8),0,dp(8));
                    holder=new AppRow();holder.icon=new ImageView(MainActivity.this);row.addView(holder.icon,new LinearLayout.LayoutParams(dp(36),dp(36)));
                    holder.label=text("",14,INK);holder.label.setPadding(dp(12),0,0,0);row.addView(holder.label,new LinearLayout.LayoutParams(0,-2,1));
                    holder.mark=text("",16,MUTED);holder.mark.setGravity(Gravity.CENTER);row.addView(holder.mark,new LinearLayout.LayoutParams(dp(36),dp(36)));
                    row.setTag(holder);old=row;
                }else holder=(AppRow)old.getTag();
                holder.icon.setImageBitmap(a.icon);updateText(holder.label,a.name);
                int index=selected.indexOf(a.id());updateText(holder.mark,index<0?"＋":String.valueOf(index+1));
                holder.mark.setTextColor(index<0?MUTED:PURPLE);return old;
            }
        };list.setAdapter(adapter);
        Runnable update=()->{updateText(count,"已选 "+selected.size()+" / 15");adapter.notifyDataSetChanged();};update.run();
        list.setOnItemClickListener((p,v,pos,id)->{
            String key=shown.get(pos).id();if(selected.contains(key))selected.remove(key);
            else if(selected.size()<15)selected.add(key);else Toast.makeText(this,"第一层最多放 15 个",Toast.LENGTH_SHORT).show();update.run();
        });
        search.addTextChangedListener(new TextWatcher(){
            private String query="";
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int n){
                String q=s.toString().toLowerCase(Locale.ROOT);if(q.equals(query))return;query=q;
                shown.clear();for(int i=0;i<all.size();i++)if(names.get(i).contains(q))shown.add(all.get(i));adapter.notifyDataSetChanged();
            }public void afterTextChanged(Editable e){}
        });
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("选择常用应用").setView(body).setNegativeButton("取消",null)
                .setPositiveButton("保存",(d,w)->{prefs.selected(selected);refreshApps();}).create();
        pickerDialog=dialog;dialog.setOnDismissListener(d->{if(pickerDialog==dialog)pickerDialog=null;});
        if(!android.animation.ValueAnimator.areAnimatorsEnabled())dialog.getWindow().setWindowAnimations(0);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);dialog.show();
    }
    private static final class AppRow {ImageView icon;TextView label,mark;}
    private void goBack(){
        cancelCalibration();
        View focused=getCurrentFocus();
        if(focused!=null)((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(focused.getWindowToken(),0);
        if(!pages.pop())finish();
    }
    @Override public void onBackPressed(){goBack();}
    @Override public void onResume(){
        super.onResume();foreground=true;
        if(prefs!=null){
            applyRecentsPreference();
            // Only the visible Activity can request a fresh start after a vendor kill.
            if(prefs.get("overlay_enabled",false)&&(prefs.get("accessibility_overlay",false)||Settings.canDrawOverlays(this))){
                try{startForegroundService(new Intent(this,OverlayService.class).setAction(OverlayService.CHECK));}
                catch(RuntimeException e){android.util.Log.w("CornerBloom","Unable to check overlay from settings",e);}
            }
        }
        if(status!=null)refreshStatus();refreshBootHint();refreshTapHint();
        ui.removeCallbacks(refreshRuntime);ui.postDelayed(refreshRuntime,1000);
    }
    @Override public void onPause(){
        foreground=false;ui.removeCallbacks(refreshRuntime);
        if(positionPage!=null)positionPage.closeImmediately();if(pages!=null)pages.finishTransition();
        cancelCalibration();if(prefs!=null)applyRecentsPreference();super.onPause();
    }
    @Override protected void onDestroy(){
        ui.removeCallbacksAndMessages(null);if(pickerDialog!=null)pickerDialog.dismiss();
        if(Build.VERSION.SDK_INT>=33&&backCallback!=null)getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        if(pages!=null)pages.dispose();cancelCalibration();worker.shutdownNow();super.onDestroy();
    }
}
