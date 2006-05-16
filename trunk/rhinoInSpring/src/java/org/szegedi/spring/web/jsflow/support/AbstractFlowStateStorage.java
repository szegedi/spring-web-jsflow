/*
   Copyright 2006 Attila Szegedi

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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.continuations.Continuation;
import org.mozilla.javascript.serialize.ScriptableInputStream;
import org.mozilla.javascript.serialize.ScriptableOutputStream;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.szegedi.spring.web.jsflow.FlowStateStorage;
import org.szegedi.spring.web.jsflow.FlowStateStorageException;
import org.szegedi.spring.web.jsflow.HostObject;
import org.szegedi.spring.web.jsflow.ScriptStorage;
import org.szegedi.spring.web.jsflow.codec.BinaryStateCodec;

/**
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
public abstract class AbstractFlowStateStorage 
implements FlowStateStorage, ApplicationContextAware, InitializingBean
{
    private ScriptStorage scriptStorage;
    private PersistenceSupport persistenceSupport;
    private ApplicationContext applicationContext;
    private BinaryStateCodec binaryStateCodec;
    private Map beansToStubs = Collections.EMPTY_MAP;
    
    public void setScriptStorage(ScriptStorage scriptStorage)
    {
        this.scriptStorage = scriptStorage;
        if(scriptStorage != null)
        {
            persistenceSupport = scriptStorage.getPersistenceSupport();
        }
    }
    
    public ScriptStorage getScriptStorage()
    {
        return scriptStorage;
    }
    
    public void setApplicationContext(ApplicationContext applicationContext)
    {
        this.applicationContext = applicationContext;
    }
    
    public void setBinaryStateCodec(BinaryStateCodec binaryStateCodec)
    {
        this.binaryStateCodec = binaryStateCodec;
    }
    
    public void afterPropertiesSet() throws Exception
    {
        createStubInfo();
    }
    
    private void createStubInfo()
    {
        String[] names = BeanFactoryUtils.beanNamesIncludingAncestors(applicationContext);
        Map beansToStubs = new IdentityHashMap();
        for (int i = 0; i < names.length; i++)
        {
            String name = names[i];
            beansToStubs.put(applicationContext.getBean(name), 
                    new ApplicationContextBeanStub(name));
        }
        beansToStubs.put(".", applicationContext);
        this.beansToStubs = beansToStubs;
    }
    
    public Continuation getState(HttpServletRequest request, String id)
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
            ObjectInputStream in = new ContinuationInputStream(
                    new ByteArrayInputStream(b));
            Object fingerprints = in.readObject();
            Continuation cont = (Continuation)in.readObject();
            FunctionFingerprintManager.checkFingerprints(cont, fingerprints);
            return cont;
        }
        catch(RuntimeException e)
        {
            throw e;
        }
        catch(Exception e)
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

    public String storeState(HttpServletRequest request, Continuation state)
    {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        try
        {
            ObjectOutputStream out = new ContinuationOutputStream(bout, 
                    state);
            out.writeObject(FunctionFingerprintManager.getFingerprints(state));
            out.writeObject(state);
            out.close();
            byte[] b = bout.toByteArray();
            if(binaryStateCodec != null)
            {
                b = binaryStateCodec.createEncoder().code(b);
            }
            return storeSerializedState(request, b);
        }
        catch(Exception e)
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

    private class ContinuationInputStream extends ScriptableInputStream
    {

        public ContinuationInputStream(InputStream in) throws IOException
        {
            super(in, persistenceSupport.getLibrary());
        }

        protected Object resolveObject(Object obj)
        throws 
            IOException
        {
            if(obj instanceof ApplicationContextBeanStub)
            {
                ApplicationContextBeanStub stub = 
                    (ApplicationContextBeanStub)obj;
                Object robj = applicationContext.getBean(stub.beanName);
                if(robj != null)
                {
                    return robj;
                }
                else
                {
                    throw new InvalidObjectException("No bean with name [" + 
                            stub.beanName + "] found");
                }
            }
            else
            {
                Object robj = persistenceSupport.resolveFunctionStub(obj);
                if(robj != null)
                {
                    return robj;
                }
            }
            return super.resolveObject(obj);
        }
    }
    
    private class ContinuationOutputStream extends ScriptableOutputStream
    {
        public ContinuationOutputStream(OutputStream out, Continuation cont) throws IOException
        {
            super(out, ScriptableObject.getTopLevelScope(cont).getPrototype());
            addExcludedName(HostObject.CLASS_NAME);
            addExcludedName(HostObject.CLASS_NAME + ".prototype");
        }
        
        protected Object replaceObject(Object obj) throws IOException
        {
            Object stub = beansToStubs.get(obj);
            if(stub != null)
            {
                return stub;
            }
            stub = persistenceSupport.getFunctionStub(obj);
            if(stub != null)
            {
                return stub;
            }
            return super.replaceObject(obj);
        }
    }
    
    private static class ApplicationContextBeanStub implements Serializable
    {
        private static final long serialVersionUID = 1L;

        private final String beanName;
        
        ApplicationContextBeanStub(String beanName)
        {
            this.beanName = beanName;
        }
        
        public boolean equals(Object obj)
        {
            if(obj instanceof ApplicationContextBeanStub)
            {
                return ((ApplicationContextBeanStub)obj).beanName.equals(beanName);
            }
            return false;
        }
        
        public int hashCode()
        {
            return beanName.hashCode();
        }
        
        public String toString()
        {
            return "stub:" + beanName;
        }
    }
}
