package org.szegedi.spring.web.jsflow;

import javax.servlet.http.HttpServletRequest;

import org.mozilla.javascript.continuations.Continuation;

/**
 * A storage for interim flow states. It is sufficient (and recommended) to have
 * exactly one flow state storage per application context, although you can
 * have exotic scenarios with several controllers, each with a different 
 * storage if you really have to. If you have a single storage in the 
 * application context, then all flow controllers that don't have their own
 * storage set will use it.
 * @author Attila Szegedi
 * @version $Id: $
 */
public interface FlowStateStorage
{
    /**
     * Stores the state associated with the current request
     * @param request the HTTP request
     * @param state the state
     * @return an identifier for the state. The identifier is unique at least in
     * the scope of the current HTTP request's session.
     */
    public String storeState(HttpServletRequest request, Continuation state);

    /**
     * Retrieves the state associated with a request
     * @param request the HTTP request
     * @param id the unique identifier for the flow state
     * @return the flow state, or null if it couldn't be resolved
     */
    public Continuation getState(HttpServletRequest request, String id);
}