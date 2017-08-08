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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import org.mozilla.javascript.NativeContinuation;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.serialize.ScriptableInputStream;
import org.mozilla.javascript.serialize.ScriptableOutputStream;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.szegedi.spring.web.jsflow.FlowController;
import org.szegedi.spring.web.jsflow.HostObject;
import org.szegedi.spring.web.jsflow.ScriptStorage;

/**
 * A class able to serialize and deserialize a continuation within a specified
 * application context.
 *
 * @author Attila Szegedi
 * @version $Id: $
 */
public abstract class FlowStateSerializer implements ApplicationContextAware, InitializingBean {
    private ScriptStorage scriptStorage;
    private PersistenceSupport persistenceSupport;
    private ApplicationContext applicationContext;
    private Map beansToStubs = Collections.EMPTY_MAP;

    public void setScriptStorage(final ScriptStorage scriptStorage) {
        this.scriptStorage = scriptStorage;
        persistenceSupport = scriptStorage.getPersistenceSupport();
    }

    public ScriptStorage getScriptStorage() {
        return scriptStorage;
    }

    @Override
    public void setApplicationContext(final ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (scriptStorage == null) {
            setScriptStorage(FlowController.createDefaultScriptStorage(applicationContext));
        }
        createStubInfo();
    }

    private void createStubInfo() {
        final String[] names = BeanFactoryUtils.beanNamesIncludingAncestors(applicationContext);
        final Map beansToStubs = new IdentityHashMap();
        for (int i = 0; i < names.length; i++) {
            final String name = names[i];
            beansToStubs.put(applicationContext.getBean(name), new ApplicationContextBeanStub(name));
        }
        beansToStubs.put(".", applicationContext);
        this.beansToStubs = beansToStubs;
    }

    /**
     * Serializes a continuation. All script function object references as well
     * as all references to objects defined in the application context will be
     * replaced by named stubs. Additionally, a digital fingerprint of the
     * internal JS bytecode representation of all JS functions on the
     * continuation's stack is written.
     *
     * @param state
     *            the continuation to serialize
     * @param stubbedFunctions
     *            a map that'll receive all mappings of stubs to functions. Can
     *            be used to locally deserialize a continuation and reconnect it
     *            with exact same functions that were current during
     *            serialization (thus making continuations immune to script
     *            reloading within a single JVM run). Can be null if tracking of
     *            stubs is not required.
     * @return the serialized form
     * @throws Exception
     */
    protected byte[] serializeContinuation(final NativeContinuation state, final Map stubbedFunctions,
            final StubProvider stubProvider) throws Exception {
        final ByteArrayOutputStream bout = new ByteArrayOutputStream();
        final ObjectOutputStream out = new ContinuationOutputStream(bout, state, stubbedFunctions, stubProvider);
        out.writeObject(FunctionFingerprintManager.getFingerprints(state));
        out.writeObject(state);
        out.close();
        return bout.toByteArray();
    }

    /**
     * Deserializes a continuation. All stubs written during serialization are
     * resolved to appropriate objects within this application context.
     * Additionally, digital fingerprints of the functions on the continuation's
     * stack are matched to the fingerprints of the same functions currently in
     * the memory. If they don't match (i.e. the script was modified since the
     * continuation was serialized), an exception is thrown to prevent undefined
     * behaviour as the continuation's stack might now contain invalid return
     * addresses.
     *
     * @param b
     *            the serialized continuation
     * @return the deserialized continuation
     * @throws Exception
     */
    protected NativeContinuation deserializeContinuation(final byte[] b, final StubResolver stubResolver)
            throws Exception {
        final ObjectInputStream in = new ContinuationInputStream(new ByteArrayInputStream(b), stubResolver);
        final Object fingerprints = in.readObject();
        final NativeContinuation cont = (NativeContinuation) in.readObject();
        FunctionFingerprintManager.checkFingerprints(cont, fingerprints);
        return cont;
    }

    private class ContinuationInputStream extends ScriptableInputStream {
        private final StubResolver stubResolver;

        public ContinuationInputStream(final InputStream in, final StubResolver stubResolver) throws IOException {
            super(in, persistenceSupport.getLibrary());
            this.stubResolver = stubResolver;
        }

        @Override
        protected Object resolveObject(final Object obj) throws IOException {
            if (obj instanceof ApplicationContextBeanStub) {
                final ApplicationContextBeanStub stub = (ApplicationContextBeanStub) obj;
                final Object robj = applicationContext.getBean(stub.beanName);
                if (robj != null) {
                    return robj;
                } else {
                    throw new InvalidObjectException("No bean with name [" + stub.beanName + "] found");
                }
            } else {
                Object robj;
                if (stubResolver != null) {
                    robj = stubResolver.resolveStub(obj);
                    if (robj != null) {
                        return robj;
                    }
                }
                try {
                    robj = persistenceSupport.resolveFunctionStub(obj);
                } catch (final IOException e) {
                    throw e;
                } catch (final RuntimeException e) {
                    throw e;
                } catch (final Exception e) {
                    throw new UndeclaredThrowableException(e);
                }
                if (robj != null) {
                    return robj;
                }
            }
            return super.resolveObject(obj);
        }
    }

    private class ContinuationOutputStream extends ScriptableOutputStream {
        private final Map stubbedFunctions;
        private final StubProvider stubProvider;

        public ContinuationOutputStream(final OutputStream out, final NativeContinuation cont,
                final Map stubbedFunctions, final StubProvider stubProvider) throws IOException {
            super(out, ScriptableObject.getTopLevelScope(cont).getPrototype());
            addExcludedName(HostObject.CLASS_NAME);
            addExcludedName(HostObject.CLASS_NAME + ".prototype");
            this.stubbedFunctions = stubbedFunctions;
            this.stubProvider = stubProvider;
        }

        @Override
        protected Object replaceObject(final Object obj) throws IOException {
            // App context
            Object stub = beansToStubs.get(obj);
            if (stub != null) {
                return stub;
            }

            // Thread context
            if (stubProvider != null) {
                stub = stubProvider.getStub(obj);
                if (stub != null) {
                    return stub;
                }
            }

            // Functions
            stub = persistenceSupport.getFunctionStub(obj);
            if (stub != null) {
                if (stubbedFunctions != null) {
                    stubbedFunctions.put(stub, obj);
                }
                return stub;
            }
            return super.replaceObject(obj);
        }
    }

    private static class ApplicationContextBeanStub implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String beanName;

        ApplicationContextBeanStub(final String beanName) {
            this.beanName = beanName;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj instanceof ApplicationContextBeanStub) {
                return ((ApplicationContextBeanStub) obj).beanName.equals(beanName);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return beanName.hashCode();
        }

        @Override
        public String toString() {
            return "stub:" + beanName;
        }
    }

    /**
     * An interface that can be implemented to provide further context-specific
     * stubs.
     *
     * @author Attila Szegedi
     * @version $Id: $
     * @since 1.2
     */
    public static interface StubProvider {
        /**
         * Return a stub for an object.
         *
         * @param obj
         *            the object to stub
         * @return the stub for the object, or null if the provider can not stub
         *         the object.
         */
        Object getStub(Object obj);
    }

    /**
     * An interface that can be implemented to resolve further context-specific
     * stubs.
     *
     * @author Attila Szegedi
     * @version $Id: $
     * @since 1.2
     */
    public static interface StubResolver {
        /**
         * Resolves a stub into an object.
         *
         * @param stub
         *            the stub to resolve
         * @return the resolved object, or null if the resolved does not
         *         recognize the object as a stub.
         * @throws InvalidObjectException
         *             if the resolver recognizes the stub, but is unable to
         *             provide the resolved object
         */
        Object resolveStub(Object stub) throws InvalidObjectException;
    }
}