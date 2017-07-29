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
package org.szegedi.spring.core.io;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.core.io.Resource;

/**
 * A resource representation that treats the resource as a Java properties file
 * and represents the resource with a bean initialized with those properties.
 * @author Attila Szegedi
 * @version $Id: $
 */
public abstract class BeanResourceRepresentation extends
PropertyResourceRepresentation
{
    protected BeanResourceRepresentation(final Resource resource)
    {
        super(resource);
    }

    /**
     * Implement in a subclass to create an uninitialized bean.
     * @return a new bean representing this resource.
     */
    protected abstract Object instantiateBean();

    protected Object loadRepresentation(final InputStream in) throws IOException
    {
        final Properties props = (Properties)super.loadRepresentation(in);
        final Object bean = instantiateBean();
        new BeanWrapperImpl(bean).setPropertyValues(new MutablePropertyValues(
                props), false);
        return bean;
    }
}
