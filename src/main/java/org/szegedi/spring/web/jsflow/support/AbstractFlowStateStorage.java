/*
   Copyright 2006, 2007 Attila Szegedi

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package org.szegedi.spring.web.jsflow.support;

import javax.servlet.http.HttpServletRequest;
import org.mozilla.javascript.NativeContinuation;
import org.szegedi.spring.web.jsflow.FlowStateStorage;
import org.szegedi.spring.web.jsflow.FlowStateStorageException;
import org.szegedi.spring.web.jsflow.codec.BinaryStateCodec;

/**
 * <p>
 * A flow state storage that serializes the flow states. It requires access to a
 * script storage. If none is configured, then the
 * {@link org.szegedi.spring.web.jsflow.FlowController} will pass it its own
 * script storage - this is usually the intention.
 * </p><p>
 * When creating the serialized flowstates, it stubs all the application context
 * beans and script function objects, thus minimizing the size of the serialized
 * state. When deserializing, it will reattach the deserialized state to stubbed
 * objects, resolving them by name. This way, it is allowed to have references
 * to application context objects in the reachability graph of the serialized
 * state, as they will get stubbed and resolved correctly. It is however
 * strongly not advised to have references to any other external objects in the
 * running scripts or objects referenced by them, as they will either fail
 * serialization, or - lacking stubbing - cause duplicate instances to be
 * created upon deserialization.
 * </p><p>
 * As a safety feature, the MD5 fingerprint of each function's code that is on
 * the continuation's call stack is stored along with the continuation, and
 * matched upon retrieval, with an exception being thrown if they don't match.
 * In case that the underlying script changed since the continuation last run
 * (i.e. because you restarted the servlet context and reloaded a changed
 * script) this causes clean fast failure, instead of unpredictable behavior
 * caused by invalid return addresses in the continuation stack frames.
 * </p><p>
 * The class supports setting a
 * {@link org.szegedi.spring.web.jsflow.codec.BinaryStateCodec}, enabling
 * pluggable compression, encryption, and/or digital signing of the serialized
 * state. This is most useful with the
 * {@link org.szegedi.spring.web.jsflow.ClientSideFlowStateStorage} subclass
 * where the client is entrusted with storing the flowstates, so you might wish
 * to ensure they're resistant to tampering.
 * @author Attila Szegedi
 * @version $Id$
 */
public abstract class AbstractFlowStateStorage extends FlowStateSerializer
implements FlowStateStorage
{
    private BinaryStateCodec binaryStateCodec;

    public void setBinaryStateCodec(final BinaryStateCodec binaryStateCodec)
    {
        this.binaryStateCodec = binaryStateCodec;
    }

    public NativeContinuation getState(final HttpServletRequest request, final String id)
    {
        try
        {
            byte[] b = getSerializedState(request, id);
            if(b == null)
            {
                return null;
            }
            if(binaryStateCodec != null)
            {
                b = binaryStateCodec.createDecoder().code(b);
            }
            return deserializeContinuation(b, null);
        }
        catch(final RuntimeException e)
        {
            throw e;
        }
        catch(final Exception e)
        {
            throw new FlowStateStorageException("Failed to load state", e);
        }
    }

    /**
     * Implement in subclasses to retrieve the serialized state.
     * @param request the HTTP request that triggered the retrieval. Can be used
     * to implement session-private storages for states.
     * @param id the id of the state
     * @return the byte array representing the serialized state
     * @throws Exception
     */
    protected abstract byte[] getSerializedState(HttpServletRequest request, String id) throws Exception;

    public String storeState(final HttpServletRequest request, final NativeContinuation state)
    {
        try
        {
            byte[] b = serializeContinuation(state, null, null);
            if(binaryStateCodec != null)
            {
                b = binaryStateCodec.createEncoder().code(b);
            }
            return storeSerializedState(request, b);
        }
        catch(final Exception e)
        {
            throw new FlowStateStorageException("Failed to store state", e);
        }
    }

    /**
     * Implement in subclasses to store the serialized state.
     * @param request the HTTP request that triggered the store operation. Can
     * be used to implement session-private storages for states.
     * @param state byte array representing the serialized state
     * @return the id of the state
     * @throws Exception
     */
    protected abstract String storeSerializedState(HttpServletRequest request, byte[] state) throws Exception;
}
