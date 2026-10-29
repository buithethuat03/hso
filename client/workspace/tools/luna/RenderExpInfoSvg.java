import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.jar.JarFile;
import java.util.regex.*;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

/** Rasterizes the supplied rectilinear pixel SVG at the native A badge's exact size. */
public final class RenderExpInfoSvg {
    static Path2D.Double path(String data){
        Matcher matcher=Pattern.compile("[MmLlHhVvZz]|[-+]?(?:[0-9]*\\.)?[0-9]+").matcher(data);
        ArrayList<String> tokens=new ArrayList<String>();int last=0;
        while(matcher.find()){if(!data.substring(last,matcher.start()).trim().replace(",","").isEmpty())throw new IllegalArgumentException("Unsupported SVG path syntax");tokens.add(matcher.group());last=matcher.end();}
        if(!data.substring(last).trim().isEmpty())throw new IllegalArgumentException("Unparsed SVG path");
        Path2D.Double shape=new Path2D.Double();double x=0,y=0,startX=0,startY=0;char command=0;
        for(int i=0;i<tokens.size();){
            String token=tokens.get(i);
            if(Character.isLetter(token.charAt(0))){command=token.charAt(0);i++;if(command=='z'||command=='Z'){shape.closePath();x=startX;y=startY;continue;}}
            boolean relative=Character.isLowerCase(command);
            if(command=='M'||command=='m'||command=='L'||command=='l'){
                double a=Double.parseDouble(tokens.get(i++)),b=Double.parseDouble(tokens.get(i++));x=relative?x+a:a;y=relative?y+b:b;
                if(command=='M'||command=='m'){shape.moveTo(x,y);startX=x;startY=y;command=relative?'l':'L';}else shape.lineTo(x,y);
            }else if(command=='H'||command=='h'){double a=Double.parseDouble(tokens.get(i++));x=relative?x+a:a;shape.lineTo(x,y);}
            else if(command=='V'||command=='v'){double a=Double.parseDouble(tokens.get(i++));y=relative?y+a:a;shape.lineTo(x,y);}
            else throw new IllegalArgumentException("Unsupported SVG command "+command);
        }
        return shape;
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=4)throw new IllegalArgumentException("source.svg native.jar icon.png preview.png");
        JarFile jar=new JarFile(args[1]);BufferedImage nativeA;
        try{InputStream in=jar.getInputStream(jar.getJarEntry("x1/interface/iconauto.png"));try{nativeA=ImageIO.read(in);}finally{in.close();}}finally{jar.close();}
        if(nativeA==null||nativeA.getWidth()!=nativeA.getHeight())throw new IllegalStateException("Native A image dimensions");
        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        Element root=factory.newDocumentBuilder().parse(new File(args[0])).getDocumentElement();
        if(!"svg".equals(root.getTagName()))throw new IllegalArgumentException("Expected SVG");
        if(Double.parseDouble(root.getAttribute("width"))!=nativeA.getWidth()
                ||Double.parseDouble(root.getAttribute("height"))!=nativeA.getHeight())
            throw new IllegalArgumentException("Use the supplied SVG whose intrinsic dimensions already match native A");
        String[] box=root.getAttribute("viewBox").trim().split("\\s+");
        if(box.length!=4)throw new IllegalArgumentException("Expected viewBox");
        int width=nativeA.getWidth(),height=nativeA.getHeight();BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_OFF);
        g.scale(width/Double.parseDouble(box[2]),height/Double.parseDouble(box[3]));g.translate(-Double.parseDouble(box[0]),-Double.parseDouble(box[1]));
        NodeList nodes=root.getElementsByTagName("path");if(nodes.getLength()!=3)throw new IllegalArgumentException("Expected the three supplied icon paths");
        for(int i=0;i<nodes.getLength();i++){Element element=(Element)nodes.item(i);g.setColor(Color.decode(element.getAttribute("fill")));g.fill(path(element.getAttribute("d")));}g.dispose();
        File output=new File(args[2]);output.getAbsoluteFile().getParentFile().mkdirs();ImageIO.write(image,"png",output);
        BufferedImage preview=new BufferedImage(width*10,height*10,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<preview.getHeight();y++)for(int x=0;x<preview.getWidth();x++)preview.setRGB(x,y,image.getRGB(x/10,y/10));
        ImageIO.write(preview,"png",new File(args[3]));System.out.println("SVG_ICON=PASS; exact supplied paths/colors; crispEdges; A="+width+"x"+height+"; info="+width+"x"+height);
    }
}
