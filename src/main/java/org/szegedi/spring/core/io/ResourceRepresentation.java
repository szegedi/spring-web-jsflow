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
import java.net.URL;
import java.net.URLConnection;
import org.springframework.core.io.Resource;

/**
 * Represents a class that is capable of loading and caching a parsed 
 * representation of contents of a Spring resource. In case the resource has a
 * URL representation and it changes, the object will be reloaded. The code 
 * also has a special optimization for "file:" URLs. All subclasses have to do
 * is implement the {@link #loadRepresentation(InputStream)} method.
 * @author Attila Szegedi
 * @version $Id: $
 */
public abstract class ResourceRepresentation
{
    private final Resource resource;
    private Object representation;
    private long lastModified;
    private long lastChecked;
    
    /**
     * Constructs a new representation for the specified resource.
     * @param resource
     */
    public ResourceRepresentation(Resource resource)
    {
        this.resource = resource;
    }
    
    /**
     * Returns the representation of the resource.
     * @param noStaleCheckPeriod If the resource's timestamp was last checked
     * no earlier than the specified number of milliseconds, it won't be 
     * checked and the cached representation will be returned instead.
     * @return the representation of the resource
     * @throws Exception if there was an exception during loading of the 
     * representation
     */
    public synchronized Object getRepresentation(long noStaleCheckPeriod)
    throws Exception
    {
        long now = System.currentTimeMillis();
        if(representation != null && now - noStaleCheckPeriod < lastChecked)
        {
            return representation;
        }
        return getRepresentationInternal(now);
    }
    
    /**
     * Returns the representation of the resource. Equal to 
     * {@link #getRepresentation(long)} with 0 parameter, although a bit more
     * optimized.
     * @return the representation of the resource
     * @throws Exception if there was an exception during loading of the 
     * representation
     */
    public synchronized Object getRepresentation() throws Exception
    {
        return getRepresentationInternal(System.currentTimeMillis());
    }
    
    private Object getRepresentationInternal(long now) throws Exception
    {
        URL url;
        try
        {
            url = resource.getURL();
        }
        catch(IOException e)
        {
            url = null;
        }
        long newLastModified;
        URLConnection conn;
        if(url != null)
        {
            if("file".equals(url.getProtocol()))
            {
                newLastModified = resource.getFile().lastModified();
                conn = null;
            }
            else
            {
                conn = url.openConnection();
                newLastModified = conn.getLastModified();
            }
        }
        else
        {
            newLastModified = 0;
            conn = null;
        }
        lastChecked = now;
        if(representation == null || newLastModified != lastModified)
        {
            lastModified = newLastModified;
            InputStream in = conn == null ? resource.getInputStream() : 
                conn.getInputStream();
            try
            {
                representation = loadRepresentation(in);
            }
            finally
            {
                in.close();
            }
        }
        else if(conn != null)
        {
            conn.getInputStream().close();
        }
        return representation;
    }
    
    public Resource getResource()
    {
        return resource;
    }
    
    /**
     * Implement in subclasses to load the representation of the resource.
     * @param in the input stream with resource bytes
     * @return the object representing the resource.
     * @throws IOException
     */
    protected abstract Object loadRepresentation(InputStream in) throws IOException;
}
