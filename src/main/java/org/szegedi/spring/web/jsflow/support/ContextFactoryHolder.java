/*
   Copyright 2017 Attila Szegedi

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

import org.mozilla.javascript.ContextFactory;

/**
 * A simple holder for a Rhino {@link ContextFactory} that returns {@link ContextFactory#getGlobal()}
 * if no context factory is explicitly set.
 */
public class ContextFactoryHolder {
    private ContextFactory contextFactory;

    /**
     * Sets the Rhino context factory to use. If not set, the global context
     * factory returned by {@link ContextFactory#getGlobal()} will be used.
     *
     * @param contextFactory
     */
    public void setContextFactory(final ContextFactory contextFactory) {
        this.contextFactory = contextFactory;
    }

    /**
     * Returns the context factory configured in this holder, or
     * {@link ContextFactory#getGlobal()} if it is null.
     * @return the context factory configured in this holder.
     */
    public ContextFactory getContextFactory() {
        return contextFactory != null ? contextFactory : ContextFactory.getGlobal();
    }
}
