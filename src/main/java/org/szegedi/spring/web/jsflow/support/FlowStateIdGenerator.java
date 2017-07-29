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

import org.mozilla.javascript.NativeContinuation;
import org.szegedi.spring.web.jsflow.FlowStateStorage;

/**
 * An interface for objects that generate flow state IDs. Might be used by
 * various {@link FlowStateStorage} implementations.
 * @author Attila Szegedi
 * @version $Id: $
 */
public interface FlowStateIdGenerator
{
    /**
     * Generate the flow state id for the specified state.
     * @param state
     * @return the new ID. Must not be negative.
     */
    public Long generateStateId(NativeContinuation state);

    /**
     * Returns true if the return value of {@link #generateStateId(NativeContinuation)}
     * depends on the passed state (that is, for a given state object, it will
     * always return the same ID). Implementations of the interface that
     * implement counters or random generators will return false.
     * Implementations that use some information contained in the continuation
     * will return true. If an implementation returns true, then returning an
     * ID equal to an existing state's ID might cause the user of the class to
     * overwrite the existing ID. If an implementation returns false, the user
     * of the class is encouraged to invoke the
     * {@link #generateStateId(NativeContinuation)} method repeatedly until it
     * receives a non-conflicting ID.
     * @return whether this generator generates IDs that depend on the state.
     */
    public boolean dependsOnContinuation();
}
