package org.szegedi.spring.core.io;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.core.io.Resource;

/**
 * A resource representation that treats the resource as a Java properties file
 * and represents the resource with a bean bean initialized with those 
 * properties.
 * @author Attila Szegedi
 * @version $Id: $
 */
public abstract class BeanResourceRepresentation extends ResourceRepresentation
{
    protected BeanResourceRepresentation(Resource resource)
    {
        super(resource);
    }

    /**
     * Implement in a subclass to create an uninitialized bean.
     * @return a new bean representing this resource.
     */
    protected abstract Object instantiateBean();
    
    protected Object loadRepresentation(InputStream in) throws IOException
    {
        Properties props = new Properties();
        props.load(in);
        Object bean = instantiateBean();
        new BeanWrapperImpl(bean).setPropertyValues(new MutablePropertyValues(
                props), false);
        return bean;
    }
}
