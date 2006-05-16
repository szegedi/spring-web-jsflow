package org.szegedi.spring.web.jsflow.codec.support;

/**
 * Basic worker interface, encapsulating a single encoding or decoding 
 * operation. 
 * @author Attila Szegedi
 * @version $Id: $
 */
public interface OneWayCodec
{
    /**
     * A single encoding or decoding operation. This method will always be 
     * invoked on a single thread at a time (although not always on the same
     * thread). It may be invoked multiple times.
     * @param data the data to encode or decode
     * @return the encoded or decoded data
     * @throws Exception 
     */
    public byte[] code(byte[] data) throws Exception;
}
