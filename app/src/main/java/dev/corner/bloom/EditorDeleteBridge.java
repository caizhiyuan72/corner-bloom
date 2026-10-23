package dev.corner.bloom;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.InputMethod;
import android.os.SystemClock;
import android.text.InputType;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;

/** API 33 only. Uses editing APIs; it never injects or takes over touch gestures. */
final class EditorDeleteBridge {
    static DeleteHold.Source capture(AccessibilityService service){
        InputMethod method=service.getInputMethod();
        if(method==null||!method.getCurrentInputStarted())return null;
        EditorInfo editor=method.getCurrentInputEditorInfo();
        InputMethod.AccessibilityInputConnection connection=method.getCurrentInputConnection();
        if(editor==null||connection==null||(editor.inputType&InputType.TYPE_MASK_CLASS)!=InputType.TYPE_CLASS_TEXT)return null;
        return new DeleteHold.Source(){
            public boolean valid(){
                return service.getInputMethod()==method&&method.getCurrentInputStarted()
                        &&method.getCurrentInputEditorInfo()==editor;
            }
            public boolean erase(){
                if(!valid())return false;
                try{
                    long now=SystemClock.uptimeMillis();
                    int flags=KeyEvent.FLAG_SOFT_KEYBOARD|KeyEvent.FLAG_KEEP_TOUCH_MODE;
                    connection.sendKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DEL,0,0,
                            KeyCharacterMap.VIRTUAL_KEYBOARD,0,flags));
                    connection.sendKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DEL,0,0,
                            KeyCharacterMap.VIRTUAL_KEYBOARD,0,flags));
                    return true;
                }catch(RuntimeException e){return false;}
            }
        };
    }
}
