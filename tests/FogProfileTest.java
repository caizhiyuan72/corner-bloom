import dev.corner.bloom.FogProfile;

public final class FogProfileTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        check(FogProfile.opacity(0)==1,"center missing");
        check(FogProfile.opacity(1)==0&&FogProfile.opacity(1.1f)==0,"outer edge not transparent");
        float previous=1;
        for(int i=0;i<=1000;i++){
            float value=FogProfile.opacity(i/1000f);
            check(value>=0&&value<=previous,"non-monotonic opacity");
            check(previous-value<.003f,"visible opacity step");previous=value;
        }
        check(FogProfile.opacity(.999f)<.00001f,"hard outer edge");
        check(FogProfile.edgeOpacity(0,120)==0,"hard left/top edge");
        check(FogProfile.edgeOpacity(-10,120)==0,"fog outside viewport");
        float prior=0;
        for(int i=0;i<=1200;i++){
            float alpha=FogProfile.edgeOpacity(i/10f,120);
            check(alpha>=prior&&alpha-prior<.002f,"edge fade discontinuity");prior=alpha;
        }
        float w=411,h=890,feather=120;
        for(float[] p:new float[][]{{0,400},{w,400},{200,0},{200,h}}){
            float alpha=FogProfile.edgeOpacity(p[0],feather)*FogProfile.edgeOpacity(w-p[0],feather)
                    *FogProfile.edgeOpacity(p[1],feather)*FogProfile.edgeOpacity(h-p[1],feather);
            check(alpha==0,"viewport edge not fully transparent");
        }
        System.out.println("PASS: smooth radial fog and fully transparent left/right/top/bottom edges");
    }
}
