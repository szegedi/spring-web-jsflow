package org.szegedi.spring.core.io;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.springframework.core.io.Resource;

/**
 * A resource representation that treats the resource as a Java properties file
 * and represents the resource with a {@link java.util.Properties} object 
 * initialized with those properties.
 * @author Attila Szegedi
 * @version $Id: $
 */
public class PropertyResourceRepresentation extends ResourceRepresentation
{
    public PropertyResourceRepresentation(Resource resource)
    {
        super(resource);
    }

    protected Object loadRepresentation(InputStream in) throws IOException
    {
        Properties properties = new Properties();
        properties.load(in);
        return properties;
    }
}
