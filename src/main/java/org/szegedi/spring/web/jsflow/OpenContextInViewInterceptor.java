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
package org.szegedi.spring.web.jsflow;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * A Spring handler interceptor that opens a Rhino context and associates it 
 * with the current thread for the complete duration of the handling, including 
 * view rendering. In most situations, you will not need a live Rhino Context 
 * object during view rendering, so you need not use this interceptor. However, 
 * if you receive an exception saying there is no current Rhino context during 
 * view rendering, you will need to use the interceptor.
 * @author Attila Szegedi
 * @version $Id$
 */
public class OpenContextInViewInterceptor implements HandlerInterceptor
{
    private ContextFactory contextFactory;
    
    /**
     * Sets the Rhino context factory to use. If not set, the global context
     * factory returned by {@link ContextFactory#getGlobal()} will be used.
     * Note that this feature (customized context factory in the interceptor) 
     * requires at least Rhino 1.6R3.
     * @param contextFactory
     */
    public void setContextFactory(ContextFactory contextFactory)
    {
        this.contextFactory = contextFactory;
    }
    
    public boolean preHandle(HttpServletRequest request,
            HttpServletResponse response, Object handler) throws Exception
    {
        Context cx;
        if(contextFactory == null)
        {
            cx = Context.enter();
        }
        else
        {
            cx = contextFactory.enterContext();
        }
        if(cx.getOptimizationLevel() != -1)
        {
            cx.setOptimizationLevel(-1);
        }
        return true;
    }
    
    public void afterCompletion(HttpServletRequest request,
            HttpServletResponse response, Object handler, Exception ex)
            throws Exception
    {
        Context.exit();
    }
    
    public void postHandle(HttpServletRequest request,
            HttpServletResponse response, Object handler,
            ModelAndView modelAndView) throws Exception
    {
    }
}
