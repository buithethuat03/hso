import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Hand-drawn, pixel-aligned 14px information badge; reproducible source artwork. */
public final class GenerateExpInfoIcon {
    private static final String[] PIXELS = {
        "....dddddd....",
        "..ddhhhhhhdd..",
        ".dhgggggggghd.",
        ".dgbbbbbbbbgd.",
        "dhgbbwwwbbbghd",
        "dhgbbwwwbbbghd",
        "dhgbbbbbbbbghd",
        "dhgbbwwwbbbghd",
        "dhgbbwwwbbbghd",
        "dhgbbwwwbbbghd",
        "dhgbbwwwbbbghd",
        ".dgbwwwwwbbgd.",
        "..ddggggggdd..",
        "....dddddd...."
    };
    public static void main(String[] args) throws Exception {
        if(args.length<1||args.length>2)throw new IllegalArgumentException("icon.png [preview.png]");
        BufferedImage icon=new BufferedImage(14,14,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<14;y++){
            if(PIXELS[y].length()!=14)throw new IllegalStateException("Icon row width "+y);
            for(int x=0;x<14;x++){
                char c=PIXELS[y].charAt(x);
                int color=c=='d'?0xFF49351D:c=='h'?0xFFFFE6A1:c=='g'?0xFFD5A044:
                    c=='b'?(y<7?0xFF315D7D:0xFF20435F):c=='w'?0xFFFFFAE7:0;
                icon.setRGB(x,y,color);
            }
        }
        File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();ImageIO.write(icon,"png",file);
        if(args.length==2){
            BufferedImage preview=new BufferedImage(140,140,BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<140;y++)for(int x=0;x<140;x++)preview.setRGB(x,y,icon.getRGB(x/10,y/10));
            ImageIO.write(preview,"png",new File(args[1]));
        }
        System.out.println("EXP_INFO_ICON=14x14; transparent; explicit pixel artwork");
    }
}
