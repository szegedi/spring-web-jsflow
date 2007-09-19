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
package org.szegedi.spring.web.jsflow;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.UndeclaredThrowableException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mozilla.javascript.continuations.Continuation;
import org.szegedi.spring.web.jsflow.support.FlowStateIdGenerator;
import org.szegedi.spring.web.jsflow.support.FlowStateSerializer;
import org.szegedi.spring.web.jsflow.support.RandomFlowStateIdGenerator;

/**
 * An implementation for flow state storage that stores flow states in 
 * the {@link javax.servlet.http.HttpSession} objects. As states are stored
 * privately to HTTP sessions, no crossover between sessions is possible 
 * (requesting a state from a session it doesn't belong to won't work, and it 
 * is also possible to have identical flowstate ids in two sessions without any 
 * interference). The implementation is aware of HTTP session persistence 
 * through serialization. States stored in a HTTP session that is serialized 
 * and later deserialized will work as expected. If your HTTP session 
 * replication uses Terracotta, you can consider using either 
 * {@link HttpSessionSingleFlowStateStorage} or 
 * @author Attila Szegedi
 * @version $Id$
 */
public class HttpSessionFlowStateStorage extends FlowStateSerializer 
implements FlowStateStorage
{
    private static final Log log = LogFactory.getLog(
            HttpSessionFlowStateStorage.class);
    
    private static final String MAP_KEY = HttpSessionFlowStateStorage.class.getName(); 

    private int maxStates = 100;
    private FlowStateIdGenerator flowStateIdGenerator;
    
    /**
     * Sets the maximum number of states per HTTP session that this manager will
     * store. If the number is exceeded, the least recently used state will be
     * discareded. Defaults to 100.
     * @param maxStates
     */
    public void setMaxStates(int maxStates)
    {
        if(maxStates <= 0)
        {
            throw new IllegalArgumentException("maxStates <= 0");
        }
        this.maxStates = maxStates;
    }
    
    /**
     * Sets a source of randomness for generating state IDs. If not explicitly 
     * set, it will create and use a private instance of {@link SecureRandom}.
     * @param random
     * @deprecated use {@link #setFlowStateIdGenerator(FlowStateIdGenerator)}
     * with a {@link RandomFlowStateIdGenerator} instead.
     */
    public void setRandom(Random random)
    {
        setRandomInternal(random);
    }
    
    private void setRandomInternal(Random random)
    {
        RandomFlowStateIdGenerator idGen = new RandomFlowStateIdGenerator();
        idGen.setRandom(random);
        flowStateIdGenerator = idGen;
    }

    public void setFlowStateIdGenerator(
            FlowStateIdGenerator flowStateIdGenerator)
    {
        this.flowStateIdGenerator = flowStateIdGenerator;
    }
    
    public void afterPropertiesSet() throws Exception
    {
        super.afterPropertiesSet();
        if(flowStateIdGenerator == null)
        {
            setRandom(new SecureRandom());
        }
    }
    
    public String storeState(HttpServletRequest request, Continuation state)
    {
        Long id;
        Map stateMap = getStateMap(request, true);
        Object objState;
        if(maxStates > 1)
        {
            // Must serialize the continuation so it is deep-copied. If we 
            // didn't do this, we couldn't keep multiple independent states.
            Map stubsToFunctions = new HashMap();
            try
            {
                objState = new LocallySerializedContinuation(
                        serializeContinuation(state, stubsToFunctions), 
                        stubsToFunctions);
            }
            catch(RuntimeException e)
            {
                throw e;
            }
            catch(Exception e)
            {
                throw new FlowStateStorageException("Failed to store state", e);
            }
        }
        else
        {
            // Optimization for maxStates == 1. Since it is not possible to go
            // back to an earlier continuation, there is no need to serialize
            // it solely for purposes of deep copying
            objState = state;
        }
        synchronized(stateMap)
        {
            for(;;)
            {
                id = flowStateIdGenerator.generateStateId(state);
                if(id.longValue() < 0)
                {
                    throw new RuntimeException("Got negative id");
                }
                if(flowStateIdGenerator.dependsOnContinuation() || 
                        !stateMap.containsKey(id))
                {
                    stateMap.put(id, new ReplicatableContinuation(this, 
                            objState));
                    break;
                }
            }
        }
        return Long.toHexString(id.longValue());
    }
    
    public Continuation getState(HttpServletRequest request, String id)
    {
        Map stateMap = getStateMap(request, false);
        if(stateMap == null)
        {
            return null;
        }
        try
        {
            ReplicatableContinuation rc;
            synchronized(stateMap)
            {
                rc = (ReplicatableContinuation)stateMap.get(Long.valueOf(id, 16));
            }
            if(rc == null)
            {
                return null;
            }
            return getContinuation(rc);
        }
        catch(RuntimeException e)
        {
            throw e;
        }
        catch(Exception e)
        {
            throw new FlowStateStorageException(
                    "Failed to load replicated state for stateId=" + id, e);
        }
    }

    private Continuation getContinuation(ReplicatableContinuation rc) 
    throws Exception, AssertionError
    {
        synchronized(rc)
        {
            Object oc = rc.getContinuation();
            if(oc instanceof Continuation)
            {
                return ((Continuation)oc);
            }
            else if(oc instanceof LocallySerializedContinuation)
            {
                LocallySerializedContinuation lsc = (LocallySerializedContinuation)oc;
                Continuation c = deserializeContinuation(
                        lsc.serializedState, lsc.stubsToFunctions);
                rc.setContinuation(this, c);
                return c;
            }
            else
            {
                throw new AssertionError();
            }
        }
    }
    
    private Map getStateMap(HttpServletRequest request, boolean create)
    {
        HttpSession session = request.getSession(create);
        if(session == null)
        {
            return null;
        }
        Map m = (Map)session.getAttribute(MAP_KEY);
        if(m == null)
        {
            synchronized(session)
            {
                m = (Map)session.getAttribute(MAP_KEY);
                if(m == null)
                {
                    m = new LinkedHashMap(maxStates * 4 / 3, .75f, true)
                    {
                        protected boolean removeEldestEntry(Entry eldest)
                        {
                            return size() > maxStates;
                        }
                    };
                    session.setAttribute(MAP_KEY, m);
                }
            }
        }
        return m;
    }

    /**
     * Enumerates all the continuations bound to a particular HTTP session. Can
     * be used from a session listener to post-process continuations in 
     * invalidated/expired sessions.
     * @param session the http session
     * @param callback a callback that will be invoked for each continuation.
     */
    public void forEachContinuation(HttpSession session, 
            ContinuationCallback callback)
    {
        Map m = (Map)session.getAttribute(MAP_KEY);
        if(m == null)
        {
            return;
        }
        for (Iterator iter = m.entrySet().iterator(); iter.hasNext();)
        {
            Map.Entry entry = (Map.Entry) iter.next();
            String id = Long.toHexString(((Long)entry.getKey()).longValue());
            try
            {
                callback.forContinuation(id, getContinuation(
                        ((ReplicatableContinuation)entry.getValue())));
            }
            catch(Exception e)
            {
                log.warn("Failed to process continuation " + id, e);
            }
        }
    }

    /**
     * Should be implemented by classes used as callbacks for 
     * {@link HttpSessionFlowStateStorage#forEachContinuation(HttpSession, 
     * ContinuationCallback)}
     * @author Attila Szegedi
     * @version $Id: $
     */
    public static interface ContinuationCallback
    {
        /**
         * Invoked to process a particular continuation.
         * @param id the ID of the continuation
         * @param continuation the continuation itself
         * @throws Exception
         */
        public void forContinuation(String id, Continuation continuation)
        throws Exception;
    }
    
    LocallySerializedContinuation serializeContinuationAccessor(
            Continuation state) throws Exception
    {
        return new LocallySerializedContinuation(serializeContinuation(state, 
                null), null);
    }
    
    /**
     * This class can serialize the continuation as a properly stubbed byte
     * array. This is important in HTTP session replication scenarios, as it
     * will allow the continuation to be transferred to another servlet 
     * container and appropriately deserialized.
     * @author Attila Szegedi
     * @version $Id: $
     */
    private static class ReplicatableContinuation implements Serializable
    {
        private static final long serialVersionUID = 1L;

        private transient HttpSessionFlowStateStorage storage;
        private transient Object state;

        ReplicatableContinuation(HttpSessionFlowStateStorage storage, 
                Object state)
        {
            setContinuation(storage, state);
        }
        
        void setContinuation(HttpSessionFlowStateStorage storage, 
                Object state)
        {
            this.storage = storage;
            this.state = state;
        }
        
        Object getContinuation()
        {
            return state;
        }
        
        private void readObject(ObjectInputStream in) throws IOException, 
        ClassNotFoundException
        {
            state = in.readObject();
        }
        
        private void writeObject(ObjectOutputStream out) throws IOException
        {
            if(state instanceof LocallySerializedContinuation)
            {
                // Already serialized -- just write it as is
                out.writeObject(state);
            }
            else if(state instanceof Continuation)
            {
                try
                {
                    // Not serialized yet -- use the storage to serialize it
                    // first
                    out.writeObject(storage.serializeContinuationAccessor(
                            (Continuation)state));
                }
                catch(IOException e)
                {
                    throw e;
                }
                catch(RuntimeException e)
                {
                    throw e;
                }
                catch(Exception e)
                {
                    throw new UndeclaredThrowableException(e);
                }
            }
            else
            {
                throw new AssertionError();
            }
        }
    }
    
    private static class LocallySerializedContinuation implements Serializable
    {
        private static final long serialVersionUID = 1L;

        final byte[] serializedState;
        transient final Map stubsToFunctions;
        
        LocallySerializedContinuation(byte[] serializedState, Map stubsToFunctions)
        {
            this.serializedState = serializedState;
            this.stubsToFunctions = stubsToFunctions;
        }
    }
}