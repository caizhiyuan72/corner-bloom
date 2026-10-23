import dev.corner.bloom.DeleteHold;
import dev.corner.bloom.TapClassifier;
import java.util.*;

public final class DeleteHoldTest {
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    static class Clock implements DeleteHold.Timer {
        long now;long seq;
        static class Task {long at,seq;Runnable action;Task(long at,long seq,Runnable action){this.at=at;this.seq=seq;this.action=action;}}
        PriorityQueue<Task> tasks=new PriorityQueue<>(Comparator.<Task>comparingLong(t->t.at).thenComparingLong(t->t.seq));
        public void later(Runnable action,long delay){tasks.add(new Task(now+delay,seq++,action));}
        void advance(long ms){long target=now+ms;while(!tasks.isEmpty()&&tasks.peek().at<=target){Task t=tasks.remove();now=t.at;t.action.run();}now=target;}
        void stalled(long ms){now+=ms;while(!tasks.isEmpty()&&tasks.peek().at<=now)tasks.remove().action.run();}
    }
    static class Editor implements DeleteHold.Source {int deleted;boolean valid=true;boolean accepted=true;public boolean valid(){return valid;}public boolean erase(){if(accepted)deleted++;return accepted;}}
    public static void main(String[] args){
        Clock clock=new Clock();Editor editor=new Editor();DeleteHold hold=new DeleteHold(clock,500,80,8);
        TapClassifier taps=new TapClassifier(8);
        // Short taps remain eligible for the existing single tap replay.
        taps.begin(4,6,0);hold.down(4,6,editor);clock.advance(80);
        check(!hold.release()&&taps.finish(4,6,80),"short press stays a tap");clock.advance(1000);
        check(editor.deleted==0,"release must invalidate a late long-press callback");

        // Hold, three repeats, real release, then an arbitrarily long wait.
        taps.begin(4,6,clock.now);hold.down(4,6,editor);clock.advance(499);
        check(editor.deleted==0,"no deletion before long-press deadline");
        clock.advance(1);clock.advance(160);check(editor.deleted==3,"regular delete pulses");
        check(hold.release(),"release claims the hold");taps.cancel();
        check(!taps.finish(4,6,clock.now),"a long delete never becomes a replayed tap");
        clock.advance(20000);check(editor.deleted==3,"no deletion after finger release");

        // An intended launcher swipe cancels deletion even if it returns to its origin.
        hold.down(4,6,editor);hold.move(4,-20);hold.move(4,6);clock.advance(1000);
        check(editor.deleted==3&&!hold.release(),"swipe cannot arm a delayed delete");

        hold.down(4,6,editor);clock.advance(500);int count=editor.deleted;
        hold.move(40,6);clock.advance(1000);check(editor.deleted==count&&hold.release(),"leaving the key stops repeats without a final tap");

        // Keyboard hiding or changing focus invalidates the source.
        hold.down(4,6,editor);clock.advance(500);editor.valid=false;count=editor.deleted;
        clock.advance(1000);check(editor.deleted==count,"keyboard/focus loss stops repeats");hold.release();
        hold.down(4,6,editor);clock.advance(1000);check(!hold.release()&&editor.deleted==count,"invalid editor cannot begin deleting");
        editor.valid=true;

        // CANCEL, pointer changes, disabling, lockscreen and service stop all cancel
        // through this same method, including stale callbacks after a new DOWN.
        hold.down(4,6,editor);clock.advance(100);hold.cancel();hold.down(4,6,editor);
        clock.advance(400);check(editor.deleted==count,"previous interaction cannot trigger the new one");
        clock.advance(100);check(editor.deleted==count+1,"new interaction has its own deadline");hold.cancel();hold.release();

        // A busy UI thread must not issue a catch-up burst.
        count=editor.deleted;hold.down(4,6,editor);clock.stalled(3000);
        check(editor.deleted==count+1,"one pulse per wakeup, no queued batch");hold.release();clock.advance(20000);
        check(editor.deleted==count+1,"stalled callback after release stays cancelled");

        // A failed request is still a consumed hold; never inject a click afterwards.
        editor.accepted=false;hold.down(4,6,editor);clock.advance(500);check(hold.release(),"failed held deletion is not a tap");
        System.out.println("Delete hold: tap coexistence, release, swipe, repeat, focus loss, cancel, stale callbacks and UI stalls passed");
    }
}
