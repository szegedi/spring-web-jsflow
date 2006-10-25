package org.szegedi.spring.web.jsflow;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptableObject;

/**
 * A class implementing this interface can hook into the initialization of the
 * global library scope and manipulate it in any way it desires.
 * @author Attila Szegedi
 * @version $Id: $
 */
public interface LibraryCustomizer
{
    /**
     * Invoked by {@link ScriptStorage} as part of the initialization of the
     * global library scope, after it was already populated with the global
     * Rhino-in-Spring functions. The method can manipulate the library scope
     * in any way it wishes. After this method returns, the scope will be 
     * sealed and no further modification will be possible.
     * @param cx the active Rhino context for the current thread
     * @param libraryScope the library scope to manipulate
     */
    public void customizeLibrary(Context cx, ScriptableObject libraryScope);
}
