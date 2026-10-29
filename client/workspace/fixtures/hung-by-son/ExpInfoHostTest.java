import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Field;
import luna.api.ExpEstimateStore;
import luna.features.ExpEstimateSupport;
import luna.features.ExpRateSupport;

/** Integrates hourly/session lifetime with the actual persisted level estimator. */
public final class ExpInfoHostTest {
    private static int checks;
    private static final String WAIT = "Đang tính toán...";
    public static void main(String[] args) throws Exception {
        ExpEstimateSupport.setStore(null);
        ExpEstimateSupport.snapshot("test", 10, 70, 500);
        check(WAIT.equals(ExpEstimateSupport.line(500)), "HUD calculating text is Vietnamese");
        check(occurrences(ExpEstimateSupport.info(500,1000L),WAIT)==3,"all three unknown fields calculate");
        ExpRateSupport.gain(1000,1000L);
        ExpRateSupport.gain(1000,2000L);
        String rateOnly=ExpEstimateSupport.info(500,301000L);
        check(rateOnly.contains("24.000")&&occurrences(rateOnly,WAIT)==2,
                "known rate alone does not expose total or ETA");

        final byte[] record=record(70,100000000L);
        ExpEstimateSupport.setStore(new ExpEstimateStore(){
            public byte[] load(){return record;}
            public boolean save(byte[] value){return true;}
        });
        ExpEstimateSupport.snapshot("test",10,70,500);
        check(ExpEstimateSupport.line(500).equals("(50M/100M)"),"native HUD exposes the known denominator");
        String totalOnly=ExpEstimateSupport.info(500,1000L);
        check(totalOnly.contains("100.000.000")&&occurrences(totalOnly,WAIT)==2,
                "known HUD denominator appears as whole EXP before rate is ready");
        ExpRateSupport.gain(1000,1000L);
        ExpRateSupport.gain(1000,2000L);
        check(occurrences(ExpEstimateSupport.info(500,300999L),WAIT)==2,
                "rate and ETA remain unavailable one millisecond before five minutes");
        String complete=ExpEstimateSupport.info(500,301000L);
        check(complete.contains("24.000")&&complete.contains("100.000.000")
                &&complete.contains("2083 giờ 20 phút")&&!complete.contains(WAIT),
                "five minute warmup rate and half-level ETA use all received EXP");
        ExpEstimateSupport.levelChanged("test",10,71,0);
        String nextLevel=ExpEstimateSupport.info(0,301000L);
        check(nextLevel.contains("24.000")&&occurrences(nextLevel,WAIT)==2,
                "rate survives a real level change while unknown next-level total hides ETA");
        ExpEstimateSupport.snapshot("different",11,70,100);
        check(occurrences(ExpEstimateSupport.info(100,301000L),WAIT)==2,
                "new identity retains shared total but discards the hourly history");
        ExpEstimateSupport.disconnect();
        check(occurrences(ExpEstimateSupport.info(100,301000L),WAIT)==3,
                "offline statistics do not leak into the next active session");

        ExpEstimateSupport.snapshot("test",10,70,500);
        long now=System.currentTimeMillis();
        ExpRateSupport.gain(1000,now-299000L);
        setLong("firstGainMillis",now-300000L);
        ExpEstimateSupport.gain("test",10,70,500,1000);
        long afterGain=ExpRateSupport.rate(System.currentTimeMillis());
        check(afterGain>=23900L&&afterGain<=24000L,
                "one real positive self gain is added once to the independent rate");
        ExpEstimateSupport.gain("test",10,70,499,-100);
        long afterLoss=ExpRateSupport.rate(System.currentTimeMillis());
        check(afterLoss>=23900L&&afterLoss<=24000L,
                "loss resets percent sampling without erasing already earned session EXP");
        ExpEstimateSupport.gain("test",10,71,0,1000);
        long afterLevel=ExpRateSupport.rate(System.currentTimeMillis());
        check(afterLevel>=35900L&&afterLevel<=36000L,
                "level-crossing packet counts once although the fitting segment is discarded");
        ExpEstimateSupport.disconnect();
        check(ExpRateSupport.rate(System.currentTimeMillis())==-1L,"disconnect clears rate clock and gains");
        ExpEstimateSupport.snapshot("test",10,70,500);
        ExpEstimateSupport.gain("test",10,70,500,1000);
        ExpRateSupport.reset();
        ExpRateSupport.gain(50000,1000L);
        ExpRateSupport.gain(50000,2000L);
        check(ExpEstimateSupport.info(500,301000L).contains("41 giờ 38 phút"),
                "ETA uses the available fine percent rather than the coarse half-level remainder");
        ExpEstimateSupport.disconnect();
        System.out.println("ExpInfoHostTest PASS checks="+checks);
    }
    private static byte[] record(int level,long total)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        out.writeInt(0x45585031);out.writeByte(2);out.writeByte(1);out.writeInt(level);
        out.writeLong(total);out.writeLong(total);out.writeShort(20);out.writeShort(30);out.flush();return bytes.toByteArray();
    }
    private static int occurrences(String value,String token){int n=0,start=0;while((start=value.indexOf(token,start))>=0){n++;start+=token.length();}return n;}
    private static void setLong(String name,long value)throws Exception{Field field=ExpRateSupport.class.getDeclaredField(name);field.setAccessible(true);field.setLong(null,value);}
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
}
