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
package org.szegedi.spring.support;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.List;

/**
 * A factory of soft-reference pooled objects. While we are aware that pooling
 * <i>in general</i> is harmful on modern VMs, we do actually have use cases - 
 * i.e. expensively initialized cryptographical transformators - that make 
 * sense to pool.
 * @author Attila Szegedi
 * @version $Id$
 */
public abstract class SoftPooledFactory
{
    private final List pool = new ArrayList();
    
    public Reference get() throws Exception
    {
        synchronized(pool)
        {
            while(!pool.isEmpty())
            {
                final Reference ref = (Reference)pool.remove(pool.size() - 1);
                final Object obj = ref.get();
                if(obj != null)
                {
                    return ref; 
                }
            }
        }
        return new SoftReference(create());
    }
    
    public void put(final Reference ref)
    {
        synchronized(pool)
        {
            pool.add(ref);
        }
    }
    
    protected abstract Object create() throws Exception;
}
