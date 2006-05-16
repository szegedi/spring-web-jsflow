package org.szegedi.spring.web.jsflow.codec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Random;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import org.szegedi.spring.web.jsflow.codec.support.OneWayCodec;

/**
 * A codec that will compress the serialized flowstate upon encoding and 
 * decompress it upon decoding. In particular useful as
 * part of a {@link org.szegedi.spring.web.jsflow.codec.CompositeCodec}, in 
 * front of a {@link org.szegedi.spring.web.jsflow.codec.ConfidentialityCodec},
 * as compression improves the security of the encryption. 
 * @author Attila Szegedi
 * @version $Id: $
 */
public class CompressionCodec implements BinaryStateCodec
{
    private int compressionLevel = Deflater.DEFAULT_COMPRESSION;
    
    /**
     * Sets the compression level - see {@link Deflater} compression level 
     * constants.
     * @param compressionLevel a compression level. Defaults to 
     * {@link Deflater#DEFAULT_COMPRESSION}.
     */
    public void setCompressionLevel(int compressionLevel)
    {
        this.compressionLevel = compressionLevel;
    }
    
    public OneWayCodec createDecoder() throws Exception
    {
        return new OneWayCodec()
        {
            private final Inflater inflater = new Inflater();
            
            public byte[] code(byte[] data) throws Exception
            {
                inflater.reset();
                InflaterInputStream in = new InflaterInputStream(
                        new ByteArrayInputStream(data), inflater);
                ByteArrayOutputStream out = new ByteArrayOutputStream(data.length * 2);
                byte[] b = new byte[512];
                for(;;)
                {
                    int i = in.read(b);
                    if(i == -1)
                    {
                        break;
                    }
                    out.write(b, 0, i);
                }
                return out.toByteArray();
            }
        };
    }
    
    public OneWayCodec createEncoder() throws Exception
    {
        return new OneWayCodec()
        {
            private final Deflater deflater = new Deflater(compressionLevel);
            
            public byte[] code(byte[] data) throws Exception
            {
                deflater.reset();
                ByteArrayOutputStream bout = new ByteArrayOutputStream(data.length / 2);
                DeflaterOutputStream out = new DeflaterOutputStream(bout, 
                        deflater);
                out.write(data);
                out.close();
                return bout.toByteArray(); 
            }
        };
    }
    
    public static void main(String[] args) throws Exception
    {
        CompressionCodec c = new CompressionCodec();
        byte[] b = new byte[4567];
        Random r = new Random();
        r.nextBytes(b);
        c.createDecoder().code(c.createEncoder().code(b));
    }
}