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
package org.szegedi.spring.beans.factory;

import java.util.Map;

import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;

/**
 * An extension to the beans factory utils in Spring.
 * @author Attila Szegedi
 * @version $Id: $
 */
public class BeanFactoryUtilsEx
{
    /**
     * Returns a bean of specified type in the specified bean factory or its 
     * ancestors. In contrast with Spring's BeanFactoryUtils, doesn't throw an
     * exception if no bean is found, but rather returns null.
     * @param lbf the bean factory to look for the bean
     * @param type the expected type of the bean
     * @return the bean - if exactly one is defined. null - if none is defined
     * @throws NoSuchBeanDefinitionException if more than one bean is defined
     */
    public static Object beanOfTypeIncludingAncestors(ListableBeanFactory lbf, Class type)
    {
        Map map = org.springframework.beans.factory.BeanFactoryUtils.beansOfTypeIncludingAncestors(lbf, type);
        switch(map.size())
        {
            case 0:
            {
                return null;
            }
            case 1:
            {
                return map.values().iterator().next();
            }
            default:
            {
                throw new NoSuchBeanDefinitionException(
                        "More than one bean of type " + type.getName()  + 
                        " found: " + map.keySet()); 
            }
        }
    }

}
