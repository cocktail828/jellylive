import org.jellyfin.androidtv.bestv.LiveConfirmGesture;
public class LiveConfirmGestureCheck {
 static int count;
 static void check(String name,boolean value){if(!value)throw new AssertionError(name);count++;System.out.println("PASS "+name);}
 public static void main(String[] args){
  LiveConfirmGesture g=new LiveConfirmGesture();
  check("unrelated release untouched",g.release(false)==LiveConfirmGesture.Release.NONE);
  g.begin("a","1",0,500);check("short press does not favorite",!g.poll(499,"a","1",true));check("short release retained",g.release(false)==LiveConfirmGesture.Release.SHORT);
  g.begin("a","1",0,500);check("threshold favorites",g.poll(500,"a","1",true));check("repeat cannot toggle again",!g.poll(1200,"a","1",true));check("long release has no short action",g.release(false)==LiveConfirmGesture.Release.LONG);check("duplicate release untouched",g.release(false)==LiveConfirmGesture.Release.NONE);
  g.begin("a","1",0,500);check("source mismatch blocks",!g.poll(500,"b","1",true));check("channel mismatch blocks",!g.poll(500,"a","2",true));g.cancel();check("canceled hold still consumes release",g.consumesRelease()&&g.release(false)==LiveConfirmGesture.Release.CANCELED);
  g.begin("a","1",0,500);check("drawer or dialog blocks",!g.poll(500,"a","1",false));g.cancel();check("focus loss stops action",!g.poll(600,"a","1",true));g.release(false);
  g.begin("a","",0,500);check("no actual playing channel cannot favorite",!g.poll(500,"a","",true));g.release(false);
  g.begin("a","1",0,500);check("canceled keyup has no click",g.release(true)==LiveConfirmGesture.Release.CANCELED);
  System.out.println(count+" live confirm checks passed");
 }
}
