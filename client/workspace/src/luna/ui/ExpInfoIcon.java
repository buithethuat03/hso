package luna.ui;

import javax.microedition.lcdui.Image;

/** Loads the independent info badge once, scaling without interpolation for MIDP. */
public final class ExpInfoIcon {
    private static Image image;
    private static int imageScale;
    private ExpInfoIcon() { }

    public static synchronized Image image(int scale) {
        if(scale<1||scale>8)scale=1;
        if(image!=null&&imageScale==scale)return image;
        try {
            Image source=Image.createImage("/luna/ui/exp-info.png");
            if(scale==1){image=source;imageScale=1;return image;}
            int width=source.getWidth(),height=source.getHeight();
            int[] pixels=new int[width*height];source.getRGB(pixels,0,width,0,0,width,height);
            int scaledWidth=width*scale,scaledHeight=height*scale;
            int[] scaled=new int[scaledWidth*scaledHeight];
            for(int y=0;y<scaledHeight;y++)for(int x=0;x<scaledWidth;x++)
                scaled[y*scaledWidth+x]=pixels[(y/scale)*width+x/scale];
            image=Image.createRGBImage(scaled,scaledWidth,scaledHeight,true);
            imageScale=scale;return image;
        } catch(Exception ignored) { return null; }
    }
}
