package org.szegedi.spring.web.jsflow;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;

import org.szegedi.spring.web.jsflow.support.AbstractFlowStateStorage;

import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;

/**
 * An implementation of the flowstate storage that returns the BASE64-encoded
 * serialized state as the ID. This way, the ID encapsulates the whole state, 
 * and the client will store it as part of the rendered HTML page, with no state
 * whatsoever remaining on the server, allowing for truly massive scalability.
 * Note however that in order to prevent the users from tampering with the 
 * flowstates while they are on the client side, or forging false flowstates, 
 * you should install at least a digital signature providing
 * {@link org.szegedi.spring.web.jsflow.codec.IntegrityCodec} into this storage
 * instance, although you can go for a full codec stack including compression,
 * encryption, and digital signature by using a 
 * {@link org.szegedi.spring.web.jsflow.codec.CompositeCodec} (that you can 
 * further enhance with {@link org.szegedi.spring.web.jsflow.codec.PooledCodec 
 * pooling}).  
 * @author Attila Szegedi
 * @version $Id: $
 */
public class ClientSideFlowStateStorage extends AbstractFlowStateStorage
{
    protected byte[] getSerializedState(HttpServletRequest request, String id) throws IOException
    {
        return new BASE64Decoder().decodeBuffer(id);
    }
    
    protected String storeSerializedState(HttpServletRequest request, byte[] state)
    {
        return new BASE64Encoder().encodeBuffer(state);
    }
}
