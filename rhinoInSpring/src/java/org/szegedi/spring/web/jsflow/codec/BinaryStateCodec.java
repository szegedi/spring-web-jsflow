package org.szegedi.spring.web.jsflow.codec;

import org.szegedi.spring.web.jsflow.codec.support.OneWayCodec;

/**
 * Instances of a binary state codec can be added to any 
 * {@link org.szegedi.spring.web.jsflow.support.AbstractFlowStateStorage}
 * to transform the binary representation of the serialized state.
 * Tipically, you will add codecs when using 
 * {@link org.szegedi.spring.web.jsflow.ClientSideFlowStateStorage} to compress,
 * encrypt, and/or digitally sign the state before passing it to the client.
 * @author Attila Szegedi
 * @version $Id: $
 */
public interface BinaryStateCodec
{
    /**
     * Create a one-way single-threaded, nonshared codec instance able to decode
     * a state as received from the client or from an upstream codec.
     * @return the codec for decoding
     * @throws Exception
     */
    public OneWayCodec createDecoder() throws Exception;

    /**
     * Create a one-way single-threaded, nonshared codec instance able to encode
     * a state as should be sent to a client or a downstream codec.
     * @return the codec for encoding
     * @throws Exception
     */
    public OneWayCodec createEncoder() throws Exception;
}
