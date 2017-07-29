package org.szegedi.spring.web.jsflow;

import javax.servlet.http.HttpServletRequest;

/**
 * A script selection strategy that uses the components of the request URL to
 * select a script to run.
 * @author Attila Szegedi
 * @version $Id: $
 */
public class UrlScriptSelectionStrategy implements ScriptSelectionStrategy
{
    private String resourcePath = "";
    private boolean usePathInfo;
    private boolean useServletPath;
    
    /**
     * Sets the resource path. If neither path info nor servlet path are used,
     * then this strategy will always select a single script, the resource 
     * path of its source file being specified in this property. If either 
     * servlet path or path info (or both) are used, then the strategy will 
     * select multiple scripts selected by servlet path and/or path info, and 
     * the path specified here will be used as a common prefix for the resource 
     * paths of script source files. Defaults to empty string, which is a handy 
     * value in case no prefix is required and either servlet path or path info 
     * are used. Be aware that this prefix can be further prefixed by a prefix
     * specified using {@link ScriptStorage#setPrefix(String)}) ScriptStorage
     * class.
     * @param resourcePath
     */
    public void setResourcePath(String resourcePath)
    {
        if(resourcePath == null)
        {
            throw new IllegalArgumentException("resourcePath == null");
        }
        this.resourcePath = resourcePath;
    }
    
    /**
     * Whether to use the path info portion of the request URI when selecting a
     * script to run. If true, the name of the script to run will be determined
     * by concatenating resource path + (optionally servlet path) + path info. 
     * If false, the name of the selected script will be determined by 
     * concatenating resource path + (optionally servlet path). Defaults to 
     * false.
     * @param usePathInfo
     */
    public void setUsePathInfo(boolean usePathInfo)
    {
        this.usePathInfo = usePathInfo;
    }

    /**
     * Whether to use the servlet path portion of the request URI when looking 
     * up the script to run. If true, the name of the script to run will be 
     * determined by concatenating resource path + servlet path + (optionally 
     * path info). If false, the name of the script to run will be determined by
     * concatenating resource path + (optionally path info). Defaults to false.
     * @param useServletPath
     */
    public void setUseServletPath(boolean useServletPath)
    {
        this.useServletPath = useServletPath;
    }
    
    public String getScriptPath(HttpServletRequest request)
    {
        if(!(usePathInfo || useServletPath))
        {
            return resourcePath;
        }
        StringBuffer buf = new StringBuffer(resourcePath);
        if(useServletPath)
        {
            String servletPath  = (String) request.getAttribute(
                    "javax.servlet.include.servlet_path");
             if(servletPath == null)
            {
                servletPath = request.getServletPath();
            }
            if(servletPath != null)
            {
                buf.append(servletPath);
            }
        }
        if(usePathInfo)
        {
            String pathInfo = (String) request.getAttribute(
                    "javax.servlet.include.path_info");
            if(pathInfo == null)
            {
                pathInfo = request.getPathInfo();
            }
            if(pathInfo != null)
            {
                buf.append(pathInfo);
            }
        }
        return buf.toString();
    }
}
