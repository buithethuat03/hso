import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.net.URL;
import java.net.URLClassLoader;

/** Loads the actual three rate engines and verifies rolling expiry and independent state. */
public final class HungRollingExpX3HostTest {
    static int checks;
    static void check(boolean b,String text){checks++;if(!b)throw new AssertionError(text);}
    static long rate(Class<?> c,long time)throws Exception{return ((Long)c.getMethod("rate",Long.TYPE).invoke(null,time)).longValue();}
    static void gain(Class<?> c,int amount,long time)throws Exception{c.getMethod("gain",Integer.TYPE,Long.TYPE).invoke(null,amount,time);}
    static void reset(Class<?> c)throws Exception{c.getMethod("reset").invoke(null);}
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IllegalArgumentException("candidate-x3.jar");
        URLClassLoader loader=new URLClassLoader(new URL[]{new File(args[0]).toURI().toURL()},HungRollingExpX3HostTest.class.getClassLoader());
        try{
            Class<?>[] types=new Class<?>[3];String[] prefix={"","b.","c."};
            Object[] arrays=new Object[3];
            for(int i=0;i<3;i++){
                Class<?> c=types[i]=Class.forName(prefix[i]+"luna.features.ExpRateSupport",true,loader);
                Field buckets=c.getDeclaredField("bucketGain");buckets.setAccessible(true);arrays[i]=buckets.get(null);
                check(((long[])arrays[i]).length==300,"slot "+i+" has fixed 300-second storage");
                reset(c);gain(c,10,0);gain(c,(i+1)*100,1000);
                check(rate(c,299999)==-1,"slot "+i+" waits five minutes");
                check(rate(c,300000)==(i+1)*1200,"slot "+i+" excludes expired first bucket");
            }
            check(arrays[0]!=arrays[1]&&arrays[1]!=arrays[2]&&arrays[0]!=arrays[2],"all bucket arrays are slot-local");
            gain(types[0],500,300000);
            check(rate(types[0],300000)==7200,"slot 1 counts its new reward");
            check(rate(types[1],300000)==2400&&rate(types[2],300000)==3600,"slot 1 gain does not alter other slots");
            check(rate(types[1],301000)==0,"slot 2 expires while idle without another packet");
            check(rate(types[2],300000)==3600,"slot 2 clock does not move slot 3");
            reset(types[0]);check(rate(types[0],300000)==-1,"reset clears slot 1 warmup");
            check(rate(types[2],300000)==3600,"reset preserves slot 3");
            for(int i=0;i<3;i++){
                Class<?> c=types[i];reset(c);gain(c,1,0);gain(c,100,1000);gain(c,200,290000);
                check(rate(c,300000)==3600,"slot "+i+" fixed five-minute hourly scale");
                check(rate(c,301000)==2400,"slot "+i+" drops only the oldest reward");
                check(rate(c,590000)==0,"slot "+i+" becomes zero after five idle minutes");
                gain(c,300,600000);check(rate(c,600000)==3600,"slot "+i+" resumes without a fresh warmup");
                check(rate(c,599999)==-1,"slot "+i+" backwards clock resets the window");
            }
            final ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream data=new DataOutputStream(bytes);
            data.writeInt(0x45585031);data.writeByte(2);data.writeByte(1);data.writeInt(70);
            data.writeLong(400000000L);data.writeLong(400000000L);data.writeShort(20);data.writeShort(30);data.close();
            for(int i=0;i<3;i++){
                Class<?> estimator=Class.forName(prefix[i]+"luna.features.ExpEstimateSupport",true,loader);
                Class<?> api=Class.forName(prefix[i]+"luna.api.ExpEstimateStore",true,loader);
                Object store=Proxy.newProxyInstance(loader,new Class<?>[]{api},new InvocationHandler(){
                    public Object invoke(Object p,Method m,Object[] a){return m.getName().equals("load")?bytes.toByteArray():Boolean.TRUE;}
                });
                estimator.getMethod("setStore",api).invoke(null,store);
                estimator.getMethod("snapshot",String.class,Integer.TYPE,Integer.TYPE,Integer.TYPE).invoke(null,"test",1,70,320);
                Method gain=estimator.getMethod("gain",String.class,Integer.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE);
                Method line=estimator.getMethod("line",Integer.TYPE),percentage=estimator.getMethod("percentage",Integer.TYPE);
                gain.invoke(null,"test",1,70,320,40000);
                String first=(String)line.invoke(null,320);
                gain.invoke(null,"test",1,70,320,100000);
                String second=(String)line.invoke(null,320),percent=(String)percentage.invoke(null,320);
                int fine=Integer.parseInt(percent.replace(",","").replace("%",""));
                long current=400000000L*fine/100000L,tenths=(current+50000L)/100000L;
                String expected="("+(tenths/10)+(tenths%10==0?"":"."+(tenths%10))+"M/400M)";
                check(!first.equals(second),"slot "+i+" numerator advances while native percentage stays 32.0");
                check(expected.equals(second),"slot "+i+" numerator matches its own displayed fine percentage");
                check(second==line.invoke(null,320),"slot "+i+" unchanged paint reuses its line cache");
            }
        }finally{loader.close();}
        System.out.println("HungRollingExpX3HostTest PASS checks="+checks);
    }
}
