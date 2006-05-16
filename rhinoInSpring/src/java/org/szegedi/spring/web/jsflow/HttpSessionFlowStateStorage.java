package org.szegedi.spring.web.jsflow;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.szegedi.spring.web.jsflow.support.AbstractFlowStateStorage;

/**
 * An implementation for flow state storage that stores flow states in 
 * the {@link javax.servlet.http.HttpSession} objects. As states are stored
 * privately to HTTP sessions, no crossover between sessions is possible 
 * (requesting a state from a session it doesn't belong to won't work, and it is 
 * also possible to have identical flowstate ids in two sessions without any 
 * interference).
 * @author Attila Szegedi
 * @version $Id: $
 */
public class HttpSessionFlowStateStorage extends AbstractFlowStateStorage
{
    private static final String MAP_KEY = HttpSessionFlowStateStorage.class.getName(); 

    private int maxStates = 100;
    private Random random;
    
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
     */
    public void setRandom(Random random)
    {
        this.random = random;
    }
    
    public void afterPropertiesSet() throws Exception
    {
        super.afterPropertiesSet();
        if(random == null)
        {
            random = new SecureRandom();
        }
    }
    
    protected String storeSerializedState(HttpServletRequest request, byte[] state)
    {
        Long id;
        Map stateMap = getStateMap(request, true);
        synchronized(stateMap)
        {
            for(;;)
            {
                id = new Long(random.nextLong() & Long.MAX_VALUE);
                if(!stateMap.containsKey(id))
                {
                    stateMap.put(id, state);
                    break;
                }
            }
        }
        return Long.toHexString(id.longValue());
    }
    
    protected byte[] getSerializedState(HttpServletRequest request, String id)
    {
        Map stateMap = getStateMap(request, false);
        if(stateMap == null)
        {
            return null;
        }
        try
        {
            synchronized(stateMap)
            {
                return (byte[])stateMap.get(Long.valueOf(id, 16));
            }
        }
        catch(NumberFormatException e)
        {
            throw e; //return null;
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
}
