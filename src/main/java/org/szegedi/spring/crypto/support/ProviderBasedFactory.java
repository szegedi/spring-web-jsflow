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
package org.szegedi.spring.crypto.support;

import org.springframework.beans.factory.config.AbstractFactoryBean;

/**
 * Base class for all factory beans that can take a security provider name.
 * @author Attila Szegedi
 * @version $Id$
 */
public abstract class ProviderBasedFactory extends AbstractFactoryBean
{
    protected String provider;
    
    /**
     * Sets the name of the security provider. If not set, uses the default
     * security provider.
     * @param provider the name of the security provider or null for the default
     * provider.
     */
    public void setProvider(String provider)
    {
        this.provider = provider;
    }
}
