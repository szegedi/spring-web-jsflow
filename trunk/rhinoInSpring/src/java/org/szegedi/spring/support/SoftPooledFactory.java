package org.szegedi.spring.support;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.List;

public abstract class SoftPooledFactory
{
    private final List pool = new ArrayList();
    
    public Reference get() throws Exception
    {
        synchronized(pool)
        {
            while(!pool.isEmpty())
            {
                Reference ref = (Reference)pool.remove(pool.size() - 1);
                Object obj = ref.get();
                if(obj != null)
                {
                    return ref; 
                }
            }
        }
        return new SoftReference(create());
    }
    
    public void put(Reference ref)
    {
        synchronized(pool)
        {
            pool.add(ref);
        }
    }
    
    protected abstract Object create() throws Exception;
}
