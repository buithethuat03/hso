import java.util.*;
import luna.features.AutoPkTargetPolicy;

/** Geometry and top-20 ranking (group, hp, distance) checked against an independent full-sort reference. */
public final class AutoPkTargetPolicyHostTest {
    static final int RADIUS = 365;
    static int checks;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static final class Candidate {
        final int id;final long distance;final boolean priority;final int hp;
        Candidate(int id,long distance,boolean priority,int hp){this.id=id;this.distance=distance;this.priority=priority;this.hp=hp;}
    }
    public static void main(String[] args){
        check(AutoPkTargetPolicy.RADIUS==RADIUS,"Auto PK radius is 365 world pixels");
        check(AutoPkTargetPolicy.distanceSquared(0,0,364,0)==132496,"364 inside");
        check(AutoPkTargetPolicy.distanceSquared(0,0,365,0)==133225,"365 inclusive");
        check(AutoPkTargetPolicy.distanceSquared(0,0,366,0)==-1,"366 outside");
        check(AutoPkTargetPolicy.distanceSquared(0,0,219,292)==133225,"Euclidean 219-292 circle border");
        check(AutoPkTargetPolicy.distanceSquared(0,0,258,258)==133128,"258-258 diagonal inside");
        check(AutoPkTargetPolicy.distanceSquared(0,0,259,259)==-1,"259-259 diagonal outside circle, not a square");
        check(AutoPkTargetPolicy.distanceSquared(0,0,-219,-292)==133225,"Negative coordinates");
        check(AutoPkTargetPolicy.distanceSquared(Integer.MIN_VALUE,0,Integer.MAX_VALUE,0)==-1,"Subtraction overflow avoided");
        check(AutoPkTargetPolicy.distanceSquared(Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE-219,Integer.MAX_VALUE-292)==133225,"Large absolute coordinates");
        AutoPkTargetPolicy p=new AutoPkTargetPolicy();
        Object near=new Object(),far40=new Object();
        p.offer(near,1,1,false,0);p.offer(far40,2,133225,true,0);
        check(p.size()==2&&p.at(0)==far40&&p.at(1)==near,"Level 40 before nearer ordinary");
        p.offer(new Object(),2,0,true,0);check(p.size()==2&&p.at(0)==far40,"Deduplicate wire IDs");
        p.offer(new Object(),3,133226,true,0);p.offer(new Object(),4,-1,true,0);p.offer(null,5,1,false,0);
        check(p.size()==2,"Bad/null/outside not offered");
        p.clear();check(p.size()==0&&p.nearestSquared()==-1,"Reset for fresh mode/world");
        Object full40=new Object(),weak40=new Object(),weakNear=new Object(),weakFar=new Object(),strong=new Object(),tieFar=new Object();
        p.offer(strong,10,100,false,900);p.offer(full40,11,400,true,800);p.offer(weakFar,12,90000,false,50);
        p.offer(weak40,13,80000,true,300);p.offer(weakNear,14,2500,false,50);p.offer(tieFar,15,120000,false,900);
        check(p.size()==6&&p.at(0)==weak40&&p.at(1)==full40&&p.at(2)==weakNear&&p.at(3)==weakFar&&p.at(4)==strong&&p.at(5)==tieFar,
                "Inside each group lower hp first, nearer on equal hp; level 40 group stays first");
        check(p.nearestSquared()==100,"Nearest distance is the smallest kept, not the first ranked");
        p.clear();
        Random random=new Random(20261025L);
        for(int trial=0;trial<1000;trial++){
            p.clear();List<Candidate> expected=new ArrayList<Candidate>();
            int n=random.nextInt(400),ox=random.nextInt(20000)-10000,oy=random.nextInt(20000)-10000;
            boolean war=(trial&1)==0,hpOrder=(trial&2)==0;
            for(int i=0;i<n;i++){
                int x=ox+random.nextInt(1601)-800,y=oy+random.nextInt(1601)-800;
                long dx=(long)x-ox,dy=(long)y-oy,d=dx*dx+dy*dy;
                Candidate c=new Candidate(i,d,war&&random.nextInt(4)==0,hpOrder?random.nextInt(8)*1000:0);
                if(d<=(long)RADIUS*RADIUS)expected.add(c);
                p.offer(c,c.id,AutoPkTargetPolicy.distanceSquared(ox,oy,x,y),c.priority,c.hp);
            }
            Collections.sort(expected,new Comparator<Candidate>(){
                public int compare(Candidate a,Candidate b){
                    if(a.priority!=b.priority)return a.priority?-1:1;
                    if(a.hp!=b.hp)return a.hp<b.hp?-1:1;
                    return a.distance<b.distance?-1:a.distance>b.distance?1:0;
                }
            });
            int count=Math.min(20,expected.size());check(p.size()==count,"Top20 size "+trial);
            for(int i=0;i<count;i++)check(p.at(i)==expected.get(i),"Top20 exact rank "+trial+"/"+i);
        }
        p.clear();for(int i=0;i<100;i++)p.offer(Integer.valueOf(i),i,10000-i,true,0);
        check(p.size()==20&&((Integer)p.at(0)).intValue()==99&&((Integer)p.at(19)).intValue()==80,">20 priority targets nearest20");
        p.clear();for(int i=0;i<100;i++)p.offer(Integer.valueOf(i),i,10000-i,true,1000+i);
        check(p.size()==20&&((Integer)p.at(0)).intValue()==0&&((Integer)p.at(19)).intValue()==19&&p.nearestSquared()==10000-19,">20 priority targets keep the 20 lowest hp");
        System.out.println("AutoPkTargetPolicyHostTest PASS checks="+checks);
    }
}
