/*
   Copyright 2006 2007 Attila Szegedi

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

import java.io.InvalidObjectException;
import java.io.Serializable;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mozilla.javascript.NativeContinuation;
import org.szegedi.spring.web.jsflow.support.FlowStateIdGenerator;
import org.szegedi.spring.web.jsflow.support.FlowStateSerializer;
import org.szegedi.spring.web.jsflow.support.RandomFlowStateIdGenerator;

/**
 * An implementation for flow state storage that stores flow states in the
 * {@link javax.servlet.http.HttpSession} objects. As states are stored
 * privately to HTTP sessions, no crossover between sessions is possible
 * (requesting a state from a session it doesn't belong to won't work, and it is
 * also possible to have identical flowstate ids in two sessions without any
 * interference).
 * 
 * @author Attila Szegedi
 * @version $Id$
 */
public class HttpSessionFlowStateStorage extends FlowStateSerializer implements FlowStateStorage {
    private static final Log log = LogFactory.getLog(HttpSessionFlowStateStorage.class);

    private static final String STUB_PROVIDER_KEY = "provider#" + HttpSessionFlowStateStorage.class.getName();
    private static final String STUB_RESOLVER_KEY = "resolver#" + HttpSessionFlowStateStorage.class.getName();
    private static final String MAP_KEY = "map#" + HttpSessionFlowStateStorage.class.getName();

    private int maxStates = 100;
    private FlowStateIdGenerator flowStateIdGenerator;

    /**
     * Sets the maximum number of states per HTTP session that this manager will
     * store. If the number is exceeded, the least recently used state will be
     * discareded. Defaults to 100.
     * 
     * @param maxStates
     */
    public void setMaxStates(final int maxStates) {
        if (maxStates <= 0) {
            throw new IllegalArgumentException("maxStates <= 0");
        }
        this.maxStates = maxStates;
    }

    /**
     * Sets a source of randomness for generating state IDs. If not explicitly
     * set, it will create and use a private instance of {@link SecureRandom}.
     * 
     * @param random
     * @deprecated use {@link #setFlowStateIdGenerator(FlowStateIdGenerator)}
     *             with a {@link RandomFlowStateIdGenerator} instead.
     */
    @Deprecated
    public void setRandom(final Random random) {
        setRandomInternal(random);
    }

    private void setRandomInternal(final Random random) {
        final RandomFlowStateIdGenerator idGen = new RandomFlowStateIdGenerator();
        idGen.setRandom(random);
        flowStateIdGenerator = idGen;
    }

    public void setFlowStateIdGenerator(final FlowStateIdGenerator flowStateIdGenerator) {
        this.flowStateIdGenerator = flowStateIdGenerator;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        super.afterPropertiesSet();
        if (flowStateIdGenerator == null) {
            setRandom(new SecureRandom());
        }
    }

    /**
     * Binds a stub provider into the HttpSession. Client code can use this
     * provider to provide serialization stubs for various session-related
     * objects
     * 
     * @param session
     *            the HttpSession to bind the provider into
     * @param provider
     *            the provider
     * @since 1.2
     */
    public static void bindStubProvider(final HttpSession session, final StubProvider provider) {
        session.setAttribute(STUB_PROVIDER_KEY, provider);
    }

    /**
     * Binds a stub resolver into the HttpSession. Client code can use this
     * resolver to resolve serialization stubs for various session-related
     * objects
     * 
     * @param session
     *            the HttpSession to bind the resolver into
     * @param resolver
     *            the resolver
     * @since 1.2
     */
    public static void bindStubResolver(final HttpSession session, final StubResolver resolver) {
        session.setAttribute(STUB_RESOLVER_KEY, resolver);
    }

    @Override
    public String storeState(final HttpServletRequest request, final NativeContinuation state) {
        Long id;
        final Map stateMap = getStateMap(request, true);
        byte[] serialized;
        // Must serialize the continuation so it is deep-copied. If we
        // didn't do this, we couldn't keep multiple independent states.
        final Map stubsToFunctions = new HashMap();
        try {
            serialized = serializeContinuation(state, stubsToFunctions,
                    (StubProvider) request.getSession().getAttribute(STUB_PROVIDER_KEY));
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new FlowStateStorageException("Failed to store state", e);
        }
        synchronized (stateMap) {
            for (;;) {
                id = flowStateIdGenerator.generateStateId(state);
                if (id.longValue() < 0) {
                    throw new RuntimeException("Got negative id");
                }
                if (flowStateIdGenerator.dependsOnContinuation() || !stateMap.containsKey(id)) {
                    stateMap.put(id, new LocallySerializedContinuation(serialized, stubsToFunctions));
                    // NOTE: this works because stateMap is a LinkedHashMap.
                    // Ordinarily, we'd subclass it and override
                    // removeEldestEntry(). Unfortunately, subclassing
                    // logically managed classes and overriding their protected
                    // methods is disallowed in Terracotta
                    while (stateMap.size() > maxStates) {
                        stateMap.entrySet().iterator().remove();
                    }
                    break;
                }
            }
        }
        return Long.toHexString(id.longValue());
    }

    @Override
    public NativeContinuation getState(final HttpServletRequest request, final String id) {
        final Map stateMap = getStateMap(request, false);
        if (stateMap == null) {
            return null;
        }
        try {
            LocallySerializedContinuation serialized;
            synchronized (stateMap) {
                serialized = (LocallySerializedContinuation) stateMap.get(Long.valueOf(id, 16));
            }
            if (serialized == null) {
                return null;
            }
            return getContinuation(serialized, request.getSession(false));
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new FlowStateStorageException("Failed to load replicated state for stateId=" + id, e);
        }
    }

    private NativeContinuation getContinuation(final LocallySerializedContinuation lsc, final HttpSession session)
            throws Exception, AssertionError {
        final Map stubsToFunctions = lsc.getStubsToFunctions();
        StubResolver stubResolver;
        if (session != null) {
            stubResolver = (StubResolver) session.getAttribute(STUB_RESOLVER_KEY);
        } else {
            stubResolver = null;
        }

        if (stubResolver != null) {
            if (stubsToFunctions != null && !stubsToFunctions.isEmpty()) {
                final StubResolver fstubResolver = stubResolver;
                stubResolver = new StubResolver() {
                    @Override
                    public Object resolveStub(final Object stub) throws InvalidObjectException {
                        final Object obj = stubsToFunctions.get(stub);
                        return obj != null ? obj : fstubResolver.resolveStub(stub);
                    }
                };
            }
        } else if (stubsToFunctions != null && !stubsToFunctions.isEmpty()) {
            stubResolver = new StubResolver() {
                @Override
                public Object resolveStub(final Object stub) throws InvalidObjectException {
                    return stubsToFunctions.get(stub);
                }
            };
        }

        return deserializeContinuation(lsc.getSerializedState(), stubResolver);
    }

    private Map getStateMap(final HttpServletRequest request, final boolean create) {
        final HttpSession session = request.getSession(create);
        if (session == null) {
            return null;
        }
        Map m = (Map) session.getAttribute(MAP_KEY);
        if (m == null) {
            synchronized (session) {
                m = (Map) session.getAttribute(MAP_KEY);
                if (m == null) {
                    m = new LinkedHashMap(maxStates);
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
     * 
     * @param session
     *            the http session
     * @param callback
     *            a callback that will be invoked for each continuation.
     */
    public void forEachContinuation(final HttpSession session, final ContinuationCallback callback) {
        final Map m = (Map) session.getAttribute(MAP_KEY);
        if (m == null) {
            return;
        }
        for (final Iterator iter = m.entrySet().iterator(); iter.hasNext();) {
            final Map.Entry entry = (Map.Entry) iter.next();
            final String id = Long.toHexString(((Long) entry.getKey()).longValue());
            try {
                callback.forContinuation(id,
                        getContinuation((LocallySerializedContinuation) entry.getValue(), session));
            } catch (final Exception e) {
                log.warn("Failed to process continuation " + id, e);
            }
        }
    }

    /**
     * Should be implemented by classes used as callbacks for
     * {@link HttpSessionFlowStateStorage#forEachContinuation(HttpSession, ContinuationCallback)}
     * 
     * @author Attila Szegedi
     * @version $Id$
     */
    public static interface ContinuationCallback {
        /**
         * Invoked to process a particular continuation.
         * 
         * @param id
         *            the ID of the continuation
         * @param continuation
         *            the continuation itself
         * @throws Exception
         */
        public void forContinuation(String id, NativeContinuation continuation) throws Exception;
    }

    private static class LocallySerializedContinuation implements Serializable {
        private static final long serialVersionUID = 1L;

        private final byte[] serializedState;
        private transient final Map stubsToFunctions;

        LocallySerializedContinuation(final byte[] serializedState, final Map stubsToFunctions) {
            this.serializedState = serializedState;
            this.stubsToFunctions = stubsToFunctions;
        }

        byte[] getSerializedState() {
            return serializedState;
        }

        Map getStubsToFunctions() {
            return stubsToFunctions;
        }
    }
}